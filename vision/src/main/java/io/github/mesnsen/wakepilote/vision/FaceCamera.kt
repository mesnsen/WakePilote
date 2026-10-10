package io.github.mesnsen.wakepilote.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import io.github.mesnsen.wakepilote.core.Observation
import io.github.mesnsen.wakepilote.core.Quality
import java.util.concurrent.Executors
import kotlin.math.*

data class VisionMetrics(val processingMs:Long=0,val observationsPerSecond:Double=0.0)

/** CameraX's single analyzer executor bounds work and owns all MediaPipe buffers. */
class FaceCamera(private val context:Context, private val owner:LifecycleOwner,
    private val onObservation:(Observation,VisionMetrics,FaceLandmarks?)->Unit,
    private val onError:(VisionFailure)->Unit) : AutoCloseable {
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var active=true
    @Volatile private var thermalPaused=false
    @Volatile var minIntervalMs=66L
    private var provider:ProcessCameraProvider?=null
    private var detector:FaceLandmarker?=null
    private var processor:FaceFrameProcessor?=null
    private var surface:Preview.SurfaceProvider?=null
    private var analysis:ImageAnalysis?=null
    private var observedCamera:LiveData<CameraState>?=null
    private var lastTime=-1L
    private var rateStarted=0L
    private var rateFrames=0

    fun start() {
        executor.execute {
            runCatching {
                detector=FaceLandmarker.createFromOptions(context, FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO).setNumFaces(2)
                    .setMinFaceDetectionConfidence(.6f).setMinFacePresenceConfidence(.6f).setMinTrackingConfidence(.6f)
                    .setOutputFaceBlendshapes(true).setOutputFacialTransformationMatrixes(true).build())
                processor=FaceFrameProcessor { input,time -> checkNotNull(detector).detectForVideo(input,time) }
            }.onSuccess {
                if(active) main.post {
                    val future=ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        if(active) runCatching { provider=future.get(); bind() }.onFailure { fail(it,Quality.CAMERA_ERROR,"Caméra indisponible") }
                    },ContextCompat.getMainExecutor(context))
                }
            }.onFailure { fail(it,Quality.ANALYSIS_ERROR,"Initialisation de l’analyse impossible") }
        }
    }

    fun setSurface(value:Preview.SurfaceProvider?) {
        if(surface===value) return
        surface=value
        if(active && provider!=null && !thermalPaused) runCatching { bind() }.onFailure { fail(it,Quality.CAMERA_ERROR,"Caméra indisponible") }
    }

    fun pauseForThermal(paused:Boolean) {
        if(thermalPaused==paused) return
        thermalPaused=paused
        if(paused) { provider?.unbindAll(); onObservation(Observation(SystemClock.elapsedRealtime(),Quality.THERMAL),VisionMetrics(),null) }
        else if(active && provider!=null) runCatching { bind() }.onFailure { fail(it,Quality.CAMERA_ERROR,"Caméra indisponible") }
    }

    private fun bind() {
        val p=provider ?: return
        if(!active || thermalPaused) return
        observedCamera?.removeObservers(owner)
        analysis?.clearAnalyzer()
        p.unbindAll()
        @Suppress("DEPRECATION")
        val a=ImageAnalysis.Builder().setTargetResolution(android.util.Size(640,480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis=a
        a.setAnalyzer(executor) { image -> analyze(image) }
        val useCases=mutableListOf<UseCase>(a)
        surface?.let { s -> useCases.add(Preview.Builder().build().also { it.setSurfaceProvider(s) }) }
        val camera=p.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,*useCases.toTypedArray())
        observedCamera=camera.cameraInfo.cameraState
        observedCamera!!.observe(owner) { state ->
            if(active && state.error!=null) onError(VisionFailure(Quality.CAMERA_ERROR,"Caméra indisponible — vérifiez les autres applications","CameraX ${state.error?.code}"))
        }
    }

    private fun analyze(image:ImageProxy) {
        var bitmap:Bitmap?=null
        var upright:Bitmap?=null
        try {
            val now=SystemClock.elapsedRealtime()
            val currentProcessor=processor
            if(!active || thermalPaused || currentProcessor==null || now-lastTime<minIntervalMs) return
            lastTime=now
            bitmap=image.toBitmap()
            val rotation=image.imageInfo.rotationDegrees
            upright=if(rotation==0) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
            val uprightToSensor=FaceOverlayGeometry.uprightToSensor(image.width,image.height,rotation,
                image.imageInfo.sensorToBufferTransformMatrix)
            val frame=currentProcessor.process(upright,now,uprightToSensor)
            val elapsed=SystemClock.elapsedRealtime()-now
            if(rateStarted==0L) rateStarted=now
            rateFrames++
            val rate=rateFrames*1000.0/max(1,now-rateStarted)
            if(now-rateStarted>5000) { rateStarted=now; rateFrames=0 }
            if(active) main.post { if(active) onObservation(frame.observation,VisionMetrics(elapsed,rate),frame.landmarks) }
        } catch(e:Exception) { fail(e,Quality.ANALYSIS_ERROR,"Analyse du visage interrompue") }
        finally {
            if(upright!==bitmap && upright?.isRecycled==false) upright.recycle()
            if(bitmap?.isRecycled==false) bitmap.recycle()
            image.close()
        }
    }

    private fun fail(e:Throwable,quality:Quality,message:String) {
        val detail="${e.javaClass.simpleName}: ${e.message.orEmpty().take(180)}"
        if(active) main.post { if(active) onError(VisionFailure(quality,message,detail)) }
    }
    override fun close() {
        active=false; surface=null
        observedCamera?.removeObservers(owner)
        analysis?.clearAnalyzer(); provider?.unbindAll()
        executor.execute { runCatching { detector?.close() }; detector=null; processor=null }
        executor.shutdown()
    }
}
