# World Live Cams (Android TV)

Leanback / Jetpack Compose for TV application for monitoring **128 verified public live web cameras** (`91 HD` + `37 SD`) across **4 countries and 11 cities** — **Serbia** (*Belgrade, Zemun, Novi Sad, Subotica, Niš* — `78`), **Russia** (*Saint Petersburg, Rostov-on-Don, Tomsk* — `23`), **Spain** (*Barcelona, Madrid* — `22`), and **Romania** (*Bucharest* — `5`). Includes **Country Grouping with Vector Country Flags & Contextual City Navigation**, **HD vs. SD Interface Split**, configurable **Camera Auto-Rotation (`Off` / `15s` / `30s` / `60s` / `2m`)**, a **Configurable Info Panel (`Bottom center` default, `Bottom right`, `Bottom left`, `Top left`, `Top right`)** displaying the **Current View Name**, **Dual Timezone Clock (`HH:mm UTC±X · Local HH:mm`)**, **Vector Weather Icons**, **Compact Numeric Air Quality (`AQI`)**, and **Vector Sunrise / Sunset / Golden Hour Icons**, **Zero-Black-Flash Zapping**, **OLED Pixel-Orbit Burn-In Protection**, **Android TV Home Screen Channel Row**, and a native **Android TV System Screensaver (`DreamService`)**.

## Architecture

- **`:domain`** — Pure Kotlin module containing canonical `CatalogManifest` v1/v2/v3/v4 serialization (`kotlinx.serialization`, `CameraCountry`, `CameraCity`, `VideoQuality`, `QualityFilter`, `AutoRotateInterval`, `InfoPanelPosition`, `ZapScope.COUNTRY`), `WmoWeatherMapper` & `GridCachedWeatherRepository`, `EaqiMapper` & `GridCachedAirQualityRepository`, `SolarPhaseCalculator`, `PixelOrbitCalculator`, `HomeChannelSelector`, `CameraClockFormatter`, 3-tier catalog bootstrap coordination, stream failover & backoff policy, `TvKeyRouter`, and `TvStageReducer`.
- **`:app`** — Android TV client (`minSdk = 26`, `targetSdk = 36`, `applicationId = "rs.belgrade.tvcams"`) built with `androidx.tv:tv-material:1.1.0`, `androidx.tvprovider:tvprovider:1.1.0`, Coil 3, DataStore Preferences, Open-Meteo Forecast + Air Quality clients, `SerbiaCamsDreamService` (`DreamService` Compose host), and a zero-black-flash dual-transport `Media3 ExoPlayer 1.11.1` (`TextureView` HLS + double-buffered JPEG snapshot) surface over a dedicated HTTP/1.1 media client.
- **`tools/catalog-probe`** — Python 3.12+ (`uv` / `ruff` / `httpx`) CLI prober that refreshes dynamic live stream tokens, verifies HTTP reachability across all 4 countries, and emits `catalog/catalog.v4.json` and `app/src/main/assets/seed_catalog.v4.json`.

## TV Remote Controls

| Remote Key | Action |
| :--- | :--- |
| `◀` / `▶` (`CH-` / `CH+`) | Switch to previous / next camera in active scope (`All cameras`, `Current country`, `Current city`, or `Favorites`) |
| `OK` (short press) / `INFO` | Toggle Street & City HUD vs. **Pure Camera Mode** (bottom panel keeps current view name + enabled ambient telemetry) |
| `OK` (long press) / `BOOKMARK` | Save or remove current camera from **Favorites** (auto-syncs Android TV Home Screen Channel row) |
| `▼` / `MENU` / `GUIDE` | Open bottom **Quick Strip** (`Quality`, `Rotate`, `Panel position`, `Zap scope`, ambient toggles, Country tabs, contextual City row, and grouped preview cards) |
| `▲` | Cycle full-screen zapping scope (`All cameras` $\rightarrow$ `Current country` $\rightarrow$ `Current city` $\rightarrow$ `Favorites`) |
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
