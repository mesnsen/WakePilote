package io.github.mesnsen.wakepilote

import io.github.mesnsen.wakepilote.audio.AudioStatus
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.vision.VisionMetrics
import io.github.mesnsen.wakepilote.vision.FaceLandmarks
import io.github.mesnsen.wakepilote.vision.CameraZoom
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionUi(
    val running:Boolean=false, val preparing:Boolean=false,
    val snapshot:Snapshot=Snapshot(), val audio:AudioStatus=AudioStatus(),
    val metrics:VisionMetrics=VisionMetrics(), val quality:Quality=Quality.NO_FACE,
    val lastObservationMs:Long=0, val error:String?=null,
    val zoom:CameraZoom=CameraZoom(), val visionDiagnostic:String?=null,
    val foregroundOnly:Boolean=false,
    val calibrationProgress:Float?=null, val calibrationMessage:String?=null,
)
object SessionHub {
    internal val mutable=MutableStateFlow(SessionUi())
    val state=mutable.asStateFlow()
    internal val mutablePreview=MutableStateFlow<FaceLandmarks?>(null)
    val preview=mutablePreview.asStateFlow()
    internal val mutableSamples=MutableSharedFlow<CameraSample>(extraBufferCapacity=2,onBufferOverflow=BufferOverflow.DROP_OLDEST)
    val samples=mutableSamples.asSharedFlow()
}
data class CameraSample(val observation:Observation,val metrics:VisionMetrics,val zoom:Float,val features:FaceFeatures?)
