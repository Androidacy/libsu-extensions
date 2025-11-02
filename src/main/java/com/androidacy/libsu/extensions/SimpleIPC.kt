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

import android.os.FileObserver
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * File-based IPC using FileObserver for shell-to-app communication. Shell writes commands
 * to randomized files, app detects via FileObserver and responds. Includes retry logic
 * for race condition protection.
 *
 * @param cacheDir Cache directory for IPC files
 * @param shell Shell instance for file permissions (use dedicated instance)
 * @param onCommand Callback when shell sends command. Receives requestId and command data.
 */
class SimpleIPC(
    cacheDir: File,
    private val shell: Shell,
    private val onCommand: (requestId: String, command: String) -> Unit
) {
    // Runtime randomized directory name to prevent attacks
    private val ipcDirName = generateRandomName("ipc", 8)
    private val ipcDir = File(cacheDir, ipcDirName)

    // Runtime randomized file prefixes
    private val cmdPrefix = generateRandomName("c", 4)
    private val rspPrefix = generateRandomName("r", 4)

    private var fileObserver: FileObserver? = null
    private val pendingCommands = ConcurrentHashMap<String, Long>()

    init {
        setupIpcDir()
        startObserver()
    }

    private fun generateRandomName(prefix: String, length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val suffix = (1..length).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        return "$prefix$suffix"
    }

    private fun setupIpcDir() {
        // Create IPC directory in cache (accessible to root shell)
        if (!ipcDir.exists()) {
            ipcDir.mkdirs()
        }

        // Make it accessible to shell
        shell.newJob().add("chmod 777 '${ipcDir.absolutePath}'").exec()

        // Clean any old files
        val oldFiles = ipcDir.listFiles()
        oldFiles?.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    private fun startObserver() {
        fileObserver = object : FileObserver(ipcDir.absolutePath, CREATE or MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return

                // Only process command files (with our randomized prefix)
                if (!path.startsWith(cmdPrefix)) return

                val file = File(ipcDir, path)
                if (!file.exists() || !file.canRead()) return

                try {
                    // Extract request ID from filename: {prefix}_{requestId}
                    val requestId = path.removePrefix("${cmdPrefix}_")
                    if (requestId.isEmpty()) return

                    // Avoid duplicate processing
                    val lastProcessed = pendingCommands[requestId] ?: 0L
                    if (file.lastModified() <= lastProcessed) return
                    pendingCommands[requestId] = file.lastModified()

                    // Read command with retry logic for race condition protection
                    val command = readCommandWithRetry(file)
                    if (command.isEmpty()) {
                        return
                    }

                    // Delete command file immediately
                    file.delete()

                    // Process command
                    try {
                        onCommand(requestId, command)
                    } catch (e: Exception) {
                        // Silently ignore command processing errors
                    }

                } catch (e: Exception) {
                    // Silently ignore file processing errors
                }
            }
        }
        fileObserver?.startWatching()
    }

    /**
     * Reads command with retry logic for race conditions.
     *
     * @param file File to read
     * @param maxRetries Max retry attempts (default: 5)
     * @param delayMs Delay between retries (default: 10ms)
     * @return File contents or empty string on failure
     */
    private fun readCommandWithRetry(file: File, maxRetries: Int = 5, delayMs: Long = 10): String {
        repeat(maxRetries) { attempt ->
            try {
                val content = file.readText().trim()

                // Validate that we have complete content (basic check for JSON)
                if (content.isNotEmpty() && content.startsWith("{") && content.endsWith("}")) {
                    return content
                }

                // If content is empty or incomplete, wait and retry
                if (attempt < maxRetries - 1) {
                    Thread.sleep(delayMs)
                }
            } catch (e: Exception) {
                // File might not be readable yet, wait and retry
                if (attempt < maxRetries - 1) {
                    Thread.sleep(delayMs)
                }
            }
        }
        return ""
    }

    /**
     * Sends response to shell script.
     *
     * @param requestId Request ID from [onCommand]
     * @param response Response data
     */
    fun sendResponse(requestId: String, response: String) {
        try {
            val responseFile = File(ipcDir, "${rspPrefix}_$requestId")
            responseFile.writeText(response)
            // Make response file readable by shell
            shell.newJob().add("chmod 666 '${responseFile.absolutePath}'").exec()
        } catch (e: Exception) {
            // Silently ignore send errors
        }
    }

    /**
     * Gets IPC config for shell scripts.
     *
     * @return Triple of (ipcDir, cmdPrefix, rspPrefix)
     */
    fun getShellConfig(): Triple<String, String, String> {
        return Triple(ipcDir.absolutePath, cmdPrefix, rspPrefix)
    }

    /**
     * Cleans up IPC resources. Stops FileObserver and deletes IPC directory.
     */
    fun cleanup() {
        fileObserver?.stopWatching()
        fileObserver = null

        // Clean up IPC directory
        try {
            ipcDir.listFiles()?.forEach { it.delete() }
            ipcDir.delete()
        } catch (e: Exception) {
            // Silently ignore cleanup errors
        }
    }
}
