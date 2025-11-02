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

import android.os.Handler
import android.os.Looper
import com.topjohnwu.superuser.Shell

/**
 * Utility functions for rebooting Android devices with root access.
 */
object RebootUtils {

    /**
     * Reboot types supported by Android devices.
     */
    enum class RebootType {
        /** Normal system reboot */
        SYSTEM,

        /** Reboot into recovery mode */
        RECOVERY,

        /** Reboot into bootloader/fastboot mode */
        BOOTLOADER,

        /** Shutdown the device */
        SHUTDOWN,

        /** Reboot into EDL (Emergency Download) mode - use with caution! */
        EDL,

        /** Reboot into Android Safe Mode */
        SAFE_MODE
    }

    /**
     * Reboots device to specified mode after delay.
     *
     * @param type Reboot type
     * @param delayMs Delay before reboot (default: 5000ms)
     * @param validationCallback Optional callback to validate reboot. Return true to allow, false to cancel.
     */
    fun reboot(
        type: RebootType,
        delayMs: Long = 5000,
        validationCallback: ((RebootType) -> Boolean)? = null
    ) {
        // Check validation callback
        if (validationCallback != null && !validationCallback(type)) {
            return // Validation failed, cancel reboot
        }

        val command = when (type) {
            RebootType.SYSTEM -> "/system/bin/svc power reboot || reboot"
            RebootType.RECOVERY -> "/system/bin/svc power reboot recovery || reboot recovery"
            RebootType.BOOTLOADER -> "/system/bin/svc power reboot bootloader || reboot bootloader"
            RebootType.SHUTDOWN -> "/system/bin/svc power shutdown || reboot -p"
            RebootType.EDL -> "/system/bin/svc power reboot edl || reboot edl"
            RebootType.SAFE_MODE -> "/system/bin/svc power reboot safemode || reboot safemode"
        }

        Handler(Looper.getMainLooper()).postDelayed({
            Shell.cmd(command).submit()
        }, delayMs)
    }

    /**
     * Reboots immediately without delay. Use with caution.
     *
     * @param type Reboot type
     * @param validationCallback Optional callback to validate reboot
     */
    fun rebootImmediate(
        type: RebootType,
        validationCallback: ((RebootType) -> Boolean)? = null
    ) {
        reboot(type, delayMs = 0, validationCallback = validationCallback)
    }
}
