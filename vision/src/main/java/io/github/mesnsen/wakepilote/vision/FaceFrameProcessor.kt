package io.github.mesnsen.wakepilote.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.mesnsen.wakepilote.core.Observation
import io.github.mesnsen.wakepilote.core.Quality
import io.github.mesnsen.wakepilote.core.FaceFeatures
import io.github.mesnsen.wakepilote.core.FeaturePoint
import kotlin.math.*

data class ProcessedFaceFrame(val observation:Observation, val landmarks:FaceLandmarks?,val features:FaceFeatures?=null)

/** Serial processor. Takes ownership of the upright bitmap and releases it AFTER pixel analysis. */
class FaceFrameProcessor(private val infer:(MPImage,Long)->FaceLandmarkerResult) {
    private var previousCenter:Pair<Double,Double>?=null
    private var previousWidth=0.0
    private var ambiguousUntil=0L

    fun process(bitmap:Bitmap,time:Long,uprightToSensor:Matrix?,collectFeatures:Boolean=false):ProcessedFaceFrame {
        val input=BitmapImageBuilder(bitmap).build()
        return try {
            val result=infer(input,time)
            val observation=extract(result,bitmap,time)
            val faces=if(uprightToSensor==null) emptyList() else result.faceLandmarks().map { points ->
                val xy=FloatArray(points.size*2) { i ->
                    if(i%2==0) points[i/2].x()*bitmap.width else points[i/2].y()*bitmap.height
                }
                uprightToSensor.mapPoints(xy)
                List(points.size) { i -> FacePoint(xy[i*2],xy[i*2+1]) }
            }.filter { points -> points.all { it.x.isFinite() && it.y.isFinite() } }
            val raw=result.faceLandmarks().singleOrNull()
            val features=if(!collectFeatures || raw==null) null else FaceFeatures(bitmap.width,bitmap.height,
                FeatureIndices.filter { it<raw.size }.map { i -> raw[i].let { FeaturePoint(i,it.x(),it.y(),it.z()) } }
                    .filter { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() },
                result.faceBlendshapes().orElse(emptyList()).firstOrNull().orEmpty()
                    .filter { it.score().isFinite() }.associate { it.categoryName() to it.score() })
            ProcessedFaceFrame(observation,if(faces.isEmpty()) null else FaceLandmarks(time,faces),features)
        } finally {
            // BitmapImageContainer.close() recycles this bitmap, including on an inference error.
            input.close()
        }
    }

    private fun extract(result:FaceLandmarkerResult, bitmap:Bitmap, time:Long):Observation {
        val faces=result.faceLandmarks()
        if(faces.isEmpty()) { previousCenter=null; return Observation(time,Quality.NO_FACE) }
        if(faces.size!=1) { previousCenter=null; return Observation(time,Quality.AMBIGUOUS_FACE) }
        val points=faces[0]
        if(points.size<468 || points.any { !it.x().isFinite() || !it.y().isFinite() }) return Observation(time,Quality.GEOMETRY)
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
        var quality=if(center.first !in .18.. .82 || center.second !in .15.. .85 || width*bitmap.width<100 || width>.95)
            Quality.GEOMETRY else Quality.GOOD
        if(quality==Quality.GOOD) previousCenter?.let { previous ->
            if(hypot(center.first-previous.first,center.second-previous.second)>.22 ||
                (previousWidth>0 && width/previousWidth !in .55..1.8)) ambiguousUntil=time+600
        }
        previousCenter=center; previousWidth=width
        if(time<ambiguousUntil && quality==Quality.GOOD) quality=Quality.AMBIGUOUS_FACE
        var brightness=0.0; var count=0
        for(gx in 1..8) for(gy in 1..8) {
            val px=((minX+(maxX-minX)*gx/9)*bitmap.width).toInt().coerceIn(0,bitmap.width-1)
            val py=((minY+(maxY-minY)*gy/9)*bitmap.height).toInt().coerceIn(0,bitmap.height-1)
            val color=bitmap.getPixel(px,py)
            brightness+=.2126*((color shr 16) and 255)+.7152*((color shr 8) and 255)+.0722*(color and 255); count++
        }
        if((brightness/count<22 || brightness/count>245) && quality==Quality.GOOD) quality=Quality.POOR_LIGHT
        val blend=result.faceBlendshapes().orElse(emptyList()).firstOrNull()?.associate { it.categoryName() to it.score().toDouble() }.orEmpty()
        val matrix=result.facialTransformationMatrixes().orElse(emptyList()).firstOrNull()
        val pitch=matrix?.let { Math.toDegrees(atan2(it[9].toDouble(),it[10].toDouble())) }
        val yaw=matrix?.let { Math.toDegrees(atan2(-it[8].toDouble(),hypot(it[9].toDouble(),it[10].toDouble()))) }
        // Pose estimates are experimental; preserve UNKNOWN rather than infer closed eyes at extreme angles.
        if(((pitch!=null && abs(pitch)>30) || (yaw!=null && abs(yaw)>35)) && quality==Quality.GOOD) quality=Quality.POSE
        // MediaPipe subject-left is image-right. Analyze unmirrored, mirror only PreviewView.
        return Observation(time,quality,
            ear(362,385,387,263,373,380),ear(33,160,158,133,153,144),
            blend["eyeBlinkLeft"],blend["eyeBlinkRight"],d(13,14)/max(1.0,d(61,291)),blend["jawOpen"],pitch,yaw,
            brightness/count,width,maxY-minY)
    }

    companion object {
        // Eye/lip geometry, face bounds, brows and irises. No image pixels are exported.
        val FeatureIndices=listOf(33,160,158,133,153,144,362,385,387,263,373,380,
            61,291,13,14,1,4,10,152,234,454,70,63,105,336,296,300,468,469,470,471,472,473,474,475,476,477)
    }
}
