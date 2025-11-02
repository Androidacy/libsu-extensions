/*
 * Copyright (c) 2025 Androidacy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidacy.libsu.extensions

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile

/**
 * Utility functions for working with libsu Shell commands.
 */
object ShellUtils {

    /**
     * Escapes shell arguments to prevent command injection. Wraps in single quotes
     * and escapes embedded quotes.
     *
     * @param arg Argument to escape
     * @return Escaped argument safe for shell use
     */
    fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    /**
     * Checks if file exists using root access.
     *
     * @param path Absolute path to check
     * @return true if file exists
     */
    fun fileExists(path: String): Boolean {
        return SuFile(path).exists()
    }

    /**
     * Checks if path is mounted.
     *
     * @param path Mount source or mount point
     * @return true if path appears in mount table
     */
    fun isMounted(path: String): Boolean {
        return Shell.cmd("mount | grep ${escapeShellArg(path)}")
            .exec()
            .out
            .isNotEmpty()
    }

    /**
     * Mounts file or device with retry logic. Retries up to 3 times on failure.
     *
     * @param path Source file or device
     * @param mountPoint Target mount point
     * @param force Force unmount if already mounted
     * @return true if mount succeeded
     */
    @Synchronized
    fun mountFile(path: String, mountPoint: String, force: Boolean = false): Boolean {
        if (isMounted(mountPoint)) {
            if (force) {
                unmountFile(mountPoint)
            } else {
                return true
            }
        }

        // Attempt mount
        val mount = Shell.cmd("mount ${escapeShellArg(path)} ${escapeShellArg(mountPoint)}")
            .exec()

        return if (mount.isSuccess) {
            true
        } else {
            // Retry up to 3 times with 150ms delays
            repeat(3) {
                Thread.sleep(150)
                val retryMount = Shell.cmd("mount ${escapeShellArg(path)} ${escapeShellArg(mountPoint)}")
                    .exec()
                if (retryMount.isSuccess) {
                    return true
                }
            }
            false
        }
    }

    /**
     * Unmounts all mounts at mount point. Syncs filesystem before unmounting.
     *
     * @param mountPoint Mount point to unmount
     * @return true if all unmounts succeeded
     */
    @Synchronized
    fun unmountFile(mountPoint: String): Boolean {
        val mountsResult = Shell.cmd("mount | grep ${escapeShellArg(mountPoint)}")
            .exec()

        if (mountsResult.isSuccess && mountsResult.out.isNotEmpty()) {
            // Unmount each matching mount
            for (line in mountsResult.out) {
                // Parse mount line to get mount point (3rd field)
                val parts = line.split(" ")
                if (parts.size >= 3) {
                    val path = parts[2]
                    val unmount = Shell.cmd("sync && umount $path")
                        .exec()
                    if (!unmount.isSuccess) {
                        return false
                    }
                }
            }
        }
        return true
    }
}
