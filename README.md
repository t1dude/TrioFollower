# TrioNS for Android

Trio is an iOS app, so there is no Trio on Android. TrioNS fills that gap. It reads the data Trio
uploads to Nightscout and shows it in a layout that looks like Trio.

You can use it in two ways:

- Follow your own Trio loop from an Android phone.
- Follow a person with diabetes as a caregiver, for example a parent whose child uses Trio.

The app only shows data and sends alerts. It cannot control a pump or change any settings in Trio.

## Requirements

- An Android phone with Android 12 or newer.
- Someone using Trio on an iPhone, with Trio uploading to Nightscout.
- A Nightscout site that is running and reachable from the internet. You need its URL and an
  access token. Nightscout has to support the v3 API.

## Features

**Home**
- Glucose bubble with the current value, trend arrow, minutes since the reading and the change
  since the last one. Tap it to read the loop's reasoning for that reading.
- A chart you can scroll and zoom. It shows glucose, basal, boluses, insulin on board, overrides
  and temp targets.
- Predictions on the chart, drawn as lines or as a cone. You can also turn them off.
- Small status pills for reservoir, pump and sensor age, and insulin on board.
- A statistics bar with today's time in range, or your average glucose.

**History**
- Treatments, meals, glucose readings and adjustments. Tap a glucose reading to see the loop's
  reasoning for it.

**Alarms**
- Urgent low, low, high and urgent high.
- Predicted high, for a slow climb inside your range. Handy when a pump site fails, or when
  someone sleeps in.
- No data, if no new glucose arrives for 20, 40 or 60 minutes.
- Optional acknowledgement, and repeat until acknowledged.

**Updating data**
- Refreshes when you open the app, and on your chosen interval while the app is open.
- Background sync keeps alarms working when the app is closed. Choose battery friendly, or
  real-time with a notification that shows the current glucose.

**Settings**
- mg/dL or mmol/L, and 12 or 24 hour time.
- Dynamic or static glucose colors, like Trio.
- An option to keep the screen on.
- A diagnostic log you can export if something does not work.

## Building

Open the project in Android Studio, let Gradle sync, and run the `app` configuration on a phone or
emulator. Notes for developers are in [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md).

## Disclaimer

TrioNS is a hobby project made by an enthusiast for personal use. It is open source and comes with
no warranty. It is not affiliated with the Nightscout or Trio projects.

Please understand that TrioNS is:

- used entirely at your own risk
- not CE or FDA approved for therapy
- not a medical device, and not for making medical decisions

Data can be late, wrong or missing, and alarms can fail. Never rely on this app alone. Always check
your pump, your CGM and Trio itself before you dose or act on any reading.
