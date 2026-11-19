# World Live Cams (Android TV)

Leanback / Jetpack Compose for TV application for monitoring **225 verified public live web cameras** (`4K UHD`, `2K QHD`, `1080p HD`, `720p HD`, and `SD`) across **13 countries and 22 cities** — **Serbia** (*Belgrade, Zemun, Novi Sad, Subotica, Niš* — `78`), **Russia** (*Moscow, Saint Petersburg, Rostov-on-Don, Tomsk, Magadan* — `35`), **Spain** (*Barcelona, Madrid* — `29`), **Romania** (*Bucharest* — `8`), **United Kingdom** (*London* — `9`), **France** (*Paris* — `8`), **Netherlands** (*Amsterdam* — `8`), **Germany** (*Berlin* — `8`), **Georgia** (*Tbilisi* — `4`), **Armenia** (*Yerevan* — `3`), **USA** (*New York* — `13`), **South Korea** (*Seoul* — `10`), and **Japan** (*Tokyo* — `12`), with **at least one live motion video stream in every city**.

Features **On-Device Zero-Backend Live Stream Resolution** (`HLS`, `YOUTUBE_LIVE`, `RUTUBE_LIVE`, `IPCAMLIVE`, `SNAPSHOT_POLL`), **`LIVE` vs. `SNAP` Transport Badges & Filtering**, a curated **`Popular` Landmark Tab**, **Low-Spec Android TV Performance Mode** (zero-copy `SurfaceView`, adaptive `720p`/`1080p`/`4K` track capping, `RGB_565` subsampled decode, trimmed `8s–12s` HLS buffer), **Country Grouping with 13 Vector Country Flags**, **HD vs. SD Interface Split**, configurable **Camera Auto-Rotation**, a **Configurable Ambient Info Panel** with **Dual Timezone Clock (`HH:mm UTC±X · Local HH:mm`)**, **Vector Weather / Solar Icons**, and **Numeric `AQI`**, **Zero-Black-Flash Zapping**, **OLED Pixel-Orbit Burn-In Protection**, **16-Camera Android TV Home Screen Channel Row**, and a native **Android TV System Screensaver (`DreamService`)**.

## Architecture

- **`:domain`** — Pure Kotlin module containing canonical `CatalogManifest` v1–v5 serialization (`kotlinx.serialization`, `CameraCountry`, `CameraCity`, `VideoQuality.UHD_4K`, `StreamTransportType`, `TransportFilter`, `PerformanceMode`, `QualityFilter`, `AutoRotateInterval`, `InfoPanelPosition`, `ZapScope`), `StreamUrlResolver` & `StreamPayloadParsers`, `WmoWeatherMapper` & `GridCachedWeatherRepository`, `EaqiMapper` & `GridCachedAirQualityRepository`, `SolarPhaseCalculator`, `PixelOrbitCalculator`, `HomeChannelSelector`, `CameraClockFormatter`, 3-tier catalog bootstrap coordination, stream failover & backoff policy, `TvKeyRouter`, and `TvStageReducer`.
- **`:app`** — Android TV client (`minSdk = 26`, `targetSdk = 36`, `applicationId = "rs.belgrade.tvcams"`) built with `androidx.tv:tv-material:1.1.0`, `androidx.tvprovider:tvprovider:1.1.0`, Coil 3 (`RGB_565` bounded cache), DataStore Preferences, `OkHttpStreamUrlResolver`, Open-Meteo Forecast + Air Quality clients, `SerbiaCamsDreamService` (`DreamService` Compose host), and an adaptive `Media3 ExoPlayer 1.11.1` (`SurfaceView` low-spec / `TextureView` max-quality HLS + subsampled double-buffered JPEG snapshot) surface.
- **`tools/catalog-probe`** — Python 3.12+ (`uv` / `ruff` / `httpx`) CLI prober that refreshes dynamic upstream stream URLs, verifies live reachability across all 5 transport types (`HLS`, `YOUTUBE_LIVE`, `RUTUBE_LIVE`, `IPCAMLIVE`, `SNAPSHOT_POLL`), and emits `catalog/catalog.v5.json` and `app/src/main/assets/seed_catalog.v5.json`.

## TV Remote Controls

| Remote Key | Action |
| :--- | :--- |
| `◀` / `▶` (`CH-` / `CH+`) | Switch to previous / next camera in active scope (`All cameras`, `Current country`, `Current city`, or `Favorites`) |
| `OK` (short press) / `INFO` | Toggle Street & City HUD vs. **Pure Camera Mode** (ambient panel keeps current view name + enabled telemetry) |
| `OK` (long press) / `BOOKMARK` | Save or remove current camera from **Favorites** (auto-syncs Android TV Home Screen Channel row) |
| `▼` / `MENU` / `GUIDE` | Open bottom **Quick Strip** (`Quality`, `Stream: All/Live/Snap`, `Perf: Auto/Low-spec/Max`, `Rotate`, `Panel position`, `Zap scope`, `Popular`, Country flags, contextual Cities, and `LIVE`/`SNAP` preview cards) |
| `▲` | Cycle full-screen zapping scope (`All cameras` $\rightarrow$ `Current country` $\rightarrow$ `Current city` $\rightarrow$ `Favorites`) |
| `BACK` | Dismiss Quick Strip or HUD to **Pure Camera Mode**; press again in Pure Camera Mode to exit |

## Verification & Build Commands

```bash
# 1. Lint & test Python catalog prober
uv run --project tools/catalog-probe ruff check tools/catalog-probe
uv run --project tools/catalog-probe pytest tools/catalog-probe/tests

# 2. Run pure Kotlin domain, stream-parser, weather, air quality, solar, OLED & state-machine unit tests
./gradlew :domain:test

# 3. Lint & assemble Android TV debug APK
./gradlew :app:lintDebug :app:assembleDebug
```

## Contributing a New City or Country

New cities and countries can be contributed via a **single JSON file** under `catalog/d/<country>-<city>.json` with zero Kotlin or Python code changes — see [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`catalog/schema/contribution.schema.json`](catalog/schema/contribution.schema.json).

