package io.github.mesnsen.wakepilote

import io.github.mesnsen.wakepilote.core.*

enum class LearningStage { IDLE, COUNTDOWN, CAPTURING, REVIEW }
data class LearningUi(
    val stage:LearningStage=LearningStage.IDLE,val countdown:Int=0,val progress:Float=0f,val frameCount:Int=0,
    val draft:TrainingDraft?=null,val clips:List<TrainingClip> = emptyList(),
    val loading:Boolean=true,val saving:Boolean=false,val error:String?=null,val message:String?=null,
)
