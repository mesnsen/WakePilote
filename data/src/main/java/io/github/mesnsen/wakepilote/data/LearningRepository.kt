package io.github.mesnsen.wakepilote.data

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import io.github.mesnsen.wakepilote.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One ViewModel owns this store. Atomic replacement; never silently discard unreadable examples. */
class LearningRepository(file:File) {
    constructor(context:Context):this(File(context.filesDir,"learning-v1.json"))
    private val file=AtomicFile(file)
    private val mutex=Mutex()

    suspend fun load():List<TrainingClip> = withContext(Dispatchers.IO) { mutex.withLock { read() } }
    suspend fun add(clip:TrainingClip):List<TrainingClip> = mutate { old ->
        require(old.size<TrainingCapture.MAX_CLIPS) { "Limite de 50 exemples atteinte. Exportez puis supprimez des exemples." }
        require(old.none { it.id==clip.id }) { "Exemple déjà enregistré" }
        old+clip
    }
    suspend fun remove(id:String):List<TrainingClip> = mutate { old -> old.filterNot { it.id==id } }
    suspend fun clear():List<TrainingClip> = mutate { emptyList() }
    suspend fun export(appVersion:String):String = withContext(Dispatchers.IO) { mutex.withLock {
        root(read()).put("exportedAtEpochMs",System.currentTimeMillis()).put("appVersion",appVersion)
            .put("device",JSONObject().put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("androidApi",Build.VERSION.SDK_INT))
            .put("dataDescription","Mesures et annotations manuelles ; aucune image, vidéo, identité ou localisation. Les labels ne certifient pas la vigilance.")
            .toString()
    } }

    private suspend fun mutate(change:(List<TrainingClip>)->List<TrainingClip>):List<TrainingClip> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated=change(read())
            val bytes=root(updated).toString().toByteArray(Charsets.UTF_8)
            require(bytes.size<=12*1024*1024) { "Fichier trop volumineux. Exportez puis supprimez des exemples." }
            val output=file.startWrite()
            try { output.write(bytes); file.finishWrite(output) }
            catch(e:Exception) { file.failWrite(output); throw e }
            updated
        }
    }
    private fun read():List<TrainingClip> {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return emptyList()
        val text=file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root=JSONObject(text)
        require(root.getInt("schemaVersion")==TrainingCapture.SCHEMA_VERSION) { "Version de fichier d'apprentissage inconnue" }
        val clips=root.getJSONArray("clips")
        require(clips.length()<=TrainingCapture.MAX_CLIPS)
        return List(clips.length()) { decodeClip(clips.getJSONObject(it)) }
    }

    companion object {
        private fun array(values:Iterable<*>):JSONArray=JSONArray().apply { values.forEach { put(it) } }
        private fun finite(value:Double?):Any=if(value!=null && value.isFinite()) value else JSONObject.NULL
        private fun number(j:JSONObject,key:String):Double?=if(j.isNull(key)) null else j.getDouble(key).takeIf { it.isFinite() }
        private fun root(clips:List<TrainingClip>)=JSONObject()
            .put("schemaVersion",TrainingCapture.SCHEMA_VERSION).put("algorithmVersion",TrainingCapture.ALGORITHM_VERSION)
            .put("modelSha256","64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff")
            .put("coordinateSystem","Points MediaPipe normalisés x/y/z dans l'image d'analyse redressée, non miroir. Indices explicites. atMs relatif au début de chaque séquence.")
            .put("rules",JSONObject().put("closedEarRatio",.5).put("openEarRatio",.7).put("closedBlinkMin",.6)
                .put("openBlinkMax",.35).put("mouthRatioMin",.6).put("jawOpenMin",.6).put("yawnDurationMs",1500).put("maxEvidenceGapMs",250))
            .put("clips",array(clips.map(::encodeClip)))

        fun encodeClip(c:TrainingClip):JSONObject=JSONObject().put("id",c.id).put("createdAtEpochMs",c.createdAtEpochMs)
            .put("expected",c.draft.expected.name).put("predicted",c.draft.predicted.name).put("actual",c.actual.name)
            .put("decision",c.decision.name).put("durationMs",c.draft.durationMs)
            .put("goodFrames",c.draft.goodFrames).put("largestGapMs",c.draft.largestGapMs)
            .put("settings",JSONObject().put("leftReference",c.draft.settings.leftReference)
                .put("rightReference",c.draft.settings.rightReference).put("calibrated",c.draft.settings.calibrated)
                .put("reinforced",c.draft.settings.reinforced).put("requestedZoom",c.draft.settings.cameraZoom))
            .put("frames",array(c.draft.frames.map { f ->
                val o=f.observation
                JSONObject().put("atMs",o.atMs).put("quality",o.quality.name)
                    .put("leftEar",finite(o.leftEar)).put("rightEar",finite(o.rightEar))
                    .put("leftBlink",finite(o.leftBlink)).put("rightBlink",finite(o.rightBlink))
                    .put("mouthRatio",finite(o.mouthRatio)).put("jawOpen",finite(o.jawOpen))
                    .put("pitchDegrees",finite(o.pitch)).put("yawDegrees",finite(o.yaw))
                    .put("brightness",finite(o.brightness)).put("faceWidth",finite(o.faceWidth)).put("faceHeight",finite(o.faceHeight))
                    .put("processingMs",f.processingMs).put("cameraZoom",f.cameraZoom)
                    .put("predictedLeft",f.left.name).put("predictedRight",f.right.name)
                    .put("features",f.features?.let { raw ->
                        JSONObject().put("width",raw.width).put("height",raw.height)
                            .put("points",array(raw.points.map { p -> array(listOf(p.index,finite(p.x.toDouble()),finite(p.y.toDouble()),finite(p.z.toDouble()))) }))
                            .put("blendshapes",JSONObject().apply { raw.blendshapes.forEach { (k,v)->put(k,finite(v.toDouble())) } })
                    } ?: JSONObject.NULL)
            }))

        fun decodeClip(j:JSONObject):TrainingClip {
            val s=j.getJSONObject("settings")
            val settings=Settings(leftReference=s.getDouble("leftReference"),rightReference=s.getDouble("rightReference"),
                calibrated=s.getBoolean("calibrated"),reinforced=s.getBoolean("reinforced"),cameraZoom=s.getDouble("requestedZoom").toFloat())
            val all=j.getJSONArray("frames")
            require(all.length() in 1..90)
            val frames=List(all.length()) { index ->
                val f=all.getJSONObject(index)
                val raw=if(f.isNull("features")) null else f.getJSONObject("features")
                val features=raw?.let {
                    val pts=it.getJSONArray("points"); val blend=it.getJSONObject("blendshapes")
                    FaceFeatures(it.getInt("width"),it.getInt("height"),List(pts.length()) { n ->
                        val p=pts.getJSONArray(n); FeaturePoint(p.getInt(0),p.getDouble(1).toFloat(),p.getDouble(2).toFloat(),p.getDouble(3).toFloat())
                    },blend.keys().asSequence().associateWith { k->blend.getDouble(k).toFloat() })
                }
                TrainingFrame(Observation(f.getLong("atMs"),Quality.valueOf(f.getString("quality")),
                    number(f,"leftEar") ?: Double.NaN,number(f,"rightEar") ?: Double.NaN,
                    number(f,"leftBlink"),number(f,"rightBlink"),number(f,"mouthRatio"),number(f,"jawOpen"),
                    number(f,"pitchDegrees"),number(f,"yawDegrees"),number(f,"brightness"),number(f,"faceWidth"),number(f,"faceHeight")),
                    f.getLong("processingMs"),f.getDouble("cameraZoom").toFloat(),features,
                    EyeState.valueOf(f.getString("predictedLeft")),EyeState.valueOf(f.getString("predictedRight")))
            }
            val draft=TrainingDraft(TrainingLabel.valueOf(j.getString("expected")),TrainingLabel.valueOf(j.getString("predicted")),
                settings,j.getLong("durationMs"),frames)
            return TrainingClip(j.getString("id"),j.getLong("createdAtEpochMs"),TrainingLabel.valueOf(j.getString("actual")),
                TrainingDecision.valueOf(j.getString("decision")),draft)
        }
    }
}
