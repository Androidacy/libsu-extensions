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

import android.content.Context
import androidx.annotation.Keep
import com.topjohnwu.superuser.Shell

/**
 * Auto-detects and configures busybox for Magisk, KernelSU, or APatch environments.
 * Configures shell to use busybox ash in standalone mode.
 *
 * @see Shell.Initializer
 */
@Keep
class BusyboxInitializer : Shell.Initializer() {

    override fun onInit(context: Context, shell: Shell): Boolean {
        // Auto-detect busybox location based on root management solution
        val detectScript = """
            exec $(
                if [[ $(su -v | cut -d':' -f2 | tr "[:upper:]" "[:lower:]") == "magisksu" ]]; then
                    echo "/data/adb/magisk/busybox"
                elif [[ $(su -v | cut -d':' -f2 | tr "[:upper:]" "[:lower:]") == "kernelsu" ]]; then
                    echo "/data/adb/ksu/bin/busybox"
                elif [[ $(su -v | cut -d':' -f2 | tr "[:upper:]" "[:lower:]") == "apatch" ]]; then
                    echo "/data/adb/ap/bin/busybox"
                else
                    which busybox
                fi
            ) ash -o standalone
        """.trimIndent()

        val result = shell.newJob()
            .add(detectScript)
            .exec()

        // Notify callback of result
        busyboxCallback?.invoke(result.isSuccess)

        // Always return true to allow shell initialization to complete
        // Even if busybox detection fails, the shell should still be usable
        return true
    }

    companion object {
        private var busyboxCallback: ((Boolean) -> Unit)? = null

        /**
         * Sets callback for busybox detection result.
         *
         * @param callback Function receiving true on success, false on failure
         */
        fun setBusyboxCallback(callback: ((Boolean) -> Unit)?) {
            busyboxCallback = callback
        }
    }
}
