package io.github.mesnsen.wakepilote

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mesnsen.wakepilote.audio.AlertPlayer
import io.github.mesnsen.wakepilote.core.Settings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioPlaybackTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)

    @Test fun staticAudioCanPlayStopAndRestart() {
        activity.scenario.onActivity { context ->
            val player=AlertPlayer(context)
            try {
                assertTrue("Test device needs nonzero media volume",player.volume()>0)
                val config=Settings(vibrate=false,gain=.1f)
                player.test(config)
                assertTrue(player.status.value.toString(),player.status.value.playing)
                assertFalse(player.status.value.interrupted)
                player.stop()
                assertFalse(player.status.value.playing)
                player.urgent(true,config)
                assertTrue(player.status.value.toString(),player.status.value.playing)
                player.stop()
                assertFalse(player.status.value.playing)
                player.test(config)
                assertTrue(player.status.value.toString(),player.status.value.playing)
            } finally { player.close() }
        }
    }
}
