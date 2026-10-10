package io.github.mesnsen.wakepilote

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.camera.core.Preview
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import io.github.mesnsen.wakepilote.audio.AlertPlayer
import io.github.mesnsen.wakepilote.core.*
import io.github.mesnsen.wakepilote.data.SettingsRepository
import io.github.mesnsen.wakepilote.vision.FaceCamera
import io.github.mesnsen.wakepilote.vision.VisionMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

class MonitoringService:LifecycleService() {
    inner class LocalBinder:Binder() { val service get()=this@MonitoringService }
    private val binder=LocalBinder()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var repository:SettingsRepository
    private lateinit var player:AlertPlayer
    private var engine=DrowsinessEngine()
    private var camera:FaceCamera?=null
    private var requestedSurface:Preview.SurfaceProvider?=null
    private var settings=Settings()
    private var session=0L
    private var earlySerial=0L
    private var lastUnavailableSound=Long.MIN_VALUE/2
    private var unavailableQuietUntil=0L
    private var lastNotification=""
    private var startedForeground=false
    private var calibration:Calibration?=null
    private var calibrationStarted=0L
    private var thermalListener:PowerManager.OnThermalStatusChangedListener?=null
    private val notifier get()=getSystemService(NotificationManager::class.java)
    private val tick=object:Runnable {
        override fun run() {
            val ui=SessionHub.state.value
            val now=SystemClock.elapsedRealtime()
            if(ui.running) apply(engine.tick(now))
            if(ui.preparing && ui.lastObservationMs>0 && now-ui.lastObservationMs>1500) {
                SessionHub.mutable.update { it.copy(quality=Quality.STALE) }
            }
            if(calibration!=null && now-calibrationStarted>10000) {
                calibration=null
                SessionHub.mutable.update { it.copy(calibrationProgress=null,calibrationMessage="Calibration incomplète. Améliorez la lumière et gardez les yeux ouverts.") }
            }
            if(ui.running || ui.preparing) handler.postDelayed(this,100)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository=SettingsRepository(this); player=AlertPlayer(this)
        notifier.createNotificationChannel(NotificationChannel(CHANNEL_SESSION,"Surveillance",NotificationManager.IMPORTANCE_LOW))
        notifier.createNotificationChannel(NotificationChannel(CHANNEL_ALERT,"Alertes et commandes",NotificationManager.IMPORTANCE_HIGH).apply { setSound(null,null); enableVibration(false) })
        scope.launch { player.status.collect { audio -> SessionHub.mutable.update { it.copy(audio=audio) } } }
        if(Build.VERSION.SDK_INT>=29) {
            thermalListener=PowerManager.OnThermalStatusChangedListener { status ->
                camera?.minIntervalMs=if(status>=PowerManager.THERMAL_STATUS_MODERATE) 100 else 66
                camera?.pauseForThermal(status>=PowerManager.THERMAL_STATUS_SEVERE)
                if(status>=PowerManager.THERMAL_STATUS_SEVERE && SessionHub.state.value.running)
                    apply(engine.unavailable(SystemClock.elapsedRealtime(),Quality.THERMAL))
            }
            getSystemService(PowerManager::class.java).addThermalStatusListener(mainExecutor,thermalListener!!)
        }
    }
    override fun onBind(intent:Intent):IBinder { super.onBind(intent); return binder }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        super.onStartCommand(intent,flags,startId)
        when(intent?.action) {
            ACTION_START -> launchCamera(true,intent.getBooleanExtra(EXTRA_VISIBLE_ONLY,false))
            ACTION_PREPARE -> launchCamera(false,true)
            ACTION_ACK -> acknowledge()
            ACTION_STOP -> terminate()
        }
        return START_NOT_STICKY
    }
    private fun launchCamera(monitoring:Boolean,visibleOnly:Boolean) {
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            SessionHub.mutable.update { it.copy(error="Autorisez la caméra avant de démarrer") }; stopSelf(); return
        }
        val token=++session
        camera?.close(); camera=null; player.stop(); engine.stop(); calibration=null
        earlySerial=0; lastUnavailableSound=Long.MIN_VALUE/2; unavailableQuietUntil=0
        SessionHub.mutable.value=SessionUi(running=monitoring,preparing=!monitoring,foregroundOnly=visibleOnly)
        try {
            ServiceCompat.startForeground(this,NOTIFICATION_SESSION,notification(if(monitoring) "Initialisation de la surveillance" else "Cadrage — surveillance arrêtée"),
                if(Build.VERSION.SDK_INT>=30) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0)
            startedForeground=true
        } catch(e:RuntimeException) {
            SessionHub.mutable.value=SessionUi(error="Android n'a pas autorisé le démarrage de la caméra (${e.javaClass.simpleName})")
            stopSelf(); return
        }
        scope.launch {
            settings=repository.settings.first()
            if(session!=token) return@launch
            engine=DrowsinessEngine(settings)
            if(monitoring) { engine.start(SystemClock.elapsedRealtime()); repository.markSession(true) }
            if(session!=token) return@launch
            camera=FaceCamera(this@MonitoringService,this@MonitoringService,
                { observation,metrics -> if(session==token) process(observation,metrics) },
                { message -> if(session==token) {
                    SessionHub.mutable.update { it.copy(error=message,quality=Quality.CAMERA_ERROR) }
                    if(SessionHub.state.value.running) apply(engine.unavailable(SystemClock.elapsedRealtime(),Quality.CAMERA_ERROR))
                } }).also { it.setSurface(requestedSurface); it.start() }
            handler.removeCallbacks(tick); handler.post(tick)
        }
    }
    private fun process(o:Observation,metrics:VisionMetrics) {
        val now=SystemClock.elapsedRealtime()
        if(now-o.atMs>500) return // never publish a frozen pipeline as fresh
        SessionHub.mutable.update { it.copy(lastObservationMs=o.atMs,quality=o.quality,metrics=metrics,error=null) }
        if(SessionHub.state.value.running) apply(engine.observe(o,now))
        else calibration?.let { c ->
            val result=c.add(o)
            SessionHub.mutable.update { it.copy(calibrationProgress=c.progress) }
            if(result!=null) {
                calibration=null
                scope.launch { repository.calibration(result.first,result.second) }
                SessionHub.mutable.update { it.copy(calibrationProgress=null,calibrationMessage="Calibration enregistrée sur ce téléphone.") }
            }
        }
    }
    private fun apply(snapshot:Snapshot) {
        val now=SystemClock.elapsedRealtime()
        SessionHub.mutable.update { it.copy(snapshot=snapshot,quality=snapshot.quality) }
        player.urgent(snapshot.soundRequested,settings)
        if(snapshot.warningSerial!=earlySerial) { earlySerial=snapshot.warningSerial; player.early(settings) }
        if(snapshot.state==MonitorState.MONITORING_UNAVAILABLE && !snapshot.soundRequested &&
            now>=unavailableQuietUntil && now-lastUnavailableSound>=60000) {
            player.unavailable(settings); lastUnavailableSound=now
        }
        val text=when {
            snapshot.level==AlertLevel.URGENT -> "Fatigue suspectée — arrêtez-vous en sécurité"
            snapshot.state==MonitorState.MONITORING_UNAVAILABLE -> "Surveillance indisponible"
            else -> snapshot.reason
        }
        if(text!=lastNotification) {
            lastNotification=text
            if(notificationsAllowed()) {
                notifier.notify(NOTIFICATION_SESSION,notification(text))
                if(snapshot.level==AlertLevel.URGENT || snapshot.state==MonitorState.MONITORING_UNAVAILABLE)
                    notifier.notify(NOTIFICATION_ALERT,notification(text,true))
                else notifier.cancel(NOTIFICATION_ALERT)
            }
        }
    }
    fun acknowledge() {
        player.stop()
        val now=SystemClock.elapsedRealtime()
        unavailableQuietUntil=now+60000
        if(SessionHub.state.value.running) apply(engine.acknowledge(now))
        notifier.cancel(NOTIFICATION_ALERT)
        lastNotification=""
    }
    fun calibrate() {
        if(!SessionHub.state.value.preparing) return
        calibration=Calibration(); calibrationStarted=SystemClock.elapsedRealtime()
        SessionHub.mutable.update { it.copy(calibrationProgress=0f,calibrationMessage=null) }
    }
    fun setPreviewSurface(surface:Preview.SurfaceProvider?) { requestedSurface=surface; camera?.setSurface(surface) }
    fun uiHidden() {
        setPreviewSurface(null)
        if(SessionHub.state.value.preparing || SessionHub.state.value.foregroundOnly) terminate()
    }
    fun terminate() {
        ++session; handler.removeCallbacks(tick); calibration=null
        camera?.close(); camera=null; player.stop(); engine.stop()
        SessionHub.mutable.value=SessionUi()
        scope.launch { repository.markSession(false) }
        notifier.cancel(NOTIFICATION_ALERT); lastNotification=""
        if(startedForeground) { stopForeground(STOP_FOREGROUND_REMOVE); startedForeground=false }
        stopSelf()
    }
    private fun notification(text:String,alert:Boolean=false):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(id:Int,value:String)=PendingIntent.getService(this,id,Intent(this,MonitoringService::class.java).setAction(value),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,if(alert) CHANNEL_ALERT else CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("WakePilote").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(if(alert) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_SERVICE)
            .apply { if(alert) addAction(0,"Arrêter l'alerte",action(1,ACTION_ACK))
                else addAction(0,"Terminer",action(2,ACTION_STOP)) }.build()
    }
    private fun notificationsAllowed()=NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
    override fun onDestroy() {
        ++session; handler.removeCallbacksAndMessages(null); camera?.close(); camera=null
        player.close(); scope.cancel()
        if(Build.VERSION.SDK_INT>=29) thermalListener?.let { getSystemService(PowerManager::class.java).removeThermalStatusListener(it) }
        SessionHub.mutable.update { it.copy(running=false,preparing=false,snapshot=Snapshot(),calibrationProgress=null) }
        super.onDestroy()
    }
    companion object {
        const val ACTION_START="io.github.mesnsen.wakepilote.START"
        const val ACTION_PREPARE="io.github.mesnsen.wakepilote.PREPARE"
        const val ACTION_ACK="io.github.mesnsen.wakepilote.ACK"
        const val ACTION_STOP="io.github.mesnsen.wakepilote.STOP"
        const val EXTRA_VISIBLE_ONLY="visible_only"
        const val CHANNEL_SESSION="wake_session"
        const val CHANNEL_ALERT="wake_alert"
        const val NOTIFICATION_SESSION=41
        const val NOTIFICATION_ALERT=42
    }
}
