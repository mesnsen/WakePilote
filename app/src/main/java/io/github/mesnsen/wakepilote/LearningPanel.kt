package io.github.mesnsen.wakepilote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mesnsen.wakepilote.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LearningPanel(vm:WakeViewModel,service:MonitoringService?,session:SessionUi,onPrepare:()->Unit) {
    val ui by vm.learning.collectAsStateWithLifecycle()
    var targetName by rememberSaveable { mutableStateOf(TrainingLabel.EYES_OPEN.name) }
    val target=TrainingLabel.valueOf(targetName)
    var confirmClear by remember { mutableStateOf(false) }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) vm.exportLearning(uri)
    }
    val recording=ui.stage==LearningStage.COUNTDOWN || ui.stage==LearningStage.CAPTURING
    Text("Apprendre avec\nmon visage",fontSize=30.sp,fontWeight=FontWeight.SemiBold,lineHeight=36.sp)
    Text("À l'arrêt : enregistrez un mouvement, vérifiez le résultat, puis gardez l'exemple ou corrigez-le.",color=Muted)
    Note("Aucune alerte de conduite dans ce mode. Un seul visage doit être présent.",Amber)
    if(session.preparing) CameraPreview(service,session,vm::saveZoom,controlsEnabled=!recording)
    else Button(onClick=onPrepare,enabled=!ui.saving,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Activer la caméra d'apprentissage") }
    if(session.preparing) Text("${qualityLabel(session.quality)} · ${"%.1f".format(session.metrics.observationsPerSecond)} mesures/s",color=Muted,fontSize=12.sp)
    ui.error?.let { Note(it,Coral) }
    ui.message?.let { Note(it,Mint) }
    when(ui.stage) {
        LearningStage.IDLE -> {
            Text("1 · Choisir le mouvement",fontWeight=FontWeight.SemiBold,fontSize=19.sp)
            TrainingChoices(target,!ui.loading && !ui.saving) { targetName=it.name }
            Text(when(target) {
                TrainingLabel.EYES_OPEN -> "Gardez les yeux naturellement ouverts pendant l'enregistrement."
                TrainingLabel.EYES_CLOSED -> "Fermez les deux yeux après le compte à rebours, puis rouvrez-les à la fin."
                TrainingLabel.BLINK -> "Clignez une ou deux fois des yeux, en commençant et en terminant yeux ouverts."
                TrainingLabel.YAWN -> "Simulez un bâillement pendant la séquence, sans chercher à provoquer de fatigue."
                else -> "Parlez ou souriez : ces exemples aident à distinguer une bouche ouverte d'un bâillement."
            },color=Muted,fontSize=14.sp)
            Button(onClick={vm.startCapture(target)},enabled=session.preparing && !ui.loading && !ui.saving && ui.clips.size<TrainingCapture.MAX_CLIPS,
                modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Enregistrer 3 secondes") }
            Text("Départ dans 2 secondes. Une vibration marque le début et la fin si elle est activée. Variez le cadrage et la lumière entre les essais.",color=Muted,fontSize=12.sp)
        }
        LearningStage.COUNTDOWN -> {
            Text("Prêt dans ${ui.countdown}…",fontSize=32.sp,color=Amber,fontWeight=FontWeight.Bold)
            Text("Préparez le mouvement choisi, puis maintenez votre position.",color=Muted)
            OutlinedButton(onClick=vm::cancelCapture) { Text("Annuler") }
        }
        LearningStage.CAPTURING -> {
            Text("Enregistrement en cours",fontSize=23.sp,color=Mint,fontWeight=FontWeight.Bold)
            LinearProgressIndicator(progress={ui.progress},modifier=Modifier.fillMaxWidth())
            Text("${ui.frameCount} mesures · ${"%.1f".format((1-ui.progress)*3)} s restantes",color=Muted)
            OutlinedButton(onClick=vm::cancelCapture) { Text("Annuler") }
        }
        LearningStage.REVIEW -> ui.draft?.let { draft ->
            LearningReview(draft,ui.saving,vm::validateDraft,vm::discardDraft)
        }
    }
    HorizontalDivider()
    Text("Mes exemples · ${ui.clips.size} / ${TrainingCapture.MAX_CLIPS}",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
    if(ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(ui.clips.isNotEmpty()) Text(ui.clips.groupingBy { it.actual }.eachCount().entries.joinToString(" · ") { "${it.key.title} : ${it.value}" },color=Muted,fontSize=13.sp)
    Text("Les exemples validés sont sauvegardés sur ce téléphone. Les seuils d'alerte ne changent pas automatiquement.",fontSize=13.sp,color=Muted)
    Button(onClick={
        val stamp=SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())
        exporter.launch("WakePilote-apprentissage-$stamp.json")
    },enabled=ui.clips.isNotEmpty() && !ui.loading && !ui.saving && ui.stage==LearningStage.IDLE,
        modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) { Text(if(ui.saving) "Enregistrement…" else "Exporter mes exemples (JSON)") }
    Text("Choisissez où enregistrer le fichier, puis joignez-le ici pour analyser les erreurs et améliorer l'algorithme. L'export contient les mesures, les repères et vos corrections, sans photos ni vidéo.",color=Muted,fontSize=12.sp)
    if(ui.clips.isNotEmpty()) {
        ui.clips.takeLast(5).reversed().forEach { clip ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(clip.actual.title,fontWeight=FontWeight.SemiBold)
                    Text("${clip.draft.frames.size} mesures · ${if(clip.decision==TrainingDecision.CORRECTED) "corrigé" else "confirmé"}",color=Muted,fontSize=12.sp)
                }
                TextButton(onClick={vm.removeLearning(clip.id)},enabled=!ui.saving && ui.stage==LearningStage.IDLE) { Text("Supprimer") }
            }
        }
        TextButton(onClick={confirmClear=true},enabled=!ui.saving && ui.stage==LearningStage.IDLE) { Text("Effacer tous mes exemples") }
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Effacer les exemples ?")},
        text={Text("Les exemples conservés dans l'application seront supprimés. Les fichiers déjà exportés resteront disponibles.")},
        confirmButton={TextButton(onClick={confirmClear=false;vm.removeLearning()}) { Text("Effacer") }},
        dismissButton={TextButton(onClick={confirmClear=false}) { Text("Annuler") }})
}

@Composable
internal fun LearningReview(draft:TrainingDraft,saving:Boolean,onValidate:(TrainingLabel,Boolean)->Unit,onDiscard:()->Unit) {
    var correcting by remember(draft) { mutableStateOf(false) }
    var actual by remember(draft) { mutableStateOf(draft.expected) }
    Text("2 · Vérifier le résultat",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
    Text("L'application propose :",color=Muted)
    Text(draft.predicted.title,fontSize=25.sp,color=Mint,fontWeight=FontWeight.Bold)
    Text("${draft.frames.size} mesures reçues · ${draft.goodFrames} avec visage exploitable",color=Muted,fontSize=13.sp)
    if(draft.largestGapMs>500) Note("Cette séquence contient une interruption de ${draft.largestGapMs} ms. Elle peut être gardée pour analyser ce problème.",Amber)
    if(!correcting) {
        Text("Ce résultat correspond-il à votre mouvement ?")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Button(onClick={onValidate(draft.predicted,false)},enabled=!saving && draft.predicted!=TrainingLabel.UNKNOWN,
                modifier=Modifier.weight(1f).heightIn(min=52.dp)) { Text("OK · garder") }
            OutlinedButton(onClick={correcting=true},enabled=!saving,modifier=Modifier.weight(1f).heightIn(min=52.dp)) { Text("Pas OK · corriger") }
        }
    } else {
        Text("Quel mouvement avez-vous réellement fait ?",fontWeight=FontWeight.SemiBold)
        TrainingChoices(actual,!saving) { actual=it }
        Button(onClick={onValidate(actual,true)},enabled=!saving,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Garder la correction") }
    }
    TextButton(onClick=onDiscard,enabled=!saving) { Text("Jeter cet essai") }
}

@Composable
private fun TrainingChoices(selected:TrainingLabel,enabled:Boolean,onSelect:(TrainingLabel)->Unit) {
    TrainingLabel.entries.filter { it!=TrainingLabel.UNKNOWN }.chunked(2).forEach { choices ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            choices.forEach { label -> FilterChip(selected=selected==label,onClick={onSelect(label)},enabled=enabled,
                label={Text(label.title)},modifier=Modifier.weight(1f)) }
            if(choices.size==1) Spacer(Modifier.weight(1f))
        }
    }
}
