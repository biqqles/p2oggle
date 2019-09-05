/*
 *  Copyright © 2019 biqqles.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.p2oggle

import java.io.DataOutputStream

object Shell {
    // Utility functions for working with shell commands.
    private val suProcess: Process by lazy {
        // Using a persistent superuser process means we don't ping toasts every time we need to issue a command.
        assert(isRootAvailable())
        Runtime.getRuntime().exec("su")
    }

    fun run(command: String): Boolean {
        // Execute the given shell command *synchronously*, returning whether successful.
        return try {
            Runtime.getRuntime().exec(command).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun runAsRoot(command: String) {
        // Execute the given shell command *asynchronously* and as root.
        // In cases where determining if the command executed successfully is critical, use `run` with `su -c`.
        val input = DataOutputStream(suProcess.outputStream)
        input.writeBytes(command + '\n')
        input.flush()
    }

    fun isRootAvailable(): Boolean {
        // Test the execution of no-op as root.
        return run("su -c :")
    }
}
