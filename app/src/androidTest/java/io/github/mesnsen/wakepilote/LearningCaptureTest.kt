package io.github.mesnsen.wakepilote

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.data.LearningRepository
import io.github.mesnsen.wakepilote.vision.VisionMetrics
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LearningCaptureTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    private fun await(condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+12000
        while(!condition()) { check(SystemClock.elapsedRealtime()<end) { "Learning flow timed out" }; Thread.sleep(25) }
    }
    private fun measures() {
        repeat(16) { n ->
            activity.scenario.onActivity {
                SessionHub.mutableSamples.tryEmit(CameraSample(
                    Observation(SystemClock.elapsedRealtime(),leftEar=.28,rightEar=.28,leftBlink=.05,rightBlink=.05,
                        pitch=if(n in 5..10) 25.0 else 0.0),VisionMetrics(20,16.0),1f,
                    FaceFeatures(480,640,listOf(FeaturePoint(33,.3f,.4f,0f)),emptyMap())))
            }
            Thread.sleep(60)
        }
    }
    @Test fun directLabelsPersistExportAndCannotReuseOldMeasuresAfterTapTargetChangeOrLeaving() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val output=File(app.cacheDir,"learning-export-test.json")
        lateinit var vm:WakeViewModel
        activity.scenario.onActivity { vm=ViewModelProvider(it)[WakeViewModel::class.java] }
        await { !vm.learning.value.loading && vm.loaded.value }
        try {
            activity.scenario.onActivity {
                SessionHub.mutable.value=SessionUi(preparing=true)
                vm.setLearningActive(true)
                vm.selectLearningTarget(TrainingLabel.HEAD_NOD)
            }
            measures()
            await { vm.learning.value.ready }
            val before=vm.learning.value.clips.size
            activity.scenario.onActivity { vm.saveLearningExample(true); vm.saveLearningExample(false) }
            await { !vm.learning.value.saving }
            assertEquals(before+1,vm.learning.value.clips.size)
            assertEquals(TrainingExample(TrainingLabel.HEAD_NOD,true),vm.learning.value.clips.last().example)
            assertFalse(vm.learning.value.ready)
            measures()
            await { vm.learning.value.ready }
            activity.scenario.onActivity { vm.saveLearningExample(false) }
            await { !vm.learning.value.saving }
            assertEquals(before+2,vm.learning.value.clips.size)
            assertEquals(TrainingExample(TrainingLabel.HEAD_NOD,false),vm.learning.value.clips.last().example)
            assertNull(vm.learning.value.clips.last().actual)
            activity.scenario.onActivity { vm.exportLearning(Uri.fromFile(output)) }
            await { !vm.learning.value.saving }
            val root=JSONObject(output.readText())
            val clips=root.getJSONArray("clips")
            val last=clips.getJSONObject(clips.length()-1)
            assertEquals(2,root.getInt("schemaVersion"))
            assertEquals("HEAD_NOD",last.getJSONObject("example").getString("target"))
            assertFalse(last.getJSONObject("example").getBoolean("matchesTarget"))
            assertTrue(last.getJSONArray("frames").length()>=8)
            assertTrue(vm.learning.value.clips.last().draft.frames.any { it.observation.pitch==25.0 })
            measures()
            await { vm.learning.value.ready }
            activity.scenario.onActivity {
                vm.selectLearningTarget(TrainingLabel.YAWN)
                vm.saveLearningExample(true)
            }
            assertFalse(vm.learning.value.ready)
            assertEquals(before+2,vm.learning.value.clips.size)
            measures()
            await { vm.learning.value.ready }
            activity.scenario.onActivity { vm.setLearningActive(false); vm.saveLearningExample(true) }
            assertFalse(vm.learning.value.ready)
            assertEquals(before+2,vm.learning.value.clips.size)
            activity.scenario.onActivity { vm.setLearningActive(true) }
            measures()
            await { vm.learning.value.ready }
            activity.scenario.onActivity { SessionHub.mutable.value=SessionUi() }
            await { !vm.learning.value.ready }
            activity.scenario.onActivity { vm.saveLearningExample(true) }
            assertEquals(before+2,vm.learning.value.clips.size)
        } finally {
            activity.scenario.onActivity { vm.setLearningActive(false);SessionHub.mutable.value=SessionUi() }
            runBlocking { LearningRepository(app).clear() }
            output.delete()
        }
    }
}
