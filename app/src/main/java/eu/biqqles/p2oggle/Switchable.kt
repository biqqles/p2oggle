/*
 *  Copyright © 2019 biqqles.
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
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


interface Switchable {
    fun switched(state: Boolean)
}

interface SwitchableAction : Switchable {
    // A user-facing "action" that can be assigned to the switch
    @get:StringRes val name: Int
    @get:DrawableRes val iconOff: Int
    @get:DrawableRes val iconOn: Int
    operator fun invoke(context: Context): SwitchableAction  // pseudo-constructor
}

object Nothing : SwitchableAction {
    override val name: Int = R.string.action_nothing
    override val iconOff = R.drawable.ic_toggle_off
    override val iconOn = R.drawable.ic_toggle_on
    override fun switched(state: Boolean) {}
    override fun invoke(context: Context): SwitchableAction = this
}

object Flashlight : SwitchableAction {
    override val name: Int = R.string.action_flashlight
    override val iconOff = R.drawable.ic_flash_off
    override val iconOn = R.drawable.ic_flash_on
    private lateinit var manager: CameraManager
    private lateinit var rearCamera: String

    override fun switched(state: Boolean) {
        try {
            manager.setTorchMode(rearCamera, state)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    override operator fun invoke(context: Context): SwitchableAction {
        manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        rearCamera = manager.cameraIdList.first()
        return this
    }
}

object DoNotDisturb : SwitchableAction {
    override val name: Int = R.string.action_dnd
    override val iconOff = R.drawable.ic_do_not_disturb_off
    override val iconOn = R.drawable.ic_do_not_disturb_on
    private lateinit var manager: NotificationManager

    override fun switched(state: Boolean) {
        val filter = if (state) NotificationManager.INTERRUPTION_FILTER_NONE
        else       NotificationManager.INTERRUPTION_FILTER_ALL
        manager.setInterruptionFilter(filter)
    }

    override operator fun invoke(context: Context): SwitchableAction {
        manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        requestPolicyAccess(context)
        return this
    }

    private fun requestPolicyAccess(context: Context) {
        if (!manager.isNotificationPolicyAccessGranted) {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

            context.startActivity(intent)
            Toast.makeText(context, R.string.request_permission, Toast.LENGTH_LONG).show()
        }
    }
}

object PowerSaver : SwitchableAction {
    override val name = R.string.action_power_saver
    override val iconOff = R.drawable.ic_toggle_off
    override val iconOn = R.drawable.ic_toggle_on

    override fun switched(state: Boolean) {
        Shell.runAsRoot("settings put global low_power ${if (state) 1 else 0}")
    }

    override operator fun invoke(context: Context): SwitchableAction = this
}

object Aeroplane : SwitchableAction {
    override val name = R.string.action_aeroplane
    override val iconOff = R.drawable.ic_airplanemode_off
    override val iconOn = R.drawable.ic_airplanemode_on
    
    override fun switched(state: Boolean) {
        Shell.runAsRoot("settings put global airplane_mode_on ${if (state) 1 else 0}")
        Shell.runAsRoot("am broadcast -a android.intent.action.AIRPLANE_MODE --ez state $state")
    }

    override fun invoke(context: Context): SwitchableAction = this
}

object WiFi : SwitchableAction {
    override val name = R.string.action_wifi
    override val iconOff = R.drawable.ic_signal_wifi_off
    override val iconOn = R.drawable.ic_signal_wifi_on
    private lateinit var manager: WifiManager

    override fun switched(state: Boolean) {
        manager.isWifiEnabled = state
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

    override fun switched(state: Boolean) {
        Shell.runAsRoot("svc data ${if (state) "enable" else "disable"}")
    }

    override operator fun invoke(context: Context): SwitchableAction = this
}

object Bluetooth : SwitchableAction {
    override val name = R.string.action_bluetooth
    override val iconOff = R.drawable.ic_bluetooth_off
    override val iconOn = R.drawable.ic_bluetooth_on
    private lateinit var adapter: BluetoothAdapter

    override fun switched(state: Boolean) {
        if (state) adapter.enable() else adapter.disable()
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

    override fun switched(state: Boolean) {
        Shell.runAsRoot("svc nfc ${if (state) "enable" else "disable"}")
    }

    override operator fun invoke(context: Context) = this
}
