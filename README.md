# TrioNS for Android

A native Android companion app for [Nightscout](https://nightscout.github.io/), visually modeled
on the [Trio](https://github.com/nightscout/Trio) iOS closed-loop app. Trio itself is iOS-only;
this app gives Trio users a way to check their glucose, insulin, and loop status from an Android
device, in a UI that matches Trio's own look rather than a generic Nightscout viewer.

It's a personal project built for the author's own daily use (type 1 diabetes, Trio looper) and is
not affiliated with the Nightscout or Trio projects.

## Use case

Trio runs on iOS only, so a Trio user who also carries (or prefers) an Android phone/watch has no
first-party way to see their loop at a glance on that device. TrioNS reads the same data Trio
already uploads to Nightscout — glucose, basal/bolus treatments, device status (IOB, pump/CGM
lifecycle) — and renders it in a layout deliberately matched to Trio's own screens, so it feels
like the same app rather than a different tool with different conventions. It's read-only: it
displays Trio's loop, it doesn't control the pump or the algorithm.

## Features

**Home**
- Glucose bubble: current reading, trend arrow, and ring gradient/colors matched pixel-for-pixel
  to Trio's `CurrentGlucoseView`
- Scrollable, zoomable chart with basal (temp + scheduled), bolus, glucose, IOB, and active
  overrides/temp targets layered in Trio's own visual arrangement
- HUD pills: reservoir level, pump/CGM lifecycle countdowns, current IOB

**History**
- Treatments: all insulin delivery — temp basals, bolus, SMB, and external doses
- Meals: all carb entries
- Glucose: every cached reading, colored by range, with its trend arrow
- Adjustments: overrides and temp targets fetched from Nightscout, with their name, target (temp
  targets only), and active period

**Settings**
- Nightscout URL + access token, with a connection test
- Glucose units: mg/dL or mmol/L
- Time format: 12-hour or 24-hour, applied everywhere a clock time is shown (chart axis, History
  rows, the real-time sync notification)
- Background sync mode: battery-friendly (WorkManager, 15+ minute floor) or real-time (a
  foreground service polling as often as every minute), with a configurable refresh interval and
  an ongoing notification showing the current glucose and last sync time in real-time mode
- Alarms: per-tier thresholds (urgent low / low / high / urgent high) with sound/vibration
  toggles, an optional "require acknowledgement" mode (the alarm notification stays on screen
  until dismissed via its OK action or by tapping it) with an optional repeat-until-acknowledged
  behavior, and tap-to-open-and-refresh on both the alarm and sync notifications
- Permissions and diagnostics sections, including an exportable debug log for troubleshooting
  background sync without a dev machine attached

## Technical overview

- **Kotlin + Jetpack Compose** (Material 3), MVVM, single Gradle module (`app`)
- **Hilt** for dependency injection
- **Room** for local caching (30-day retention) of glucose/treatment/device-status data
- **DataStore Preferences** for settings and small runtime state; **EncryptedSharedPreferences**
  for the Nightscout access token
- **Retrofit + OkHttp + kotlinx.serialization** against the Nightscout v3 API
- **WorkManager** (battery-friendly mode) and a foreground **Service** (real-time mode) for
  background sync, with alarm checks running on each sync cycle
- Gradle 9.7.1 / AGP 9.3.2 / Kotlin 2.3.21 / KSP, minSdk 31, target/compileSdk 37

### Package layout

```
data/nightscout/   domain models + NightscoutRepository (fetch/cache orchestration)
data/remote/       Retrofit API interface + DTOs
data/local/        Room entities/DAOs/Database
data/settings/     UserSettings/AlarmSettings + DataStore-backed repository
data/alarm/        AlarmZone evaluation, AlarmStateStore, AlarmCheckRunner
data/notification/ AlarmNotifier
data/logging/      DiagnosticLogger (the exportable debug log), DiagnosticHttpLogger
sync/              RefreshWorker (WorkManager), RefreshForegroundService, BackgroundSyncScheduler
ui/home/           GlucoseChart, GlucoseBubble, GlucoseHud, IobCalculator, HomeViewModel
ui/history/        History tab (Treatments/Meals/Glucose/Adjustments) + ViewModel
ui/settings/       Settings screen + ViewModel
ui/theme/          Colors ported directly from Trio's own asset catalog
di/                Hilt modules
```

See [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md) for implementation notes, Nightscout/Trio
data-shape quirks verified against Trio's actual source, and current feature status.

## Requirements

- A running Nightscout instance (v3 API) with an access token
- Android 12 (API 31) or newer

## Building

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on a device
or emulator.
