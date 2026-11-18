# Serbia Live Cams (Android TV)

Leanback / Jetpack Compose for TV application for monitoring **74 verified public live web cameras** across **Belgrade (40), Zemun (6), Novi Sad (8), Subotica (9), and Niš (11)** (`47 HD` + `27 SD`), featuring an **HD vs. SD Interface Split**, independently toggleable **Current Time**, **Current Weather**, **Live Air Quality (`EAQI` / `PM2.5`)**, **Sunrise / Sunset / Golden Hour**, **Zero-Black-Flash Zapping**, **OLED Pixel-Orbit Burn-In Protection**, **Android TV Home Screen Channel Row**, and a native **Android TV System Screensaver (`DreamService`)**.

## Architecture

- **`:domain`** — Pure Kotlin module containing canonical `CatalogManifest` v1/v2/v3 serialization (`kotlinx.serialization`, `SerbiaCity.ZEMUN`, `VideoQuality`, `QualityFilter`), `WmoWeatherMapper` & `GridCachedWeatherRepository`, `EaqiMapper` & `GridCachedAirQualityRepository`, `SolarPhaseCalculator`, `PixelOrbitCalculator`, `HomeChannelSelector`, `CameraClockFormatter`, 3-tier catalog bootstrap coordination, stream failover & backoff policy, `TvKeyRouter`, and `TvStageReducer`.
- **`:app`** — Android TV client (`minSdk = 26`, `targetSdk = 36`) built with `androidx.tv:tv-material:1.1.0`, `androidx.tvprovider:tvprovider:1.1.0`, Coil 3, DataStore Preferences, Open-Meteo Forecast + Air Quality clients, `SerbiaCamsDreamService` (`DreamService` Compose host), and a zero-black-flash dual-transport `Media3 ExoPlayer 1.11.1` (`TextureView` HLS + double-buffered JPEG snapshot) surface.
- **`tools/catalog-probe`** — Python 3.12+ (`uv` / `ruff` / `httpx[http2]`) CLI prober that verifies HTTP reachability across Serbia camera endpoints and emits `catalog/catalog.v3.json` and `app/src/main/assets/seed_catalog.v3.json`.

## TV Remote Controls

| Remote Key | Action |
| :--- | :--- |
| `◀` / `▶` (`CH-` / `CH+`) | Switch to previous / next camera in active scope (`All cameras`, `Current city`, or `Favorites`) |
| `OK` (short press) / `INFO` | Toggle Street & City HUD vs. **Pure Camera Mode** (ambient overlays remain visible if toggled on) |
| `OK` (long press) / `BOOKMARK` | Save or remove current camera from **Favorites** (auto-syncs Android TV Home Screen Channel row) |
| `▼` / `MENU` / `GUIDE` | Open bottom **Quick Strip** (`Clock`, `Weather`, `AQI`, `Sun`, `OLED guard`, `Zap scope`, city tabs, and live preview cards) |
| `▲` | Cycle full-screen zapping scope (`All cameras` $\rightarrow$ `Current city` $\rightarrow$ `Favorites`) |
| `BACK` | Dismiss Quick Strip or HUD to **Pure Camera Mode**; press again in Pure Camera Mode to exit |

## Verification & Build Commands

```bash
# 1. Lint & test Python catalog prober
uv run --project tools/catalog-probe ruff check tools/catalog-probe
uv run --project tools/catalog-probe pytest tools/catalog-probe/tests

# 2. Run pure Kotlin domain, weather, air quality, solar, OLED & state-machine unit tests
./gradlew :domain:test

# 3. Lint & assemble Android TV debug APK
./gradlew :app:lintDebug :app:assembleDebug
```
