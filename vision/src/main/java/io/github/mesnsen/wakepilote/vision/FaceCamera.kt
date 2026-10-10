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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.mesnsen.wakepilote.core.Observation
import io.github.mesnsen.wakepilote.core.Quality
import java.util.concurrent.Executors
import kotlin.math.*

data class VisionMetrics(val processingMs:Long=0,val observationsPerSecond:Double=0.0)

/** CameraX's single analyzer executor bounds work and owns all MediaPipe buffers. */
class FaceCamera(private val context:Context, private val owner:LifecycleOwner,
    private val onObservation:(Observation,VisionMetrics)->Unit,
    private val onError:(String)->Unit) : AutoCloseable {
    private val executor=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var active=true
    @Volatile private var thermalPaused=false
    @Volatile var minIntervalMs=66L
    private var provider:ProcessCameraProvider?=null
    private var detector:FaceLandmarker?=null
    private var surface:Preview.SurfaceProvider?=null
    private var analysis:ImageAnalysis?=null
    private var observedCamera:LiveData<CameraState>?=null
    private var lastTime=-1L
    private var previousCenter:Pair<Double,Double>?=null
    private var previousWidth=0.0
    private var ambiguousUntil=0L
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
            }.onSuccess {
                if(active) main.post {
                    val future=ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        if(active) runCatching { provider=future.get(); bind() }.onFailure { fail(it) }
                    },ContextCompat.getMainExecutor(context))
                }
            }.onFailure { fail(it) }
        }
    }

    fun setSurface(value:Preview.SurfaceProvider?) {
        if(surface===value) return
        surface=value
        if(active && provider!=null && !thermalPaused) runCatching { bind() }.onFailure { fail(it) }
    }

    fun pauseForThermal(paused:Boolean) {
        if(thermalPaused==paused) return
        thermalPaused=paused
        if(paused) { provider?.unbindAll(); onObservation(Observation(SystemClock.elapsedRealtime(),Quality.THERMAL),VisionMetrics()) }
        else if(active && provider!=null) runCatching { bind() }.onFailure { fail(it) }
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
            if(active && state.error!=null) onError("Caméra indisponible — vérifiez les autres applications")
        }
    }

    private fun analyze(image:ImageProxy) {
        var bitmap:Bitmap?=null
        var upright:Bitmap?=null
        try {
            val now=SystemClock.elapsedRealtime()
            val model=detector
            if(!active || thermalPaused || model==null || now-lastTime<minIntervalMs) return
            lastTime=now
            bitmap=image.toBitmap()
            val rotation=image.imageInfo.rotationDegrees
            upright=if(rotation==0) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply { postRotate(rotation.toFloat()) },true)
            val input=BitmapImageBuilder(upright).build()
            val result=try { model.detectForVideo(input,now) } finally { input.close() }
            val observation=extract(result,upright,now)
            val elapsed=SystemClock.elapsedRealtime()-now
            if(rateStarted==0L) rateStarted=now
            rateFrames++
            val rate=rateFrames*1000.0/max(1,now-rateStarted)
            if(now-rateStarted>5000) { rateStarted=now; rateFrames=0 }
            if(active) main.post { if(active) onObservation(observation,VisionMetrics(elapsed,rate)) }
        } catch(e:Exception) { fail(e) }
        finally {
            if(upright!==bitmap) upright?.recycle()
            bitmap?.recycle()
            image.close()
        }
    }

    private fun extract(result:FaceLandmarkerResult, bitmap:Bitmap, time:Long):Observation {
        val faces=result.faceLandmarks()
        if(faces.isEmpty()) { previousCenter=null; return Observation(time,Quality.NO_FACE) }
        if(faces.size!=1) { previousCenter=null; return Observation(time,Quality.AMBIGUOUS_FACE) }
        val points=faces[0]
        if(points.size<468) return Observation(time,Quality.GEOMETRY)
        fun x(i:Int)=points[i].x().toDouble()*bitmap.width
        fun y(i:Int)=points[i].y().toDouble()*bitmap.height
        fun d(a:Int,b:Int)=hypot(x(a)-x(b),y(a)-y(b))
        fun ear(a:Int,b:Int,c:Int,corner:Int,e:Int,f:Int):Double {
            val width=d(a,corner)
            return if(width<8) Double.NaN else (d(b,f)+d(c,e))/(2*width)
        }
        val minX=points.minOf { it.x() }.toDouble(); val maxX=points.maxOf { it.x() }.toDouble()
        val minY=points.minOf { it.y() }.toDouble(); val maxY=points.maxOf { it.y() }.toDouble()
        val center=(minX+maxX)/2 to (minY+maxY)/2
        val width=maxX-minX
        if(center.first !in .18.. .82 || center.second !in .15.. .85 || width*bitmap.width<100 || width>.95) return Observation(time,Quality.GEOMETRY)
        previousCenter?.let { previous ->
            if(hypot(center.first-previous.first,center.second-previous.second)>.22 ||
                (previousWidth>0 && width/previousWidth !in .55..1.8)) ambiguousUntil=time+600
        }
        previousCenter=center; previousWidth=width
        if(time<ambiguousUntil) return Observation(time,Quality.AMBIGUOUS_FACE)
        var brightness=0.0; var count=0
        for(gx in 1..8) for(gy in 1..8) {
            val px=((minX+(maxX-minX)*gx/9)*bitmap.width).toInt().coerceIn(0,bitmap.width-1)
            val py=((minY+(maxY-minY)*gy/9)*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
            val color=bitmap.getPixel(px,py)
            brightness+=.2126*((color shr 16) and 255)+.7152*((color shr 8) and 255)+.0722*(color and 255); count++
        }
        if(brightness/count<22 || brightness/count>245) return Observation(time,Quality.POOR_LIGHT)
        val blend=result.faceBlendshapes().orElse(emptyList()).firstOrNull()?.associate { it.categoryName() to it.score().toDouble() }.orEmpty()
        val matrix=result.facialTransformationMatrixes().orElse(emptyList()).firstOrNull()
        val pitch=matrix?.let { Math.toDegrees(atan2(it[9].toDouble(),it[10].toDouble())) }
        val yaw=matrix?.let { Math.toDegrees(atan2(-it[8].toDouble(),hypot(it[9].toDouble(),it[10].toDouble()))) }
        // Pose estimates are experimental; preserve UNKNOWN rather than infer closed eyes at extreme angles.
        if((pitch!=null && abs(pitch)>30) || (yaw!=null && abs(yaw)>35)) return Observation(time,Quality.POSE)
        // MediaPipe subject-left is image-right. Analyze unmirrored, mirror only PreviewView.
        return Observation(time,Quality.GOOD,
            ear(362,385,387,263,373,380),ear(33,160,158,133,153,144),
            blend["eyeBlinkLeft"],blend["eyeBlinkRight"],d(13,14)/max(1.0,d(61,291)),blend["jawOpen"],pitch,yaw)
    }
    private fun fail(e:Throwable) { if(active) main.post { if(active) onError("Analyse indisponible (${e.javaClass.simpleName})") } }
    override fun close() {
        active=false; surface=null
        observedCamera?.removeObservers(owner)
        analysis?.clearAnalyzer(); provider?.unbindAll()
        executor.execute { runCatching { detector?.close() }; detector=null }
        executor.shutdown()
    }
}
