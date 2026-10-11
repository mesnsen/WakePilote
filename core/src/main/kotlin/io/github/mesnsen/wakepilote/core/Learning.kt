package io.github.mesnsen.wakepilote.core

enum class TrainingLabel(val title:String) {
    EYES_OPEN("Yeux ouverts"), EYES_CLOSED("Yeux fermés"), BLINK("Clignement"),
    YAWN("Bâillement"), HEAD_NOD("Basculement de la tête vers le bas"),
    OTHER("Parler / sourire / autre"), UNKNOWN("Indéterminé")
}
enum class TrainingDecision { CONFIRMED, CORRECTED }
/** A negative example rejects ONLY this target. It does not assert another movement. */
data class TrainingExample(val target:TrainingLabel,val matchesTarget:Boolean) {
    init { require(target!=TrainingLabel.UNKNOWN) }
    val title get()=if(matchesTarget) "Exemple correct" else "Contre-exemple"
}
data class FeaturePoint(val index:Int,val x:Float,val y:Float,val z:Float)
data class FaceFeatures(val width:Int,val height:Int,val points:List<FeaturePoint>,val blendshapes:Map<String,Float>)
data class TrainingFrame(
    val observation:Observation, // atMs is relative to the start of this recording, never wall time.
    val processingMs:Long,val cameraZoom:Float,val features:FaceFeatures?,
    val left:EyeState,val right:EyeState,
)
data class TrainingDraft(
    val expected:TrainingLabel,val predicted:TrainingLabel,val settings:Settings,
    val durationMs:Long,val frames:List<TrainingFrame>,
) {
    val goodFrames get()=frames.count { it.observation.quality==Quality.GOOD }
    val largestGapMs:Long get() {
        val times=listOf(0L)+frames.map { it.observation.atMs }+durationMs
        return times.zipWithNext().maxOfOrNull { (a,b)->b-a } ?: durationMs
    }
}
data class TrainingClip(
    val id:String,val createdAtEpochMs:Long,val actual:TrainingLabel?,
    val decision:TrainingDecision?,val draft:TrainingDraft,
    val example:TrainingExample?=null,
) {
    init {
        if(example==null) {
            require(actual!=null && actual!=TrainingLabel.UNKNOWN && decision!=null)
            require(decision!=TrainingDecision.CONFIRMED || actual==draft.predicted)
        } else {
            require(actual==null && decision==null && example.target==draft.expected)
        }
    }
    val target get()=example?.target ?: requireNotNull(actual)
    val annotationTitle get()=example?.title ?: "Ancien exemple · ${if(decision==TrainingDecision.CORRECTED) "corrigé" else "confirmé"}"
}

/** Only numerical measures, kept in RAM until an explicit label. No bitmap/video ownership. */
class TrainingWindow(private var openedAtMs:Long) {
    private val samples=ArrayDeque<TrainingFrame>()
    fun clear(now:Long) { samples.clear(); openedAtMs=now }
    fun add(o:Observation,processingMs:Long,cameraZoom:Float,features:FaceFeatures?) {
        if(o.atMs<openedAtMs || samples.lastOrNull()?.let { o.atMs-it.observation.atMs<50 }==true) return
        samples.addLast(TrainingFrame(o,processingMs,cameraZoom,features,EyeState.UNKNOWN,EyeState.UNKNOWN))
        prune(o.atMs)
    }
    private fun prune(now:Long) {
        while(samples.isNotEmpty() && samples.first().observation.atMs<now-WINDOW_MS) samples.removeFirst()
        while(samples.size>61) samples.removeFirst()
    }
    fun count(now:Long):Int { prune(now); return samples.size }
    fun ready(now:Long):Boolean {
        prune(now)
        val first=samples.firstOrNull() ?: return false
        val last=samples.last()
        val face=samples.lastOrNull { !it.features?.points.isNullOrEmpty() } ?: return false
        return now-last.observation.atMs in 0..500 && now-face.observation.atMs in 0..500 &&
            last.observation.atMs-first.observation.atMs>=500
    }
    fun snapshot(target:TrainingLabel,settings:Settings,now:Long):TrainingDraft? {
        if(!ready(now)) return null
        val start=maxOf(openedAtMs,now-WINDOW_MS)
        val left=EyeClassifier(); val right=EyeClassifier()
        var previous:Long?=null
        val frames=samples.map { f ->
            val o=f.observation
            val good=o.quality==Quality.GOOD
            if(!good || previous?.let { o.atMs-it>250 }==true) { left.reset(); right.reset() }
            previous=o.atMs
            f.copy(observation=o.copy(atMs=o.atMs-start),
                left=if(good) left.classify(o.leftEar,settings.leftReference,o.leftBlink,o.atMs) else EyeState.UNKNOWN,
                right=if(good) right.classify(o.rightEar,settings.rightReference,o.rightBlink,o.atMs) else EyeState.UNKNOWN)
        }
        return TrainingDraft(target,TrainingCapture.predict(frames),settings,now-start,frames)
    }
    companion object { const val WINDOW_MS=3000L }
}

/** Short, bounded, main-thread capture. Bad detections are valuable negative examples too. */
class TrainingCapture(
    private val expected:TrainingLabel,private val settings:Settings,
    private val startedAtMs:Long,private val durationMs:Long=3000,
) {
    private val frames=mutableListOf<TrainingFrame>()
    private val left=EyeClassifier()
    private val right=EyeClassifier()
    private var lastAt:Long?=null
    val count get()=frames.size

    fun add(o:Observation,processingMs:Long,cameraZoom:Float,features:FaceFeatures?) {
        val relative=o.atMs-startedAtMs
        if(relative !in 0 until durationMs || frames.size>=90 || (lastAt!=null && o.atMs-lastAt!!<50)) return
        if(lastAt!=null && o.atMs-lastAt!!>250) { left.reset(); right.reset() }
        lastAt=o.atMs
        val good=o.quality==Quality.GOOD
        if(!good) { left.reset(); right.reset() }
        val l=if(good) left.classify(o.leftEar,settings.leftReference,o.leftBlink,o.atMs) else EyeState.UNKNOWN
        val r=if(good) right.classify(o.rightEar,settings.rightReference,o.rightBlink,o.atMs) else EyeState.UNKNOWN
        frames+=TrainingFrame(o.copy(atMs=relative),processingMs,cameraZoom,features,l,r)
    }

    fun finish():TrainingDraft? = if(frames.isEmpty()) null else
        TrainingDraft(expected,predict(frames),settings,durationMs,frames.toList())

    companion object {
        const val MAX_CLIPS=50
        const val SCHEMA_VERSION=2
        const val ALGORITHM_VERSION="wake-rules-0.1.2"

        /** Summarizes observed motion, never a diagnosis of drowsiness. Gaps add no evidence. */
        fun predict(frames:List<TrainingFrame>):TrainingLabel {
            var mouthRun=0L; var hasYawn=false
            var closedRun=0L; var longestClosed=0L; var hasBlink=false
            var openedBefore=false
            var openMs=0L; var closedMs=0L
            var previous:TrainingFrame?=null
            for(f in frames) {
                val p=previous
                val dt=if(p==null) 0L else f.observation.atMs-p.observation.atMs
                val continuous=p!=null && dt in 1..250 && p.observation.quality==Quality.GOOD && f.observation.quality==Quality.GOOD
                val closed=f.left==EyeState.CLOSED && f.right==EyeState.CLOSED
                val open=f.left==EyeState.OPEN && f.right==EyeState.OPEN
                val wasClosed=p?.let { it.left==EyeState.CLOSED && it.right==EyeState.CLOSED }==true
                val wasOpen=p?.let { it.left==EyeState.OPEN && it.right==EyeState.OPEN }==true
                if(!continuous) { mouthRun=0; closedRun=0; openedBefore=false }
                if(continuous && open && wasClosed && openedBefore && closedRun in 80..700) hasBlink=true
                if(open) openedBefore=true
                closedRun=if(continuous && closed && wasClosed) closedRun+dt else 0
                longestClosed=maxOf(longestClosed,closedRun)
                if(continuous && open && wasOpen) openMs+=dt
                if(continuous && closed && wasClosed) closedMs+=dt
                fun wide(x:TrainingFrame?)=x!=null && x.observation.quality==Quality.GOOD &&
                    (x.observation.mouthRatio ?: 0.0)>=.6 && (x.observation.jawOpen ?: 0.0)>=.6
                mouthRun=if(continuous && wide(f) && wide(p)) mouthRun+dt else 0
                if(mouthRun>=1500) hasYawn=true
                previous=f
            }
            return when {
                hasYawn -> TrainingLabel.YAWN
                longestClosed>=700 && closedMs>openMs -> TrainingLabel.EYES_CLOSED
                hasBlink -> TrainingLabel.BLINK
                openMs>=500 && openMs>closedMs -> TrainingLabel.EYES_OPEN
                else -> TrainingLabel.UNKNOWN
            }
        }
    }
}
