# Changelog

All notable user-visible changes to this project are documented in this file.

## [Unreleased]

### Added
- **Expanded 74-Camera Serbia Catalog & First-Class Zemun Support (`catalog.v3.json`):** Promoted **Zemun** (`6` HD cameras covering Nova Galenika / Pupinov most approach, Zmaj / Tošin bunar E-70, Batajnica–Stara Pazova A1, and Dobanovci–Šimanovci / Airport E-70) to a dedicated city filter with local weather/AQI coordinates, and added **18 new verified live cameras** across **Subotica — Gradski trg & Narodno pozorište (`1080p Full HD`)**, **Astronomska stanica Vidojevica (`3K` AllSky)**, **Fruškogorski koridor / Ruma**, **Podunavlje / Smederevo**, **Dunavski koridor / Požarevac**, **Posavina / Šabac**, **Južna Morava / Leskovac**, **Koridor A4 / Dimitrovgrad**, **GP Đala**, and **GP Gradina** (**74 total cameras**: Belgrade `40`, Zemun `6`, Novi Sad `8`, Subotica `9`, Niš `11`).
- **HD vs. Non-HD (`SD`) Interface Split:** Added per-camera `VideoQuality` metadata (`2K QHD`, `1080p HD`, `720p HD`, `SD`), HD-first stream ordering, dedicated **`HD streams` (`2K · 1080p · 720p`)** and **`SD streams` (`576p · Standard`)** section divider cards in the Quick Strip, a persisted **`Quality: All / HD only / SD only`** filter chip governing both browsing and channel zapping, and resolution badges on every camera card and the Street Info HUD.

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
