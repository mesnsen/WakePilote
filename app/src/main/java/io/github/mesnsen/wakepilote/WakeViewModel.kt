package io.github.mesnsen.wakepilote

import android.app.Application
import android.os.SystemClock
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mesnsen.wakepilote.audio.AlertPlayer
import io.github.mesnsen.wakepilote.audio.OutputDevice
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.data.SettingsRepository
import io.github.mesnsen.wakepilote.data.LearningRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WakeViewModel(app:Application):AndroidViewModel(app) {
    private val repository=SettingsRepository(app)
    private val learningRepository=LearningRepository(app)
    val learning=MutableStateFlow(LearningUi())
    private var capture:TrainingCapture?=null
    private var captureJob:Job?=null
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
        viewModelScope.launch {
            runCatching { learningRepository.load() }
                .onSuccess { clips->learning.update { it.copy(clips=clips,loading=false) } }
                .onFailure { e->learning.update { it.copy(loading=false,error="Lecture des exemples impossible : ${e.message}") } }
        }
        viewModelScope.launch { SessionHub.samples.collect { sample ->
            capture?.add(sample.observation,sample.metrics.processingMs,sample.zoom,sample.features)
        } }
        viewModelScope.launch { session.collect { if(!it.preparing) cancelCapture() } }
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
    fun saveZoom(value:Float) { viewModelScope.launch { runCatching { repository.zoom(value) }.onFailure { message.value="Zoom non enregistré" } } }

    fun startCapture(expected:TrainingLabel) {
        val ui=learning.value
        if(!session.value.preparing || session.value.running || ui.loading || ui.saving || ui.stage!=LearningStage.IDLE || ui.clips.size>=TrainingCapture.MAX_CLIPS) return
        tester.stop()
        captureJob=viewModelScope.launch {
            learning.update { it.copy(stage=LearningStage.COUNTDOWN,countdown=2,error=null,message=null) }
            for(second in 2 downTo 1) {
                learning.update { it.copy(countdown=second) }; delay(1000)
            }
            val started=SystemClock.elapsedRealtime()
            val recorder=TrainingCapture(expected,settings.value.copy(cameraZoom=session.value.zoom.requested),started)
            capture=recorder
            trainingPulse(false)
            learning.update { it.copy(stage=LearningStage.CAPTURING,progress=0f,frameCount=0) }
            while(SystemClock.elapsedRealtime()-started<3000) {
                delay(100)
                learning.update { it.copy(progress=((SystemClock.elapsedRealtime()-started)/3000f).coerceAtMost(1f),frameCount=recorder.count) }
            }
            capture=null
            trainingPulse(true)
            val draft=recorder.finish()
            learning.update { it.copy(stage=if(draft==null) LearningStage.IDLE else LearningStage.REVIEW,draft=draft,
                error=if(draft==null) "Aucune mesure reçue. Vérifiez la caméra puis réessayez." else null) }
            captureJob=null
        }
    }
    fun cancelCapture() {
        if(learning.value.stage!=LearningStage.COUNTDOWN && learning.value.stage!=LearningStage.CAPTURING) return
        captureJob?.cancel(); captureJob=null; capture=null
        learning.update { it.copy(stage=LearningStage.IDLE,progress=0f,message="Enregistrement annulé. Aucun exemple ajouté.") }
    }
    private fun trainingPulse(finished:Boolean) {
        if(!settings.value.vibrate) return
        val context=getApplication<Application>()
        val vibrator=if(android.os.Build.VERSION.SDK_INT>=31) context.getSystemService(android.os.VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator)
        if(vibrator.hasVibrator()) runCatching {
            if(finished) vibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0,70,90,70),-1))
            else vibrator.vibrate(android.os.VibrationEffect.createOneShot(70,android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    fun discardDraft() { if(!learning.value.saving) learning.update { it.copy(stage=LearningStage.IDLE,draft=null,message=null) } }
    fun validateDraft(actual:TrainingLabel,corrected:Boolean) {
        val draft=learning.value.draft ?: return
        if(learning.value.saving || actual==TrainingLabel.UNKNOWN || (!corrected && actual!=draft.predicted)) return
        learning.update { it.copy(saving=true,error=null) }
        viewModelScope.launch {
            runCatching { learningRepository.add(TrainingClip(java.util.UUID.randomUUID().toString(),System.currentTimeMillis(),actual,
                if(corrected) TrainingDecision.CORRECTED else TrainingDecision.CONFIRMED,draft)) }
                .onSuccess { clips -> learning.update { it.copy(stage=LearningStage.IDLE,draft=null,clips=clips,saving=false,message="Exemple enregistré : ${actual.title}.") } }
                .onFailure { e -> learning.update { it.copy(saving=false,error="Enregistrement impossible : ${e.message}") } }
        }
    }
    fun removeLearning(id:String?=null) {
        if(learning.value.saving || learning.value.loading || learning.value.stage!=LearningStage.IDLE) return
        learning.update { it.copy(saving=true,error=null) }
        viewModelScope.launch {
            runCatching { if(id==null) learningRepository.clear() else learningRepository.remove(id) }
                .onSuccess { clips -> learning.update { it.copy(clips=clips,saving=false,message="Exemples mis à jour.") } }
                .onFailure { e -> learning.update { it.copy(saving=false,error="Suppression impossible : ${e.message}") } }
        }
    }
    fun exportLearning(uri:Uri) {
        if(learning.value.saving) return
        learning.update { it.copy(saving=true,error=null) }
        viewModelScope.launch {
            runCatching {
                val context=getApplication<Application>()
                val version=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "0.1.2"
                val json=learningRepository.export(version)
                withContext(Dispatchers.IO) {
                    checkNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "Destination inaccessible" }
                        .bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
            }.onSuccess { learning.update { it.copy(saving=false,message="Fichier JSON exporté. Vous pouvez le joindre dans cette conversation.") } }
                .onFailure { e -> learning.update { it.copy(saving=false,error="Export impossible : ${e.message}") } }
        }
    }
    override fun onCleared() { tester.close(); super.onCleared() }
}
