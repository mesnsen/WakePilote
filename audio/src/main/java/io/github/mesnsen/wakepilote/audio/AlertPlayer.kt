package io.github.mesnsen.wakepilote.audio

import android.content.Context
import android.media.*
import android.os.*
import io.github.mesnsen.wakepilote.core.OutputPreference
import io.github.mesnsen.wakepilote.core.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.sin

data class AudioStatus(val playing:Boolean=false, val route:String="Sortie à tester", val detail:String="",
    val routeObserved:Boolean=false, val interrupted:Boolean=false, val mediaVolume:Int=0,val diagnostic:String?=null)
data class OutputDevice(val id:Int,val label:String)

/** Main-thread confined. A preference is never reported as an observed route. */
class AlertPlayer(context:Context) : AutoCloseable {
    private val app=context.applicationContext
    private val manager=app.getSystemService(AudioManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private val vibrator:Vibrator = if(Build.VERSION.SDK_INT>=31) app.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    private val mutable=MutableStateFlow(AudioStatus(mediaVolume=volume()))
    val status=mutable.asStateFlow()
    private var track:AudioTrack?=null
    private var focus:AudioFocusRequest?=null
    private var urgentWanted=false
    private var settings=Settings()
    private var disposed=false
    private var generation=0L
    private var fallback=false
    private val deviceCallback=object:AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removed:Array<out AudioDeviceInfo>) {
            val t=track ?: return
            val routed=t.routedDevice
            val preferred=t.preferredDevice
            if(removed.any { it.id==routed?.id || it.id==preferred?.id }) {
                fallback=true
                t.setPreferredDevice(phone())
                updateRoute("Connexion perdue — repli téléphone demandé")
            }
        }
        override fun onAudioDevicesAdded(added:Array<out AudioDeviceInfo>) { if(track!=null) updateRoute() }
    }
    init { manager.registerAudioDeviceCallback(deviceCallback,handler) }

    fun volume()=manager.getStreamVolume(AudioManager.STREAM_MUSIC)
    fun devices():List<OutputDevice> = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { multimediaBluetooth(it) }.map { OutputDevice(it.id, it.productName.toString().ifBlank { "Bluetooth multimédia" }) }
    fun urgent(wanted:Boolean, config:Settings=settings) {
        if(disposed) return
        settings=config
        if(wanted==urgentWanted) return
        urgentWanted=wanted
        if(wanted) begin(true) else stop()
    }
    fun test(config:Settings) { if(disposed || urgentWanted) return; settings=config; begin(false,2000) }
    fun early(config:Settings) { if(disposed || urgentWanted) return; settings=config; begin(false,700) }
    fun unavailable(config:Settings) { if(disposed || urgentWanted) return; settings=config; begin(false,450,330.0) }

    fun stop() {
        urgentWanted=false
        generation++
        handler.removeCallbacksAndMessages(null)
        stopOutput()
        focus?.let { manager.abandonAudioFocusRequest(it) }; focus=null
        mutable.value=mutable.value.copy(playing=false,routeObserved=false,route="Dernière sortie : ${mutable.value.route.removePrefix("Dernière sortie : ")}")
    }

    private fun begin(urgent:Boolean, lengthMs:Long=1000, frequency:Double=if(urgent) 740.0 else 520.0, alreadyFocused:Boolean=false) {
        if(disposed) return
        generation++; val token=generation
        stopOutput(); fallback=false
        vibrate(urgent)
        if(volume()==0) { mutable.value=AudioStatus(detail="Volume multimédia à zéro",mediaVolume=0,interrupted=true); return }
        if(!alreadyFocused) {
            focus?.let { manager.abandonAudioFocusRequest(it) }
            lateinit var request:AudioFocusRequest
            request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes()).setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener({ change ->
                    if(focus!==request) return@setOnAudioFocusChangeListener
                    when(change) {
                        AudioManager.AUDIOFOCUS_GAIN -> if(urgentWanted && !disposed && track==null) begin(true,alreadyFocused=true)
                        AudioManager.AUDIOFOCUS_LOSS,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                            stopOutput()
                            if(urgentWanted) vibrate(true)
                            mutable.value=AudioStatus(detail="Son interrompu par Android ou une autre application",interrupted=true,mediaVolume=volume())
                        }
                    }
                },handler).build()
            focus=request
            if(manager.requestAudioFocus(request)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mutable.value=AudioStatus(detail="Audio focus refusé — alerte sonore indisponible",interrupted=true,mediaVolume=volume())
                return
            }
        }
        runCatching {
            val pcm=tone(frequency,urgent)
            val player=AudioTrack.Builder().setAudioAttributes(attributes())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size*2).build()
            track=player
            // MODE_STATIC starts in STATE_NO_STATIC_DATA until the first successful write.
            check(player.state!=AudioTrack.STATE_UNINITIALIZED) { "Lecteur non initialisé" }
            check(player.write(pcm,0,pcm.size)==pcm.size) { "Tampon audio incomplet" }
            check(player.state==AudioTrack.STATE_INITIALIZED) { "Tampon audio non initialisé" }
            val preferred=when(settings.output) {
                OutputPreference.AUTO -> null
                OutputPreference.PHONE -> phone()
                OutputPreference.CAR -> manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id==settings.carDeviceId && multimediaBluetooth(it) }
            }
            if(settings.output==OutputPreference.CAR && preferred==null) { fallback=true; player.setPreferredDevice(phone()) }
            else if(!player.setPreferredDevice(preferred)) { fallback=true; player.setPreferredDevice(phone()) }
            player.setVolume(settings.gain.coerceIn(.1f,1f))
            if(urgent) player.setLoopPoints(0,pcm.size,-1)
            player.addOnRoutingChangedListener({ if(track===player) updateRoute() },handler)
            player.play()
            mutable.value=AudioStatus(true,"Vérification de la sortie…",if(fallback) "Sortie demandée indisponible — repli téléphone" else "",false,false,volume())
            handler.postDelayed({ if(generation==token && track===player) updateRoute() },180)
            handler.postDelayed({
                if(generation==token && track===player && player.routedDevice==null) {
                    fallback=true; player.setPreferredDevice(phone())
                    mutable.value=mutable.value.copy(detail="Route non vérifiable — repli téléphone demandé",interrupted=true)
                }
            },900)
            if(!urgent) handler.postDelayed({ if(generation==token) stop() },lengthMs)
        }.onFailure {
            stopOutput()
            if(urgentWanted) vibrate(true)
            mutable.value=AudioStatus(detail="Impossible de jouer le son — relancez le test à l'arrêt",interrupted=true,mediaVolume=volume(),
                diagnostic="${it.javaClass.simpleName}: ${it.message.orEmpty().take(180)}")
        }
    }
    private fun updateRoute(detail:String?=null) {
        val t=track ?: return
        val device=t.routedDevice
        val route=when {
            device==null -> "Sortie non vérifiée"
            device.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Haut-parleur du téléphone"
            multimediaBluetooth(device) -> "Bluetooth multimédia"
            device.type==AudioDeviceInfo.TYPE_WIRED_HEADPHONES || device.type==AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Écouteurs filaires"
            else -> "Sortie audio Android"
        }
        val mismatch = device!=null && ((settings.output==OutputPreference.PHONE && device.type!=AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) ||
            (settings.output==OutputPreference.CAR && device.id!=settings.carDeviceId))
        mutable.value=AudioStatus(t.playState==AudioTrack.PLAYSTATE_PLAYING,route,
            detail ?: if(fallback || mismatch) "La sortie réelle diffère du choix demandé — vérifiez le son à l'arrêt" else "L'audibilité doit être confirmée à l'arrêt",
            device!=null,false,volume())
    }
    private fun stopOutput() {
        vibrator.cancel()
        val old=track; track=null
        if(old!=null) { runCatching { old.pause(); old.flush(); old.stop() }; old.release() }
    }
    private fun vibrate(urgent:Boolean) {
        if(settings.vibrate && vibrator.hasVibrator()) {
            if(urgent) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0,140,860),0))
            else vibrator.vibrate(VibrationEffect.createOneShot(120,VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
    private fun phone()=manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    private fun multimediaBluetooth(d:AudioDeviceInfo)=d.type==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        (Build.VERSION.SDK_INT>=31 && (d.type==AudioDeviceInfo.TYPE_BLE_HEADSET || d.type==AudioDeviceInfo.TYPE_BLE_SPEAKER))
    private fun attributes()=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private fun tone(frequency:Double,urgent:Boolean):ShortArray = ShortArray(24000) { i ->
        val time=i/24000.0
        val phase=if(urgent) time % .5 else time
        val envelope=if(phase<.28) minOf(phase/.06,1.0, (.28-phase)/.06).coerceAtLeast(0.0) else 0.0
        (sin(2*PI*frequency*time)*envelope*(if(urgent) 15000 else 10000)).toInt().toShort()
    }
    override fun close() { if(!disposed) { stop(); disposed=true; manager.unregisterAudioDeviceCallback(deviceCallback) } }
}
