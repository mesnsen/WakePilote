package io.github.mesnsen.wakepilote

import io.github.mesnsen.wakepilote.audio.AudioStatus
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.vision.VisionMetrics
import io.github.mesnsen.wakepilote.vision.FaceLandmarks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionUi(
    val running:Boolean=false, val preparing:Boolean=false,
    val snapshot:Snapshot=Snapshot(), val audio:AudioStatus=AudioStatus(),
    val metrics:VisionMetrics=VisionMetrics(), val quality:Quality=Quality.NO_FACE,
    val lastObservationMs:Long=0, val error:String?=null,
    val landmarks:FaceLandmarks?=null, val visionDiagnostic:String?=null,
    val foregroundOnly:Boolean=false,
    val calibrationProgress:Float?=null, val calibrationMessage:String?=null,
)
object SessionHub {
    internal val mutable=MutableStateFlow(SessionUi())
    val state=mutable.asStateFlow()
}
