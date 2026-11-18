# Changelog

All notable user-visible changes to this project are documented in this file.

## [Unreleased]

### Added
- Toggleable **Current Time** (`Europe/Belgrade` `HH:mm` with tabular numerals `tnum`) and **Current Weather** ([Open-Meteo](https://open-meteo.com/en/docs) temperature, condition, and wind speed with `15-minute` `0.05°` grid caching) ambient corner pill, persisted in DataStore and visible across both HUD and Pure Camera modes when enabled.
- Expanded verified Serbia camera catalog (`catalog.v2.json`, **56 live cameras**): **Belgrade (40)** including 13 newly added 2K QHD (`2560x1440`) urban streams (*Kralja Milana — London*, *Kneza Miloša*, *Kraljice Marije — Poštanska štedionica*, *Južni bulevar*, *Autokomanda — Franš*, *Most na Adi — Topčider*, *Sava Centar*, *Jurija Gagarina — Belville & Ada*, *Zmaj*, *Lakat — Nova Galenika*, *Patrijarha Pavla — Košutnjak*, *Trgovačka — Žarkovo*), **Novi Sad (6)** (*Pupinova palata*, *SNP & Centar*, *Varadinski most / Duga*, *Mišeluk*, *Novi Sad Jug Ulaz/Izlaz*), **Subotica (6)** (*MZ Zorka*, *Subotica Jug Ulaz/Izlaz*, *GP Kelebija Ulaz/Izlaz*, *GP Horgoš Ulaz*), and **Niš (4)** (*Niš Sever Ulaz/Izlaz*, *Niš Jug Ulaz/Izlaz*).
- City filter tabs (`All Serbia`, `Belgrade`, `Novi Sad`, `Subotica`, `Niš`) and overlay/zap control chips (`Clock: On/Off`, `Weather: On/Off`, `Zap: All / Current city / Favorites`) inside the Quick Strip drawer.
- Deterministic `app/debug.keystore` signing configuration so local and CI debug APK builds share an identical signature across ADB upgrades.

### Changed
- Renamed Android TV launcher label from `Belgrade live cams` to **`Serbia live cams`** while retaining `applicationId = "rs.belgrade.tvcams"` to preserve user Favorites and preferences.

## [0.1.0] - 2026-11-17

### Added
- Initial Android TV live camera viewer covering 27 public traffic, bridge, square, boulevard, and highway-approach feeds across Belgrade, Serbia.
- Dual-transport media engine supporting hardware-decoded HLS (`.m3u8`) playback via Media3 ExoPlayer with automatic zero-flicker fallback to double-buffered HTTP JPEG snapshot polling.
- Remote D-pad channel zapping (`Left`/`Right` and `CH+`/`CH-`), bottom Quick Strip carousel, Street Info HUD, Pure Camera Mode (`OK`), and DataStore Favorites (`Hold OK`).
