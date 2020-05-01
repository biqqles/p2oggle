/*
 *  Copyright © 2019-2020 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.animation.LayoutTransition
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.widget.Switch
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import org.xjy.android.treasure.TreasurePreferences

class MainActivity : AppCompatActivity() {
    private lateinit var toolbar: Toolbar
    private lateinit var serviceSwitch: Switch
    private lateinit var preferences: TreasurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = TreasurePreferences.getInstance(applicationContext, "service")

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportFragmentManager.beginTransaction().replace(R.id.contentFrame, SettingsFragment()).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar.
        menuInflater.inflate(R.menu.toolbar, menu)

        // add switch and listener
        serviceSwitch = menu.findItem(R.id.serviceSwitch).actionView.findViewById(R.id.toolbarSwitch)
        serviceSwitch.setOnCheckedChangeListener { _, checked ->
            onServiceToggled(checked && initialSetup())
        }

        onServiceToggled(preferences.getBoolean("service_enabled", false))  // set initial state

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle menu item selected.
        when (item.itemId) {
            R.id.viewRepo -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.view_repo_url))))
            R.id.viewThread -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.pref_thread_url))))
            else -> super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun initialSetup(): Boolean {
        // Complete initial compatibility checks and setup.
        // Return true if all checks pass successfully, otherwise false.
        if (!Shell.isRootAvailable) {
            showSimpleDialogue(R.string.request_root)
        } else if (!Device.ensureReady()) {
            showSimpleDialogue(R.string.supolicy_issue)
        } else {
            return true
        }
        return false
    }

    private fun onServiceToggled(enabled: Boolean) {
        // Handle service being enabled or disabled.
        serviceSwitch.isChecked = enabled
        // enable/disable settings
        val currentFragment = supportFragmentManager.findFragmentById(R.id.contentFrame) as? SettingsFragment
        currentFragment?.preferenceScreen?.isEnabled = enabled

        preferences.edit().putBoolean("service_enabled", enabled).apply()

        if (enabled) {
            SwitchService.start(this)
            peekActionBarSubtitle(R.string.service_enabled)
        } else {
            SwitchService.stop(this)
            peekActionBarSubtitle(R.string.service_disabled)
        }
    }

    private fun peekActionBarSubtitle(@StringRes resId: Int, timeout: Long=1500) {
        // "Peek" a subtitle in the action bar.
        supportActionBar?.setSubtitle(resId)
        Handler().postDelayed({
            toolbar.layoutTransition = LayoutTransition()
            supportActionBar?.subtitle = null  // set visibility to GONE
            toolbar.layoutTransition = LayoutTransition()  // force layout update, this doesn't happen otherwise
        }, timeout)
    }

    fun showSimpleDialogue(message: String) = with(AlertDialog.Builder(this)) {
        // Show a simple dialogue with an OK button.
        setPositiveButton(android.R.string.ok) { dialogue, _ -> dialogue.dismiss() }
        setMessage(message)
        create()
        show()
    }

    fun showSimpleDialogue(@StringRes message: Int) = showSimpleDialogue(getString(message))
}
