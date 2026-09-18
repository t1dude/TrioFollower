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
   alarm notifications — implemented, **but see Known Issue below, not yet confirmed working**

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

## Known issue in progress (as of commit `2b2bdc7`)

**"Real-time" (foreground service) background mode appears to run exactly one sync cycle and
then stop**, even though it's supposed to loop every N minutes indefinitely. Reproduced twice:
once over ~90 minutes, once over ~7 minutes past the expected next cycle — in both cases the
ongoing notification's "last synced" time froze after one update rather than advancing.

Ruled out so far:
- Not the foreground service dying outright — the *service* (and its notification) persisted;
  it's the internal refresh loop that stopped advancing.
- Confirmed the device (Samsung Galaxy Z Fold 8 / One UI) had the app's battery setting already
  set to "Unrestricted" before the second reproduction — problem persisted anyway, so this is
  either not a (pure) Samsung battery-management issue, or that setting alone isn't sufficient.
- The `distinctUntilChangedBy { backgroundMode to refreshIntervalMinutes }` guard in
  `TrioNSApplication.onCreate()` was checked and should correctly prevent `AlarmStateStore`
  writes (which share the same DataStore file as `SettingsRepositoryImpl`) from spuriously
  re-triggering `BackgroundSyncScheduler.apply()` — reasoned through, not yet proven with a log.

Not yet ruled out / next steps: commit `2b2bdc7` added logging specifically to distinguish two
remaining hypotheses without more guessing:
- **OS killing and restarting the service** (each restart doing exactly one cycle before being
  killed again) — would show a fresh `"Service onCreate"` and/or `"onStartCommand ... intent=null
  (likely a START_STICKY restart)"` recurring roughly every cycle interval.
- **The coroutine loop itself hanging** (most likely `delay()` never resuming, or the scope
  getting cancelled some other way) — would show only one `onCreate`/`onStartCommand`, with
  `"Cycle N starting at HH:mm"` lines simply stopping after cycle 1 and no `"Loop exited"` line
  either.

**New lead worth checking against the next log, found since** (commit `fd3b8ff`): a real, separate
bug was found where switching background mode in Settings silently and permanently clamped
`refreshIntervalMinutes` down to whatever the newly-selected mode's lowest allowed value was (e.g.
Real-time's 5m → 15m on a switch to Battery-friendly), and switching back never restored it, since
15m is valid for both modes. Fixed by no longer persisting that clamp
(`SettingsViewModel.onBackgroundModeChange`). It's plausible earlier reproductions of the "loop
stops after one cycle" symptom were partly or wholly this bug instead — e.g. an earlier session's
own testing/mode-switching silently changed the interval to something much longer than expected,
making the loop look "stopped" when it just hadn't reached its (longer than assumed) next cycle
yet. Worth explicitly ruling in/out on the next fresh log rather than assumed fixed — the ~90
minute reproduction in particular doesn't fully fit (even a 15-minute cadence should have advanced
several times in that window), so this likely doesn't explain everything on its own.

**Waiting on**: a fresh diagnostic log from the user after another ~15-20 minute test with
Real-time mode active, to read off which of the two patterns above actually shows up. Pick this up
by asking for that log if it hasn't been provided yet, or reading it if it has.

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
