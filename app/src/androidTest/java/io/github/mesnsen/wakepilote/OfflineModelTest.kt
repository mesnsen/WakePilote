package io.github.mesnsen.wakepilote

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class OfflineModelTest {
    @Test fun packagedModelLoadsAndProcessesWithoutCameraOrNetwork() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bytes=context.assets.open("face_landmarker.task").use { it.readBytes() }
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",digest)
        val detector=FaceLandmarker.createFromOptions(context,FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.IMAGE).setOutputFaceBlendshapes(true).setOutputFacialTransformationMatrixes(true).build())
        val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val input=BitmapImageBuilder(bitmap).build()
        try { assertTrue(detector.detect(input).faceLandmarks().isEmpty()) }
        finally { input.close(); bitmap.recycle(); detector.close() }
    }
    @Test fun distributedAppDoesNotRequestNetworkLocationOrMicrophone() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val permissions=context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        listOf(Manifest.permission.INTERNET,Manifest.permission.RECORD_AUDIO,Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION).forEach { assertFalse("Unexpected permission: $it",it in permissions) }
    }
}
