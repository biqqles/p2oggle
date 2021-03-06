/*
 *  Copyright © 2019-2021 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileDescriptor
import java.lang.RuntimeException
import java.text.DateFormat
import java.util.*

interface Switchable {
    fun switched(toggled: Boolean)
}

interface SwitchableAction : Switchable {
    // A user-facing "action" that can be assigned to the switch.
    @get:StringRes val name: Int
    @get:DrawableRes val iconOff: Int
    @get:DrawableRes val iconOn: Int

    // This pseudo-constructor allows Switchables to be objects, meaning they can be referenced statically.
    operator fun invoke(context: Context): SwitchableAction = this

    fun getAlertParametersOff(context: Context): Pair<Drawable, String> {
        // Return an icon and message to alert the user of this action being toggled off.
        return Pair(icon(context,false), text(context,false))
    }

    fun getAlertParametersOn(context: Context): Pair<Drawable, String> {
        // Return parameters to alert the user of this action being toggled on.
        return Pair(icon(context,true), text(context,true))
    }

    fun icon(context: Context, switched: Boolean): Drawable {
        // The icon for the given switch position.
        return ContextCompat.getDrawable(context, if (switched) iconOn else iconOff)!!
    }

    fun text(context: Context, switched: Boolean): String {
        // The text for the given switch position.
        val state = context.getString(if (switched) R.string.on else R.string.off)
        return context.getString(name) + state
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
    override val iconOff = R.drawable.ic_flashlight_off
    override val iconOn = R.drawable.ic_flashlight_on
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

    override fun switched(toggled: Boolean) {
        Shell.runAsRoot("svc wifi ${if (toggled) "enable" else "disable"}")
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

internal interface AudioAction : SwitchableAction {
    var audioManager: AudioManager
    var notificationManager: NotificationManager

    override operator fun invoke(context: Context): SwitchableAction {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return this
    }

    fun setRingerMode(ringerMode: Int) {
        // Set the ringer mode, working around a bug in Android whereby setting RINGER_MODE_SILENT doesn't work and
        // instead enables Do not disturb.
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        audioManager.ringerMode = ringerMode
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
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
            wakelock.release()
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

object PlayPause : SwitchableAction {
    override val name = R.string.action_play_pause
    override val iconOff = R.drawable.ic_pause
    override val iconOn = R.drawable.ic_play
    private const val nameOff = R.string.pause
    private const val nameOn = R.string.play
    private lateinit var audioManager: AudioManager

    override fun switched(toggled: Boolean) {
        sendMediaKeyEvent(if (toggled) KeyEvent.KEYCODE_MEDIA_PLAY else KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    override fun text(context: Context, switched: Boolean): String {
        return context.getString(if (switched) nameOn else nameOff)
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        // Send a media key event.
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun invoke(context: Context): SwitchableAction {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return this
    }
}

object Dictaphone : SwitchableAction {
    override val name = R.string.action_dictaphone
    override val iconOff = R.drawable.ic_save
    override val iconOn = R.drawable.ic_recording
    private const val nameOff = R.string.saved
    private const val nameOn = R.string.recording
    private const val saveTo = "Music/P2oggle"

    private lateinit var contentResolver: ContentResolver
    private lateinit var confirmToast: Toast
    private var recorder: MediaRecorder? = null

    private fun startRecording() {
        // Start making a recording. Create `mediaRecorder` and set `currentFilename`.
        val file = newFile() ?: return

        recorder = MediaRecorder().apply {
            try {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(file)
            } catch (e: RuntimeException) {
                Log.e(this::class.java.simpleName, "Tried to record with permissions denied")
                return
            }
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
            start()
        }
    }

    private fun stopRecording() {
        // Finish the current recording and save the file.
        recorder?.apply {
            stop()
            release()
        }
        recorder = null

        confirmToast.show()
    }

    private fun newFile(): FileDescriptor? {
        // Create a file descriptor for a new recording.
        val date = DateFormat.getDateTimeInstance().format(Calendar.getInstance().time)
        val filename = "$date.mp3"

        val values = ContentValues().apply {
            put(MediaColumns.TITLE, date)
            put(MediaColumns.MIME_TYPE, "audio/mp3")

            // store the file in a subdirectory
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaColumns.DISPLAY_NAME, filename)
                put(MediaColumns.RELATIVE_PATH, saveTo)
            } else {
                // RELATIVE_PATH was added in Q, so work around it by using DATA and creating the file manually
                @Suppress("DEPRECATION")
                val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).path

                with(File("$music/P2oggle/$filename")) {
                    @Suppress("DEPRECATION")
                    put(MediaColumns.DATA, path)

                    parentFile!!.mkdir()
                    createNewFile()
                }
            }
        }

        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)!!
        return contentResolver.openFileDescriptor(uri, "w")?.fileDescriptor
    }

    override fun switched(toggled: Boolean) {
        if (toggled) {
            startRecording()
        } else {
            stopRecording()
        }
    }

    override fun text(context: Context, switched: Boolean): String {
        return context.getString(if (switched) nameOn else nameOff)
    }

    override fun invoke(context: Context): SwitchableAction {
        contentResolver = context.contentResolver

        val savedTo = context.getString(R.string.saved_to, saveTo)
        confirmToast = Toast.makeText(context, savedTo, Toast.LENGTH_LONG)
        return this
    }
}
