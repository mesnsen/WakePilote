package io.github.mesnsen.wakepilote.core

import org.junit.Assert.*
import org.junit.Test

class LearningTest {
    private fun observation(at:Long,closed:Boolean=false,yawn:Boolean=false,quality:Quality=Quality.GOOD)=
        Observation(at,quality,if(closed) .08 else .28,if(closed) .08 else .28,
            if(closed) .95 else .05,if(closed) .95 else .05,if(yawn) .8 else .1,if(yawn) .85 else .05)
    private fun capture(make:(Long)->Observation):TrainingDraft {
        val recorder=TrainingCapture(TrainingLabel.EYES_OPEN,Settings(),1000)
        for(at in 1000L until 4000L step 100) recorder.add(make(at),25,1.35f,null)
        return recorder.finish()!!
    }
    @Test fun summarizesOpenClosedAndYawnSequences() {
        assertEquals(TrainingLabel.EYES_OPEN,capture { observation(it) }.predicted)
        assertEquals(TrainingLabel.EYES_CLOSED,capture { observation(it,true) }.predicted)
        assertEquals(TrainingLabel.YAWN,capture { observation(it,yawn=true) }.predicted)
    }
    @Test fun blinkNeedsOpeningBeforeAndAfterClosure() {
        assertEquals(TrainingLabel.BLINK,capture { observation(it,closed=it in 1800L..2100L) }.predicted)
        assertNotEquals(TrainingLabel.BLINK,capture { observation(it,closed=it<1400) }.predicted)
    }
    @Test fun gapsAndInvalidFramesCannotInventSustainedMotion() {
        val recorder=TrainingCapture(TrainingLabel.YAWN,Settings(),1000)
        for(at in 1000L until 4000L step 600) recorder.add(observation(at,true,true),25,1f,null)
        val sparse=recorder.finish()!!
        assertEquals(TrainingLabel.UNKNOWN,sparse.predicted)
        assertEquals(600,sparse.largestGapMs)
        assertEquals(TrainingLabel.UNKNOWN,capture { observation(it,true,true,Quality.POOR_LIGHT) }.predicted)
    }
    @Test fun capturesOnlyTheWindowAndIgnoresDuplicateOrTooFastFrames() {
        val c=TrainingCapture(TrainingLabel.EYES_OPEN,Settings(),1000)
        for(at in 0L..5000L step 10) { c.add(observation(at),1,1f,null); c.add(observation(at),1,1f,null) }
        val first=c.finish()!!
        assertEquals(60,first.frames.size)
        assertEquals(0,first.frames.first().observation.atMs)
        assertEquals(2950,first.frames.last().observation.atMs)
        c.add(observation(5000),1,1f,null)
        assertEquals(60,first.frames.size)
        assertNull(TrainingCapture(TrainingLabel.EYES_OPEN,Settings(),1000).finish())
    }
    @Test fun rejectedDetectionPreservesUsefulFeaturesAndQuality() {
        val c=TrainingCapture(TrainingLabel.EYES_CLOSED,Settings(),1000)
        val raw=FaceFeatures(480,640,listOf(FeaturePoint(33,.2f,.3f,.01f)),mapOf("eyeBlinkLeft" to .9f))
        c.add(observation(1200,true,quality=Quality.POSE),80,2f,raw)
        val f=c.finish()!!.frames.single()
        assertEquals(Quality.POSE,f.observation.quality)
        assertEquals(.08,f.observation.leftEar,.0001)
        assertEquals(EyeState.UNKNOWN,f.left)
        assertEquals(raw,f.features)
        assertEquals(200,f.observation.atMs)
    }
    @Test fun correctionKeepsOriginalPredictionAndHumanGroundTruthSeparate() {
        val draft=capture { observation(it) }
        val c=TrainingClip("one",123,TrainingLabel.YAWN,TrainingDecision.CORRECTED,draft)
        assertEquals(TrainingLabel.EYES_OPEN,c.draft.predicted)
        assertEquals(TrainingLabel.YAWN,c.actual)
        assertThrows(IllegalArgumentException::class.java) { TrainingClip("bad",123,TrainingLabel.YAWN,TrainingDecision.CONFIRMED,draft) }
        assertThrows(IllegalArgumentException::class.java) { TrainingClip("unknown",123,TrainingLabel.UNKNOWN,TrainingDecision.CORRECTED,draft) }
    }
}
