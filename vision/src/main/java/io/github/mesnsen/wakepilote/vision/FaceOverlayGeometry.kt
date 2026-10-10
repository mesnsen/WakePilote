package io.github.mesnsen.wakepilote.vision

import android.graphics.Matrix
import android.graphics.RectF
import io.github.mesnsen.wakepilote.core.Quality

/** Only transient coordinates, in camera sensor space. No pixels are retained by the UI. */
data class FacePoint(val x:Float,val y:Float)
data class FaceLandmarks(val atMs:Long,val faces:List<List<FacePoint>>)
data class VisionFailure(val quality:Quality,val message:String,val diagnostic:String)

object FaceOverlayGeometry {
    fun uprightToSensor(width:Int,height:Int,rotationDegrees:Int,sensorToBuffer:Matrix):Matrix? {
        // Bitmap.createBitmap rotates the complete buffer and translates the bounds to (0,0).
        val rotation=Matrix().apply { setRotate(rotationDegrees.toFloat()) }
        val bounds=RectF(0f,0f,width.toFloat(),height.toFloat())
        rotation.mapRect(bounds)
        rotation.postTranslate(-bounds.left,-bounds.top)
        val sensorToUpright=Matrix(sensorToBuffer).apply { postConcat(rotation) }
        return Matrix().takeIf { sensorToUpright.invert(it) }
    }

    fun mapToPreview(face:List<FacePoint>,sensorToView:Matrix):List<FacePoint> {
        val xy=FloatArray(face.size*2) { i -> if(i%2==0) face[i/2].x else face[i/2].y }
        sensorToView.mapPoints(xy)
        return List(face.size) { i -> FacePoint(xy[i*2],xy[i*2+1]) }
    }
}
