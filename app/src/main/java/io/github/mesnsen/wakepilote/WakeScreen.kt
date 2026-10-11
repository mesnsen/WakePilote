package io.github.mesnsen.wakepilote

import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.audio.AudioStatus
import io.github.mesnsen.wakepilote.vision.FaceOverlayGeometry

internal val Navy=Color(0xFF0D1822)
internal val Panel=Color(0xFF182733)
internal val Mint=Color(0xFF9EE5C6)
internal val Amber=Color(0xFFF4C775)
internal val Coral=Color(0xFFFFB4A8)
internal val Muted=Color(0xFFAAC0CD)

@Composable
fun WakeTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=darkColorScheme(primary=Mint,onPrimary=Navy,background=Navy,surface=Panel,
        onBackground=Color(0xFFF3F7F8),onSurface=Color(0xFFF3F7F8),secondary=Amber,error=Coral),content=content)
}

@Composable
fun WakeScreen(vm:WakeViewModel,service:MonitoringService?,onPrepare:()->Unit,onStart:()->Unit,
    onStop:()->Unit,onSilence:()->Unit,onDim:(Boolean)->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val testAudio by vm.audio.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val heard by vm.testHeard.collectAsStateWithLifecycle()
    val testRoute by vm.testRouteObserved.collectAsStateWithLifecycle()
    var parameters by rememberSaveable { mutableStateOf(false) }
    var learning by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable { mutableStateOf(true) }
    var dim by rememberSaveable { mutableStateOf(false) }
    var diagnostic by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(session.running) { if(!session.running) { dim=false; onDim(false) } else preview=false }
    DisposableEffect(Unit) { onDispose { onDim(false) } }
    DisposableEffect(service,learning) {
        service?.setLearningEnabled(learning)
        onDispose { service?.setLearningEnabled(false) }
    }
    WakeTheme {
        Surface(modifier=Modifier.fillMaxSize(),color=Navy) {
            if(!loaded) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() }
            else if(!settings.onboarded) Onboarding(vm::acceptOnboarding)
            else Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp,vertical=20.dp),
                verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    EyeMark(Modifier.size(38.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("WakePilote",fontWeight=FontWeight.Bold,fontSize=24.sp)
                        Text("VOTRE COMPAGNON DE PAUSE",fontSize=10.sp,letterSpacing=1.4.sp,color=Muted)
                    }
                    if(!session.running) TextButton(onClick={learning=false;parameters=!parameters;vm.refreshOutputs()}) {
                        Text(if(parameters) "Fermer" else stringResource(R.string.settings))
                    }
                }
                message?.let { Note(it,Amber) { vm.message.value=null } }
                session.error?.let { Note(it,Coral) }
                if(!session.running && !parameters) Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected=!learning,onClick={learning=false},label={Text("Préparation")})
                    FilterChip(selected=learning,onClick={learning=true;vm.stopTest()},label={Text("Apprentissage")})
                }
                if(learning && !session.running) LearningPanel(vm,service,session,onPrepare)
                else if(parameters && !session.running) SettingsPanel(settings,vm,service,session,onPrepare)
                else {
                    val urgent=session.snapshot.level==AlertLevel.URGENT
                    if(session.running && urgent) {
                        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF482A27)),shape=RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                Text("FAITES UNE PAUSE",color=Coral,fontSize=13.sp,letterSpacing=1.sp,fontWeight=FontWeight.Bold)
                                Text(stringResource(R.string.safety_message),fontSize=27.sp,fontWeight=FontWeight.Bold,lineHeight=33.sp)
                                Button(onClick=onSilence,modifier=Modifier.fillMaxWidth().heightIn(min=76.dp),
                                    colors=ButtonDefaults.buttonColors(containerColor=Coral,contentColor=Navy),shape=RoundedCornerShape(18.dp)) {
                                    Text(stringResource(R.string.stop_alert),fontSize=22.sp,fontWeight=FontWeight.Bold)
                                }
                                if(!session.snapshot.soundRequested) Text("Son acquitté. La surveillance continue.",color=Muted)
                            }
                        }
                    } else {
                        val unavailable=session.running && (session.error!=null || session.snapshot.state==MonitorState.MONITORING_UNAVAILABLE)
                        Text(when {
                            unavailable -> "Le suivi est indisponible."
                            session.running && session.snapshot.state==MonitorState.INITIALIZING -> "Le suivi se prépare."
                            session.running -> "Le suivi est en cours."
                            else -> "Avant de prendre\nla route."
                        },fontSize=34.sp,fontWeight=FontWeight.SemiBold,lineHeight=40.sp)
                        Text(if(unavailable) "Aucun suivi fiable du visage pour le moment. Vérifiez le cadrage à l'arrêt."
                            else if(session.running) "Restez attentif aux signes de fatigue et arrêtez-vous en sécurité."
                            else "Installez votre téléphone à l'arrêt, vérifiez le cadrage et testez votre alerte.",color=Muted,fontSize=16.sp,lineHeight=23.sp)
                    }
                    if(session.snapshot.pauseRecommended) Note("Alertes répétées. Une pause est fortement recommandée.",Amber)
                    if(session.snapshot.state==MonitorState.RECOVERING) Note("Son arrêté. La réouverture des yeux ne prouve pas que vous pouvez continuer à conduire.",Amber)
                    if(session.running && session.snapshot.state==MonitorState.SUSPECTED_DROWSINESS) Note("Signes de fatigue possibles — prévoyez une pause.",Amber)
                    if(session.foregroundOnly && session.running) Note("Mode visible uniquement. Ouvrir le GPS ou verrouiller l'écran terminera cette session.",Amber)
                    StatusCard("01",if(session.running) "ANALYSE DU VISAGE" else "CADRAGE DU VISAGE",
                        if(session.running || session.preparing) qualityLabel(session.quality) else "Positionnez votre téléphone",
                        if(session.running) session.snapshot.reason else "Surveillance arrêtée",session.quality!=Quality.GOOD)
                    if((session.preparing || session.running) && preview) CameraPreview(service,session,vm::saveZoom)
                    if(session.running || session.preparing) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Aperçu caméra",Modifier.weight(1f),color=Muted)
                        Switch(checked=preview,onCheckedChange={preview=it;if(!it) service?.setPreviewSurface(null)})
                    }
                    if(!session.running && !session.preparing) OutlinedButton(onClick=onPrepare,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                        Text(stringResource(R.string.prepare_camera))
                    }
                    val audio=if(session.running) session.audio else testAudio
                    StatusCard("02","ALERTE SONORE",audio.route,
                        if(audio.detail.isNotBlank()) audio.detail else if(heard) "Dernier test entendu à l'arrêt" else "Test recommandé avant chaque départ",audio.interrupted)
                    if(session.running) {
                        if(audio.playing && !urgent) OutlinedButton(onClick=onSilence,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) { Text(stringResource(R.string.stop_alert)) }
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick={dim=!dim;onDim(dim)},modifier=Modifier.weight(1f).heightIn(min=54.dp)) { Text(if(dim) "Éclaircir" else "Assombrir") }
                            OutlinedButton(onClick=onStop,modifier=Modifier.weight(1f).heightIn(min=54.dp)) { Text(stringResource(R.string.stop_monitoring)) }
                        }
                    } else {
                        OutlinedButton(onClick={if(testAudio.playing) vm.stopTest() else vm.test()},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                            Text(if(testAudio.playing) "Arrêter le test" else stringResource(R.string.test_alert))
                        }
                        if(testRoute && !heard) TextButton(onClick=vm::confirmHeard,modifier=Modifier.fillMaxWidth()) { Text("J'ai entendu le son") }
                        if(heard) Text("Test entendu. Retestez si la sortie ou le volume change.",color=Mint,fontSize=13.sp)
                        Button(onClick={parameters=false;onStart()},enabled=session.calibrationProgress==null,
                            modifier=Modifier.fillMaxWidth().heightIn(min=72.dp),shape=RoundedCornerShape(20.dp)) {
                            Text(stringResource(R.string.start_monitoring),fontSize=19.sp,fontWeight=FontWeight.Bold)
                        }
                        if(session.preparing) TextButton(onClick=onStop,modifier=Modifier.fillMaxWidth()) { Text("Arrêter la caméra de préparation") }
                    }
                    Text(stringResource(R.string.privacy),fontSize=12.sp,color=Muted)
                    Text(stringResource(R.string.limits),fontSize=12.sp,color=Muted,lineHeight=17.sp)
                    TextButton(onClick={diagnostic=!diagnostic}) { Text(if(diagnostic) "Masquer les diagnostics" else "Diagnostics du prototype",fontSize=12.sp) }
                    if(diagnostic) DiagnosticPanel(session,if(session.running) session.audio else testAudio)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Onboarding(onAccept:()->Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(26.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(12.dp)); EyeMark(Modifier.size(72.dp))
        Text("Bienvenue dans\nWakePilote.",fontSize=38.sp,fontWeight=FontWeight.Bold,lineHeight=44.sp)
        Text("Une alerte pour penser à faire une pause.",color=Mint,fontSize=20.sp)
        StatusCard("1","PRÉPAREZ À L'ARRÊT","Placez le téléphone face à vous","Un support stable, sans gêner votre vue. Le visage et les deux yeux doivent être visibles.")
        StatusCard("2","VOTRE VISAGE RESTE LOCAL","La caméra sert uniquement à l'analyse","Aucune image sauvegardée, aucun compte, aucun envoi dans le cloud.")
        StatusCard("3","TESTEZ ET CONNAISSEZ LES LIMITES","Une alerte ne remplace pas une pause","Le son peut être interrompu par Android. La nuit et les lunettes opaques peuvent empêcher la détection. Les notifications permettent d'arrêter le son depuis le GPS.")
        Text("Version expérimentale : premiers essais uniquement à l'arrêt.",color=Amber)
        Button(onClick=onAccept,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) { Text("J'ai compris — préparer mon trajet") }
    }
}

@Composable
private fun StatusCard(number:String,label:String,title:String,description:String,warning:Boolean=false) {
    Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
        Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(number,color=if(warning) Amber else Mint,fontWeight=FontWeight.Bold,fontSize=18.sp)
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(label,fontSize=10.sp,color=Muted,letterSpacing=1.2.sp)
                Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                Text(description,fontSize=13.sp,color=Muted,lineHeight=18.sp)
            }
        }
    }
}
@Composable
internal fun Note(text:String,color:Color,onDismiss:(()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color.copy(alpha=.11f)).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(text,color=color,modifier=Modifier.weight(1f),fontSize=14.sp)
        onDismiss?.let { TextButton(onClick=it) { Text("OK",color=color) } }
    }
}
@Composable
internal fun CameraPreview(service:MonitoringService?,session:SessionUi,onZoomSaved:(Float)->Unit={},controlsEnabled:Boolean=true) {
    val context=LocalContext.current
    val landmarks by SessionHub.preview.collectAsStateWithLifecycle()
    val view=remember { PreviewView(context).apply { implementationMode=PreviewView.ImplementationMode.COMPATIBLE; scaleType=PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(service,view) { service?.setPreviewSurface(view.surfaceProvider); onDispose { service?.setPreviewSurface(null) } }
    Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
        Box(Modifier.matchParentSize().graphicsLayer { scaleX=session.zoom.previewScale; scaleY=session.zoom.previewScale }) {
        AndroidView(factory={view},modifier=Modifier.fillMaxSize(),update={service?.setPreviewSurface(it.surfaceProvider)})
        Canvas(Modifier.fillMaxSize()) {
            // Preview and points share the same extra display zoom, including unsupported camera zoom.
            val transform=view.sensorToViewTransform ?: return@Canvas
            val frame=landmarks ?: return@Canvas
            if(android.os.SystemClock.elapsedRealtime()-frame.atMs>500) return@Canvas
            val color=if(session.quality==Quality.GOOD) Mint else Amber
            frame.faces.forEach { face ->
                FaceOverlayGeometry.mapToPreview(face,transform).forEachIndexed { index,point ->
                    if(point.x.isFinite() && point.y.isFinite() && point.x in 0f..size.width && point.y in 0f..size.height) {
                        val radius=if(index in FaceDetailPoints) 2.dp.toPx() else 1.dp.toPx()
                        val center=Offset(point.x,point.y)
                        drawCircle(Color.Black.copy(alpha=.5f),radius+.5.dp.toPx(),center)
                        drawCircle(color.copy(alpha=if(index in FaceDetailPoints) 1f else .7f),radius,center)
                    }
                }
            }
        }
        }
        val count=landmarks?.faces?.sumOf { it.size } ?: 0
        Text(if(count>0) "$count POINTS · LOCAL" else "APERÇU LOCAL · EN ATTENTE DU VISAGE",
            Modifier.align(Alignment.BottomStart).padding(12.dp).background(Navy.copy(alpha=.8f)).padding(6.dp),color=Color.White,fontSize=10.sp)
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Zoom ${"%.2f".format(session.zoom.requested)}×",Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
            TextButton(enabled=controlsEnabled,onClick={service?.setZoom(1f);onZoomSaved(1f)}) { Text("1×") }
            TextButton(enabled=controlsEnabled && landmarks!=null,onClick={
                val transform=view.sensorToViewTransform
                val face=landmarks?.faces?.singleOrNull()
                if(transform!=null && face!=null && face.isNotEmpty()) {
                    val points=FaceOverlayGeometry.mapToPreview(face,transform)
                    val width=points.maxOf { it.x }-points.minOf { it.x }
                    val height=points.maxOf { it.y }-points.minOf { it.y }
                    if(width>1 && height>1) {
                        val zoom=(session.zoom.actual*minOf(view.width*.80f/width,view.height*.85f/height)).coerceIn(1f,3f)
                        service?.setZoom(zoom);onZoomSaved(zoom)
                    }
                }
            }) { Text("Ajuster au visage") }
        }
        Slider(value=session.zoom.requested,onValueChange={service?.setZoom(it)},onValueChangeFinished={onZoomSaved(session.zoom.requested)},
            valueRange=1f..3f,enabled=controlsEnabled)
        Text(if(session.zoom.previewScale>1.02f) "Zoom d'aperçu complémentaire : il agrandit l'image sans ajouter de détails à l'analyse."
            else "Centrez un seul visage. Gardez le front et le menton visibles.",fontSize=12.sp,color=Muted)
    }
}
@Composable
private fun SettingsPanel(settings:Settings,vm:WakeViewModel,service:MonitoringService?,session:SessionUi,onPrepare:()->Unit) {
    val devices by vm.outputs.collectAsStateWithLifecycle()
    var gain by remember(settings.gain) { mutableFloatStateOf(settings.gain) }
    Text("Réglages avant le départ",fontSize=26.sp,fontWeight=FontWeight.Bold)
    Text("Les réglages sont figés pendant une session.",color=Muted)
    SettingToggle("Sensibilité renforcée","Plus sensible, avec davantage de fausses alertes possibles.",settings.reinforced) { vm.save(settings.copy(reinforced=it)) }
    Text("Intensité de l'alerte : ${(gain*100).toInt()} %",fontWeight=FontWeight.SemiBold)
    Slider(value=gain,onValueChange={gain=it},valueRange=.1f..1f,onValueChangeFinished={vm.save(settings.copy(gain=gain))})
    Text("Le volume multimédia d'Android reste prioritaire.",fontSize=12.sp,color=Muted)
    SettingToggle("Vibration","En complément du signal sonore.",settings.vibrate) { vm.save(settings.copy(vibrate=it)) }
    Text("Sortie audio",fontWeight=FontWeight.SemiBold)
    OutputPreference.entries.forEach { value ->
        Row(Modifier.fillMaxWidth().clickable { vm.save(settings.copy(output=value)) }.heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected=settings.output==value,onClick={vm.save(settings.copy(output=value))})
            Text(when(value) { OutputPreference.AUTO->"Automatique";OutputPreference.PHONE->"Smartphone";OutputPreference.CAR->"Sortie voiture" })
        }
    }
    if(settings.output==OutputPreference.CAR) {
        if(devices.isEmpty()) Note("Aucune sortie Bluetooth multimédia trouvée. Le téléphone sera demandé en repli.",Amber)
        devices.forEach { device -> Row(Modifier.fillMaxWidth().clickable {vm.save(settings.copy(carDeviceId=device.id))},verticalAlignment=Alignment.CenterVertically) {
            RadioButton(selected=settings.carDeviceId==device.id,onClick={vm.save(settings.copy(carDeviceId=device.id))}); Text(device.label)
        } }
        TextButton(onClick=vm::refreshOutputs) { Text("Actualiser les sorties") }
    }
    Text("Calibration facultative",fontSize=20.sp,fontWeight=FontWeight.SemiBold)
    Text(if(settings.calibrated) "Référence personnelle enregistrée localement." else "Référence générique utilisée. Une calibration peut améliorer l'adaptation à vos yeux.",color=Muted)
    Text("À l'arrêt, regardez naturellement devant vous, yeux ouverts pendant 3 à 5 secondes.",fontSize=14.sp)
    if(session.preparing) {
        CameraPreview(service,session,vm::saveZoom)
        session.calibrationProgress?.let { LinearProgressIndicator(progress={it},modifier=Modifier.fillMaxWidth()) }
        Button(onClick={service?.calibrate()},enabled=session.calibrationProgress==null,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text("Calibrer mes yeux") }
    } else OutlinedButton(onClick=onPrepare,modifier=Modifier.fillMaxWidth()) { Text("Activer la caméra pour calibrer") }
    session.calibrationMessage?.let { Note(it,Mint) }
    if(settings.calibrated) TextButton(onClick=vm::resetCalibration) { Text("Supprimer ma calibration") }
}
@Composable
private fun SettingToggle(title:String,detail:String,checked:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title,fontWeight=FontWeight.SemiBold); Text(detail,color=Muted,fontSize=12.sp) }
        Switch(checked=checked,onCheckedChange=onChange)
    }
}
@Composable
private fun DiagnosticPanel(s:SessionUi,audio:AudioStatus) {
    val p=s.snapshot
    Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("Mesures expérimentales — aucune estimation d'aptitude à conduire",color=Amber,fontSize=12.sp)
        Text("État : ${p.state}\nQualité : ${s.quality}\nYeux : ${p.left} / ${p.right}\nFermeture observée : ${p.closedMs} ms")
        Text("PERCLOS estimé : ${p.perclos?.let { "%.1f %%".format(it*100) } ?: "Données insuffisantes"}\nCouverture : ${"%.0f".format(p.coverage*100)} %")
        Text("Traitement : ${s.metrics.processingMs} ms\nCadence observée : ${"%.1f".format(s.metrics.observationsPerSecond)} / s\nÉpisode : ${p.episode}")
        s.visionDiagnostic?.let { Text("Détail analyse : $it",color=Coral) }
        audio.diagnostic?.let { Text("Détail audio : $it",color=Coral) }
    } }
}
@Composable
private fun EyeMark(modifier:Modifier) {
    Canvas(modifier) {
        val w=size.width; val h=size.height
        val path=Path().apply { moveTo(w*.04f,h*.5f); cubicTo(w*.30f,h*.13f,w*.70f,h*.13f,w*.96f,h*.5f); cubicTo(w*.70f,h*.87f,w*.30f,h*.87f,w*.04f,h*.5f) }
        drawPath(path,Mint,style=Stroke(w*.055f))
        drawCircle(Amber,w*.105f)
    }
}
internal fun qualityLabel(q:Quality)=when(q) {
    Quality.GOOD->"Visage détecté"
    Quality.NO_FACE->"Positionnez votre visage face au téléphone"
    Quality.AMBIGUOUS_FACE->"Un seul visage doit rester dans le cadre"
    Quality.POOR_LIGHT->"Lumière insuffisante ou trop forte"
    Quality.POSE->"Ajustez l'angle du téléphone à l'arrêt"
    Quality.GEOMETRY->"Les deux yeux ne sont pas assez visibles"
    Quality.CAMERA_ERROR->"Caméra indisponible"
    Quality.ANALYSIS_ERROR->"Analyse du visage interrompue"
    Quality.THERMAL->"Téléphone trop chaud — analyse suspendue"
    Quality.STALE->"Analyse interrompue ou trop lente"
}

private val FaceDetailPoints=setOf(33,160,158,133,153,144,362,385,387,263,373,380,
    61,40,37,0,267,270,291,321,314,17,84,91,13,14,468,469,470,471,472,473,474,475,476,477)
