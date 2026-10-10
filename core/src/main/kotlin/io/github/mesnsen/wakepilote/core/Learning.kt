package io.github.mesnsen.wakepilote.core

enum class TrainingLabel(val title:String) {
    EYES_OPEN("Yeux ouverts"), EYES_CLOSED("Yeux fermés"), BLINK("Clignement"),
    YAWN("Bâillement"), OTHER("Parler / sourire / autre"), UNKNOWN("Indéterminé")
}
enum class TrainingDecision { CONFIRMED, CORRECTED }
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
    val id:String,val createdAtEpochMs:Long,val actual:TrainingLabel,
    val decision:TrainingDecision,val draft:TrainingDraft,
) {
    init {
        require(actual!=TrainingLabel.UNKNOWN)
        require(decision!=TrainingDecision.CONFIRMED || actual==draft.predicted)
    }
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
        const val SCHEMA_VERSION=1
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
