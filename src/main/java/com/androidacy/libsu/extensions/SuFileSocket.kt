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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * File-based bidirectional socket for app-shell IPC. Uses polling and file clearing
 * to detect changes. Implements [Closeable] - must be closed to clean up resources.
 *
 * @param filePath Absolute path for socket file
 * @param scope CoroutineScope for read loop (e.g., lifecycleScope)
 * @param shellInitializer Optional Shell.Initializer
 * @param onContentsChanged Callback when shell writes. If null, socket is write-only.
 */
class SuFileSocket(
    private val filePath: String,
    private val scope: CoroutineScope,
    shellInitializer: Class<out Shell.Initializer>? = null,
    private val onContentsChanged: ((String) -> Unit)? = null
) : Closeable {

    private val shellForSuFile: Shell = Shell.Builder.create()
        .setFlags(Shell.FLAG_MOUNT_MASTER)
        .apply {
            if (shellInitializer != null) {
                setInitializers(shellInitializer)
            }
        }
        .build()

    private val suFile: SuFile by lazy {
        val file = SuFile(filePath)
        file.shell = shellForSuFile

        // Clean up any existing file
        if (file.exists()) {
            file.delete()
        }
        file.createNewFile()
        file
    }

    private var lastContentsRead = ""

    init {
        // Only start read loop if we have a callback
        if (onContentsChanged != null) {
            scope.launch {
                readFromInputStream(onContentsChanged)
            }
        }
    }

    private suspend fun readFromInputStream(callback: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            // Read from file until it's deleted
            while (suFile.exists()) {
                try {
                    val currentContent = suFile.readText().trim()

                    if (currentContent.isNotEmpty() && currentContent != lastContentsRead) {
                        // Deliver content to consumer
                        callback(currentContent)

                        // Immediately clear the file so identical consecutive commands
                        // are detected as changes by the next write
                        try {
                            suFile.writeText("")
                        } catch (e: Exception) {
                            // Silently ignore clear errors
                        }
                        lastContentsRead = ""
                    }
                } catch (e: Exception) {
                    // Silently ignore read errors
                }

                delay(100) // Poll every 100ms
            }
        }
    }

    /**
     * Writes data to socket. Suspend function using Dispatchers.IO.
     *
     * @param data Data to write (trimmed before writing)
     */
    suspend fun writeToOutputStream(data: String) {
        withContext(Dispatchers.IO) {
            try {
                suFile.writeText(data.trim())
            } catch (e: Exception) {
                // Silently ignore write errors
            }
        }
    }

    /**
     * Checks if socket is open.
     *
     * @return true if socket file exists
     */
    fun isOpen(): Boolean {
        return suFile.exists()
    }

    /**
     * Closes socket and cleans up resources. Deletes file, closes shell, stops read loop.
     */
    override fun close() {
        try {
            suFile.delete()
        } catch (e: Exception) {
            // Silently ignore delete errors
        }

        try {
            shellForSuFile.close()
        } catch (e: Exception) {
            // Silently ignore shell close errors
        }
    }

    override fun toString(): String {
        return "SuFileSocket(filePath='$filePath', open=${isOpen()})"
    }
}
