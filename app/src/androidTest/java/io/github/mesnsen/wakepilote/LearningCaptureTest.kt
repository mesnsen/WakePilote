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
    @Test fun countdownCaptureCorrectionAndDocumentExportWorkAndLeavingCancelsNextCapture() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val output=File(app.cacheDir,"learning-export-test.json")
        lateinit var vm:WakeViewModel
        activity.scenario.onActivity { vm=ViewModelProvider(it)[WakeViewModel::class.java] }
        await { !vm.learning.value.loading && vm.loaded.value }
        try {
            activity.scenario.onActivity {
                SessionHub.mutable.value=SessionUi(preparing=true)
                vm.startCapture(TrainingLabel.EYES_OPEN)
            }
            await { vm.learning.value.stage==LearningStage.CAPTURING }
            repeat(40) {
                activity.scenario.onActivity {
                    SessionHub.mutableSamples.tryEmit(CameraSample(
                        Observation(SystemClock.elapsedRealtime(),leftEar=.28,rightEar=.28,leftBlink=.05,rightBlink=.05),
                        VisionMetrics(20,16.0),1f,null))
                }
                Thread.sleep(60)
            }
            await { vm.learning.value.stage==LearningStage.REVIEW }
            assertEquals(TrainingLabel.EYES_OPEN,vm.learning.value.draft!!.predicted)
            activity.scenario.onActivity { vm.validateDraft(TrainingLabel.EYES_CLOSED,true) }
            await { !vm.learning.value.saving && vm.learning.value.draft==null }
            assertEquals(TrainingLabel.EYES_CLOSED,vm.learning.value.clips.last().actual)
            activity.scenario.onActivity { vm.exportLearning(Uri.fromFile(output)) }
            await { !vm.learning.value.saving }
            val root=JSONObject(output.readText())
            val clips=root.getJSONArray("clips")
            val last=clips.getJSONObject(clips.length()-1)
            assertEquals("EYES_CLOSED",last.getString("actual"))
            assertEquals("EYES_OPEN",last.getString("predicted"))
            assertTrue(last.getJSONArray("frames").length()>=15)
            val before=vm.learning.value.clips.size
            activity.scenario.onActivity {
                vm.startCapture(TrainingLabel.YAWN)
                SessionHub.mutable.value=SessionUi()
            }
            await { vm.learning.value.stage==LearningStage.IDLE }
            assertEquals(before,vm.learning.value.clips.size)
        } finally {
            activity.scenario.onActivity { vm.cancelCapture();SessionHub.mutable.value=SessionUi() }
            runBlocking { LearningRepository(app).clear() }
            output.delete()
        }
    }
}
