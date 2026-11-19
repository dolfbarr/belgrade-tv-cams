from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from catalog_probe.models import CameraCity, CameraCountry, CameraEntry

_CAMERA_ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{2,63}$")


def default_contributions_dir() -> Path:
    # tools/catalog-probe/src/catalog_probe/contributions.py -> <repo_root>/catalog/d
    return Path(__file__).resolve().parents[4] / "catalog" / "d"


def validate_camera_entry(cam: CameraEntry, source_label: str = "catalog") -> None:
    if not _CAMERA_ID_RE.match(cam.id):
        raise ValueError(
            f"[{source_label}] Invalid camera id {cam.id!r}: "
            "expected lowercase slug (e.g. 'at-vie-stephansplatz')"
        )
    if not cam.name.strip():
        raise ValueError(f"[{source_label}] Camera {cam.id!r} must have a non-empty name")
    _ = cam.resolved_country
    try:
        ZoneInfo(cam.timezone)
    except ZoneInfoNotFoundError as exc:
        raise ValueError(
            f"[{source_label}] Camera {cam.id!r} has invalid IANA timezone {cam.timezone!r}"
        ) from exc

    if not (-90.0 <= cam.coordinates.lat <= 90.0):
        raise ValueError(
            f"[{source_label}] Camera {cam.id!r} latitude {cam.coordinates.lat} out of [-90, 90]"
        )
    if not (-180.0 <= cam.coordinates.lon <= 180.0):
        raise ValueError(
            f"[{source_label}] Camera {cam.id!r} longitude {cam.coordinates.lon} out of [-180, 180]"
        )
    if not cam.streets or any(not s.strip() for s in cam.streets):
        raise ValueError(f"[{source_label}] Camera {cam.id!r} must list at least 1 street/landmark")
    if not cam.municipality.strip():
        raise ValueError(f"[{source_label}] Camera {cam.id!r} must specify municipality/district")
    if not cam.direction.strip():
        raise ValueError(f"[{source_label}] Camera {cam.id!r} must specify camera view direction")
    if not cam.poster_url.startswith("https://"):
        raise ValueError(
            f"[{source_label}] Camera {cam.id!r} poster_url must use https:// ({cam.poster_url!r})"
        )
    if not cam.streams:
        raise ValueError(f"[{source_label}] Camera {cam.id!r} must define at least 1 stream")
    for stream in cam.streams:
        if not stream.url.startswith("https://"):
            raise ValueError(
                f"[{source_label}] Camera {cam.id!r} stream url must use https:// ({stream.url!r})"
            )


def parse_contribution_file(path: Path) -> list[CameraEntry]:
    raw_text = path.read_text(encoding="utf-8")
    data = json.loads(raw_text)
    if not isinstance(data, dict):
        raise ValueError(f"[{path.name}] Root JSON value must be an object")

    file_country = CameraCountry(str(data["country"])) if "country" in data else None
    file_country_name = str(data["country_name"]).strip() if data.get("country_name") else None
    file_country_iso = str(data["country_iso"]).strip().upper() if data.get("country_iso") else None
    file_city = CameraCity(str(data["city"])) if "city" in data else None
    file_city_name = str(data["city_name"]).strip() if data.get("city_name") else None
    file_timezone = str(data["timezone"]).strip() if data.get("timezone") else None

    raw_cameras = data.get("cameras")
    if not isinstance(raw_cameras, list) or not raw_cameras:
        raise ValueError(f"[{path.name}] Must contain a non-empty 'cameras' array")

    parsed: list[CameraEntry] = []
    for idx, item in enumerate(raw_cameras):
        if not isinstance(item, dict):
            raise ValueError(f"[{path.name}] Camera #{idx} must be a JSON object")
        merged: dict[str, Any] = dict(item)
        if "country" not in merged and file_country is not None:
            merged["country"] = file_country.value
        if "country_name" not in merged and file_country_name is not None:
            merged["country_name"] = file_country_name
        if "country_iso" not in merged and file_country_iso is not None:
            merged["country_iso"] = file_country_iso
        if "city" not in merged and file_city is not None:
            merged["city"] = file_city.value
        if "city_name" not in merged and file_city_name is not None:
            merged["city_name"] = file_city_name
        if "timezone" not in merged and file_timezone is not None:
            merged["timezone"] = file_timezone
        if merged.get("featured") is False:
            merged.pop("featured", None)

        cam = CameraEntry.from_dict(merged)
        validate_camera_entry(cam, source_label=path.name)
        parsed.append(cam)

    return parsed


def load_contributed_cameras(directory: Path | None = None) -> list[CameraEntry]:
    target_dir = directory if directory is not None else default_contributions_dir()
    if not target_dir.exists() or not target_dir.is_dir():
        return []

    cameras: list[CameraEntry] = []
    for json_path in sorted(target_dir.glob("*.json")):
        if json_path.name.startswith("_"):
            continue
        cameras.extend(parse_contribution_file(json_path))
    return cameras
