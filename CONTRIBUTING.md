# Contributing New Cities & Countries to World Live Cams

Adding a new city, a new country, or new public cameras requires **only a single JSON file** — **zero Kotlin or Python code edits are required**, and existing installed Android TV apps automatically discover new countries and cities over-the-air once merged.

## 1. Quick Start (1-File Pull Request)

1. Copy [`catalog/d/_example.json.template`](catalog/d/_example.json.template) to a new `.json` file under `catalog/d/` (files starting with `_` are ignored as templates):
   ```bash
   cp catalog/d/_example.json.template catalog/d/austria-vienna.json
   ```
2. Fill in the city metadata and one or more verified public cameras (validated against [`catalog/schema/contribution.schema.json`](catalog/schema/contribution.schema.json)):
   - `country`: `UPPER_SNAKE_CASE` identifier (e.g. `"AUSTRIA"`, `"CZECHIA"`, `"BRAZIL"`).
   - `country_iso`: 2-letter ISO 3166-1 alpha-2 code (e.g. `"AT"`, `"CZ"`, `"BR"`). Used by the Android TV client to draw either a built-in vector flag or an automatic vector ISO flag badge.
   - `country_name` / `city_name` *(optional)*: Human-readable display overrides when diacritics or custom casing are needed (e.g. `"São Paulo"`, `"Zürich"`). If omitted, `UPPER_SNAKE_CASE` codes are humanized automatically (`"BUENOS_AIRES"` $\rightarrow$ `"Buenos Aires"`).
   - `city`: `UPPER_SNAKE_CASE` identifier (e.g. `"VIENNA"`, `"PRAGUE"`).
   - `timezone`: Valid IANA timezone string (e.g. `"Europe/Vienna"`), used for dual local/camera clocks and sunrise/sunset/golden-hour calculation.
   - `coordinates`: Accurate `lat` / `lon` in decimal degrees, used by Open-Meteo for local weather and air quality (`AQI`).
3. Supported zero-backend `streams[].type` transports:
   - `"HLS"` — Direct unauthenticated `.m3u8` URL (`headers` optional).
   - `"YOUTUBE_LIVE"` — Public 24/7 YouTube live URL (`https://www.youtube.com/watch?v=<11-char-ID>`), resolved to HLS directly on the viewer's TV. Use `https://i.ytimg.com/vi/<ID>/hqdefault.jpg` as `poster_url`.
   - `"RUTUBE_LIVE"` — Public Rutube live stream page (`https://rutube.ru/video/<32-hex-id>/`).
   - `"IPCAMLIVE"` — Public IPCamLive alias URL (`https://ipcamlive.com/<alias>`).
   - `"SNAPSHOT_POLL"` — Direct HTTPS `.jpg` / `.webp` snapshot URL (`interval_ms` and `cache_buster_param`, e.g. `"_"` or `"t"`).

## 2. Verify Locally Before Opening a PR

Run the contributor validation command to verify schema compliance, unique IDs, IANA timezones, coordinate bounds, and live HTTP stream reachability:

```bash
uv run --project tools/catalog-probe catalog-probe --check-contributions
```

## 3. Public Camera & Privacy Policy

- Submit only **publicly accessible** municipal, transit, scenic, port, airport, or landmark feeds.
- For EU/EEA locations, prefer wide-angle skyline, harbor, square, or traffic-corridor panoramas where individuals/plates are not identifiable (aligned with EDPB Guidelines 3/2019).
- Never submit indoor private cameras, residential feeds, or streams requiring credentials.
