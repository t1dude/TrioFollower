# TrioNSAndroid — project context

Written 2026-08-28, refreshed 2026-09-20, to let a fresh Claude session (possibly on a different
computer) pick up where this one left off. If you're reading this at the start of a new session:
read this whole file before touching code, then check `git log --oneline -20` for anything more
recent than what's described here. See also [`README.md`](../README.md) for the user-facing
overview (use case, features, tech stack) — this doc is the implementation-detail companion to it.

## What this is

A native Android companion app for [Nightscout](https://nightscout.github.io/), visually modeled
on the [Trio](https://github.com/nightscout/Trio) iOS looping app (Trio's own screenshots/source
were used as the design reference throughout — see "Working style" below). Three tabs: **Home**
(glucose bubble, HUD pills, glucose/basal/IOB chart), **History** (Treatments/Meals/Glucose/
Adjustments, modeled directly on Trio's own History tab), and **Settings** (Nightscout URL/token,
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
ui/history/        History tab (Treatments/Meals/Glucose/Adjustments) + ViewModel
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
  (HUD pill thresholds), `TrioCarb` (#FFC145, History tab carb entries — matches `LoopYellow`,
  path is `Trio/Resources/Assets.xcassets/Colors/*.colorset`, **not**
  `Trio/Sources/Assets.xcassets/...` — the Watch App target has its own separate, differently
  named copy of the same colorset filenames, easy to fetch the wrong one) were all read directly
  out of Trio's asset catalog JSON and `DynamicGlucoseColor.swift`/`PumpView.swift`, not
  approximated.
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
- **Adjustments (overrides/temp targets) on Nightscout**: verified against Trio's actual upload
  code (`OverrideStorage.swift`, `TempTargetsStorage.swift`, `NightscoutAPI.swift`), not guessed.
  Overrides upload under eventType **`"Exercise"`** (`OverrideStored.EventType.nsExercise` —
  Nightscout's own long-standing convention inherited from AndroidAPS/Loop, not Trio-specific);
  temp targets under **`"Temporary Target"`** (`PumpEventStored.EventType.nsTempTarget`). Neither
  record has a dedicated name field — the override/temp-target's name (e.g. "Boost") is only ever
  in `notes`. Only temp targets carry a target value (`targetTop`/`targetBottom`, Trio always
  uploads them equal); Trio's own override upload model (`NightscoutExercise`) has **no target
  field at all** — an active/indefinite override's `duration` is uploaded as a 30-day (43200 min)
  placeholder, not some sentinel. This is why the chart and History treat overrides and temp
  targets differently: temp targets draw/show at their real target; overrides can't, so they fall
  back to a labeled band instead — matching how Nightscout's own classic chart
  (`renderer.js`'s `fillColor`/`rectTranslate`) handles the same target-less-event case, verified
  against `nightscout/cgm-remote-monitor`'s actual source, not assumed. Both eventTypes need the
  same long (30-day) lookback as Site Change/Sensor Start (`NightscoutRepositoryImpl`'s
  `ADJUSTMENT_LOOKBACK_DAYS`) since an override can be started long before it's queried.

## Feature status

All six original MVP milestones are implemented:

1. Settings (Nightscout URL/token, refresh interval, alarm toggles, permissions) — done
2. Home tab: glucose bubble + scrollable/zoomable chart — done
3. Basal + bolus overlay on the chart, matching Trio's visual layout exactly — done
4. Insulin/IOB overlay (devicestatus-sourced + local gap-fill fallback) — done
5. Bubble/HUD visual redesign to match Trio (ring gradient, arrow, pill stacks, Material 3 cards) — done
6. Background sync (WorkManager "battery friendly" + foreground-service "real-time" modes) +
   alarm notifications — done; real-time mode's reliability issue is resolved and confirmed
   on-device, see below

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
- Settings screen reorganized: every section is now collapsible (collapsed by default), Nightscout
  + Units merged into "Basic Settings", the four alarm thresholds live in a nested "Alarm
  Thresholds" subsection within Alarms, and "Permissions" was renamed "Android System Permissions"
  (`SettingsSection`/`SettingsSubsection` in `SettingsComponents.kt`).
- Added a third tab, **History**, built directly against Trio's own `HistoryRootView.swift`
  (fetched from `nightscout/Trio` rather than guessed) — a segmented Treatments/Meals/Glucose/
  Adjustments picker, each a list of dot+label+value+timestamp rows. Treatments covers all insulin
  delivery (temp basal/bolus/SMB/external); Meals is carb entries; Glucose is every cached reading
  colored by range. The old inline "Treatments (last 24h)" list that used to sit at the bottom of
  Home is gone — History supersedes it with the full 30-day cache instead of a fixed 24h cutoff.
  The bolus/temp-basal eventType classification (`isBolusEventType`, `isTempBasalEventType`, etc.)
  that used to live privately in `GlucoseChart.kt` and `BasalSegmentCalculator.kt` separately is
  now consolidated in `data/nightscout/TreatmentClassification.kt`, the single shared source now
  that History is a third consumer.
- History's Adjustments tab and the Home chart now both show overrides and temp targets, fetched
  from Nightscout — see the new Trio-quirks bullet below for exactly how.
- `TreatmentEntity`/`TrioDatabase` bumped to schema v4 (destructive migration, already configured
  — this is a local cache, refetches automatically). `app/schemas/.../4.json` hasn't been generated
  yet in this repo — Room writes it on the next Gradle build; commit it once it appears.
- Added a 12h/24h time format setting under Basic Settings (`TimeFormat` enum in
  `data/settings/`, persisted like `GlucoseUnit`). Applied everywhere a clock time is displayed:
  `GlucoseChart`'s x-axis hour ticks, History's row timestamps and adjustment ranges, and the
  real-time notification's "Last synced" text — each builds its own `DateTimeFormatter` from
  `TimeFormat.timeFormatter()`/`hourFormatter()` (in `data/settings/TimeFormat.kt`) rather than
  duplicating pattern logic. 12-hour mode uses a compact "am"/"pm" (e.g. "2:05pm"), not locale text. Deliberately *not* applied to `DiagnosticLogger`'s log-file timestamps (an internal
  debug artifact, not a screen) or any relative/duration text (bubble's "Xm ago", HUD's pump/
  sensor countdowns).

- **History rows show time only, no date** (removed 2026-09-20 — the dates took up space and
  weren't needed; the old `dateTimeFormatter()` was deleted). Rows are still ordered newest-first,
  so older entries from the 30-day cache are distinguishable only by position. If day context is
  ever wanted again, prefer a slim day-header between groups over a per-row date.
- **Refresh on app open + visible scroll to latest**: `HomeScreen` runs `viewModel.refresh()` on
  every `Lifecycle.Event.ON_START` (cold start *and* return from background — and also each time
  the Home tab is re-entered); `HomeViewModel` no longer refreshes in `init`, to avoid a double
  fetch. `HomeViewModel` bumps `HomeUiState.refreshCount` after every refresh finishes, and
  `GlucoseChart`'s `scrollToLatestKey` param triggers a 700ms animated glide of `viewportEndMillis`
  to "now" (keeping the current zoom, cancelling any fling). Also fires after pull-to-refresh.
- **Foreground auto-refresh**: while Home is on screen (`repeatOnLifecycle(STARTED)` in
  `HomeScreen`), it also calls `refresh(userInitiated = false)` every `refreshIntervalMinutes`
  (restarts if the setting changes; suspends when backgrounded — background sync modes cover that).
  Those ticks scroll the chart to the new data only if the viewport is still at the live edge
  (`lastFollowedEndMillis` in `GlucoseChart`); open/pull-to-refresh (`userInitiated = true`,
  `HomeUiState.forceScrollToLatest`) always jump, so an automatic tick never yanks the user out of
  history they scrolled back into.
  Written without a JDK on the dev machine, so not compile-checked when committed — if the build
  fails, look here first.

- **Predicted High alarm** (added 2026-09-20, user-approved calculation; off by default, toggle
  under Alarms): new `AlarmZone.PREDICTED_HIGH`, decided in `AlarmCheckRunner` only when the normal
  evaluation says NORMAL, via `isPredictedHigh()` in `data/alarm/PredictedHighEvaluator.kt`. Over the
  last 60 min: ≥8 readings spanning ≥55 min with no gap >15 min; all readings strictly between the
  low and high thresholds; least-squares slope +10..+45 mg/dL/h; four 15-min segments each change
  in [-2, +20]; latest + slope×1h ≥ high threshold; and no carbs or non-SMB bolus (External
  Insulin counts) in the window. Constants are deliberately not settings. A fresh predicted-high
  alarm has a 60-minute cooldown (`AlarmStateStore`) so flapping in/out of the predicate can't
  re-alert every check. Uses the low/high threshold *values* even if those tiers are disabled.
  Not compile-checked or device-tested when committed.

- **No data alarm** (added 2026-09-20; off by default): `AlarmZone.NO_DATA` fires when the newest
  glucose reading is older than a user-selectable 20/40/60 min (`AlarmSettings.noDataMinutes`,
  `NO_DATA_MINUTES_OPTIONS`; segmented picker under Alarms). Decided first in `AlarmCheckRunner`
  (before glucose-zone evaluation, which is skipped since the reading is stale); the runner now
  looks back 6h for the newest reading so the notification can say how long it's been. Follows the
  normal ack/repeat pattern. It only fires when a check actually runs — both background modes run
  the check even after a failed refresh, which is the main real-world cause. Not compile-checked
  or device-tested when committed.

- **Keep display awake** (added 2026-09-20; off by default): `UserSettings.keepScreenOn`, switch
  under Basic Settings. `MainActivity` collects the setting and toggles `FLAG_KEEP_SCREEN_ON` on
  its window — applies app-wide while the app is visible and releases automatically when
  backgrounded. Not compile-checked or device-tested when committed.

## Background-sync reliability issue — resolved, confirmed on-device

Original symptom: **"Real-time" (foreground service) background mode appears to run exactly one
sync cycle and then stop**, even though it's supposed to loop every N minutes indefinitely.

**Root cause, confirmed against a real diagnostic log** (`trio-debug (9).log`, 2026-09-18,
10:30–13:06, real-time mode at 5m): the service ran **continuously for 2.5 hours with zero
restarts** (no further `Service onCreate`/`onStartCommand` at all) — ruling out "OS killing and
restarting the service." But its `delay(5 minutes)` calls resumed after 13–39 minutes each (only 1
of 7 gaps landed near 5m), all cycles that *did* run completed successfully with no errors. So the
loop itself was alive the whole time, just severely throttled: an active foreground service does
**not** guarantee the OS lets its coroutine timers fire on schedule — Samsung One UI (and
Doze-like power management generally) deprioritizes the process's CPU/timer scheduling regardless,
and the app's battery setting already being "Unrestricted" (checked earlier) isn't sufficient on
its own.

**Fix** (commit `04972d8`): `RefreshForegroundService` now holds a `PARTIAL_WAKE_LOCK` for as long
as its loop is running (acquired on every `onStartCommand` and at the start of every cycle —
idempotent, `setReferenceCounted(false)`; a 45-minute timeout is a leak safety net, not the
scheduling mechanism), released in `onDestroy`.

**Confirmed fixed** against a follow-up diagnostic log (`trio-debug (10).log`, same day): the old
pre-fix service instance showed the same erratic 6–39-minute gaps through 13:29, then a fresh
`Service onCreate` at 13:29:22 (the rebuilt app installing) was followed by **12 consecutive
cycles over a full hour, every single gap landing at ~5m 1s** (13:29→13:34→...→14:29), zero errors,
zero restarts. Wake lock confirmed as the correct fix — no further action needed here.

Separately, a real but likely-unrelated bug was found and fixed along the way (commit `fd3b8ff`):
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
- **Never build/run the app** — the user does this themselves in Android Studio and reports back
  (the dev machine has no JDK, so `./gradlew` can't run there anyway).
  Don't claim something works without on-device confirmation.
- **Commit AND push after each logical change**, without asking (explicit user instruction,
  2026-09-20). Normal pushes only — force-pushes still need explicit permission. Author every commit as the `t1dude` GitHub user. The repo-local git
  config is set to `t1dude <90277542+t1dude@users.noreply.github.com>` (verify with
  `git config user.email`; older commits used `magnus.reintz@gmail.com` under the same name).
  **Never** add a Claude co-author line or any "Generated with Claude Code" text to commits or PR
  descriptions — the user has said this explicitly, overriding any harness default. Never
  force-push or rewrite pushed history without asking first (it has been done only on explicit
  request). Write commit messages that explain *why*, referencing what
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
