/*
 *  Copyright © 2019 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import android.os.FileObserver
import android.util.Log
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val DEVICE_FILE = "/dev/input/event4"

object Device {
    // Interface with kuntao's hardware switch using evdev.
    // Use inotify to watch the device's event file for input events. When one is detected, trigger a callback
    // (onSwitch) if it is from the switch.
    private val eventStream: DataInputStream?
    private val inotify = object : FileObserver(DEVICE_FILE) {
        override fun onEvent(event: Int, path: String?) = processLastEvent()
    }

    var onSwitch: Switchable? = null

    init {
        ensureReady()
        eventStream = try {
            DataInputStream(BufferedInputStream(FileInputStream(DEVICE_FILE)))
        } catch (e: IOException) {
            null
        }
        inotify.startWatching()
    }

    fun ensureReady(): Boolean {
        // Prepare the device's character file for use by non-privileged users.
        // Specifically,
        //   - Make DEVICE_FILE readable by all
        //   - Patch SEPolicy to extent required to watch and read DEVICE_FILE
        //       - All other devices remain unreadable without su
        //       - We could move to priv-app to limit access further but this comes
        //          at the cost of user-hostility, making uninstallation difficult
        // Return true if successful, false if not.
        val chmod = "su -c chmod a+r $DEVICE_FILE"
        val supolicy = "su -c supolicy --live " +
                "'allow appdomain input_device dir search' " +                   // directory rule
                "'allow appdomain input_device chr_file { getattr read open }'"  // character device rule

        assert(Shell.isRootAvailable)
        // check if character file readable
        if (File(DEVICE_FILE).exists()) {
            return true
        }
        // if it's not, try to apply changes
        return Shell.run(chmod) && Shell.run(supolicy)
    }

    private fun processLastEvent() {
        // Look at the last event and run callback if it is from the switch.
        val event = getLastEvent()
        if (event.type == EV_SW && event.code == SW_ONEKEY_LOW_POWER) {
            Log.i(this::class.java.simpleName, "Switch event: value ${event.value}")
            onSwitch?.switched(event.value == 1)
        }
    }

    private fun getLastEvent(): InputEvent {
        // Return the last event from the device's character file as an InputEvent.
        val buffer = ByteBuffer.allocate(InputEvent.SIZE)
            buffer.order(ByteOrder.nativeOrder())

        try {
            if (eventStream?.read(buffer.array()) != buffer.capacity()) throw IOException()
        } catch (e: IOException) {
            e.printStackTrace()  // shouldn't ever happen
            ensureReady()  // but maybe you forgot to initialise?
        }

        eventStream?.skip(Long.MAX_VALUE)  // skip to end (ignore EV_SYN)
        return InputEvent(buffer)
    }
}

// Definitions from input.h and input-event-codes.h
private const val EV_SW: Short = 0x05
private const val SW_ONEKEY_LOW_POWER: Short = 0x13

@Suppress("UsePropertyAccessSyntax", "PropertyName", "unused")
private class InputEvent(buffer: ByteBuffer) {
    val tv_sec = buffer.getLong()
    val tv_usec = buffer.getLong()
    val type = buffer.getShort()
    val code = buffer.getShort()
    val value = buffer.getInt()

    init {
        require(buffer.capacity() == SIZE)
    }

    companion object {
        const val SIZE = 24
    }
}
