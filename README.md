# Serbia Live Cams (Android TV)

Leanback / Jetpack Compose for TV application for monitoring **56 verified public live web cameras** across **Belgrade (40), Novi Sad (6), Subotica (6), and Niš (4)**, featuring independently toggleable **Current Time** and **Current Weather** ambient overlays.

## Architecture

- **`:domain`** — Pure Kotlin module containing canonical `CatalogManifest` v1/v2 serialization (`kotlinx.serialization`), `WmoWeatherMapper` & `GridCachedWeatherRepository` (`0.05°` grid cell `15-min` TTL cache), `CameraClockFormatter`, 3-tier catalog bootstrap coordination, stream failover & backoff policy, `TvKeyRouter`, and the unidirectional TV D-pad state reducer (`TvStageReducer`).
- **`:app`** — Android TV client (`minSdk = 26`, `targetSdk = 36`) built with `androidx.tv:tv-material:1.1.0`, `androidx.compose.foundation`, Coil 3, DataStore Preferences, Open-Meteo HTTP weather client, and a dual-transport `Media3 ExoPlayer 1.11.1` (`TextureView` HLS + double-buffered JPEG snapshot) surface.
- **`tools/catalog-probe`** — Python 3.12+ (`uv` / `ruff` / `httpx[http2]`) CLI prober that verifies HTTP reachability (`HEAD` with streaming `GET` fallback) across Serbia camera endpoints and emits `catalog/catalog.v2.json` (served via GitHub Pages alongside immutable `catalog.v1.json`) and `app/src/main/assets/seed_catalog.v2.json`.

## TV Remote Controls

| Remote Key | Action |
| :--- | :--- |
| `◀` / `▶` (`CH-` / `CH+`) | Switch to previous / next camera in active scope (`All cameras`, `Current city`, or `Favorites`) |
| `OK` (short press) / `INFO` | Toggle Street & City HUD vs. **Pure Camera Mode** (ambient Clock/Weather remain visible if toggled on) |
| `OK` (long press) / `BOOKMARK` | Save or remove current camera from **Favorites** |
| `▼` / `MENU` / `GUIDE` | Open bottom **Quick Strip** (`Clock: On/Off`, `Weather: On/Off`, `Zap scope`, `Belgrade` / `Novi Sad` / `Subotica` / `Niš` tabs, and live preview cards) |
| `▲` | Cycle full-screen zapping scope (`All cameras` $\rightarrow$ `Current city` $\rightarrow$ `Favorites`) |
| `BACK` | Dismiss Quick Strip or HUD to **Pure Camera Mode**; press again in Pure Camera Mode to exit |

## Verification & Build Commands

```bash
# 1. Lint & test Python catalog prober
uv run --project tools/catalog-probe ruff check tools/catalog-probe
uv run --project tools/catalog-probe pytest tools/catalog-probe/tests

# 2. Refresh live Serbia stream health into catalog.v2.json + APK seed asset
uv run --project tools/catalog-probe catalog-probe \
  --output catalog/catalog.v2.json \
  --output app/src/main/assets/seed_catalog.v2.json

# 3. Run pure Kotlin domain, weather, clock & state-machine unit tests
./gradlew :domain:test

# 4. Lint & assemble Android TV debug APK
./gradlew :app:lintDebug :app:assembleDebug
```
