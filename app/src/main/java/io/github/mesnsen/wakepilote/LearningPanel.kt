package io.github.mesnsen.wakepilote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    var confirmClear by remember { mutableStateOf(false) }
    DisposableEffect(vm) { vm.setLearningActive(true); onDispose { vm.setLearningActive(false) } }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) vm.exportLearning(uri)
    }
    Text("Apprendre avec\nmon visage",fontSize=30.sp,fontWeight=FontWeight.SemiBold,lineHeight=36.sp)
    Text("Choisissez une détection, faites le mouvement, puis classez-le d'un appui.",color=Muted)
    TrainingTargetDropdown(ui.target,!ui.loading && !ui.saving,vm::selectLearningTarget)
    Text(when(ui.target) {
        TrainingLabel.EYES_OPEN -> "Exemple correct : gardez les yeux ouverts. Contre-exemple : fermez-les ou plissez-les."
        TrainingLabel.EYES_CLOSED -> "Exemple correct : fermez les deux yeux, puis appuyez. Contre-exemple : yeux ouverts ou simplement plissés."
        TrainingLabel.BLINK -> "Exemple correct : clignez des yeux, puis appuyez. Contre-exemple : gardez les yeux ouverts ou fermés sans cligner."
        TrainingLabel.YAWN -> "Exemple correct : simulez un bâillement, puis appuyez. Contre-exemple : parlez, souriez ou ouvrez brièvement la bouche."
        TrainingLabel.HEAD_NOD -> "Exemple correct : baissez la tête puis relevez-la avant d'appuyer. Contre-exemple : regardez sur le côté ou gardez la tête stable."
        else -> "Exemple correct : parlez ou souriez. Contre-exemple : gardez le visage au repos."
    },color=Muted,fontSize=14.sp)
    if(session.preparing) {
        // The live preview always includes the existing landmark overlay in learning mode.
        CameraPreview(service,session,vm::saveZoom,controlsEnabled=!ui.saving)
        Text("${qualityLabel(session.quality)} · ${"%.1f".format(session.metrics.observationsPerSecond)} mesures/s",color=Muted,fontSize=12.sp)
    } else Button(onClick=onPrepare,enabled=!ui.saving,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
        Text("Activer la caméra d'apprentissage")
    }
    LearningLabelButtons(ui.target,session.preparing && ui.ready && !ui.loading && !ui.saving && ui.clips.size<TrainingCapture.MAX_CLIPS,
        vm::saveLearningExample)
    Text(when {
        ui.saving -> "Sauvegarde en cours…"
        ui.clips.size>=TrainingCapture.MAX_CLIPS -> "Limite atteinte. Exportez vos exemples puis supprimez-en pour continuer."
        !session.preparing -> "Activez la caméra pour préparer un exemple."
        !ui.ready -> "En attente de mesures récentes du visage…"
        else -> "Prêt · ${ui.frameCount} mesures disponibles"
    },color=if(ui.ready) Mint else Muted,fontSize=13.sp)
    Text("Chaque appui sauvegarde jusqu'à 3 secondes de mesures précédentes. Maintenez la position ou terminez le mouvement juste avant d'appuyer. Aucun compte à rebours ni validation supplémentaire.",color=Muted,fontSize=12.sp)
    Text("Aucune photo ni vidéo enregistrée. Seuls les points du visage, les mesures et votre choix sont conservés après l'appui.",color=Muted,fontSize=12.sp)
    ui.error?.let { Note(it,Coral) }
    ui.message?.let { Note(it,Mint) }
    Note("À utiliser à l'arrêt. Aucune alerte de conduite dans ce mode. Un seul visage doit être présent.",Amber)
    HorizontalDivider()
    Text("Mes exemples · ${ui.clips.size} / ${TrainingCapture.MAX_CLIPS}",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
    if(ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    if(ui.clips.isNotEmpty()) {
        val correct=ui.clips.count { it.example?.matchesTarget==true }
        val negative=ui.clips.count { it.example?.matchesTarget==false }
        val legacy=ui.clips.count { it.example==null }
        Text("$correct corrects · $negative contre-exemples"+if(legacy>0) " · $legacy anciens" else "",color=Muted,fontSize=13.sp)
    }
    Text("Les exemples sont sauvegardés sur ce téléphone. Les seuils d'alerte ne changent pas automatiquement.",fontSize=13.sp,color=Muted)
    Button(onClick={
        val stamp=SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())
        exporter.launch("WakePilote-apprentissage-$stamp.json")
    },enabled=ui.clips.isNotEmpty() && !ui.loading && !ui.saving,
        modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) { Text("Exporter mes exemples (JSON)") }
    Text("Choisissez où enregistrer le fichier, puis joignez-le dans cette conversation pour analyser les erreurs et améliorer l'algorithme.",color=Muted,fontSize=12.sp)
    if(ui.clips.isNotEmpty()) {
        ui.clips.takeLast(5).reversed().forEach { clip ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(clip.target.title,fontWeight=FontWeight.SemiBold)
                    Text("${clip.annotationTitle} · ${clip.draft.frames.size} mesures",color=Muted,fontSize=12.sp)
                }
                TextButton(onClick={vm.removeLearning(clip.id)},enabled=!ui.saving) { Text("Supprimer") }
            }
        }
        TextButton(onClick={confirmClear=true},enabled=!ui.saving) { Text("Effacer tous mes exemples") }
    }
    if(confirmClear) AlertDialog(onDismissRequest={confirmClear=false},title={Text("Effacer les exemples ?")},
        text={Text("Les exemples conservés dans l'application seront supprimés. Les fichiers déjà exportés resteront disponibles.")},
        confirmButton={TextButton(onClick={confirmClear=false;vm.removeLearning()}) { Text("Effacer") }},
        dismissButton={TextButton(onClick={confirmClear=false}) { Text("Annuler") }})
}

@Composable
internal fun TrainingTargetDropdown(selected:TrainingLabel,enabled:Boolean,onSelect:(TrainingLabel)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text("Détection à apprendre",fontWeight=FontWeight.SemiBold,fontSize=19.sp)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick={expanded=true},enabled=enabled,
            modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("training-target")) {
            Text(selected.title,Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded=expanded && enabled,onDismissRequest={expanded=false}) {
            TrainingLabel.entries.filter { it!=TrainingLabel.UNKNOWN }.forEach { label ->
                DropdownMenuItem(text={Text(label.title)},onClick={expanded=false;onSelect(label)})
            }
        }
    }
}

@Composable
internal fun LearningLabelButtons(target:TrainingLabel,enabled:Boolean,onLabel:(Boolean)->Unit) {
    Text("Ce mouvement correspond-il à « ${target.title} » ?",fontWeight=FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Button(onClick={onLabel(true)},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=64.dp)) {
            Text("Exemple correct")
        }
        OutlinedButton(onClick={onLabel(false)},enabled=enabled,modifier=Modifier.weight(1f).heightIn(min=64.dp)) {
            Text("Contre-exemple")
        }
    }
    Text("Exemple correct : oui, c'est la détection choisie. Contre-exemple : non, ce mouvement ne doit pas être reconnu comme cette détection.",color=Muted,fontSize=12.sp)
}
