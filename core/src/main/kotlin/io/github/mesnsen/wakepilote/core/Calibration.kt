package io.github.mesnsen.wakepilote.core

/** Session-only scalar samples. No automatic adaptation while monitoring. */
class Calibration {
    private val samples = ArrayList<Pair<Double, Double>>()
    private var previous: Long? = null
    private var validMs = 0L
    val progress: Float get() = (validMs / 2500f).coerceIn(0f, 1f)
    fun add(o: Observation): Pair<Double, Double>? {
        val valid = o.quality == Quality.GOOD && o.leftEar in 0.16..0.5 && o.rightEar in 0.16..0.5 &&
            (o.leftBlink ?: 1.0) <= 0.25 && (o.rightBlink ?: 1.0) <= 0.25
        if (!valid) { previous = null; return null }
        val delta = previous?.let { o.atMs - it }
        if (delta != null && delta in 1..250) validMs += delta
        previous = o.atMs
        samples.add(o.leftEar to o.rightEar)
        if (samples.size > 200) samples.removeAt(0)
        if (validMs < 2500 || samples.size < 15) return null
        val l = samples.map { it.first }.sorted(); val r = samples.map { it.second }.sorted()
        val lm = l[l.size / 2]; val rm = r[r.size / 2]
        if (l[l.size * 9 / 10] - l[l.size / 10] > 0.12 || r[r.size * 9 / 10] - r[r.size / 10] > 0.12) return null
        return lm to rm
    }
}
