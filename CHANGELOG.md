# Changelog

All notable user-visible changes to this project are documented in this file.

## [Unreleased]

### Added
- Android TV live camera viewer covering 27 public traffic, bridge, square, boulevard, and highway-approach feeds across Belgrade, Serbia.
- Dual-transport media engine supporting hardware-decoded HLS (`.m3u8`) playback via Media3 ExoPlayer with automatic zero-flicker fallback to double-buffered HTTP JPEG snapshot polling.
- Remote D-pad channel zapping (`Left`/`Right` and `CH+`/`CH-`), bottom Quick Strip carousel grouped by category (`Bridges`, `Squares`, `Boulevards`, `Highway approaches`, `Favorites`), and `All` vs. `Favorites` scope switching (`Up`).
- Street and orientation HUD displaying intersection name, monitored streets, municipality, camera look-direction, and live stream telemetry, with one-press `OK` / `Info` toggle into Pure Camera Mode.
- Local Favorites persistence (`Hold OK` or `Bookmark`) backed by AndroidX DataStore.
- 3-tier initial catalog bootstrap (`seed_catalog.v1.json` bundled asset -> local disk cache -> conditional `ETag` sync from GitHub Pages `catalog.v1.json`) paired with a daily GitHub Actions stream health prober (`tools/catalog-probe`).
