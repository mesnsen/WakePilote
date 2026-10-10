package io.github.mesnsen.wakepilote.core

import org.junit.Assert.*
import org.junit.Test

class DrowsinessEngineTest {
    private fun eye(t: Long, closed: Boolean = false) = Observation(t, leftEar = if (closed) 0.06 else 0.28,
        rightEar = if (closed) 0.06 else 0.28, leftBlink = if (closed) 0.95 else 0.05, rightBlink = if (closed) 0.95 else 0.05)
    private fun feed(e: DrowsinessEngine, start: Long, end: Long, closed: Boolean = false, step: Long = 50): Snapshot {
        for (t in start..end step step) e.observe(eye(t, closed))
        return e.snapshot()
    }
    @Test fun U01_openEyesStayMonitoring() {
        val e = DrowsinessEngine(); e.start(0)
        val s = feed(e, 0, 125000)
        assertEquals(MonitorState.MONITORING, s.state); assertEquals(0.0, s.perclos!!, 0.00001); assertFalse(s.soundRequested)
    }
    @Test fun U02_shortBlinksDoNotAccumulate() {
        val e = DrowsinessEngine(); e.start(0)
        for (t in 0L..10000 step 50) e.observe(eye(t, t % 1000 < 200))
        assertEquals(0, e.snapshot().episode); assertFalse(e.snapshot().soundRequested)
    }
    @Test fun U03_urgentAtRealTimeThresholdWithoutPerclos() {
        val e = DrowsinessEngine(); e.start(0)
        feed(e, 0, 1450, true); assertFalse(e.snapshot().soundRequested)
        val s = e.observe(eye(1500, true))
        assertTrue(s.soundRequested); assertNull(s.perclos); assertEquals(MonitorState.ALERTING, s.state)
    }
    @Test fun U04_winkDoesNotTrigger() {
        val e = DrowsinessEngine(); e.start(0)
        for (t in 0L..5000 step 50) e.observe(eye(t).copy(leftEar = 0.05, leftBlink = 0.9))
        assertFalse(e.snapshot().soundRequested)
    }
    @Test fun U05_reopenRequiresTwoContinuousSeconds() {
        val e = DrowsinessEngine(); e.start(0); feed(e, 0, 1600, true)
        feed(e, 1650, 3600); assertTrue(e.snapshot().soundRequested)
        val s = e.observe(eye(3650)); assertFalse(s.soundRequested); assertEquals(MonitorState.RECOVERING, s.state)
    }
    @Test fun U06_manualStopKeepsMonitoring() {
        val e = DrowsinessEngine(); e.start(0); feed(e, 0, 1600, true)
        val s = e.acknowledge(1600); assertFalse(s.soundRequested); assertEquals(MonitorState.ALERTING, s.state)
        assertEquals(1600L, s.closedMs)
    }
    @Test fun U07_persistentCriticalResumesAfterFiveSeconds() {
        val e = DrowsinessEngine(); e.start(0); feed(e, 0, 1600, true); e.acknowledge(1600)
        feed(e, 1650, 6550, true); assertFalse(e.snapshot().soundRequested)
        assertTrue(e.observe(eye(6600, true)).soundRequested)
        assertTrue(e.observe(eye(6650)).soundRequested) // no premature stop when reopening begins
    }
    @Test fun U08_missingFaceIsNotClosed() {
        val e = DrowsinessEngine(); e.start(0)
        for (t in 0L..3000 step 100) e.observe(Observation(t, Quality.NO_FACE))
        assertEquals(MonitorState.MONITORING_UNAVAILABLE, e.snapshot().state); assertEquals(0, e.snapshot().episode)
    }
    @Test fun U09_shortGapDoesNotCountAsClosedTime() {
        val e = DrowsinessEngine(); e.start(0); feed(e, 0, 1000, true)
        e.observe(Observation(1050, Quality.NO_FACE)); e.observe(eye(1100, true))
        assertEquals(1000L, e.snapshot().closedMs)
        e.observe(eye(1150, true)); assertEquals(1050L, e.snapshot().closedMs)
    }
    @Test fun U10_frozenClosedFrameCannotTriggerUrgency() {
        val e = DrowsinessEngine(); e.start(0); feed(e, 0, 1000, true)
        for (t in 1100L..5000 step 100) e.tick(t)
        assertEquals(0, e.snapshot().episode); assertEquals(MonitorState.MONITORING_UNAVAILABLE, e.snapshot().state)
    }
    @Test fun U11_irregularIntervalsUseTimeAndIgnoreOldFrames() {
        val e = DrowsinessEngine(); e.start(0)
        listOf(0L, 100, 270, 400, 600, 800, 980, 1100, 1300, 1499).forEach { e.observe(eye(it, true)) }
        assertFalse(e.snapshot().soundRequested)
        assertTrue(e.observe(eye(1500, true)).soundRequested)
        val before = e.snapshot(); e.observe(eye(1400), 1510); assertEquals(before.closedMs, e.snapshot().closedMs)
    }
    @Test fun U12_yawningAloneCannotCauseUrgency() {
        val e = DrowsinessEngine(); e.start(0)
        for (t in 0L..70000 step 50) e.observe(eye(t).copy(mouthRatio = if (t % 5000 < 2000) .8 else .2, jawOpen = if (t % 5000 < 2000) .9 else .1))
        assertEquals(0, e.snapshot().episode)
    }
    @Test fun U13_perclosIsTimeWeightedAndLowCoverageIsUnavailable() {
        val e = DrowsinessEngine(); e.start(0)
        feed(e, 0, 19900)
        for (t in 20000L..40100 step 100) e.observe(Observation(t, Quality.NO_FACE))
        feed(e, 40200, 60000)
        assertNull(e.snapshot().perclos); assertTrue(e.snapshot().coverage < .75)
        val clean = DrowsinessEngine(); clean.start(0)
        for (t in 0L..60000 step 100) clean.observe(if (t < 10000) eye(t, true).copy(leftEar=.03,rightEar=.03) else eye(t))
        assertEquals(9900.0/59900.0, clean.snapshot().perclos!!, .0001)
    }
    @Test fun U14_conflictingSignalsAndHysteresisAreBounded() {
        val c = EyeClassifier(); assertEquals(EyeState.CLOSED, c.classify(.1,.28,.9,0))
        assertEquals(EyeState.CLOSED, c.classify(.17,.28,.5,100))
        assertEquals(EyeState.UNKNOWN, c.classify(.17,.28,.5,250))
        assertEquals(EyeState.UNKNOWN, c.classify(.05,.28,.1,300))
    }
    @Test fun U15_stoppedOrPreviousSessionFramesCannotAlert() {
        val e = DrowsinessEngine(); e.start(0); feed(e,0,1600,true); e.stop()
        assertEquals(MonitorState.INACTIVE,e.observe(eye(1700,true)).state)
        e.start(2000); e.observe(eye(1800,true),2000); assertEquals(0,e.snapshot().episode)
    }
    @Test fun U16_lostFaceDoesNotAutoStopExistingAlert() {
        val e = DrowsinessEngine(); e.start(0); feed(e,0,1600,true)
        for (t in 1700L..3500 step 100) e.observe(Observation(t,Quality.NO_FACE))
        assertTrue(e.snapshot().soundRequested); assertEquals(MonitorState.MONITORING_UNAVAILABLE,e.snapshot().state)
        e.acknowledge(3500); e.tick(10000); assertFalse(e.snapshot().soundRequested)
    }
    @Test fun U17_newClosureDuringRecoveryAlertsAgain() {
        val e = DrowsinessEngine(); e.start(0); feed(e,0,1600,true); feed(e,1650,3700)
        assertFalse(e.snapshot().soundRequested)
        feed(e,3750,5300,true); assertTrue(e.snapshot().soundRequested); assertEquals(2,e.snapshot().episode)
    }
    @Test fun U18_repeatedEpisodesRecommendPauseAndResetOnStop() {
        val e = DrowsinessEngine(); e.start(0)
        for (i in 0L..2) { feed(e,i*5000,i*5000+1600,true); feed(e,i*5000+1650,i*5000+4900) }
        assertTrue(e.snapshot().pauseRecommended); assertEquals(3,e.snapshot().episode)
        assertFalse(e.stop().pauseRecommended)
    }
    @Test fun interruptedReopeningRestartsTimer() {
        val e=DrowsinessEngine(); e.start(0); feed(e,0,1600,true); feed(e,1650,2800)
        e.observe(Observation(2850,Quality.NO_FACE)); feed(e,2900,4800)
        assertTrue(e.snapshot().soundRequested); assertFalse(e.observe(eye(4900)).soundRequested)
    }
    @Test fun reinforcedProfileAlertsAtItsOwnThreshold() {
        val e=DrowsinessEngine(Settings(reinforced=true)); e.start(0); feed(e,0,1150,true)
        assertFalse(e.snapshot().soundRequested); assertTrue(e.observe(eye(1200,true)).soundRequested)
    }
    @Test fun staleResultsAreNotEvidence() {
        val e=DrowsinessEngine(); e.start(0); e.observe(eye(100,true),2000)
        assertEquals(0L,e.snapshot().closedMs); assertFalse(e.snapshot().soundRequested)
    }
    @Test fun calibrationNeedsOpenEyesAndRealDuration() {
        val c=Calibration(); for(t in 0L..3000 step 50) assertNull(c.add(eye(t,true)))
        var result:Pair<Double,Double>?=null
        for(t in 3100L..5800 step 50) result=c.add(eye(t)) ?: result
        assertNotNull(result); assertEquals(.28,result!!.first,.0001)
    }
    @Test fun combinedRiskCanAlertEvenWhenEyesAreCurrentlyOpen() {
        // Disable only the immediate threshold to isolate the combined decision path.
        val e=DrowsinessEngine(limits=Thresholds(urgentMs=1000000)); e.start(0)
        for(t in 0L..30000 step 50) e.observe(eye(t,true).copy(leftEar=.03,rightEar=.03))
        for(t in 30050L..41000 step 50) e.observe(eye(t,t % 1500 < 650))
        feed(e,41050,62000)
        assertTrue(e.snapshot().score!!>=70)
        assertTrue(e.snapshot().soundRequested)
        feed(e,62050,64500)
        assertFalse(e.snapshot().soundRequested)
        assertEquals(1,e.snapshot().episode)
    }
    @Test fun noFrameAfterManualAcknowledgementDoesNotResumeSound() {
        val e=DrowsinessEngine(); e.start(0); feed(e,0,1600,true); e.acknowledge(1600)
        for(t in 1700L..10000 step 100) e.tick(t)
        assertFalse(e.snapshot().soundRequested)
    }
}
