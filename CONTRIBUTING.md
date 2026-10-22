# Contributing to Wavelet (Personal Fork)

> [!NOTE]
> **Wavelet** is a custom-fit personal fork of **BitChord**, an open-source YouTube Music client created by **Kushagra Singh** ([@kushagrasinghx](https://github.com/kushagrasinghx)) at [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord).
>
> - **Fork Maintainer:** **Ajay Kumar Reddy Krishnareddy Gari** ([@ajaykumarreddy-k](https://github.com/ajaykumarreddy-k))
> - **App Name:** **Wavelet** — tailored with custom features (such as lazy YouTube playlist local importing) and dedicated UI/UX enhancements.
> - **Usage:** Changes here are made primarily for personal use. If you find them enjoyable or helpful, feel free to use them! If you are looking for general contributions or the official upstream project, please refer to the original repository at [kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord).

## Development Guidelines
If you are experimenting with or contributing to this personal fork:
- Keep changes aligned with the UI/UX enhancement goals.
- Keep pull requests or patches focused and clean.
- Ensure any added dependencies or modifications comply with the project's GPLv3 license.

## Development Setup
The project requires the following tools:
- **JDK**: Java Development Kit 17 (Eclipse Temurin 17 recommended).
- **Android SDK**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
- **C/C++ NDK & CMake**: CMake 3.22.1+ and Android NDK (for native audio DSP components configured under `app/src/main/cpp`).
- **Listen Together Backend (Optional)**: Go 1.22+ if developing or testing the party server (`backend/`).

## Build and Test Commands
Run Gradle commands from the repository root.

Build:
```bash
./gradlew assembleDevDebug
```

Windows:
```powershell
.\gradlew.bat assembleDevDebug
```

Tests:
```bash
./gradlew testDevDebugUnitTest
```

Windows:
```powershell
.\gradlew.bat testDevDebugUnitTest
```

Run a specific test:
```bash
./gradlew testDevDebugUnitTest --tests "com.music.bitchord.playback.audio.DirectAudioStreamingRegressionTest"
```

## Code Quality
- Follow idiomatic Kotlin and Jetpack Compose conventions.
- Maintain the project's existing architecture and separation of concerns (UI, domain logic, playback services, and data repositories).
- Write unit tests for new logic, fixes, and edge cases.
- Contributors should run relevant tests, must not introduce new failures, and should document known pre-existing failures when applicable.
- Keep diffs focused and minimal. Avoid unnecessary third-party dependencies.

## Audio Changes and Telemetry
For audio changes: application telemetry must not be presented as proof of physical hardware behavior unless that hardware behavior was actually verified.

Distinguish clearly between source metadata, decoder format, internal DSP format, AudioTrack output, AudioFlinger/HAL state, advertised device capability, and external physical endpoint behavior.

## Review Process
All pull requests require review and approval by repository maintainers before merging. Maintainers review changes for correctness, architecture fit, and maintainability.

## Dependencies and Licensing
BitChord is licensed under the **GNU General Public License v3.0 (GPLv3)**.

All contributed code and dependencies must be strictly compatible with GPLv3. Avoid introducing external dependencies unless strictly necessary; any new dependency must be evaluated for necessity, binary size, and license compliance.
