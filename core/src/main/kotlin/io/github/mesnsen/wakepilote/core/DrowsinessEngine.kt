package io.github.mesnsen.wakepilote.core

import kotlin.math.min

/** Single-thread confined. Only observations extend evidence; tick() only expires it. */
class DrowsinessEngine(private val settings: Settings = Settings(), private val limits: Thresholds = settings.thresholds()) {
    private data class Segment(val start: Long, val end: Long, val strong: Boolean)
    private val segments = ArrayDeque<Segment>()
    private val longBlinks = ArrayDeque<Long>()
    private val yawns = ArrayDeque<Long>()
    private val nods = ArrayDeque<Long>()
    private val episodes = ArrayDeque<Long>()
    private val leftClassifier = EyeClassifier()
    private val rightClassifier = EyeClassifier()
    private var started: Long? = null
    private var latest: Long? = null
    private var lastValid: Long? = null
    private var lastClosed: Long? = null
    private var previousLeft = EyeState.UNKNOWN
    private var previousRight = EyeState.UNKNOWN
    private var previousStrong = false
    private var closed = 0L
    private var opened = 0L
    private var validRun = 0L
    private var earlyRun = 0L
    private var urgentRun = 0L
    private var lowRun = 0L
    private var recoveringRun = 0L
    private var urgentLatched = false
    private var aggregateArmed = true
    private var recovering = false
    private var quietUntil = 0L
    private var lastWarning = Long.MIN_VALUE / 2
    private var warningSerial = 0L
    private var episode = 0L
    private var mouthSince: Long? = null
    private var mouthCounted = false
    private var lastYawn = Long.MIN_VALUE / 2
    private var nodSince: Long? = null
    private var nodBaseline: Double? = null
    private var current = Snapshot()

    fun snapshot() = current

    fun start(now: Long): Snapshot {
        stop()
        started = now
        current = Snapshot(state = MonitorState.INITIALIZING, reason = "Positionnez votre visage dans le cadre")
        return current
    }

    fun stop(): Snapshot {
        started = null; latest = null; lastValid = null; lastClosed = null
        segments.clear(); longBlinks.clear(); yawns.clear(); nods.clear(); episodes.clear()
        leftClassifier.reset(); rightClassifier.reset()
        previousLeft = EyeState.UNKNOWN; previousRight = EyeState.UNKNOWN; previousStrong = false
        closed = 0; opened = 0; validRun = 0; earlyRun = 0; urgentRun = 0; lowRun = 0; recoveringRun = 0
        urgentLatched = false; aggregateArmed = true; recovering = false; quietUntil = 0
        lastWarning = Long.MIN_VALUE / 2; warningSerial = 0; episode = 0
        mouthSince = null; mouthCounted = false; lastYawn = Long.MIN_VALUE / 2; nodSince = null; nodBaseline = null
        current = Snapshot()
        return current
    }

    fun acknowledge(now: Long): Snapshot {
        if (started == null) return current
        quietUntil = now + limits.manualQuietMs
        current = current.copy(soundRequested = false, quietUntilMs = quietUntil,
            reason = "Son arrêté — faites une pause dès que possible en sécurité")
        return current
    }

    fun unavailable(now: Long, quality: Quality): Snapshot {
        if (started == null) return current
        clearContinuity()
        lastValid = null
        current = current.copy(state = MonitorState.MONITORING_UNAVAILABLE, quality = quality,
            closedMs = 0, openedMs = 0, left = EyeState.UNKNOWN, right = EyeState.UNKNOWN,
            soundRequested = urgentLatched && now >= quietUntil && current.soundRequested,
            reason = "Surveillance indisponible")
        return current
    }

    fun tick(now: Long): Snapshot {
        val start = started ?: return current
        if (latest != null && now < latest!!) return current
        if (lastClosed != null && now - lastClosed!! > limits.maxGapMs) closed = 0
        if (lastValid == null || now - lastValid!! > limits.maxGapMs) {
            opened = 0; validRun = 0; earlyRun = 0; urgentRun = 0; lowRun = 0; recoveringRun = 0
        }
        prune(now)
        val stale = if (lastValid != null) now - lastValid!! >= limits.unavailableMs else now - start >= limits.initTimeoutMs
        if (stale) return unavailable(now, Quality.STALE)
        // Deliberately do not re-arm sound from a tick alone after manual acknowledgement.
        current = current.copy(closedMs = closed, openedMs = opened)
        return current
    }

    fun observe(o: Observation, now: Long = o.atMs): Snapshot {
        val start = started ?: return current
        if (o.atMs < start || o.atMs > now || now - o.atMs > limits.staleMs ||
            (latest != null && o.atMs <= latest!!)) return tick(now)
        val before = latest
        val delta = before?.let { o.atMs - it } ?: 0L
        latest = o.atMs
        if (delta > limits.maxGapMs) clearContinuity()
        val good = o.quality == Quality.GOOD
        val l = if (good) leftClassifier.classify(o.leftEar, settings.leftReference, o.leftBlink, o.atMs) else EyeState.UNKNOWN
        val r = if (good) rightClassifier.classify(o.rightEar, settings.rightReference, o.rightBlink, o.atMs) else EyeState.UNKNOWN
        val valid = l != EyeState.UNKNOWN && r != EyeState.UNKNOWN
        if (!valid) {
            previousLeft = EyeState.UNKNOWN; previousRight = EyeState.UNKNOWN
            leftClassifier.reset(); rightClassifier.reset()
            opened = 0; validRun = 0; earlyRun = 0; urgentRun = 0; lowRun = 0; recoveringRun = 0
            mouthSince = null; mouthCounted = false; nodSince = null
            current = current.copy(left = l, right = r, quality = if (good) Quality.GEOMETRY else o.quality,
                openedMs = 0)
            val noValidSince = lastValid ?: start
            if (o.atMs - noValidSince >= limits.unavailableMs) return unavailable(o.atMs, current.quality)
            return tick(o.atMs)
        }
        val priorValid = previousLeft != EyeState.UNKNOWN && previousRight != EyeState.UNKNOWN
        val continuous = priorValid && delta in 1..limits.maxGapMs
        lastValid = o.atMs
        validRun = if (continuous) validRun + delta else 0
        val bothClosed = l == EyeState.CLOSED && r == EyeState.CLOSED
        val bothOpen = l == EyeState.OPEN && r == EyeState.OPEN
        val wasClosed = previousLeft == EyeState.CLOSED && previousRight == EyeState.CLOSED
        val wasOpen = previousLeft == EyeState.OPEN && previousRight == EyeState.OPEN
        if (bothClosed) {
            if (lastClosed == null || o.atMs - lastClosed!! > limits.maxGapMs) closed = 0
            if (continuous && wasClosed) closed += delta
            lastClosed = o.atMs
            opened = 0
        } else {
            if (wasClosed && closed in 400 until 800) longBlinks.addLast(o.atMs)
            closed = 0; lastClosed = null
            opened = if (bothOpen && wasOpen && continuous) opened + delta else 0
        }
        val strong = bothClosed && o.leftEar / settings.leftReference <= 0.20 && o.rightEar / settings.rightReference <= 0.20
        if (continuous && l == previousLeft && r == previousRight && strong == previousStrong) {
            segments.addLast(Segment(before!!, o.atMs, strong))
        }
        updateSecondary(o)
        previousLeft = l; previousRight = r; previousStrong = strong
        prune(o.atMs)
        val floor = o.atMs - limits.windowMs
        val validTime = segments.sumOf { it.end - maxOf(it.start, floor) }
        val strongTime = segments.filter { it.strong }.sumOf { it.end - maxOf(it.start, floor) }
        val elapsed = min(o.atMs - start, limits.windowMs)
        val coverage = if (elapsed > 0) (validTime.toDouble() / elapsed).coerceIn(0.0, 1.0) else 0.0
        val perclos = if (elapsed >= limits.windowMs && validTime >= limits.minValidMs && coverage >= 0.75)
            strongTime.toDouble() / validTime else null
        val p = perclos?.let { ((it - 0.10) / 0.20).coerceIn(0.0, 1.0) }
        val ocular = p?.let { 55 * it + 25 * min(1.0, longBlinks.size / 6.0) }
        val score = ocular?.let { it + repeatedWeight(yawns.size) + repeatedWeight(nods.size) }
        earlyRun = if (continuous && score != null && score >= limits.earlyScore) earlyRun + delta else 0
        urgentRun = if (continuous && score != null && score >= limits.urgentScore && ocular!! >= 50) urgentRun + delta else 0
        lowRun = if (continuous && (score == null || score <= 25) && !bothClosed) lowRun + delta else 0
        if (lowRun >= 10000) aggregateArmed = true
        val critical = closed >= limits.urgentMs || (aggregateArmed && urgentRun >= 2000)
        if (critical && !urgentLatched) {
            urgentLatched = true; recovering = false; opened = 0; episode++; episodes.addLast(o.atMs)
        }
        if (urgentLatched && opened >= limits.reopenMs) {
            urgentLatched = false; aggregateArmed = false; recovering = true; recoveringRun = 0
        }
        recoveringRun = if (recovering && bothOpen && continuous) recoveringRun + delta else 0
        if (recoveringRun >= 10000 && lowRun >= 10000) recovering = false
        val early = closed >= limits.earlyMs || (aggregateArmed && earlyRun >= 3000)
        if (!urgentLatched && early && o.atMs >= quietUntil && o.atMs - lastWarning >= 30000) {
            warningSerial++; lastWarning = o.atMs
        }
        val oldUnavailable = current.state == MonitorState.MONITORING_UNAVAILABLE
        val state = when {
            urgentLatched -> MonitorState.ALERTING
            oldUnavailable && validRun < 1000 -> MonitorState.MONITORING_UNAVAILABLE
            recovering -> MonitorState.RECOVERING
            early -> MonitorState.SUSPECTED_DROWSINESS
            validRun < 1000 && current.state == MonitorState.INITIALIZING -> MonitorState.INITIALIZING
            else -> MonitorState.MONITORING
        }
        // Resumption after a manual stop needs a current critical sign, not just a latched episode.
        if (urgentLatched && quietUntil > 0 && o.atMs >= quietUntil && critical) quietUntil = 0
        val sound = urgentLatched && o.atMs >= quietUntil && quietUntil == 0L
        current = Snapshot(state, if (urgentLatched) AlertLevel.URGENT else if (early) AlertLevel.EARLY else AlertLevel.NONE,
            sound, warningSerial, episode, Quality.GOOD, l, r, closed, opened, perclos, coverage, score,
            when {
                urgentLatched && !sound -> "Son arrêté — faites une pause dès que possible en sécurité"
                urgentLatched -> "Fatigue suspectée — arrêtez-vous dès que possible en sécurité"
                recovering -> "Son arrêté — une réouverture des yeux ne prouve pas la vigilance"
                early -> "Signes de fatigue possibles — prévoyez une pause"
                state == MonitorState.INITIALIZING || state == MonitorState.MONITORING_UNAVAILABLE -> "Vérification du suivi du visage"
                else -> "Surveillance active"
            }, quietUntil, episodes.size >= 3)
        return current
    }

    private fun repeatedWeight(count: Int) = when { count >= 3 -> 10.0; count == 2 -> 5.0; else -> 0.0 }
    private fun clearContinuity() {
        closed = 0; opened = 0; validRun = 0; earlyRun = 0; urgentRun = 0; lowRun = 0; recoveringRun = 0
        previousLeft = EyeState.UNKNOWN; previousRight = EyeState.UNKNOWN
        leftClassifier.reset(); rightClassifier.reset(); lastClosed = null
        mouthSince = null; mouthCounted = false; nodSince = null
    }
    private fun prune(now: Long) {
        while (segments.isNotEmpty() && segments.first().end <= now - limits.windowMs) segments.removeFirst()
        while (longBlinks.isNotEmpty() && longBlinks.first() < now - 60000) longBlinks.removeFirst()
        while (yawns.isNotEmpty() && yawns.first() < now - 120000) yawns.removeFirst()
        while (nods.isNotEmpty() && nods.first() < now - 60000) nods.removeFirst()
        while (episodes.isNotEmpty() && episodes.first() < now - 600000) episodes.removeFirst()
    }
    private fun updateSecondary(o: Observation) {
        val mouth = o.mouthRatio; val jaw = o.jawOpen
        if (mouth != null && jaw != null && mouth >= 0.6 && jaw >= 0.6) {
            if (mouthSince == null) mouthSince = o.atMs
            if (!mouthCounted && o.atMs - mouthSince!! >= 1500 && o.atMs - lastYawn >= 3000) {
                yawns.addLast(o.atMs); lastYawn = o.atMs; mouthCounted = true
            }
        } else if (mouth == null || jaw == null || mouth < 0.35 || jaw < 0.35) {
            mouthSince = null; mouthCounted = false
        }
        val pitch = o.pitch
        if (pitch == null || !pitch.isFinite() || (o.yaw ?: 0.0).let { kotlin.math.abs(it) > 25 }) { nodSince = null; return }
        if (nodBaseline == null) nodBaseline = pitch
        val offset = pitch - nodBaseline!!
        if (offset >= 15 && nodSince == null) nodSince = o.atMs
        if (offset < 5 && nodSince != null) {
            if (o.atMs - nodSince!! in 500..2000) nods.addLast(o.atMs)
            nodSince = null
        }
        if (nodSince != null && o.atMs - nodSince!! > 2000) nodSince = null
    }
}
