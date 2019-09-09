/*
 *  Copyright © 2019 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class Overlay(private val context: Context, switchable: SwitchableAction, private val showText: Boolean) {
    // An "overlay" which displays a notification on switch toggle above other apps, by inflating a custom layout
    // inside a Toast. This approach negates the need for the SYSTEM_OVERLAY permission but has the same restrictions,
    // namely that it will not display on the lock screen or above other system UI elements like the status bar.
    // Not entirely sure inflating an arbitrary layout in a toast should be allowed, but anyway...
    private inner class SwitchToast(drawable: Drawable?, message: String): Toast(context) {
        val icon: ImageView by lazy { view.findViewById<ImageView>(R.id.overlayIcon) }
        val text: TextView by lazy { view.findViewById<TextView>(android.R.id.message) }
        private val display: Display

        init {
            view = LayoutInflater.from(context).inflate(R.layout.overlay, null)
            duration = LENGTH_SHORT
            icon.setImageDrawable(drawable)

            if (showText) {
                text.text = message
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            display = windowManager.defaultDisplay
        }

        override fun show() {
            @Suppress("RtlHardcoded")
            when (display.rotation) {
                Surface.ROTATION_0 -> setGravity(Gravity.TOP or Gravity.LEFT, OFFSET_Y, OFFSET_X)
                Surface.ROTATION_90 -> setGravity(Gravity.BOTTOM or Gravity.LEFT, OFFSET_X, OFFSET_Y)
                Surface.ROTATION_180 -> setGravity(Gravity.BOTTOM or Gravity.RIGHT, OFFSET_Y, OFFSET_X)
                Surface.ROTATION_270 -> setGravity(Gravity.TOP or Gravity.RIGHT, OFFSET_X, OFFSET_Y)
            }
            super.show()
        }
    }

    private val toastOff: SwitchToast  // having two toasts is far more reliable than updating one
    private val toastOn: SwitchToast

    init {
        toastOff = SwitchToast(context.getDrawable(switchable.iconOff),
            context.getString(switchable.name) + context.getString(R.string.off))
        toastOn = SwitchToast(context.getDrawable(switchable.iconOn),
            context.getString(switchable.name) + context.getString(R.string.on))
    }

    fun draw(toggled: Boolean) {
        // Draw the overlay next to the physical location of the switch.
        val toast = if (toggled) toastOn else toastOff
        toast.show()
    }

    companion object {
        const val OFFSET_X = 520  // physical position of the switch, relative to ROTATION_0
        const val OFFSET_Y = 5    // margin from edge of display
    }
}