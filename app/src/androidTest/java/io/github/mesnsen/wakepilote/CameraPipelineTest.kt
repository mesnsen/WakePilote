package io.github.mesnsen.wakepilote

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import io.github.mesnsen.wakepilote.core.Quality
import io.github.mesnsen.wakepilote.vision.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Optional
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class CameraPipelineTest {
    @Test fun detectedFaceCanBeMeasuredBeforeItsPixelsAreReleased() {
        // The previous no-face smoke test never reached getPixel(). A detected face must do so.
        val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val processor=FaceFrameProcessor { _,time -> result(time,listOf(face())) }
        val frame=processor.process(bitmap,1000,Matrix())
        assertEquals(Quality.GOOD,frame.observation.quality)
        assertTrue(frame.observation.leftEar.isFinite())
        assertEquals(478,frame.landmarks!!.faces.single().size)
        assertTrue("Pixels must be released after measurement, not before it",bitmap.isRecycled)
    }

    @Test fun failedInferenceStillReleasesTheBitmap() {
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        val processor=FaceFrameProcessor { _,_ -> throw IllegalStateException("inference failed") }
        assertThrows(IllegalStateException::class.java) { processor.process(bitmap,1000,Matrix()) }
        assertTrue(bitmap.isRecycled)
    }

    @Test fun lostFaceRemovesThePreviousLandmarks() {
        val processor=FaceFrameProcessor { _,time -> result(time,if(time==1000L) listOf(face()) else emptyList()) }
        fun frame(time:Long)=processor.process(Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.LTGRAY) },time,Matrix())
        assertNotNull(frame(1000).landmarks)
        val lost=frame(1100)
        assertEquals(Quality.NO_FACE,lost.observation.quality)
        assertNull(lost.landmarks)
    }

    @Test fun pointsAlignAfterAllRotationsCropAndFrontCameraMirror() {
        val sensorToBuffer=Matrix().apply { setScale(.5f,.5f); postTranslate(-10f,-20f) }
        // One physical point at sensor (340,280), buffer (160,120), seen at four rotations.
        val rotated=listOf(0 to floatArrayOf(160f,120f),90 to floatArrayOf(360f,160f),
            180 to floatArrayOf(480f,360f),270 to floatArrayOf(120f,480f))
        for((rotation,xy) in rotated) {
            val inverse=FaceOverlayGeometry.uprightToSensor(640,480,rotation,sensorToBuffer)!!
            inverse.mapPoints(xy)
            assertArrayEquals("rotation $rotation",floatArrayOf(340f,280f),xy,.01f)
            // Mirrored portrait preview, with a nonzero horizontal letterbox offset.
            val sensorToView=Matrix().apply { setValues(floatArrayOf(0f,.5f,100f,.5f,0f,30f,0f,0f,1f)) }
            val projected=FaceOverlayGeometry.mapToPreview(listOf(FacePoint(xy[0],xy[1])),sensorToView).single()
            assertEquals(240f,projected.x,.01f)
            assertEquals(200f,projected.y,.01f)
        }
    }

    private fun result(time:Long,faces:List<List<NormalizedLandmark>>)=object:FaceLandmarkerResult() {
        override fun timestampMs()=time
        override fun faceLandmarks()=faces
        override fun faceBlendshapes():Optional<List<List<Category>>> = Optional.empty()
        override fun facialTransformationMatrixes():Optional<List<FloatArray>> = Optional.empty()
    }

    private fun face():List<NormalizedLandmark> {
        // Synthetic landmarks, no person's photo or biometric sample is stored in the repository.
        val points=MutableList(478) { i ->
            val angle=2*PI*i/478
            NormalizedLandmark.create((.5+.25*cos(angle)).toFloat(),(.5+.3*sin(angle)).toFloat(),0f)
        }
        fun point(i:Int,x:Float,y:Float) { points[i]=NormalizedLandmark.create(x,y,0f) }
        point(33,.30f,.4f); point(133,.44f,.4f); point(160,.34f,.38f); point(158,.40f,.38f)
        point(153,.40f,.42f); point(144,.34f,.42f)
        point(362,.56f,.4f); point(263,.70f,.4f); point(385,.60f,.38f); point(387,.66f,.38f)
        point(373,.66f,.42f); point(380,.60f,.42f)
        point(61,.40f,.65f); point(291,.60f,.65f); point(13,.50f,.64f); point(14,.50f,.66f)
        return points
    }
}
