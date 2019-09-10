/*
 *  Copyright © 2019 biqqles.
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
import android.os.*
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.app.ActivityManager
import android.content.SharedPreferences
import android.util.Log
import org.xjy.android.treasure.TreasurePreferences

class SwitchService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var preferences: TreasurePreferences

    private val ready: Boolean
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

            if (whileScreenOn !is Nothing && preferences.getBoolean("show_overlay", true)) {
                widget = Overlay(applicationContext, whileScreenOn, preferences)
            } else {
                widget = null
            }
        }

        override fun switched(state: Boolean) {
            if (powerManager.isInteractive) {  // if display on
                widget?.draw(state)
                whileScreenOn.switched(state)
            } else {
                whileScreenOff.switched(state)
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

        preferences = TreasurePreferences.getInstance(this, "service")
        preferences.registerOnSharedPreferenceChangeListener(this,
            listOf("action_screen_off", "action_screen_on", "show_overlay", "overlay_text",
                "overlay_system_accent", "overlay_bg_colour"))

        initialiseCallback()
    }

    override fun onDestroy() {
        // Re-create service if killed by system while enabled.
        if (ready) {
            start(this)
        } else {
            Device.onSwitch = null
            super.onDestroy()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle starting intents.
        super.onStartCommand(intent, flags, startId)

        // abort conditions
        if (!ready ||
            (preferences.getBoolean("start_on_boot", true) && intent?.action == Intent.ACTION_BOOT_COMPLETED)) {
            super.onDestroy()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun initialiseCallback() {
        // Initialise the on-switch callback.
        Device.onSwitch = OnSwitch()
    }

    private fun createNotification(): Notification {
        // Create this service's mandatory persistent notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val appIntent = Intent(this, MainActivity::class.java)  // open this app

        val hideIntent = getNotificationSettingsIntent(packageName, applicationInfo.uid)

        val appAction =
            NotificationCompat.Action.Builder(0, getString(R.string.service_notification_configure),
                PendingIntent.getActivity(this, 0, appIntent, 0)).build()

        val hideAction =
            NotificationCompat.Action.Builder(0, getString(R.string.service_notification_hide),
                PendingIntent.getActivity(this, 0, hideIntent, 0)).build()

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_toggle_on)
            setContentTitle(getString(R.string.service_notification_title))
            setContentText(getString(R.string.service_description))
            setPriority(NotificationCompat.PRIORITY_LOW)
            addAction(appAction)
            addAction(hideAction)
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

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) = initialiseCallback()

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        val switchables =  // all the switchable actions selectable by the user
            listOf(Nothing, Flashlight, DoNotDisturb, PowerSaver, Aeroplane, WiFi, MobileData, Bluetooth, Nfc)
                .associateBy({it::class.java.simpleName}, {it})

        // intent actions
        const val ACTION_START = "eu.biqqles.p2oggle.START"  // generic start action
        const val ACTION_STOP = "eu.biqqles.p2oggle.STOP"
        private const val ACTION_CHANNEL_NOTIFICATION_SETTINGS = "android.settings.CHANNEL_NOTIFICATION_SETTINGS"

        // notification constants
        private const val NOTIFICATION_CHANNEL_ID = "service"
        private const val NOTIFICATION_ID = 0xF05F8

        fun getNotificationSettingsIntent(packageName: String, uid: Int) = Intent().apply {
            // An intent to open the settings for this service's notification channel.
            action = ACTION_CHANNEL_NOTIFICATION_SETTINGS
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                putExtra("app_package", packageName)
                putExtra("app_uid", uid)
            } else {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, NOTIFICATION_CHANNEL_ID)
            }
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
