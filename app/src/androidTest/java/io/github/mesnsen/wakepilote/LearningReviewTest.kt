package io.github.mesnsen.wakepilote

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
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
    private fun content(enabled:Boolean=true,onLabel:(TrainingLabel,Boolean)->Unit) {
        compose.setContent { WakeTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            var target by remember { mutableStateOf(TrainingLabel.EYES_OPEN) }
            TrainingTargetDropdown(target,true) { target=it }
            LearningLabelButtons(target,enabled) { onLabel(target,it) }
        } } }
    }
    @Test fun dropdownSelectsHeadMovementAndPositiveTapSavesDirectly() {
        var saved:Pair<TrainingLabel,Boolean>?=null
        content { target,matches->saved=target to matches }
        compose.onNodeWithTag("training-target").performClick()
        compose.onNodeWithText("Basculement de la tête vers le bas").performClick()
        compose.onNodeWithText("Exemple correct").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TrainingLabel.HEAD_NOD to true,saved) }
        compose.onNodeWithText("Garder la correction").assertDoesNotExist()
    }
    @Test fun counterExampleIsImmediatelyAssociatedWithSelectedDetection() {
        var saved:Pair<TrainingLabel,Boolean>?=null
        content { target,matches->saved=target to matches }
        compose.onNodeWithTag("training-target").performClick()
        compose.onNodeWithText("Bâillement").performClick()
        compose.onNodeWithText("Contre-exemple").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(TrainingLabel.YAWN to false,saved) }
    }
    @Test fun bothLabelsAreDisabledUntilRecentFaceMeasuresAreAvailable() {
        content(enabled=false) { _,_->fail("No measures must not be saved") }
        compose.onNodeWithText("Exemple correct").assertIsNotEnabled()
        compose.onNodeWithText("Contre-exemple").assertIsNotEnabled()
    }
}
