/*
 *  Copyright © 2019-2021 biqqles.
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

object Device {
    // Interface with kuntao's hardware switch using evdev.
    // Use inotify to watch the device's event file for input events. When one is detected, trigger a callback
    // (onSwitch) if it is from the switch.
    private val eventStream: DataInputStream
    private val inotify: FileObserver
    private val logTag = this::class.java.simpleName

    var onSwitch: Switchable? = null

    init {
        ensureReady()

        var file = DEVICE_FILE
        eventStream = try {
            DataInputStream(BufferedInputStream(FileInputStream(file)))
        } catch (e: IOException) {
            file = initialiseQShim()
            DataInputStream(BufferedInputStream(FileInputStream(file)))
        }

        inotify = object : FileObserver(file) {
            override fun onEvent(event: Int, path: String?) = processLastEvent()
        }
        inotify.startWatching()
        Log.i(logTag, "Watching device $file for events")
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
        val chmod = "chmod a+r $DEVICE_FILE"
        val supolicy = "supolicy --live " +
                "'allow appdomain input_device dir search' " +                   // directory rule
                "'allow appdomain input_device chr_file { getattr read open }'"  // character device rule

        // check if character file visible (though not necessarily readable)
        if (File(DEVICE_FILE).exists()) {
            Log.i(logTag, "$DEVICE_FILE: visible")
            return true
        }
        // if it's not, try to apply changes
        Log.i(logTag, "Applying chmod and supolicy")
        return Shell.runAsRoot(chmod) && Shell.runAsRoot(supolicy)
    }

    private fun processLastEvent() {
        // Look at the last event and run callback if it is from the switch.
        val event = getLastEvent()
        if (event.type == EV_SW && event.code == SW_ONEKEY_LOW_POWER) {
            Log.d(logTag, "Switch event: value ${event.value}")
            onSwitch?.switched(event.value == 1)
        }
    }

    private fun getLastEvent(): InputEvent {
        // Return the last event from the device's character file as an InputEvent.
        val buffer = ByteBuffer.allocate(InputEvent.SIZE)
            buffer.order(ByteOrder.nativeOrder())

        val read = try {eventStream.read(buffer.array())} catch (e: IOException) {0}
        // skip to end (ignore EV_SYN)
        val skipped = try {eventStream.skip(Long.MAX_VALUE)} catch (e: IOException) {0}
        Log.i(logTag, "Input event: read $read, skipped $skipped")
        return InputEvent(buffer)
    }

    private fun initialiseQShim(): String {
        // On Android Q and above, access to the filesystem from the JVM is restricted. To work around this, watch the
        // device file with inotifyd and mirror writes to a file we _can_ access. Return this file's path.
        File.createTempFile("dummy", null).parentFile.listFiles().forEach { it.delete() }  // clear cache directory

        val pipe = File.createTempFile("shim", ".pipe").absolutePath
        val script = File.createTempFile("shim", ".sh").absolutePath
        Log.i(logTag, "Initialising shim with pipe '$pipe' and script '$script'")

        // create a script which copies each struct from the device to the pipe
        val copyStruct = "head -n ${InputEvent.SIZE} $DEVICE_FILE >> $pipe"
        Shell.runAsRoot("echo '$copyStruct' > $script && chmod a+x $script")

        // start inotifyd in a new process
        Shell.run("su -c inotifyd $script $DEVICE_FILE")

        return pipe
    }
}

// Paths
private const val DEVICE_FILE = "/dev/input/event4"

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
