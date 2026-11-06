# AI News Broker

A fluid, readable Android news feed for the newest things in AI-assisted development: **Claude** and **GitHub Copilot** updates, better **workflows** (agents, parallel/fleet usage, context engineering), and **new tools** as they launch. Every card shows a summary; tap it to open the full article.

Built with Kotlin Multiplatform + Compose Multiplatform, so the same UI runs on Android and in a web browser (WebAssembly) for quick previews.

## Features

- Category chips (All / Claude / Copilot / Workflows / New tools) with live counts
- Full-width, one-per-row cards with headline and summary, grouped by day (Today, Yesterday, This week, Earlier), with source, relative time, and unread markers
- Articles open in Chrome Custom Tabs (Android) or a new tab (web)
- Editable search queries in Settings (add, edit, toggle, delete, reset)
- Light / Dark / System theme; Material You dynamic color on Android 12+
- Background refresh every 1/3/6/12 hours (WorkManager on Android)

## Preview in a browser

```powershell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

Open <http://localhost:8080>. The dev server forwards `/proxy/<host>/<path>` to `https://<host>/<path>` to get around CORS (local and private hosts are blocked). That proxy only exists in the dev server, so a static web build cannot fetch news on its own.

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

## Sources

Defaults live in `composeApp/src/commonMain/kotlin/com/ainewsbroker/data/DefaultQueries.kt`. Each search uses one source:

- **Feed**: official RSS/Atom, e.g. the GitHub Copilot changelog and Claude Code releases
- **Bing News**: short phrase searches over the past week (short phrases stay more on-topic than long AND/OR chains)
- **Hacker News** / **Show HN**: Algolia title search over the past week with a minimum-points filter; ` OR ` splits into one request per term

All searches can be edited in Settings. When the defaults change, bump `DefaultQueries.VERSION`; existing installs then get the new defaults, keep their custom searches, and refresh on next launch.

## Project layout

```
composeApp/src/
  commonMain/   shared UI, data layer, RSS/Atom and Hacker News parsing
  androidMain/  Activity, Custom Tabs, file storage, WorkManager
  wasmJsMain/   web entry point, localStorage
composeApp/webpack.config.d/proxy.js   dev-server CORS proxy
```

Fonts: Inter (SIL Open Font License, see `THIRD_PARTY_FONT_LICENSE.txt`).
