# Hourly Reminder Alarm

Hourly Reminder Alarm is a production-grade, offline-first periodic timer application designed for modern Android (Android 8.0 through Android 15). Built using **Jetpack Compose**, **Material 3 guidelines**, and optimized for extreme background reliability.

---

## Architecture & Visual Polish

This application is engineered as a highly responsive single-screen dashboard combining all timer controllers, custom audio configurations, and diagnostic tools in a clean, high-contrast Material 3 layout utilizing the **"Cosmic Slate"** theme.

- **Dynamic Status Monitoring**: Interactive card displays real-time details of the active timer and calculates the exact next trigger timestamp.
- **Vibrant Controls**: Segmented chips for rapid interval selection alongside manual numeric entry fields.
- **Custom Auditory Feedback**: Preloaded sound tones selected from assets or a silent fallback to default system alarm ringtones.
- **Permission Diagnostics**: Self-diagnosing dashboard directly detects missing Android system permissions and links users to system configuration screens to fix them.

---

## Background & Reliability Architecture

To keep the timer firing accurately when the screen is locked, minimized, or when the phone reboots, the application implements several core Android OS mechanics:

1. **AlarmManager**: Uses exact, non-batched alarms (`setExactAndAllowWhileIdle`) scheduled one-by-one to prevent Android from delay-batching periodic events.
2. **Foreground Service**: Starts a persistent status tracker service with custom `specialUse` foreground types to keep the application process resident in device memory.
3. **BootReceiver**: Automatically intercepting `ACTION_BOOT_COMPLETED` to reschedule active timers when the device starts up.
4. **WakeLock**: CPU partial wakelocks keep processing active when playing sound notifications.
5. **Battery Optimization Exemptions**: Directly asks users to disable Doze mode restrictions to preserve alert timings.

---

## System Permissions Analysis

The application declares and requests several permissions to function securely:

- `POST_NOTIFICATIONS`: Prompts the user on Android 13+ to grant alert displaying rights.
- `SCHEDULE_EXACT_ALARM`: Granted for scheduling exact, predictable reminders.
- `RECEIVE_BOOT_COMPLETED`: Essential for starting the boot receiver upon phone restarts.
- `WAKE_LOCK`: Kept to wake device processors temporarily upon alarm firing.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`: Required for background residency of the monitoring service.
- `VIBRATE`: Enables physical tactile alerts.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Prompts the system to ignore Doze limits.

---

## Build Instructions

To build and run this application locally, you can use the standard Gradle commands:

- Run tests: `./gradlew testDebugUnitTest`
- Compile Applet: Use the standard compile tools in AI Studio.
