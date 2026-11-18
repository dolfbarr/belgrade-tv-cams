# Changelog

All notable user-visible changes to this project are documented in this file.

## [Unreleased]

### Added
- **World Live Cams Expansion (`catalog.v4.json`, 128 Cameras Across 4 Countries & 11 Cities):** Added **50 verified international live cameras** (`40 HD` + `10 SD`) across **Saint Petersburg, Russia** (`12 HD` — Palace Bridge & Neva River, Admiralty Embankment & Peter and Paul Fortress, Church of the Savior on Spilled Blood, Nevsky Prospekt — Gostiny Dvor / Anichkov Palace / Main Avenue, Griboyedov Canal, Cruiser Aurora, Tchaikovskogo Rooftops, Five Corners, Sevkabel Port, State Hermitage Courtyard; `Europe/Moscow`), **Rostov-on-Don, Russia** (`1 HD` — Leninsky District Central Station & Siversa Flyover panoramic HLS; `Europe/Moscow`), **Tomsk, Russia** (`9 HD` + `1 SD` — Yuzhnaya Square `1080p` live HLS, Novosobornaya Square, Tom River Embankment, Prospekt Lenina, Krasnoarmeyskaya, Transportnaya Square, TSU Campus, Vershinina, Plekhanova, Tomsky Lane; `Asia/Tomsk`), **Barcelona, Spain** (`1 HD` + `9 SD` — 3Cat Catalonia `1080p` live HLS panorama, Port Olímpic & W Hotel, La Rambla, Avinguda Diagonal — Francesc Macià, Gran Via — Glòries, Carrer de la Marina, Ronda General Mitre, Plaça Cerdà, Ronda Litoral B-10, Ronda de Dalt B-20; `Europe/Madrid`), **Madrid, Spain** (`12 HD` — Ayuntamiento de Madrid `720p` municipal feeds at Plaza de Cibeles, Plaza del Callao, Gran Vía / Metrópolis, Plaza de España, Puerta de Alcalá, Fuente de Neptuno, Paseo del Prado, Plaza de Colón, Santiago Bernabéu, Torres KIO, Atocha, Calle de Bailén / Almudena; `Europe/Madrid`), and **Bucharest, Romania** (`5 HD` — Piața Romană, Bulevardul I. C. Brătianu / Bărăția Tower, Piața Unirii Fountains, Militari West Skyline, Clinceni Airfield; `Europe/Bucharest`), alongside **Serbia** (`78` cameras across Belgrade, Zemun, Novi Sad, Subotica, and Niš).
- **Country Grouping (`Serbia`, `Russia`, `Spain`, `Romania`), Country Flags & Country Zapping Scope:** Added vector `CountryFlagIcon` badges (`Serbia`, `Russia`, `Spain`, `Romania`), country filter tabs and contextual per-country city rows in `CameraQuickStrip`, country-branded `HD streams` / `SD streams` section divider cards in the horizontal camera carousel, country badges in `StreetInfoHud` and preview cards, and a `ZapScope.COUNTRY` (`Current country`) zapping mode (`All cameras` $\rightarrow$ `Current country` $\rightarrow$ `Current city` $\rightarrow$ `Favorites`).
- **Current View Name, Dual Timezone Clock & Vector Telemetry Icons in Persistent Ambient Panel:** `AmbientClockWeatherBar` now displays the active camera view name with country flag and `City, Country` tag on line 1, paired on line 2 with **Dual Clock (`HH:mm UTC±X · Local HH:mm`)**, **Vector Weather Condition Icons (`WeatherConditionIcon`)** replacing condition text, **Compact Numeric Air Quality (`AQI <number>`)** with semantic status dot, and **Vector Sunrise / Sunset / Golden Hour Icons (`SolarPhaseIcon`)**.
- **Configurable Info Panel Position (Default: `Bottom center`):** Added a persisted **`Panel: Bottom center / Bottom right / Bottom left / Top left / Top right`** selector in Quick Strip Row 1 (`InfoPanelPosition.BOTTOM_CENTER` by default), with automatic `TopStatusLayer` collision avoidance when `Top right` is selected.

### Changed
- **App Rename & Branded Multi-Density TV Banners (`World live cams`):** Renamed launcher label, system screensaver (`DreamService`), and Android TV Home Screen preview channel titles (`World live cams — Favorites` / `World live cams — Featured`) to **`World live cams`** (retaining `applicationId = "rs.belgrade.tvcams"` for seamless upgrades) and updated multi-density Leanback banners (`xxhdpi`, `xhdpi`, `hdpi`, `mdpi`).
- **HTTP/1.1 Media Transport Hardening:** Configured a dedicated `Protocol.HTTP_1_1` `OkHttpClient` for `Media3 ExoPlayer` HLS and `Coil` snapshot/poster fetching to prevent RFC 7540 HTTP/2 stream errors on upstream broadcasters emitting legacy `Keep-Alive` headers.

## [0.4.0] - 2026-11-18

### Added
- **Expanded 78-Camera Serbia Catalog, Zemun & Landmark Views (`catalog.v3.json`):** Promoted **Zemun** (`8` HD cameras covering Nova Galenika / Pupinov most approach, Zmaj / Tošin bunar E-70, Batajnica–Stara Pazova A1, Dobanovci–Šimanovci / Airport E-70, and Šid A3/E-70) to a dedicated city filter with local weather/AQI coordinates, highlighted **TC Ušće**, **TC Galerija Belgrade**, **Beograd na vodi**, **Ada Mall**, and **TC Delta City** across verified Belgrade camera frames, and added **22 new verified live cameras** including **Subotica — Gradski trg & Narodno pozorište (`1080p Full HD`)**, **Astronomska stanica Vidojevica (`3K` AllSky)**, **Fruškogorski koridor / Ruma**, **Podunavlje / Smederevo**, **Dunavski koridor / Požarevac**, **Posavina / Šabac**, **Južna Morava / Leskovac**, **Koridor A1 Jug / Preševo**, **Koridor A4 / Dimitrovgrad**, **GP Đala**, and **GP Gradina** (**78 total cameras**: Belgrade `40`, Zemun `8`, Novi Sad `8`, Subotica `9`, Niš `13`).
- **HD vs. Non-HD (`SD`) Interface Split:** Added per-camera `VideoQuality` metadata (`2K QHD`, `1080p HD`, `720p HD`, `SD`), HD-first stream ordering, dedicated **`HD streams` (`2K · 1080p · 720p`)** and **`SD streams` (`576p · Standard`)** section divider cards in the Quick Strip, a persisted **`Quality: All / HD only / SD only`** filter chip governing both browsing and channel zapping, and resolution badges on every camera card and the Street Info HUD.
- **Configurable Camera Auto-Rotation (App & Screensaver):** Added a persisted **`Rotate: Off / 15s / 30s / 60s / 2m`** control in Quick Strip Row 1 for hands-free camera tours inside the main app, plus automatic `30s` default rotation in `SerbiaCamsDreamService` screensaver mode with single-camera scope fallback so tours never stall.

### Changed
- **Bottom-Right Info Corner Layout:** Relocated both the ambient status bar (`Clock` / `Weather` / `AQI` / `Sunrise-Sunset-Golden Hour`) and the `StreetInfoHud` camera card into a unified bottom-right safe-area stack (`Alignment.BottomEnd`), keeping top-left timestamps and the main camera viewport unobstructed.

## [0.3.0] - 2026-11-18

### Added
- **Android TV System Screensaver (`DreamService`):** Registerable under `Settings -> Device Preferences -> Screen saver` (`Serbia live cams`), launching directly into Pure Camera Mode with active ambient overlays and automatic `45-second` camera patrol across the selected scope.
- **Live Air Quality (`EAQI` / `PM2.5` / `PM10`) Overlay:** Toggleable `[AQI: On/Off]` chip powered by the Open-Meteo Air Quality API (`0.05°` grid `30-min` cache) with semantic European Air Quality Index status indicator.
- **Sunrise, Sunset & Golden Hour Overlay:** Toggleable `[Sun: On/Off]` chip computing Dawn, Morning Golden Hour, Daytime Sunset countdown, Evening Golden Hour, Dusk, and Next-Day Sunrise per camera timezone.
- **Zero-Black-Flash Channel Zapping:** Persistent street poster underlay inside `DualTransportCameraSurface` held visible until `ExoPlayer` outputs its first decoded video frame (`onRenderedFirstFrame`), eliminating black flashes when switching cameras.
- **OLED / Panel Burn-In Protection:** Toggleable `[OLED guard: On/Off]` deterministic 16-step diamond pixel orbit (`±8.dp` x `±5.dp` every `90s`) paired with `1.025x` video surface overscan pan to shift both app pills and upstream burned-in camera watermarks across physical pixels.
- **Android TV Home Screen Channel Row:** Publishes saved Favorites (or a 12-camera multi-city Featured lineup across Belgrade, Novi Sad, Subotica, and Niš when Favorites is empty) directly onto the Android TV Home Launcher via `androidx.tvprovider`, with instant `serbiatvcams://camera/{id}` deep-linking.
- **Branded `320x180dp` Leanback TV Banner & Adaptive Launcher Icon:** Multi-density `SERBIA LIVE CAMS` Leanback home banner (`xhdpi` / `xxhdpi` / `mdpi`) and cable-stayed bridge adaptive icon.

## [0.2.0] - 2026-11-18

### Added
- Toggleable **Current Time** (`Europe/Belgrade` `HH:mm` tabular `tnum`) and **Current Weather** (Open-Meteo `0.05°` grid `15-min` cache) ambient corner pill.
- Expanded verified Serbia camera catalog (`catalog.v2.json`, **56 live cameras**) across **Belgrade (40)**, **Novi Sad (6)**, **Subotica (6)**, and **Niš (4)**, plus Quick Strip city tabs and deterministic debug APK signing.

### Changed
- Renamed Android TV launcher label from `Belgrade live cams` to **`Serbia live cams`**.

## [0.1.0] - 2026-11-17

### Added
- Initial Android TV live camera viewer covering 27 public traffic, bridge, square, boulevard, and highway-approach feeds across Belgrade, Serbia.
