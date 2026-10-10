package io.github.mesnsen.wakepilote.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import io.github.mesnsen.wakepilote.core.OutputPreference
import io.github.mesnsen.wakepilote.core.Settings
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.wakeStore by preferencesDataStore("wake_preferences")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.wakeStore
    private object Key {
        val reinforced=booleanPreferencesKey("reinforced")
        val gain=floatPreferencesKey("gain")
        val vibration=booleanPreferencesKey("vibration")
        val output=stringPreferencesKey("output")
        val device=intPreferencesKey("car_device")
        val left=doublePreferencesKey("calibration_v1_left")
        val right=doublePreferencesKey("calibration_v1_right")
        val onboarding=booleanPreferencesKey("onboarding")
        val active=booleanPreferencesKey("session_was_active")
    }
    private val preferences = store.data.catch { if(it is IOException) emit(emptyPreferences()) else throw it }
    val settings = preferences.map { p ->
        val l=p[Key.left]; val r=p[Key.right]
        val calibrated = l != null && r != null && l in .16.. .5 && r in .16.. .5
        Settings(p[Key.reinforced] ?: false, (p[Key.gain] ?: .75f).coerceIn(.1f,1f), p[Key.vibration] ?: true,
            runCatching { OutputPreference.valueOf(p[Key.output] ?: "AUTO") }.getOrDefault(OutputPreference.AUTO),
            p[Key.device], if(calibrated) l!! else .28, if(calibrated) r!! else .28, calibrated, p[Key.onboarding] ?: false)
    }
    val interruptedSession = preferences.map { it[Key.active] ?: false }
    suspend fun update(s:Settings) { store.edit { p ->
        p[Key.reinforced]=s.reinforced; p[Key.gain]=s.gain.coerceIn(.1f,1f); p[Key.vibration]=s.vibrate
        p[Key.output]=s.output.name
        s.carDeviceId?.let { p[Key.device]=it } ?: p.remove(Key.device)
        p[Key.onboarding]=s.onboarded
    } }
    suspend fun calibration(left:Double,right:Double) { require(left in .16.. .5 && right in .16.. .5); store.edit { it[Key.left]=left; it[Key.right]=right } }
    suspend fun resetCalibration() { store.edit { it.remove(Key.left); it.remove(Key.right) } }
    suspend fun markSession(active:Boolean) { store.edit { it[Key.active]=active } }
}
