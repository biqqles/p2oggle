/*
 *  Copyright © 2019 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.ImageView
import android.widget.Toast

class Overlay(context: Context, switchable: SwitchableAction, private val showText: Boolean) {
    // An "overlay" which displays a notification on switch toggle above other apps, by inflating a custom layout
    // inside a Toast. This approach negates the need for the SYSTEM_OVERLAY permission but has the same restrictions,
    // namely that it will not display on the lock screen or above other system UI elements like the status bar.
    // Not entirely sure inflating an arbitrary layout in a toast should be allowed, but anyway...
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val display: Display = windowManager.defaultDisplay
    private val handler = Handler(Looper.getMainLooper())
    private val toast: Toast = Toast(context)
    private val icon: ImageView

    private val messageOff = context.getString(switchable.name) + context.getString(R.string.off)
    private val messageOn = context.getString(switchable.name) + context.getString(R.string.on)
    private val iconOff = context.getDrawable(switchable.iconOff)
    private val iconOn = context.getDrawable(switchable.iconOn)

    init {
        toast.duration = Toast.LENGTH_SHORT
        toast.view  = LayoutInflater.from(context).inflate(R.layout.overlay, null)
        icon = toast.view.findViewById(R.id.overlayIcon)
    }

    fun draw(toggled: Boolean) {
        // Draw the bg_overlay next to the physical location of the switch.
        handler.run {
            toast.cancel()
            @Suppress("RtlHardcoded")  // we are concerned with physical orientation
            when (display.rotation) {
                Surface.ROTATION_0 -> toast.setGravity(Gravity.TOP or Gravity.LEFT, OFFSET_Y, OFFSET_X)
                Surface.ROTATION_90 -> toast.setGravity(Gravity.BOTTOM or Gravity.LEFT, OFFSET_X, OFFSET_Y)
                Surface.ROTATION_180 -> toast.setGravity(Gravity.BOTTOM or Gravity.RIGHT, OFFSET_Y, OFFSET_X)
                Surface.ROTATION_270 -> toast.setGravity(Gravity.TOP or Gravity.RIGHT, OFFSET_X, OFFSET_Y)
            }

            icon.setImageDrawable(if (toggled) iconOn else iconOff)
            if (showText) {
                toast.setText(if (toggled) messageOn else messageOff)
            }

            toast.show()
        }
    }

    companion object {
        const val OFFSET_X = 520  // physical position of the switch, relative to ROTATION_0
        const val OFFSET_Y = 5    // margin from edge of display
    }
}