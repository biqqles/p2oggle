/*
 *  Copyright © 2019 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import androidx.preference.*
import org.xjy.android.treasure.TreasurePreferences
import java.util.*
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var sharedPreferences: SharedPreferences  // SharedPreferences does not work across processes...
    private lateinit var multiprocessPreferences: TreasurePreferences  // so we mirror it to an implementation that does
    private lateinit var activityManager: ActivityManager
    private var statTimer = Timer()

    override fun onAttach(context: Context) {
        // Called when added to activity.
        super.onAttach(context)

        PreferenceManager.setDefaultValues(context, R.xml.preferences, true)  // only runs once
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        multiprocessPreferences = TreasurePreferences.getInstance(context, "service")

        activityManager = activity!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Populate preference screen.
        addPreferencesFromResource(R.xml.preferences)

        // populate action selectors
        // this would be nicer if done in a class extending ListPreference but unless I defined it in Java rather
        // than Kotlin I would get ClassNotFoundException when trying to inflate
        val actionScreenOff = findPreference<ListPreference>("action_screen_off")!!
        val actionScreenOn = findPreference<ListPreference>("action_screen_on")!!

        val entries = SwitchService.switchables.entries.associate { it.key to getString(it.value.name) }

        for (preference in arrayOf(actionScreenOff, actionScreenOn)) {
            preference.entryValues = entries.keys.toTypedArray()
            preference.entries = entries.values.toTypedArray()
            preference.setDefaultValue(entries["Nothing"])
        }

        // disable notification settings on N. Prior to notification channels the only option is to hide all
        // notifications from an app, and doing this also disables toast messages which we use for the overlay
        val notificationSettings = findPreference<Preference>("service_notification")!!
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationSettings.isEnabled = false
            notificationSettings.setSummary(R.string.requires_o)
        } else {
            notificationSettings.intent = SwitchService.createNotificationSettingsIntent(activity?.packageName!!)
        }
    }

    override fun onResume() {
        // Called on parent activity resume, after onCreatePreferences if applicable.
        super.onResume()

        // begin mirroring preference changes
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        // begin polling statistics
        statTimer.scheduleAtFixedRate(object : TimerTask() {
            val statPreference = findPreference<Preference>("service_stats")!!
            override fun run() {
                activity?.runOnUiThread {
                    updateStatistics(statPreference)
                }
            }
        }, 0, STAT_UPDATE_PERIOD_MS)
    }

    override fun onPause() {
        // Called on parent activity pause.
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        statTimer.cancel()
        statTimer = Timer()  // a Timer that has been cancelled can't be restarted
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
        // Pass preference changes from the default implementation through to the multi-process implementation.
        when (val preference = findPreference<Preference>(key)!!) {
            is ListPreference -> multiprocessPreferences.edit().putString(key, preference.value).apply()
            is CheckBoxPreference -> multiprocessPreferences.edit().putBoolean(key, preference.isChecked).apply()
            else -> throw NotImplementedError()  // if any more Preference types are used in future, add them here
        }
    }

    fun updateStatistics(preference: Preference) {
        // Update the statistics.
        val (uptime, memory) = SwitchService.getStats(activityManager)
        val memoryFormatted = Formatter.formatFileSize(activity, memory)
        val uptimeFormatted = formatDuration(uptime)

        preference.summary = getString(R.string.pref_summary_stats, memoryFormatted, uptimeFormatted)
    }

    private fun formatDuration(ms: Long) = "%02d:%02d:%02d"
        // It's almost unbelievable that there is *still* no built-in way to do this in Java...
        .format(TimeUnit.MILLISECONDS.toHours(ms),
                TimeUnit.MILLISECONDS.toMinutes(ms) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(ms) % TimeUnit.MINUTES.toSeconds(1))

    companion object {
        const val STAT_UPDATE_PERIOD_MS = 1000L
    }
}