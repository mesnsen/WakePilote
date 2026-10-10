package io.github.mesnsen.wakepilote

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class MainActivity:ComponentActivity() {
    private val vm:WakeViewModel by viewModels()
    private var service by mutableStateOf<MonitoringService?>(null)
    private var bound=false
    private var pendingAction:String?=null
    private val connection=object:ServiceConnection {
        override fun onServiceConnected(name:ComponentName,binder:IBinder) { service=(binder as MonitoringService.LocalBinder).service }
        override fun onServiceDisconnected(name:ComponentName) { service=null }
    }
    private val permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val action=pendingAction; pendingAction=null
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED && action!=null) launch(action)
        else vm.message.value="La caméra est nécessaire. Autorisez-la dans les réglages Android pour continuer."
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { WakeScreen(vm,service,
            onPrepare={ request(MonitoringService.ACTION_PREPARE) },
            onStart={ if(vm.prepareStart()) request(MonitoringService.ACTION_START) },
            onStop={ service?.terminate(); vm.clearTestForNextSession() },
            onSilence={ vm.stopTest(); service?.acknowledge() },
            onDim={ dim -> window.attributes=window.attributes.apply { screenBrightness=if(dim) .03f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }) }
    }
    override fun onStart() {
        super.onStart()
        bound=bindService(Intent(this,MonitoringService::class.java),connection,Context.BIND_AUTO_CREATE)
        vm.refreshOutputs()
        if(SessionHub.state.value.running && !notificationsAvailable()) {
            startService(Intent(this,MonitoringService::class.java).setAction(MonitoringService.ACTION_STOP))
            vm.message.value="Notifications désactivées : session terminée. Vous pouvez redémarrer en mode visible."
        }
    }
    override fun onStop() {
        vm.stopTest()
        if(!isChangingConfigurations) service?.uiHidden() else service?.setPreviewSurface(null)
        if(bound) { unbindService(connection); bound=false }
        service=null
        window.attributes=window.attributes.apply { screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        super.onStop()
    }
    private fun request(action:String) {
        val permissions=mutableListOf<String>()
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) permissions+=Manifest.permission.CAMERA
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permissions+=Manifest.permission.POST_NOTIFICATIONS
        if(permissions.isNotEmpty()) { pendingAction=action; permissionLauncher.launch(permissions.toTypedArray()) }
        else launch(action)
    }
    private fun launch(action:String) {
        val visibleOnly=!notificationsAvailable()
        if(visibleOnly) vm.message.value="Mode visible uniquement : quitter ou verrouiller l'application arrête la surveillance."
        runCatching { ContextCompat.startForegroundService(this,Intent(this,MonitoringService::class.java).setAction(action)
            .putExtra(MonitoringService.EXTRA_VISIBLE_ONLY,visibleOnly)) }
            .onFailure { vm.message.value="Démarrage impossible (${it.javaClass.simpleName}). Réessayez depuis cet écran." }
    }
    private fun notificationsAvailable():Boolean {
        val manager=getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            manager.getNotificationChannel(MonitoringService.CHANNEL_ALERT)?.importance!=NotificationManager.IMPORTANCE_NONE &&
            manager.getNotificationChannel(MonitoringService.CHANNEL_SESSION)?.importance!=NotificationManager.IMPORTANCE_NONE
    }
}
