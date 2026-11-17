# Belgrade Live Cams (Android TV)

Leanback / Jetpack Compose for TV application for monitoring public street, bridge, square, and highway-approach web cameras across Belgrade, Serbia.

## Architecture

- **`:domain`** — Pure Kotlin module containing canonical `CatalogManifest` serialization (`kotlinx.serialization`), 3-tier catalog bootstrap coordination, stream failover & backoff policy, snapshot cache-buster URI construction, and the unidirectional TV D-pad / Pure Camera Mode state reducer (`TvStageReducer`).
- **`:app`** — Android TV client (`minSdk = 26`, `targetSdk = 35`) built with `androidx.tv:tv-material:1.1.0`, `androidx.compose.foundation`, Coil 3, DataStore Preferences, and a dual-transport `Media3 ExoPlayer 1.11.1` HLS + double-buffered JPEG snapshot surface.
- **`tools/catalog-probe`** — Python 3.12+ (`uv` / `ruff` / `httpx[http2]`) CLI prober that verifies HTTP/2 reachability across all 54 Belgrade HLS & snapshot endpoints and generates `catalog/catalog.v1.json` (served via GitHub Pages) and `app/src/main/assets/seed_catalog.v1.json`.

## TV Remote Controls

| Remote Key | Action |
| :--- | :--- |
| `◀` / `▶` (`CH-` / `CH+`) | Switch to previous / next camera in active scope (`All` or `Favorites`) |
| `OK` (short press) / `INFO` | Toggle street & camera HUD vs. **Pure Camera Mode** |
| `OK` (long press) / `BOOKMARK` | Save or remove current camera from **Favorites** |
| `▼` / `MENU` / `GUIDE` | Open bottom **Camera Quick Strip** with category filters & live thumbnails |
| `▲` | Toggle zapping scope between **All cameras** and **Favorites** |
| `BACK` | Dismiss Quick Strip or HUD to **Pure Camera Mode**; press again in Pure Camera Mode to exit |

## Verification & Build Commands

```bash
# 1. Lint & test Python catalog prober
uv run --project tools/catalog-probe ruff check tools/catalog-probe
uv run --project tools/catalog-probe pytest tools/catalog-probe/tests

# 2. Refresh live Belgrade stream health into catalog.v1.json + APK seed asset
uv run --project tools/catalog-probe catalog-probe \
  --output catalog/catalog.v1.json \
  --output app/src/main/assets/seed_catalog.v1.json

# 3. Run pure Kotlin domain & state-machine unit tests (no Android SDK required)
./gradlew :domain:test

# 4. Assemble debug Android TV APK (requires ANDROID_HOME / Android Studio SDK 35)
./gradlew :app:assembleDebug
```
