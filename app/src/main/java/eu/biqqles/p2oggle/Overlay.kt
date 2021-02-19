/*
 *  Copyright © 2019-2021 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import org.xjy.android.treasure.TreasurePreferences

class Overlay(private val context: Context, switchable: SwitchableAction, private val preferences: TreasurePreferences) {
    // An "overlay" which displays a notification on switch toggle above other apps, by inflating a custom layout
    // inside a Toast. This approach negates the need for the SYSTEM_OVERLAY permission but has the same restrictions,
    // namely that it will not display on the lock screen or above other system UI elements like the status bar.
    // Not entirely sure inflating an arbitrary layout in a toast should be allowed, but anyway...
    private inner class SwitchToast(alertParameters: Pair<Drawable, String>): Toast(context) {
        private val icon: ImageView by lazy { view.findViewById(R.id.overlayIcon) }
        private val text: TextView by lazy { view.findViewById(android.R.id.message) }
        private val showText = preferences.getBoolean("overlay_text", true)
        private val systemAccent = preferences.getBoolean("overlay_system_accent", true)
        private val background = preferences.getString("overlay_bg_colour", "dark")
        private val display: Display

        init {
            @SuppressLint("InflateParams")
            view = LayoutInflater.from(context).inflate(R.layout.overlay, null)

            duration = LENGTH_SHORT

            val (drawable, message) = alertParameters
            icon.setImageDrawable(drawable)
            if (showText) {
                text.text = message
                // set margin after icon only if text has content
                (icon.layoutParams as ViewGroup.MarginLayoutParams).apply { marginEnd = context.resources.getDimension(R.dimen.overlay_padding).toInt() }
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            display = windowManager.defaultDisplay
        }

        override fun show() {
            // Display this toast adjacent to the physical position of the switch.
            @Suppress("RtlHardcoded")
            when (display.rotation) {
                Surface.ROTATION_0 -> setGravity(Gravity.TOP or Gravity.LEFT, OFFSET_Y, OFFSET_X)
                Surface.ROTATION_90 -> setGravity(Gravity.BOTTOM or Gravity.LEFT, OFFSET_X, OFFSET_Y)
                Surface.ROTATION_180 -> setGravity(Gravity.BOTTOM or Gravity.RIGHT, OFFSET_Y, OFFSET_X)
                Surface.ROTATION_270 -> setGravity(Gravity.TOP or Gravity.RIGHT, OFFSET_X, OFFSET_Y)
            }

            // set colours to match system theme
            if (systemAccent) {
                icon.imageTintList = ColorStateList.valueOf(getSystemAccentColour())
            }

            val backgroundColour = context.getColor(when(background) {
                "system" -> getSystemColour()
                "dark" -> android.R.color.black
                "light" -> android.R.color.white
                else -> android.R.color.black
            })

            view.background.setColorFilter(backgroundColour, PorterDuff.Mode.ADD)
            text.setTextColor(invertColour(backgroundColour))

            super.show()
        }
    }

    private val toasts: HashMap<Boolean, SwitchToast> = hashMapOf(
        // Having two toasts is far more reliable than updating one.
        false to SwitchToast(switchable.getAlertParametersOff(context)),
        true to SwitchToast(switchable.getAlertParametersOn(context))
    )

    fun draw(toggled: Boolean) {
        // Draw the overlay next to the physical location of the switch.
        toasts[toggled]?.show()
        toasts[!toggled]?.cancel()
    }

    @ColorRes
    private fun getSystemColour(): Int {
        // Return the main system colour (light or dark).
        return when(context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> android.R.color.black
            Configuration.UI_MODE_NIGHT_NO -> android.R.color.white
            else -> android.R.color.black
        }
    }

    @ColorInt
    private fun getSystemAccentColour(): Int = with(TypedValue()) {
        // Return the system accent colour.
        context.theme.resolveAttribute(android.R.attr.colorAccent, this, true)
        this.data
    }

    @ColorInt
    private fun invertColour(@ColorInt colour: Int): Int {
        // Invert an ARGB colour by inverting all values except alpha.
        return colour xor 0x00FFFFFF
    }

    companion object {
        const val OFFSET_X = 520  // physical position of the switch, relative to ROTATION_0
        const val OFFSET_Y = 5    // margin from edge of display
    }
}