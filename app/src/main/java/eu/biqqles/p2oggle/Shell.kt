/*
 *  Copyright © 2019-2021 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import java.io.DataOutputStream
import java.io.IOException

object Shell {
    // Utility functions for working with shell commands.
    private val suProcess: Process by lazy {
        // Using a persistent superuser process means we don't ping toasts every time we need to issue a command.
        run("su")
    }

    val isRootAvailable: Boolean
        // Check whether suProcess has exited. If it has, assume su is not available.
        get() = try {
            suProcess.exitValue()
            false
        } catch (e: IOException) {
            false
        } catch (e: IllegalThreadStateException) {
            true
        }

    fun run(command: String): Process {
        // Execute the given shell command *asynchronously* in a new process, which is returned.
        return Runtime.getRuntime().exec(command)
    }

    fun runAsRoot(command: String): Boolean {
        // Execute the given shell command *synchronously* and as root, returning whether successful.
        // Because we need to run everything in a persistent su process (each su call creates a toast), we can't use
        // waitFor(). Instead, emit an indicator if the command successfully completes, and block until it arrives.
        val input = DataOutputStream(suProcess.outputStream)
        val output = suProcess.inputStream
        val error = suProcess.errorStream

        input.writeBytes("$command && echo -n $completionIndicator \n")
        input.flush()

        if (error.available() > 0) return false
        while (output.read() != completionIndicator.toInt()) continue
        return true
    }

    private const val completionIndicator = 'U'  // a char that marks the successful completion of a command
}
