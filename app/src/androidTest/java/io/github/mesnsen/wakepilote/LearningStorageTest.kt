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
            assertEquals(2,root.getInt("schemaVersion"))
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
    @Test fun positiveAndNegativeTargetsSurviveRestartWithoutInventingActualClass()=runBlocking {
        val file=temporary()
        try {
            val draft=clip("legacy").draft.copy(expected=TrainingLabel.HEAD_NOD)
            val repository=LearningRepository(file)
            repository.add(TrainingClip("yes",123,null,null,draft,TrainingExample(TrainingLabel.HEAD_NOD,true)))
            repository.add(TrainingClip("no",124,null,null,draft,TrainingExample(TrainingLabel.HEAD_NOD,false)))
            val loaded=LearningRepository(file).load()
            assertEquals(listOf(true,false),loaded.map { it.example!!.matchesTarget })
            assertTrue(loaded.all { it.target==TrainingLabel.HEAD_NOD && it.actual==null && it.decision==null })
            val saved=JSONObject(repository.export("0.1.3-test")).getJSONArray("clips").getJSONObject(1)
            assertEquals("TARGET_EXAMPLE",saved.getString("annotationMode"))
            assertEquals("HEAD_NOD",saved.getJSONObject("example").getString("target"))
            assertFalse(saved.getJSONObject("example").getBoolean("matchesTarget"))
            assertTrue(saved.isNull("actual"))
        } finally { file.delete() }
    }
    @Test fun versionOneIsMigratedWithoutTurningOldCorrectionsIntoNegativeExamples()=runBlocking {
        val file=temporary()
        try {
            val legacy=LearningRepository.encodeClip(clip("old"))
            legacy.remove("annotationMode"); legacy.remove("example")
            file.writeText(JSONObject().put("schemaVersion",1).put("clips",org.json.JSONArray().put(legacy)).toString())
            val repository=LearningRepository(file)
            assertNull(repository.load().single().example)
            val draft=clip("new").draft
            repository.add(TrainingClip("new",124,null,null,draft,TrainingExample(TrainingLabel.YAWN,false)))
            val reopened=LearningRepository(file).load()
            assertEquals(2,reopened.size)
            assertEquals(TrainingLabel.YAWN,reopened.first().actual)
            assertEquals(TrainingDecision.CORRECTED,reopened.first().decision)
            assertNull(reopened.first().example)
            assertEquals(false,reopened.last().example!!.matchesTarget)
            assertEquals(2,JSONObject(file.readText()).getInt("schemaVersion"))
        } finally { file.delete() }
    }

}
