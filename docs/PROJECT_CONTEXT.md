# TrioNSAndroid — project context

Written 2026-08-28, refreshed 2026-09-18, to let a fresh Claude session (possibly on a different
computer) pick up where this one left off. If you're reading this at the start of a new session:
read this whole file before touching code, then check `git log --oneline -20` for anything more
recent than what's described here. See also [`README.md`](../README.md) for the user-facing
overview (use case, features, tech stack) — this doc is the implementation-detail companion to it.

## What this is

A native Android companion app for [Nightscout](https://nightscout.github.io/), visually modeled
on the [Trio](https://github.com/nightscout/Trio) iOS looping app (Trio's own screenshots/source
were used as the design reference throughout — see "Working style" below). Two tabs: **Home**
(glucose bubble, HUD pills, glucose/basal/IOB chart) and **Settings** (Nightscout URL/token,
units, background sync mode, alarms, permissions, diagnostics).

The user (Magnus) has type 1 diabetes and uses Trio himself; this app is for his own daily use,
tested on a physical Samsung Galaxy Z Fold 8. **He builds and installs the APK himself in Android
Studio — Claude does not build or run the app.** Feedback loop is: Claude edits code and commits,
Magnus rebuilds/installs/tests on-device, reports back with a screenshot and/or a diagnostic log
exported from Settings > Diagnostics (a debug log the app writes to its own files dir, because the
test device isn't connected to a dev machine).

## Tech stack

- Kotlin + Jetpack Compose (Material 3), MVVM, Hilt DI, single Gradle module (`app`)
- Gradle 9.7.1 / AGP 9.3.2 (AGP 9's built-in Kotlin — no `org.jetbrains.kotlin.android` plugin) /
  Kotlin 2.3.21 / KSP
- minSdk 31, compileSdk/targetSdk 37
- Room (local cache, 30-day retention), DataStore Preferences (settings + small runtime state),
  EncryptedSharedPreferences (access token)
- Retrofit + OkHttp + kotlinx.serialization against Nightscout API v3
- WorkManager + a foreground Service for background sync (see Milestone 6 below)

## Package layout

```
data/nightscout/   domain models + NightscoutRepository (fetch/cache orchestration)
data/remote/       Retrofit API interface + DTOs
data/local/        Room entities/DAOs/Database
data/settings/     UserSettings/AlarmSettings + DataStore-backed repository
data/alarm/        AlarmZone evaluation, AlarmStateStore, AlarmCheckRunner, AlarmAcknowledger
data/notification/ AlarmNotifier, AlarmAckReceiver (backs the alarm notification's OK action)
data/logging/      DiagnosticLogger (the exportable debug log), DiagnosticHttpLogger
sync/              RefreshWorker (WorkManager), RefreshForegroundService, BackgroundSyncScheduler
ui/home/           GlucoseChart, GlucoseBubble, GlucoseHud, IobCalculator, HomeViewModel
ui/settings/       Settings screen + ViewModel
ui/theme/          Colors ported from Trio's actual color assets (see below)
di/                Hilt modules
```

## Nightscout/Trio quirks learned the hard way (don't re-derive these — trust them)

Trio's own Swift source was cloned locally during earlier sessions (GitHub tarball) to verify
these against ground truth rather than guessing. If the source isn't present, fetch
`nightscout/Trio` from GitHub again before assuming any of the below has changed.

- **Trio uploads treatments and devicestatus via the legacy v1 API with no `date` field** —
  Nightscout's v3 server assigns `created_at` instead on receipt. Both collections need dual
  `date$gte` / `created_at$gte` queries merged by identifier (`NightscoutDataApi.kt`,
  `NightscoutRepositoryImpl.refresh()`). This bit us twice (treatments, then devicestatus) before
  the pattern was recognized and applied proactively to the later lifecycle-event query too.
- **Bolus eventTypes**: "Bolus"-substring catches most, but Trio specifically uploads SMB doses
  and manual/external doses as exactly `"SMB"` / `"External Insulin"` — neither contains "Bolus".
  See `isBolusEventType()` in `GlucoseChart.kt`.
- **IOB** is read directly from Trio's own `devicestatus.openaps.{suggested,enacted}.IOB` /
  `.iob` — not recomputed locally as the primary source (a local oref0 decay model was built,
  removed in favor of devicestatus, then partially reinstated as a fallback — see next point).
  `openaps.iob` can be a JSON object or array depending on upload path; both are handled.
- **IOB gap-fill**: the chart and the HUD's "current IOB" pill both fall back to a local
  bolus-decay estimate (`IobCalculator.kt`, `computeIobSeries`) when the latest devicestatus point
  is more than `IOB_GAP_THRESHOLD_MILLIS` (20 min) old — rendered dashed/semi-transparent on the
  chart to visually distinguish it from Trio's own reported IOB. This is deliberately only a
  gap-filler, not the primary source.
- **Reservoir**: `devicestatus.pump.reservoir` is *absent* (not present, not zero, not a sentinel
  number) when Omnipod doesn't know the precise level (≥50U) — confirmed against
  `NightscoutManager.swift` (`reservoir: reservoir != 0xDEAD_BEEF ? reservoir : nil`), not just
  the sentinel-comparison code in the local-display `PumpView.swift` (an earlier fix only checked
  the literal sentinel and was wrong — see `Mappers.kt`'s `DeviceStatusDto.toEntity()` for the
  corrected mapping: "pump present, reservoir key absent" → `Double.POSITIVE_INFINITY` → displays
  "50+", same as an explicit sentinel value would).
- **Site Change / Sensor Start** treatments are the only way to derive pump-site and CGM-sensor
  "time remaining" from Nightscout alone (Trio gets real pod expiry from its PumpManager, not
  Nightscout — there's no such field in devicestatus). These are rare (days apart) so they need
  their own long-lookback, small-limit query (`getLatestLifecycleEvent(By CreatedAt)` in
  `NightscoutDataApi.kt`) rather than relying on the regular 24h treatments window. The assumed
  durations (3-day site, 10-day sensor) are hardcoded defaults in `GlucoseHud.kt`, not derived —
  flagged there as a good candidate for a Settings field if they don't fit the user's hardware.
- **Colors**: `TrioInsulin` (#1E96FC — blue, used for basal, bolus, and the HUD's IOB/reservoir
  icons), the bubble's 5-stop ring gradient, `TrioLoopGreen`/`TrioLoopRed`/`TrioWarningOrange`
  (HUD pill thresholds) were all read directly out of Trio's `Assets.xcassets/Colors/*.colorset`
  JSON and `DynamicGlucoseColor.swift`/`PumpView.swift`, not approximated.
- **Bubble arrow rotation**: matches Trio's `CurrentGlucoseView.swift` exactly — the ring and the
  trend triangle rotate together as one rigid unit (not independently), degrees per direction:
  flat=0, up=-90, 45up=-45, 45down=45, down=90.
- **Basal chart** (`BasalSegmentCalculator.kt`/`GlucoseChart.kt`, verified against Trio's
  `BasalChart.swift`): (1) which temp basal rate applies at a given moment is resolved by
  "most-recently-*started* temp basal governs, until its own duration elapses" — **not** "the
  temp basal whose [start,end) window contains this moment," which can pick a stale superseded
  entry or land in a gap between Nightscout's periodic re-announcements of the same active temp,
  producing phantom reversions to the scheduled rate (`computeBasalSegments`'s `overriding`
  logic). (2) The strip's y-axis ceiling (`basalDomainMaxRate`) is the max of recent (24h) temp
  rates and the profile's scheduled rates — always computed independent of whatever range the
  chart is currently zoomed/panned to (matches Trio's `basalDomainMax`), otherwise minor rate
  variations get visually exaggerated whenever the visible window excludes the day's peak rate.
- **SwiftUI `AngularGradient` angle convention** (bit us twice — two wrong rotation values shipped
  before this was pinned down): 0° is 3 o'clock (East), positive angles sweep clockwise, same as
  Compose's `sweepGradient` — **not** 0°=top as first assumed. So Trio's ring gradient
  (`startAngle: 270°, endAngle: -90°`) actually starts at 12 o'clock (top), sweeping
  counterclockwise. `GlucoseBubble.kt`'s ring reproduces this with
  `TrioRingGradient.asReversed()` (flips Compose's inherently-clockwise sweep to counterclockwise)
  rotated `270°` (moves Compose's East-anchored first stop to top) — verified stop-by-stop against
  Trio's actual angles, not just visually eyeballed. If this ever needs re-deriving, work in the
  single "0°=East, positive=clockwise" frame both platforms share — don't re-derive SwiftUI's
  convention from memory.

## Feature status

All six original MVP milestones are implemented:

1. Settings (Nightscout URL/token, refresh interval, alarm toggles, permissions) — done
2. Home tab: glucose bubble + scrollable/zoomable chart — done
3. Basal + bolus overlay on the chart, matching Trio's visual layout exactly — done
4. Insulin/IOB overlay (devicestatus-sourced + local gap-fill fallback) — done
5. Bubble/HUD visual redesign to match Trio (ring gradient, arrow, pill stacks, Material 3 cards) — done
6. Background sync (WorkManager "battery friendly" + foreground-service "real-time" modes) +
   alarm notifications — implemented; real-time mode's reliability issue has a root cause and a
   fix shipped but not yet confirmed on-device, see below

Since, on top of the six milestones:
- The real-time-mode notification shows the current glucose reading (value/unit/trend arrow) and
  last sync time, and tapping it opens the app and triggers an immediate refresh
  (`RefreshForegroundService`, `MainActivity.EXTRA_REFRESH_ON_OPEN`).
- Alarms gained two settings: **require acknowledgement** (the notification becomes ongoing —
  can't be swiped away — until its OK action or a tap dismisses it via `AlarmAcknowledger`) and
  **repeat if not acknowledged** (re-fires every 5 minutes while unacknowledged, only shown/usable
  alongside the first). The repeat cadence is bounded by how often `AlarmCheckRunner` actually
  runs, so it's a true 5 minutes only in real-time mode — in battery-friendly (WorkManager, 15min
  floor) mode it means "the next check after 5 minutes have passed."
- App icon is now Nightscout's own owl logo (adaptive icon, white-on-navy, monochrome layer for
  Android 13+ themed icons) instead of the earlier placeholder droplet.

## Background-sync reliability issue — root cause found, fix shipped, not yet confirmed on-device

Original symptom: **"Real-time" (foreground service) background mode appears to run exactly one
sync cycle and then stop**, even though it's supposed to loop every N minutes indefinitely.

**Root cause, confirmed against a real diagnostic log** (`trio-debug (9).log`, 2026-09-18,
10:30–13:06, real-time mode at 5m): after a brief, benign startup burst (the user trying a few
Settings combinations, each correctly and immediately reconfiguring scheduling — noisy in the log
but not a bug), the service ran **continuously for 2.5 hours with zero restarts** (no further
`Service onCreate`/`onStartCommand` at all) — ruling out "OS killing and restarting the service."
But its `delay(5 minutes)` calls resumed after 13–39 minutes each (only 1 of 7 gaps landed near 5m,
average ~22m), all cycles that *did* run completed successfully with no errors. So the loop itself
was alive the whole time, just severely throttled: an active foreground service does **not**
guarantee the OS lets its coroutine timers fire on schedule — Samsung One UI (and Doze-like power
management generally) deprioritizes the process's CPU/timer scheduling regardless, and the app's
battery setting already being "Unrestricted" (checked earlier) isn't sufficient on its own.

**Fix shipped** (commit `04972d8`): `RefreshForegroundService` now holds a `PARTIAL_WAKE_LOCK` for
as long as its loop is running (acquired on every `onStartCommand` and at the start of every
cycle — idempotent, `setReferenceCounted(false)`; a 45-minute timeout is a leak safety net, not
the scheduling mechanism), released in `onDestroy`. This is the standard fix for exactly this
symptom. **Not yet confirmed working on-device** — pick this up by asking for a fresh diagnostic
log after another ~15-20 minute real-time-mode test if one hasn't been provided since commit
`04972d8`, and check the cycle-to-cycle gaps the same way the original diagnosis did.

Separately, a real but likely-secondary bug was found and fixed along the way (commit `fd3b8ff`):
switching background mode in Settings used to silently and permanently clamp
`refreshIntervalMinutes` down to the new mode's lowest allowed value (e.g. Real-time's 5m → 15m on
a switch to Battery-friendly), never restoring it on switching back since 15m is valid for both
modes. Worth keeping in mind if a future log ever shows a suspiciously long interval that doesn't
match what the user thinks they configured.

## Working style / preferences (read before doing anything non-trivial)

- **Auto mode**: proceed without stopping for confirmation on routine work; only ask (via a real
  question, not rhetorically) when genuinely blocked or when a decision has real tradeoffs the
  user should pick between (e.g. scope questions were asked before building the alarm-notification
  feature, and before choosing how to handle IOB history gaps).
- **Never build/run the app** — the user does this themselves in Android Studio and reports back.
  Don't claim something works without on-device confirmation.
- **Commit after each logical change** (this has been the norm all session, done proactively
  without being asked each time). Author every commit as `t1dude <magnus.reintz@gmail.com>`
  (`git commit --author="t1dude <magnus.reintz@gmail.com>"`), per the user's global CLAUDE.md —
  never add a Claude co-author line. Write commit messages that explain *why*, referencing what
  was verified against Trio's source where relevant.
- **Check `git status` before every commit**, even after an explicit single-file `git add` —
  Android Studio runs concurrently on the same repo and can pre-stage its own changes to `.idea/*`
  files (`deploymentTargetSelector.xml`, `misc.xml`) directly in the index, which a plain
  `git commit` would otherwise sweep in unreviewed (this happened once — caught and fixed with a
  follow-up commit before pushing). Leave `.idea/*` churn alone; it's the user's live local state,
  not something to commit or revert.
- **Verify against Trio's actual source before implementing anything Trio-visual or
  Nightscout-data-shape related** — guessing has repeatedly been wrong and cost cycles (the
  reservoir sentinel bug is a direct example: guessed from the wrong Swift file, wasted a round
  trip). Re-clone `nightscout/Trio` if the earlier `/tmp/trio_src` checkout is gone.
- **When a symptom repeats after a "fix" based on reasoning without seeing the device**, stop
  guessing and either (a) ask for a fresh screenshot/log, or (b) add temporary/permanent
  diagnostic instrumentation rather than attempt a third blind guess. This exact pattern played
  out with the x-axis label clipping bug (turned out to not be a bug at all — content was simply
  below the fold) and is currently playing out with the background-sync issue above.
- Prefers concise, direct answers; dislikes generic hedging when something can be checked instead.
