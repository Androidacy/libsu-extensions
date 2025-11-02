# libsu-extensions

Powerful utilities and extensions for [libsu](https://github.com/topjohnwu/libsu) - the industry-standard library for Android root access.

Production-ready utilities designed to make working with root access easier, safer, and more efficient.

## Features

- **SuFile Extensions** - Simplified file I/O with root access
- **Shell Utilities** - Command injection prevention, mount/unmount operations with retry logic
- **Reboot Utilities** - Support for all reboot types with validation callbacks
- **SimpleIPC** - File-based IPC mechanism for shell-to-app communication
- **SuFileSocket** - Bidirectional file socket for continuous communication
- **BusyboxInitializer** - Automatic busybox detection for Magisk/KernelSU/APatch

## Installation

Add to your `build.gradle.kts`:

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.Androidacy:libsu-extensions:1.0.0")
}
```

## Usage

### SuFile Extensions

```kotlin
import com.androidacy.libsu.extensions.*
import com.topjohnwu.superuser.io.SuFile

val file = SuFile("/data/local/tmp/test.txt")
val contents = file.readText()
file.writeText("Hello World")
file.appendText("\nNew line")
```

### Shell Utilities

```kotlin
import com.androidacy.libsu.extensions.ShellUtils

// Prevent command injection attacks
val safe = ShellUtils.escapeShellArg(userInput)
Shell.cmd("cat $safe").exec()

// Mount operations with automatic retry
ShellUtils.mountFile("/data/image.img", "/data/mnt")
ShellUtils.unmountFile("/data/mnt")

// File operations
if (ShellUtils.fileExists("/system/build.prop")) {
    // File exists
}
```

### Reboot Utilities

```kotlin
import com.androidacy.libsu.extensions.RebootUtils

// Basic reboot
RebootUtils.reboot(RebootUtils.RebootType.RECOVERY)

// With validation callback
RebootUtils.reboot(
    type = RebootUtils.RebootType.EDL,
    validationCallback = { type ->
        // Custom validation logic
        if (shouldAllowReboot(type)) true else false
    }
)
```

### SimpleIPC

```kotlin
import com.androidacy.libsu.extensions.SimpleIPC

val ipc = SimpleIPC(
    cacheDir = context.cacheDir,
    shell = Shell.Builder.create().build(),
    onCommand = { requestId, command ->
        val response = processCommand(command)
        ipc.sendResponse(requestId, response)
    }
)

// Get configuration for shell scripts
val (ipcDir, cmdPrefix, rspPrefix) = ipc.getShellConfig()
```

### SuFileSocket

```kotlin
import com.androidacy.libsu.extensions.SuFileSocket

val socket = SuFileSocket(
    filePath = "/data/local/tmp/socket",
    scope = lifecycleScope,
    onContentsChanged = { data ->
        handleShellMessage(data)
    }
)

lifecycleScope.launch {
    socket.writeToOutputStream("message")
}
```

### BusyboxInitializer

```kotlin
import com.androidacy.libsu.extensions.BusyboxInitializer

val shell = Shell.Builder.create()
    .setInitializers(BusyboxInitializer::class.java)
    .build()

// With callback
BusyboxInitializer.setBusyboxCallback { success ->
    if (success) {
        // Busybox configured successfully
    }
}
```

## Security

### Command Injection Prevention

Always escape user input before using in shell commands:

```kotlin
// ❌ Vulnerable to injection
Shell.cmd("cat $userInput").exec()

// ✅ Safe
val safe = ShellUtils.escapeShellArg(userInput)
Shell.cmd("cat $safe").exec()
```

### Dedicated Shell Instances

For IPC and file operations, use dedicated shell instances to prevent blocking:

```kotlin
val mainShell = Shell.Builder.create().build()
val ioShell = Shell.Builder.create().build()

val ipc = SimpleIPC(cacheDir, ioShell) { id, cmd ->
    // Handle command
}
```

## Documentation

Full API documentation available at: https://javadoc.jitpack.io/com/github/Androidacy/libsu-extensions/latest/javadoc/

Each class and method includes KDoc documentation.

## Requirements

- Android API 24+ (Android 7.0+)
- Kotlin 1.9+
- libsu 6.0.0+

## License

```
Copyright (c) 2025 Androidacy

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Acknowledgments

Used in production by Androidacy applications.

Built on [libsu](https://github.com/topjohnwu/libsu) by topjohnwu.
