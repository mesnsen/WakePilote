package io.github.mesnsen.wakepilote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mesnsen.wakepilote.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningReviewTest {
    @get:Rule val compose=createComposeRule()
    private fun draft(predicted:TrainingLabel)=TrainingDraft(TrainingLabel.EYES_CLOSED,predicted,Settings(),3000,
        listOf(TrainingFrame(Observation(100),20,1f,null,EyeState.UNKNOWN,EyeState.UNKNOWN)))
    @Test fun notOkLetsTheUserCorrectTheLabelBeforeSaving() {
        var selected:Pair<TrainingLabel,Boolean>?=null
        compose.setContent { WakeTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LearningReview(draft(TrainingLabel.EYES_OPEN),false,{ label,corrected->selected=label to corrected },{})
        } } }
        compose.onNodeWithText("Pas OK · corriger").performScrollTo().performClick()
        compose.onNodeWithText("Bâillement").performScrollTo().performClick()
        compose.onNodeWithText("Garder la correction").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TrainingLabel.YAWN to true,selected) }
    }
    @Test fun unknownCannotBeConfirmedButCanBeLabeledOrDiscarded() {
        var discarded=false
        compose.setContent { WakeTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            LearningReview(draft(TrainingLabel.UNKNOWN),false,{_,_->fail("Unknown must not be confirmed")},{discarded=true})
        } } }
        compose.onNodeWithText("OK · garder").assertIsNotEnabled()
        compose.onNodeWithText("Jeter cet essai").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(discarded) }
    }
}
