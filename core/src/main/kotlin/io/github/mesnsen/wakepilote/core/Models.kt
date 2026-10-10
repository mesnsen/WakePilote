package io.github.mesnsen.wakepilote.core

enum class MonitorState { INACTIVE, INITIALIZING, MONITORING, SUSPECTED_DROWSINESS, ALERTING, RECOVERING, MONITORING_UNAVAILABLE }
enum class AlertLevel { NONE, EARLY, URGENT }
enum class EyeState { OPEN, CLOSED, UNKNOWN }
enum class Quality { GOOD, NO_FACE, AMBIGUOUS_FACE, POOR_LIGHT, POSE, GEOMETRY, CAMERA_ERROR, THERMAL, STALE }
enum class OutputPreference { AUTO, PHONE, CAR }

data class Settings(
    val reinforced: Boolean = false,
    val gain: Float = 0.75f,
    val vibrate: Boolean = true,
    val output: OutputPreference = OutputPreference.AUTO,
    val carDeviceId: Int? = null,
    val leftReference: Double = 0.28,
    val rightReference: Double = 0.28,
    val calibrated: Boolean = false,
    val onboarded: Boolean = false,
) {
    fun thresholds() = Thresholds(earlyMs = if (reinforced) 600 else 800, urgentMs = if (reinforced) 1200 else 1500,
        earlyScore = if (reinforced) 35.0 else 40.0)
}

data class Thresholds(
    val earlyMs: Long = 800, val urgentMs: Long = 1500,
    val reopenMs: Long = 2000, val manualQuietMs: Long = 5000,
    val maxGapMs: Long = 250, val staleMs: Long = 500,
    val unavailableMs: Long = 1500, val initTimeoutMs: Long = 5000,
    val windowMs: Long = 60000, val minValidMs: Long = 45000,
    val earlyScore: Double = 40.0, val urgentScore: Double = 70.0,
)

data class Observation(
    val atMs: Long,
    val quality: Quality = Quality.GOOD,
    val leftEar: Double = Double.NaN, val rightEar: Double = Double.NaN,
    val leftBlink: Double? = null, val rightBlink: Double? = null,
    val mouthRatio: Double? = null, val jawOpen: Double? = null,
    val pitch: Double? = null, val yaw: Double? = null,
)

data class Snapshot(
    val state: MonitorState = MonitorState.INACTIVE,
    val level: AlertLevel = AlertLevel.NONE,
    val soundRequested: Boolean = false,
    val warningSerial: Long = 0,
    val episode: Long = 0,
    val quality: Quality = Quality.NO_FACE,
    val left: EyeState = EyeState.UNKNOWN, val right: EyeState = EyeState.UNKNOWN,
    val closedMs: Long = 0, val openedMs: Long = 0,
    val perclos: Double? = null, val coverage: Double = 0.0,
    val score: Double? = null,
    val reason: String = "Surveillance arrêtée",
    val quietUntilMs: Long = 0,
    val pauseRecommended: Boolean = false,
)

/** No synthetic confidences: a valid landmark is not proof of visibility through sunglasses. */
class EyeClassifier {
    private var state = EyeState.UNKNOWN
    private var ambiguousSince: Long? = null
    fun reset() { state = EyeState.UNKNOWN; ambiguousSince = null }
    fun classify(ear: Double, reference: Double, blink: Double?, at: Long): EyeState {
        if (!ear.isFinite() || ear <= 0 || reference !in 0.10..0.60 ||
            (blink != null && (!blink.isFinite() || blink !in 0.0..1.0))) {
            reset(); return state
        }
        val r = ear / reference
        val candidate = when {
            r <= 0.5 && (blink == null || blink >= 0.60) -> EyeState.CLOSED
            r >= 0.7 && (blink == null || blink <= 0.35) -> EyeState.OPEN
            (r <= 0.5 && blink != null && blink < 0.60) ||
                (r >= 0.7 && blink != null && blink > 0.35) -> EyeState.UNKNOWN
            else -> null
        }
        if (candidate != null) { state = candidate; ambiguousSince = null }
        else {
            val since = ambiguousSince ?: at.also { ambiguousSince = it }
            if (at - since >= 150) state = EyeState.UNKNOWN
        }
        return state
    }
}
