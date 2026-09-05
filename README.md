# Health Widget: Eye Care Timer

A personal 10-minute eye care timer application and adaptive home-screen widget for Android written in Kotlin using Jetpack Compose and Jetpack Glance.

## Features

- **10-Minute Eye Care Timer**: Designed to encourage regular screen breaks and the 20-20-20 rule (looking at an object 20 feet away for 20 seconds) to prevent digital eye strain.
- **Jetpack Glance Widget**:
  - Scales responsively from compact phone layouts up to a **4×2 size on tablets**.
  - Three distinct states:
    - **Ready**: Shows eye break status and a prominent "Start 10m" button.
    - **Running**: Displays live remaining countdown and a "Cancel" button.
    - **Finished**: Displays "Time's Up! Rest Your Eyes" with an action to reset or start again.
  - Tapping the widget body launches the main app.
- **Background Resiliency**:
  - Uses timestamp-based elapsed time (`System.currentTimeMillis()`) so the countdown stays strictly accurate even if the app process is paused or killed.
  - Integrates `AlarmManager.setExactAndAllowWhileIdle()` on supported devices with fallback to `setAndAllowWhileIdle()` to reliably alert even in doze mode.
  - Device reboot handling via `BootReceiver` to restore or reconcile timers.
  - Prevents accidentally overwriting or restarting timers while one is actively running.
- **High-Priority Notifications**:
  - Triggers an Android notification with sound, vibration, and actionable buttons when the 10-minute break arrives.
  - Handles Android 13+ (`POST_NOTIFICATIONS`) and Android 12+ (`SCHEDULE_EXACT_ALARM`) permission requirements cleanly.
- **CI / CD**:
  - GitHub Actions workflow (`.github/workflows/build-debug.yml`) automatically executes unit tests and compiles `app-debug.apk`.

## Building the Project

### Prerequisites
- JDK 17
- Android SDK 35 (compileSdk 35, minSdk 26)

### Gradle Commands
```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

The assembled APK is generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
