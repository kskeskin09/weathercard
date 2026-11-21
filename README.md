# WeatherCard

A clean, offline-friendly weather app for Android, built with Kotlin and Jetpack Compose.
It uses the free [Open-Meteo](https://open-meteo.com/) API, so it needs no account and no API key.

The app is available in **English** and **Turkish** and follows your phone's system language
(on Android 13 and later you can also pick a language just for WeatherCard in the system's app settings).

## Features

- **Big, readable current conditions**: temperature, condition and local time at a glance, on a full-screen sky backdrop that changes with day, night, rain and snow.
- **Hourly curve**: a horizontally scrollable temperature line that starts at the current hour, with an icon, wind speed and time for every hour.
- **7-day forecast** with daily highs, lows and conditions.
- **Details**: wind direction and speed on a compass, sunrise and sunset with the sun's position, humidity, feels-like temperature, UV index, pressure and chance of rain.
- **Several cities**: search for a city or district, save up to 10, and swipe between them. Your current location (approximate, or a one-time precise GPS fix if you allow it) can be the first page.
- **Home-screen widget** that shows the selected place from the saved forecast.
- **Works offline**: forecasts are stored on the device per location and shown even without a connection. A failed refresh never deletes saved data.
- **Light on the free API**: a normal refresh asks only for the next 6 hours; the full 7 days are downloaded once for a new place or when you choose “Update all”. While the app is on screen it refreshes those 6 hours once per hour, and it never runs in the background.
- **Quota page** (three-dot menu) that estimates how much of the free Open-Meteo quota this device has used.
- Original vector icons for all 28 WMO weather codes, with accessible descriptions, and the embedded [Inter](https://rsms.me/inter/) typeface.

Requires Android 8.0 (API 26) or newer.

## Install

1. Open the [latest nightly release](https://github.com/kskeskin09/weathercard/releases/tag/nightly) on your phone.
2. Download **weathercard-nightly.apk** and open it.
3. If Android asks, allow your browser to install apps from unknown sources.

Every nightly is signed with the same key, so a newer nightly installs over the existing app and keeps your data.
Nightly builds are test builds. A stable, versioned release will be published separately.

## Privacy

- No account, analytics or ads.
- Location is used only while the app is open, and only when you ask for it. There is no background tracking.
- The coordinates of the places you view are sent to Open-Meteo to get the forecast, and city searches go to the Open-Meteo geocoding API. The name of your current location comes from Android's own geocoder when available.
- Forecasts, saved cities and the usage counter stay in the app's private storage. Cloud backup is turned off.

## Data source and terms

Weather data by [Open-Meteo](https://open-meteo.com/), licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
WeatherCard turns the raw forecast into its own visual summaries.

The free Open-Meteo API is for **non-commercial use** only. Commercial use (for example with ads or subscriptions) needs a paid Open-Meteo plan. See their [terms](https://open-meteo.com/en/terms), [pricing](https://open-meteo.com/en/pricing) and [API documentation](https://open-meteo.com/en/docs).
The free limits are shared per IP address and are not guaranteed per device, so the quota page is only an estimate.

### How the app uses the quota

1. A place without a saved forecast downloads 7 days of hourly and daily data **once** (about 2.3 quota units).
2. Opening the app again reads the saved forecast from disk. It never starts a full download on its own.
3. Pull to refresh, **Refresh**, and the hourly automatic refresh ask only for the current hour plus the next 5 (about 1.4 units). For example, at 14:35 it fetches 14:00 to 19:00 and replaces only those hours.
4. A new 7-day forecast is downloaded only when you pick **Update all**. When the daily data gets old, the app tells you.
5. City searches use a separate geocoding endpoint. Showing cached data costs nothing.

There is no automatic polling or retry; after an error the app keeps the saved forecast and waits for the next hour.

## Building

All builds, tests, lint and APK signing run on **GitHub Actions**; there is no need to build locally.

- `android.yml` runs on every push and pull request: unit tests, Android lint and a release build. On `main` it also produces a signed candidate APK, but it does not publish anything automatically.
- `ui-smoke.yml` (manual) runs the UI tests on an Android emulator and saves screenshots as artifacts.
- `publish-nightly.yml` (manual) publishes an already tested, signed APK from a successful `main` run to the single `nightly` release without rebuilding it.

```sh
gh workflow run android.yml --ref main --repo kskeskin09/weathercard
gh workflow run ui-smoke.yml --ref main --repo kskeskin09/weathercard
```

The toolchain is pinned to JDK 17, Android SDK 35 and Gradle 8.11.1. You can still open the project in Android Studio to read or edit the code.
The signing key and its password are stored only as GitHub Secrets and are never committed to the repository. Pull requests are built without signing.

Version names look like `0.1.0-nightly.YYYYMMDD.N`; the base version lives in `gradle.properties` and is shown at the bottom of the main screen.

## Project structure

- `core`: platform-independent models, the WMO condition mapping, the 6-hour merge logic, saved cities and the usage tracker, with unit tests.
- `app`: the Open-Meteo client, the on-disk cache, location, the Compose screens, vector icons and the home-screen widget.
- `app/src/main/res/values/` holds the English text (the default) and `values-tr/` the Turkish translation. A new language is a new `values-xx/strings.xml`.

## License

WeatherCard is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) as published by the Free Software Foundation.
If you distribute a modified version, it must also be released under the GPL-3.0 with its source code.

The bundled Inter font is licensed separately under the SIL Open Font License (`app/src/main/assets/fonts/Inter-OFL.txt`).
Weather data comes from Open-Meteo under CC BY 4.0 (see above).
