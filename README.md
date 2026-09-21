# Trio Follower app for Android

Trio Follower is an Android app that reads data from Nightscout and shows it in a familiar layout resembling Trio. It is specifically built to get data uploaded by Trio, and will probably not work well with data uploaded from other OSAID systems.

You can use it in two ways:

- Follow your own Trio data from an Android device.
- Follow a person with diabetes as a caregiver, for example a parent whose child uses Trio.

The app only shows data and sends alerts. It cannot control a pump or change any settings in Trio.

## Screenshots

| Home | Inspect a point in time | Algorithm reasoning |
|------|-------------------------|---------------------|
| <img src="docs/screenshots/Trio%20Follower%201%20-%20Home%20Screen.png" width="230"> | <img src="docs/screenshots/Trio%20Follower%202%20-%20Tap%20to%20view%20data.png" width="230"> | <img src="docs/screenshots/Trio%20Follower%203%20-%20Algorithm%20reasoning.png" width="230"> |

| History | Settings | Widgets |
|---------|----------|---------|
| <img src="docs/screenshots/Trio%20Follower%204%20-%20History%20view.png" width="230"> | <img src="docs/screenshots/Trio%20Follower%205%20-%20Settings.png" width="230"> | <img src="docs/screenshots/Trio%20Follower%206%20-%20Widget.png" width="230"> |

## Requirements

- An Android device with Android 12 or newer. The app is built to support phones, not tablets or foldable phones.
- Someone using Trio on an iPhone, with Trio uploading to Nightscout.
- A Nightscout site that is running and reachable from the internet. You need its URL and an
  access token. Nightscout has to support the v3 API. Nocturne should also work, but limited testing has been done.

## Features

**Home**
- Glucose bubble with the current value, trend arrow, minutes since the reading and the change
  since the last one. Tap it to read the algorithm's reasoning for that reading.
- A chart you can scroll and zoom. It shows glucose, basal, boluses, carb entries, insulin on board,
  carbs on board, overrides and temp targets.
- Press and hold on the chart, then drag, to see the time, glucose, IOB and COB at any point. Dragging
  near either edge scrolls the chart.
- Predictions on the chart, drawn as lines or as a cone. You can also turn them off.
- Small status pills for reservoir, insulin on board, carbs on board, pump and sensor age, and the
  algorithm's eventual glucose.
- A notice on the Home screen when a newer version is available on GitHub, with what changed. You can turn the check off in Settings.
- A statistics bar with today's time in range, or your average glucose.
- Two home screen widgets: one with just the glucose bubble, and one with the bubble and a graph
  of the last 6 hours plus the 2 hour forecast. You choose how transparent each one is when you add it.

**History**
- Treatments, meals, glucose readings and adjustments. Tap a glucose reading to see the algorithm's
  reasoning for it.

**Alarms**
- Urgent low, low, high and urgent high.
- Predicted high, for a slow climb inside your range. Handy when a pump site fails, or when
  someone sleeps in.
- No data, if no new glucose arrives for 20, 40 or 60 minutes.
- Optional acknowledgement, and repeat until acknowledged.

**Updating data**
- Data refreshes when you open the app, and on your chosen interval while the app is open.
- Background sync keeps alarms working when the app is closed.
- Choose battery friendly, or real-time with a notification that shows the current glucose.
- Alarms work better when real-time is selected.

**Settings**
- mg/dL or mmol/L, and 12 or 24 hour time.
- Dynamic or static glucose colors.
- An option to keep the screen on.
- A diagnostic log you can export if something does not work.

## Installation

There are two ways to install the app:

- **Download from GitHub Releases:** get the latest APK from the
  [releases page](https://github.com/t1dude/TrioFollower/releases) and open it on your phone. Android
  asks you to allow installs from your browser or file manager the first time.
- **Build it yourself:** open the project in Android Studio, let Gradle sync, and build an APK using your own signing certificate.

If you switch between a downloaded APK and one you built yourself, uninstall the app first. Android
refuses to update an app that was signed with a different key.

**Updates:** the app checks GitHub about once a day. When a new version is out, a card on the Home
screen shows what's new, with a button that opens the download page. You can turn the check off, or
check by hand, in Settings > Information and Releases.

## Development

TrioFollower is written in Kotlin. The screens use Jetpack Compose with Material 3. It is built with
Gradle and needs Android 12 (API 31) or newer.

Main dependencies:

- Jetpack Compose, Navigation, Lifecycle and Activity for the interface.
- Room and DataStore for the local cache and the settings.
- WorkManager for background sync.
- Hilt for dependency injection.
- Retrofit, OkHttp and kotlinx.serialization for the Nightscout API.
- AndroidX Security for storing the access token.

The app is developed with code assistance from Claude Code, an AI coding assistant. The developer
reviews and tests the changes.

## Disclaimer

TrioFollower is a hobby project made by an enthusiast for personal use. It is open source and comes with
no warranty. The developer is also a contributor to the Trio project, but TrioFollower is not affiliated 
with the Nightscout or Trio projects. Several features and design elements are borrowed from Trio. A big thank you to the entire Trio developer team and community!

Please understand that TrioFollower is:

- used entirely at your own risk
- not CE or FDA approved for therapy
- not a medical device, and not for making medical decisions

Data can be late, wrong or missing, and alarms can fail. Never rely on this app alone. Always check
your pump, your CGM and Trio itself before you dose or act on any reading.

## License

MIT. See [`LICENSE`](LICENSE).
