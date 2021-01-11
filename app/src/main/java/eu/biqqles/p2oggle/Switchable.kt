/*
 *  Copyright © 2019-2020 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import android.annotation.SuppressLint as SuppressLint1

interface Switchable {
    fun switched(toggled: Boolean)
}

@SuppressLint1("UseCompatLoadingForDrawables")
interface SwitchableAction : Switchable {
    // A user-facing "action" that can be assigned to the switch.
    @get:StringRes val name: Int
    @get:DrawableRes val iconOff: Int
    @get:DrawableRes val iconOn: Int

    operator fun invoke(context: Context): SwitchableAction = this  // pseudo-constructor

    fun getAlertParametersOff(context: Context): Pair<Drawable, String> {
        // Return an icon and message to alert the user of this action being toggled off.
        return Pair(context.getDrawable(iconOff), context.getString(name) + context.getString(R.string.off))
    }

    fun getAlertParametersOn(context: Context): Pair<Drawable, String> {
        // Return parameters to alert the user of this action being toggled on.
        return Pair(context.getDrawable(iconOn), context.getString(name) + context.getString(R.string.on))
    }
}

object Nothing : SwitchableAction {
    override val name: Int = R.string.action_nothing
    override val iconOff = R.drawable.ic_toggle_off
    override val iconOn = R.drawable.ic_toggle_on
    override fun switched(toggled: Boolean) {}
}

object Flashlight : SwitchableAction {
    override val name: Int = R.string.action_flashlight
    override val iconOff = R.drawable.ic_flash_off
    override val iconOn = R.drawable.ic_flash_on
    private lateinit var cameraManager: CameraManager
    private lateinit var rearCamera: String

    override fun switched(toggled: Boolean) {
        try {
            cameraManager.setTorchMode(rearCamera, toggled)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override operator fun invoke(context: Context): SwitchableAction {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        rearCamera = cameraManager.cameraIdList.first()
        return this
    }
}

object PowerSaver : SwitchableAction {
    override val name = R.string.action_power_saver
    override val iconOff = R.drawable.ic_toggle_off
    override val iconOn = R.drawable.ic_toggle_on

    override fun switched(toggled: Boolean) {
        Shell.runAsRoot("settings put global low_power ${if (toggled) 1 else 0}")
    }
}

object Aeroplane : SwitchableAction {
    override val name = R.string.action_aeroplane
    override val iconOff = R.drawable.ic_airplanemode_off
    override val iconOn = R.drawable.ic_airplanemode_on
    
    override fun switched(toggled: Boolean) {
        Shell.runAsRoot("settings put global airplane_mode_on ${if (toggled) 1 else 0}")
        Shell.runAsRoot("am broadcast -a android.intent.action.AIRPLANE_MODE --ez toggled $toggled")
    }
}

object WiFi : SwitchableAction {
    override val name = R.string.action_wifi
    override val iconOff = R.drawable.ic_signal_wifi_off
    override val iconOn = R.drawable.ic_signal_wifi_on
    private lateinit var manager: WifiManager

    override fun switched(toggled: Boolean) {
        manager.isWifiEnabled = toggled
    }

    override operator fun invoke(context: Context): SwitchableAction {
        manager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return this
    }
}

object MobileData : SwitchableAction {
    override val name = R.string.action_data
    override val iconOff = R.drawable.ic_signal_cellular_off
    override val iconOn = R.drawable.ic_signal_cellular_on

    override fun switched(toggled: Boolean) {
        Shell.runAsRoot("svc data ${if (toggled) "enable" else "disable"}")
    }
}

object Bluetooth : SwitchableAction {
    override val name = R.string.action_bluetooth
    override val iconOff = R.drawable.ic_bluetooth_off
    override val iconOn = R.drawable.ic_bluetooth_on
    private lateinit var adapter: BluetoothAdapter

    override fun switched(toggled: Boolean) {
        if (toggled) adapter.enable() else adapter.disable()
    }

    override operator fun invoke(context: Context): SwitchableAction {
        adapter = BluetoothAdapter.getDefaultAdapter()
        return this
    }
}

object Nfc : SwitchableAction {
    override val name = R.string.action_nfc
    override val iconOff = R.drawable.ic_nfc
    override val iconOn = R.drawable.ic_nfc

    override fun switched(toggled: Boolean) {
        Shell.runAsRoot("svc nfc ${if (toggled) "enable" else "disable"}")
    }
}

object Location : SwitchableAction {
    override val name = R.string.action_location
    override val iconOff = R.drawable.ic_location_off
    override val iconOn = R.drawable.ic_location_on

    override fun switched(toggled: Boolean) {
        val modifier = if (toggled) '+' else '-'
        Shell.runAsRoot("settings put secure location_providers_allowed ${modifier}gps,${modifier}network")
    }
}

private interface AudioAction : SwitchableAction {
    var audioManager: AudioManager
    var notificationManager: NotificationManager

    override operator fun invoke(context: Context): SwitchableAction {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        requestNotificationPolicyAccess(context)
        return this
    }

    fun setRingerMode(ringerMode: Int) {
        // Set the ringer mode, working around a bug in Android whereby setting RINGER_MODE_SILENT doesn't work and
        // instead enables Do not disturb.
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audioManager.ringerMode = ringerMode
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    fun sendMediaKeyEvent(keyCode: Int) {
        // Send a media key event.
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun requestNotificationPolicyAccess(context: Context) {
        // If notification policy access is not granted, request it.
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            context.startActivity(intent)
            Toast.makeText(context, R.string.request_permission, Toast.LENGTH_LONG).show()
        }
    }
}

private interface DnDAction : AudioAction {
    val toFilter: Int

    override fun switched(toggled: Boolean) {
        val filter = if (toggled) toFilter else NotificationManager.INTERRUPTION_FILTER_ALL
        notificationManager.setInterruptionFilter(filter)
    }
}

private interface RingerAction : AudioAction {
    val toMode: Int
    var previousMode: Int

    override fun switched(toggled: Boolean) {
        @Suppress("CascadeIf")
        if (toggled) {
            previousMode = audioManager.ringerMode
            setRingerMode(toMode)
        } else if (previousMode != toMode) {
            setRingerMode(previousMode)
        } else {
            setRingerMode(AudioManager.RINGER_MODE_NORMAL)
        }
    }
}

object Silent : RingerAction {
    override val name = R.string.action_silent
    override val iconOff = R.drawable.ic_silent_off
    override val iconOn = R.drawable.ic_silent_on
    override lateinit var audioManager: AudioManager
    override lateinit var notificationManager: NotificationManager
    override val toMode = AudioManager.RINGER_MODE_SILENT
    override var previousMode = AudioManager.RINGER_MODE_NORMAL
}

object Vibrate : RingerAction {
    override val name = R.string.action_vibrate
    override val iconOff = R.drawable.ic_vibrate_off
    override val iconOn = R.drawable.ic_vibrate_on
    override lateinit var audioManager: AudioManager
    override lateinit var notificationManager: NotificationManager
    override val toMode = AudioManager.RINGER_MODE_VIBRATE
    override var previousMode = AudioManager.RINGER_MODE_NORMAL
}

object PriorityOnly : DnDAction {
    override val name: Int = R.string.action_priority_only
    override val iconOff = R.drawable.ic_do_not_disturb_off
    override val iconOn = R.drawable.ic_do_not_disturb_on
    override lateinit var audioManager: AudioManager
    override lateinit var notificationManager: NotificationManager
    override val toFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY
}

object AlarmsOnly : DnDAction {
    override val name: Int = R.string.action_alarms_only
    override val iconOff = R.drawable.ic_do_not_disturb_off
    override val iconOn = R.drawable.ic_do_not_disturb_on
    override lateinit var audioManager: AudioManager
    override lateinit var notificationManager: NotificationManager
    override val toFilter = NotificationManager.INTERRUPTION_FILTER_ALARMS
}

object TotalSilence : DnDAction {
    override val name: Int = R.string.action_total_silence
    override val iconOff = R.drawable.ic_do_not_disturb_off
    override val iconOn = R.drawable.ic_do_not_disturb_on
    override lateinit var audioManager: AudioManager
    override lateinit var notificationManager: NotificationManager
    override val toFilter = NotificationManager.INTERRUPTION_FILTER_NONE
}

@SuppressLint1("UseCompatLoadingForDrawables")
object PlayPause : AudioAction {
    override val name = R.string.action_play_pause
    override val iconOff = R.drawable.ic_pause
    override val iconOn = R.drawable.ic_play
    override lateinit var audioManager: AudioManager
    override lateinit var notificationManager: NotificationManager
    private const val nameOff = R.string.pause
    private const val nameOn = R.string.play

    override fun switched(toggled: Boolean) {
        sendMediaKeyEvent(if (toggled) KeyEvent.KEYCODE_MEDIA_PLAY else KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    override fun getAlertParametersOff(context: Context): Pair<Drawable, String> {
        return Pair(context.getDrawable(iconOff), context.getString(nameOff))
    }

    override fun getAlertParametersOn(context: Context): Pair<Drawable, String> {
        return Pair(context.getDrawable(iconOn), context.getString(nameOn))
    }
}

object Caffeine : SwitchableAction {
    // An action that keeps the screen awake using the deprecated "screen on" wakelock. The wakelock is cancelled when
    // the user presses the power button or moves the switch to off.
    //
    // Another method I came up with is to display an invisible toast every few seconds which resets the screen off
    // timeout counter. This works on AOSP-like ROMs (Q and above), e.g. Lineage, but not on others.
    //
    // Though this wakelock type has been deprecated for years it still seems to work fine on most if not all Q ROMs.
    // We'll see how long that stays the case...

    override val name = R.string.action_caffeine
    override val iconOff = R.drawable.ic_caffeine
    override val iconOn = R.drawable.ic_caffeine
    private lateinit var wakelock: PowerManager.WakeLock

    @Suppress("WakelockTimeout")
    override fun switched(toggled: Boolean) {
        if (toggled) {
            wakelock.acquire()
        } else {
            wakelock.release();
        }
    }

    @Suppress("DEPRECATION")
    override fun invoke(context: Context): SwitchableAction {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakelock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "P2oggle:Caffeine")
        wakelock.setReferenceCounted(false)  // allows one "release" to undo any number of "acquires"
        return this
    }
}
