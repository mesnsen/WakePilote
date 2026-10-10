package io.github.mesnsen.wakepilote

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mesnsen.wakepilote.audio.AlertPlayer
import io.github.mesnsen.wakepilote.audio.OutputDevice
import io.github.mesnsen.wakepilote.core.Settings
import io.github.mesnsen.wakepilote.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WakeViewModel(app:Application):AndroidViewModel(app) {
    private val repository=SettingsRepository(app)
    private val tester=AlertPlayer(app)
    private val prefs=MutableStateFlow(Settings())
    val settings=prefs.asStateFlow()
    val loaded=MutableStateFlow(false)
    val session=SessionHub.state
    val audio=tester.status
    val message=MutableStateFlow<String?>(null)
    val outputs=MutableStateFlow<List<OutputDevice>>(emptyList())
    val testHeard=MutableStateFlow(false)
    val testRouteObserved=MutableStateFlow(false)
    private var testStarted=0L
    init {
        viewModelScope.launch { repository.settings.collect { prefs.value=it; loaded.value=true } }
        viewModelScope.launch {
            if(repository.interruptedSession.first() && !session.value.running)
                message.value="La session précédente a été interrompue. La surveillance est arrêtée."
        }
        viewModelScope.launch { tester.status.collect {
            if(testStarted>0 && it.routeObserved && !it.interrupted) testRouteObserved.value=true
            if(it.interrupted) { testHeard.value=false; testRouteObserved.value=false }
        } }
    }
    fun save(value:Settings) {
        testHeard.value=false; testRouteObserved.value=false
        viewModelScope.launch { runCatching { repository.update(value) }.onFailure { message.value="Impossible d'enregistrer les réglages" } }
    }
    fun acceptOnboarding()=save(settings.value.copy(onboarded=true))
    fun refreshOutputs() { outputs.value=tester.devices() }
    fun test() {
        if(session.value.running) return
        testHeard.value=false; testRouteObserved.value=false; testStarted=SystemClock.elapsedRealtime()
        tester.test(settings.value); refreshOutputs()
    }
    fun confirmHeard() { if(testRouteObserved.value) testHeard.value=true }
    fun stopTest() { tester.stop() }
    fun prepareStart():Boolean {
        if(tester.volume()==0) { message.value="Le volume multimédia est à zéro. Réglez-le puis testez l'alerte."; return false }
        tester.stop(); testStarted=0; return true
    }
    fun clearTestForNextSession() { testHeard.value=false; testRouteObserved.value=false }
    fun resetCalibration() { viewModelScope.launch { repository.resetCalibration() } }
    override fun onCleared() { tester.close(); super.onCleared() }
}
