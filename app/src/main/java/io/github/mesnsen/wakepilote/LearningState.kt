package io.github.mesnsen.wakepilote

import io.github.mesnsen.wakepilote.core.*

data class LearningUi(
    val active:Boolean=false,val target:TrainingLabel=TrainingLabel.EYES_OPEN,
    val ready:Boolean=false,val frameCount:Int=0,val clips:List<TrainingClip> = emptyList(),
    val loading:Boolean=true,val saving:Boolean=false,val error:String?=null,val message:String?=null,
)
