# Catalog Probe

Maintains, refreshes, and health-checks `catalog/catalog.v5.json` and `app/src/main/assets/seed_catalog.v5.json` across 225+ public live cameras (13+ countries, 22+ cities, and community drop-in files under `catalog/d/*.json`), validating `HLS`, `YOUTUBE_LIVE` (Innertube `ANDROID` player API + single-camera channel rotation recovery), `RUTUBE_LIVE`, `IPCAMLIVE`, and `SNAPSHOT_POLL` endpoints.

## Commands

```bash
uv run ruff format --check .
uv run ruff check .
uv run pytest --cov=catalog_probe --cov-fail-under=85
uv run catalog-probe --check-contributions
uv run catalog-probe --report-unreachable --output ../../catalog/catalog.v5.json --output ../../app/src/main/assets/seed_catalog.v5.json
```
