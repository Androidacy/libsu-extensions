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

import com.topjohnwu.superuser.io.SuFile
import java.io.FileNotFoundException

/**
 * Reads file contents as String. Blocks until complete.
 *
 * @param exceptionOnFailure Throw exceptions on failure, otherwise return empty string
 * @return File contents or empty string
 * @throws FileNotFoundException if file missing and [exceptionOnFailure] true
 */
fun SuFile.readText(exceptionOnFailure: Boolean = false): String {
    if (!exists()) {
        if (exceptionOnFailure) {
            throw FileNotFoundException("File does not exist: ${this.absolutePath}")
        }
        return ""
    }

    return try {
        this.newInputStream().use { inputStream ->
            inputStream.readBytes().decodeToString()
        }
    } catch (e: Exception) {
        if (exceptionOnFailure) {
            throw e
        }
        ""
    }
}

/**
 * Writes text to file, replacing existing content. File must exist. Blocks until complete.
 *
 * @param text Text to write
 * @param exceptionOnFailure Throw exceptions on failure, otherwise fail silently
 * @throws FileNotFoundException if file missing and [exceptionOnFailure] true
 */
fun SuFile.writeText(text: String, exceptionOnFailure: Boolean = false) {
    if (!exists()) {
        if (exceptionOnFailure) {
            throw FileNotFoundException("File does not exist: ${this.absolutePath}")
        }
        return
    }

    try {
        this.newOutputStream().use { outputStream ->
            outputStream.write(text.toByteArray())
        }
    } catch (e: Exception) {
        if (exceptionOnFailure) {
            throw e
        }
    }
}

/**
 * Appends text to file. Creates file if missing. Blocks until complete.
 *
 * @param text Text to append
 * @param exceptionOnFailure Throw exceptions on failure, otherwise fail silently
 */
fun SuFile.appendText(text: String, exceptionOnFailure: Boolean = false) {
    if (!exists()) {
        createNewFile()
    }

    try {
        this.newOutputStream(true).use { outputStream ->
            outputStream.write(text.toByteArray())
        }
    } catch (e: Exception) {
        if (exceptionOnFailure) {
            throw e
        }
    }
}
