package io.github.mesnsen.wakepilote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun freshInstallExplainsLimitsBeforeAnySession() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("J'ai compris — préparer mon trajet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("J'ai compris — préparer mon trajet").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Démarrer la surveillance").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Démarrer la surveillance").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Surveillance arrêtée").assertExists()
    }
}
