/*
 *  Copyright © 2019-2021 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import org.xjy.android.treasure.TreasurePreferences

class SwitchService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var preferences: TreasurePreferences

    private val enabled: Boolean
        get() = preferences.getBoolean("service_enabled", false) && Device.ensureReady()

    private inner class OnSwitch : Switchable {
        private val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        private val whileScreenOff: SwitchableAction
        private val whileScreenOn: SwitchableAction
        private val widget: Overlay?

        init {
            // initialise actions based on sharedPreferences
            whileScreenOff = loadSwitchableFromPreferences("action_screen_off")
            whileScreenOn = loadSwitchableFromPreferences("action_screen_on")

            widget = if (whileScreenOn !is Nothing && preferences.getBoolean("show_overlay", true)) {
                Overlay(applicationContext, whileScreenOn, preferences)
            } else {
                null
            }
        }

        override fun switched(toggled: Boolean) {
            if (powerManager.isInteractive) {  // if display on
                widget?.draw(toggled)
                whileScreenOn.switched(toggled)
            } else {
                whileScreenOff.switched(toggled)
            }

            if (preferences.getBoolean("send_broadcasts", false)) {
                sendBroadcast(Intent(if (toggled) ACTION_SWITCH_UP else ACTION_SWITCH_DOWN))
            }
        }

        private fun loadSwitchableFromPreferences(preferencesKey: String): SwitchableAction {
            // Load a SwitchableAction from its class name, stored in preferences. If this name is invalid,
            // set the preference to Nothing and return this type.
            val simpleName = preferences.getString(preferencesKey, "Nothing")!!
            Log.i(this::class.java.simpleName, "Attempting to initialise Switchable with name $simpleName")
            return if (simpleName in switchables.keys) {
                switchables.getValue(simpleName)(applicationContext)
            } else {
                preferences.edit().putString(preferencesKey, "Nothing").apply()
                Nothing(applicationContext)
            }
        }
    }

    override fun onCreate() {
        // Start this service in the foreground.
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        // Re-create service if killed by system while enabled.
        if (enabled) {
            start(this)
        } else {
            detachCallback()
            super.onDestroy()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle start intents.
        super.onStartCommand(intent, flags, startId)

        bindPreferences()

        // abort conditions
        if (!enabled ||
            (preferences.getBoolean("start_on_boot", true) && intent?.action == Intent.ACTION_BOOT_COMPLETED)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun bindPreferences() {
        // Bind this service to the shared preferences.
        preferences = TreasurePreferences.getInstance(this, "service")
        preferences.registerOnSharedPreferenceChangeListener(this,
            listOf("action_screen_off", "action_screen_on", "show_overlay", "overlay_text",
                "overlay_system_accent", "overlay_bg_colour"))
        attachCallback()
    }

    private fun attachCallback() {
        // Initialise the on-switch callback and bind it to the switch.
        Device.onSwitch = OnSwitch()
    }

    private fun detachCallback() {
        // Unbind the callback from the switch.
        Device.onSwitch = null
    }

    private fun createNotification(): Notification {
        // Create this service's mandatory persistent notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val appAction =  // open this app
            NotificationCompat.Action.Builder(0, getString(R.string.service_notification_configure),
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), 0)).build()

        val hideAction =  // open notification channel settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCompat.Action.Builder(0, getString(R.string.service_notification_hide),
                    PendingIntent.getActivity(this, 0, createNotificationSettingsIntent(packageName), 0)).build()
            } else {
                null
            }

        @Suppress("UsePropertyAccessSyntax")
        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_toggle_on)
            setContentTitle(getString(R.string.service_notification_title))
            setContentText(getString(R.string.service_description))
            setPriority(NotificationCompat.PRIORITY_LOW)
            setVisibility(NotificationCompat.VISIBILITY_SECRET)
            addAction(appAction)
            hideAction?.let { addAction(hideAction) }
        }

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, getString(R.string.service_notification_name), NotificationManager.IMPORTANCE_MIN)
            channel.description = getString(R.string.service_notification_description)
            channel.setShowBadge(false)

        notificationManager.createNotificationChannel(channel)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = attachCallback()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        val switchables =  // all the switchable actions selectable by the user
            listOf(Nothing, Flashlight, PowerSaver, Aeroplane, WiFi, MobileData, Bluetooth, Nfc, Location,
                   Silent, Vibrate, PriorityOnly, AlarmsOnly, TotalSilence, PlayPause, Caffeine, Dictaphone)
            .associateBy({it::class.java.simpleName}, {it})

        // intent actions
        private const val ACTION_START = "eu.biqqles.p2oggle.START"  // generic service start and stop actions
        private const val ACTION_STOP = "eu.biqqles.p2oggle.STOP"
        const val ACTION_SWITCH_DOWN = "eu.biqqles.p2oggle.SWITCH_DOWN"  // external broadcasts
        const val ACTION_SWITCH_UP = "eu.biqqles.p2oggle.SWITCH_UP"

        // notification constants
        private const val NOTIFICATION_CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 0xF05F8

        @RequiresApi(Build.VERSION_CODES.O)
        fun createNotificationSettingsIntent(packageName: String) = Intent().apply {
            // An intent to open the settings for this service's notification channel.
            action = Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID)
        }

        fun getStats(manager: ActivityManager): Pair<Long, Long> {
            // Return the duration this service has been running for, in milliseconds, and the memory usage of this
            // service's process, in bytes.
            @Suppress("DEPRECATION")  // method not deprecated for own services
            val serviceInfo = manager.getRunningServices(Int.MAX_VALUE)
                .firstOrNull { it.service.className == SwitchService::class.java.name }

            return if (serviceInfo != null) {
                val processInfo = manager.getProcessMemoryInfo(intArrayOf(serviceInfo.pid)).first()
                val usageKb = processInfo.getMemoryStat("summary.total-pss").toLong()
                Pair(SystemClock.elapsedRealtime() - serviceInfo.activeSince, usageKb * 1024)
            } else {  // service isn't running yet
                Pair(0, 0)
            }
        }

        // Service start, stop and communication logic
        // This receiver is only used to handle system events; to start and stop the service manually, use the methods
        // defined below.
        class Starter : BroadcastReceiver() {
            // Receive system events and pass through to service.
            @SuppressLint("UnsafeProtectedBroadcastReceiver")
            override fun onReceive(context: Context, intent: Intent?) = start(context.applicationContext, intent)
        }

        fun start(context: Context, intent: Intent? = Intent(ACTION_START)) {
            // Start the service.
            val serviceIntent = Intent(context, SwitchService::class.java)
                serviceIntent.action = intent?.action  // passthrough

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                context.startService(serviceIntent)
            } else {
                context.startForegroundService(serviceIntent)
            }
        }

        fun stop(context: Context, intent: Intent? = Intent(ACTION_STOP)) {
            // Stop the service.
            val serviceIntent = Intent(context, SwitchService::class.java)
                serviceIntent.action = intent?.action
            context.stopService(serviceIntent)
        }
    }
}
