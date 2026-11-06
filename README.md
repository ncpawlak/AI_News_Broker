# AI News Broker

A fluid, readable Android news feed for staying current on **Claude**, **GitHub Copilot**, and new **AI engineering paradigms**. Articles come from Bing News RSS searches built from Google-Alerts-style queries, each with the publisher's summary. Tap a card to open the full article on the publisher's site.

Built with Kotlin Multiplatform + Compose Multiplatform, so the same UI runs on Android and in a web browser (WebAssembly) for quick previews.

## Features

- Category chips (All / Claude / Copilot / Paradigms) with live counts
- Full-width, one-per-row cards with headline and summary, grouped by day (Today, Yesterday, This week, Earlier), with source, relative time, and unread markers
- Articles open in Chrome Custom Tabs (Android) or a new tab (web)
- Editable search queries in Settings (add, edit, toggle, delete, reset)
- Light / Dark / System theme; Material You dynamic color on Android 12+
- Background refresh every 1/3/6/12 hours (WorkManager on Android)

## Preview in a browser

```powershell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

Open <http://localhost:8080>. The dev server proxies `/bing` to `www.bing.com` to get around CORS. That proxy only exists in the dev server, so a static web build cannot fetch news on its own.

## Run on Android

Requires JDK 17+ and the Android SDK (`local.properties` with `sdk.dir=...`).

```powershell
.\gradlew.bat :composeApp:installDebug      # phone connected with USB debugging
.\gradlew.bat :composeApp:assembleDebug     # APK at composeApp/build/outputs/apk/debug/
```

CI also uploads the debug APK as a build artifact.

## Tests

```powershell
.\gradlew.bat :composeApp:testDebugUnitTest
```

## Default searches

Seeded from `composeApp/src/commonMain/kotlin/com/ainewsbroker/data/DefaultQueries.kt`. Queries are written Google-Alerts style (`"Claude" AND ("API" OR ...)`), which Bing News accepts as-is. Results are limited to the past week.

## Project layout

```
composeApp/src/
  commonMain/   shared UI, data layer, RSS parsing
  androidMain/  Activity, Custom Tabs, file storage, WorkManager
  wasmJsMain/   web entry point, localStorage
composeApp/webpack.config.d/proxy.js   dev-server CORS proxy
```

Fonts: Inter (SIL Open Font License, see `THIRD_PARTY_FONT_LICENSE.txt`).
