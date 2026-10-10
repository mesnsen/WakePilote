package io.github.mesnsen.wakepilote

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.data.LearningRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LearningStorageTest {
    private fun temporary()=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"learning-${UUID.randomUUID()}.json")
    private fun clip(id:String)=TrainingClip(id,123456,TrainingLabel.YAWN,TrainingDecision.CORRECTED,
        TrainingDraft(TrainingLabel.YAWN,TrainingLabel.UNKNOWN,Settings(),3000,listOf(
            TrainingFrame(Observation(100,Quality.NO_FACE),40,1f,null,EyeState.UNKNOWN,EyeState.UNKNOWN),
            TrainingFrame(Observation(200,leftEar=.28,rightEar=.29,leftBlink=.1,mouthRatio=.8),35,1.5f,
                FaceFeatures(480,640,listOf(FeaturePoint(33,.3f,.4f,-.1f)),mapOf("jawOpen" to .8f)),EyeState.OPEN,EyeState.OPEN))))

    @Test fun correctedLabelsAndRawFeaturesSurviveRestartAndExport()=runBlocking {
        val file=temporary()
        try {
            LearningRepository(file).add(clip("one"))
            val reopened=LearningRepository(file)
            val c=reopened.load().single()
            assertEquals(TrainingLabel.YAWN,c.actual)
            assertEquals(TrainingLabel.UNKNOWN,c.draft.predicted)
            assertTrue(c.draft.frames.first().observation.leftEar.isNaN())
            assertEquals(33,c.draft.frames.last().features!!.points.single().index)
            val json=reopened.export("0.1.2-test")
            val root=JSONObject(json)
            assertEquals(1,root.getInt("schemaVersion"))
            val saved=root.getJSONArray("clips").getJSONObject(0)
            assertEquals("CORRECTED",saved.getString("decision"))
            assertTrue(saved.getJSONArray("frames").getJSONObject(0).isNull("leftEar"))
            assertFalse(json.contains("NaN"))
            assertFalse(root.has("images"))
            assertFalse(root.getJSONObject("device").has("serial"))
            assertEquals("0.1.2-test",root.getString("appVersion"))
        } finally { file.delete() }
    }
    @Test fun unreadableStoreIsReportedAndNeverOverwritten()=runBlocking {
        val file=temporary(); file.writeText("damaged-but-preserved")
        try {
            val repository=LearningRepository(file)
            assertTrue(runCatching { repository.load() }.isFailure)
            assertTrue(runCatching { repository.add(clip("new")) }.isFailure)
            assertEquals("damaged-but-preserved",file.readText())
        } finally { file.delete() }
    }
    @Test fun deletingAnExampleAndResettingPersistWithoutDeletingExportedData()=runBlocking {
        val file=temporary()
        try {
            val r=LearningRepository(file)
            r.add(clip("one"));r.add(clip("two"))
            val exported=r.export("test")
            r.remove("one")
            assertEquals(listOf("two"),LearningRepository(file).load().map { it.id })
            r.clear()
            assertTrue(LearningRepository(file).load().isEmpty())
            assertEquals(2,JSONObject(exported).getJSONArray("clips").length())
        } finally { file.delete() }
    }
}
