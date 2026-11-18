from __future__ import annotations

import json
from dataclasses import dataclass
from enum import StrEnum
from typing import Any


class CameraCountry(StrEnum):
    SERBIA = "SERBIA"
    RUSSIA = "RUSSIA"
    SPAIN = "SPAIN"
    ROMANIA = "ROMANIA"


class CameraCity(StrEnum):
    BELGRADE = "BELGRADE"
    ZEMUN = "ZEMUN"
    NOVI_SAD = "NOVI_SAD"
    SUBOTICA = "SUBOTICA"
    NIS = "NIS"
    SAINT_PETERSBURG = "SAINT_PETERSBURG"
    ROSTOV_ON_DON = "ROSTOV_ON_DON"
    TOMSK = "TOMSK"
    BARCELONA = "BARCELONA"
    MADRID = "MADRID"
    BUCHAREST = "BUCHAREST"

    @property
    def default_country(self) -> CameraCountry:
        if self in (
            CameraCity.BELGRADE,
            CameraCity.ZEMUN,
            CameraCity.NOVI_SAD,
            CameraCity.SUBOTICA,
            CameraCity.NIS,
        ):
            return CameraCountry.SERBIA
        if self in (
            CameraCity.SAINT_PETERSBURG,
            CameraCity.ROSTOV_ON_DON,
            CameraCity.TOMSK,
        ):
            return CameraCountry.RUSSIA
        if self in (CameraCity.BARCELONA, CameraCity.MADRID):
            return CameraCountry.SPAIN
        return CameraCountry.ROMANIA


SerbiaCity = CameraCity


class CameraCategory(StrEnum):
    BRIDGES = "BRIDGES"
    SQUARES = "SQUARES"
    BOULEVARDS = "BOULEVARDS"
    HIGHWAYS = "HIGHWAYS"


class VideoQuality(StrEnum):
    UHD_2K = "UHD_2K"
    FHD_1080P = "FHD_1080P"
    HD_720P = "HD_720P"
    SD = "SD"

    @property
    def is_hd(self) -> bool:
        return self in (VideoQuality.UHD_2K, VideoQuality.FHD_1080P, VideoQuality.HD_720P)

    @property
    def sort_rank(self) -> int:
        if self == VideoQuality.UHD_2K:
            return 0
        if self == VideoQuality.FHD_1080P:
            return 1
        if self == VideoQuality.HD_720P:
            return 2
        return 3


class StreamTransportType(StrEnum):
    HLS = "HLS"
    SNAPSHOT_POLL = "SNAPSHOT_POLL"


@dataclass(frozen=True, slots=True)
class GeoCoordinates:
    lat: float
    lon: float

    def to_dict(self) -> dict[str, float]:
        return {"lat": self.lat, "lon": self.lon}

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> GeoCoordinates:
        return cls(lat=float(raw["lat"]), lon=float(raw["lon"]))


@dataclass(frozen=True, slots=True)
class StreamDescriptor:
    type: StreamTransportType
    url: str
    interval_ms: int | None = None
    cache_buster_param: str | None = None
    headers: dict[str, str] | None = None

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "type": self.type.value,
            "url": self.url,
        }
        if self.interval_ms is not None:
            payload["interval_ms"] = self.interval_ms
        if self.cache_buster_param is not None:
            payload["cache_buster_param"] = self.cache_buster_param
        if self.headers is not None and len(self.headers) > 0:
            payload["headers"] = dict(self.headers)
        return payload

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> StreamDescriptor:
        raw_headers = raw.get("headers")
        headers = {str(k): str(v) for k, v in raw_headers.items()} if raw_headers else None
        interval_raw = raw.get("interval_ms")
        cache_param_raw = raw.get("cache_buster_param")
        return cls(
            type=StreamTransportType(str(raw["type"])),
            url=str(raw["url"]),
            interval_ms=int(interval_raw) if interval_raw is not None else None,
            cache_buster_param=str(cache_param_raw) if cache_param_raw is not None else None,
            headers=headers,
        )


@dataclass(frozen=True, slots=True)
class CameraEntry:
    id: str
    name: str
    streets: tuple[str, ...]
    municipality: str
    category: CameraCategory
    direction: str
    poster_url: str
    coordinates: GeoCoordinates
    streams: tuple[StreamDescriptor, ...]
    country: CameraCountry | None = None
    city: CameraCity = CameraCity.BELGRADE
    quality: VideoQuality = VideoQuality.SD
    timezone: str = "Europe/Belgrade"
    poster_headers: dict[str, str] | None = None

    @property
    def resolved_country(self) -> CameraCountry:
        return self.country if self.country is not None else self.city.default_country

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "id": self.id,
            "name": self.name,
        }
        if self.country is not None:
            payload["country"] = self.country.value
        payload.update(
            {
                "city": self.city.value,
                "quality": self.quality.value,
                "timezone": self.timezone,
                "streets": list(self.streets),
                "municipality": self.municipality,
                "category": self.category.value,
                "direction": self.direction,
                "poster_url": self.poster_url,
                "coordinates": self.coordinates.to_dict(),
                "streams": [stream.to_dict() for stream in self.streams],
            }
        )
        if self.poster_headers is not None and len(self.poster_headers) > 0:
            payload["poster_headers"] = dict(self.poster_headers)
        return payload

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> CameraEntry:
        raw_poster_headers = raw.get("poster_headers")
        poster_headers = (
            {str(k): str(v) for k, v in raw_poster_headers.items()} if raw_poster_headers else None
        )
        raw_country = raw.get("country")
        country = CameraCountry(str(raw_country)) if raw_country is not None else None
        raw_city = raw.get("city", CameraCity.BELGRADE.value)
        raw_quality = raw.get("quality", VideoQuality.SD.value)
        raw_timezone = raw.get("timezone", "Europe/Belgrade")
        return cls(
            id=str(raw["id"]),
            name=str(raw["name"]),
            country=country,
            city=CameraCity(str(raw_city)),
            quality=VideoQuality(str(raw_quality)),
            timezone=str(raw_timezone),
            streets=tuple(str(s) for s in raw["streets"]),
            municipality=str(raw["municipality"]),
            category=CameraCategory(str(raw["category"])),
            direction=str(raw["direction"]),
            poster_url=str(raw["poster_url"]),
            coordinates=GeoCoordinates.from_dict(raw["coordinates"]),
            streams=tuple(StreamDescriptor.from_dict(item) for item in raw["streams"]),
            poster_headers=poster_headers,
        )


@dataclass(frozen=True, slots=True)
class CatalogManifest:
    schema_version: int
    revision: int
    updated_at: str
    cameras: tuple[CameraEntry, ...]

    def to_dict(self) -> dict[str, Any]:
        return {
            "schema_version": self.schema_version,
            "revision": self.revision,
            "updated_at": self.updated_at,
            "cameras": [cam.to_dict() for cam in self.cameras],
        }

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> CatalogManifest:
        return cls(
            schema_version=int(raw["schema_version"]),
            revision=int(raw["revision"]),
            updated_at=str(raw["updated_at"]),
            cameras=tuple(CameraEntry.from_dict(item) for item in raw["cameras"]),
        )


def manifest_to_json(manifest: CatalogManifest) -> str:
    return json.dumps(manifest.to_dict(), ensure_ascii=False, indent=2) + "\n"


def manifest_from_json(payload: str) -> CatalogManifest:
    raw = json.loads(payload)
    if not isinstance(raw, dict):
        raise ValueError("Catalog manifest root must be a JSON object")
    return CatalogManifest.from_dict(raw)
