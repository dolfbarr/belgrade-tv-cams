from __future__ import annotations

import json
import re
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

_LOCATION_CODE_RE = re.compile(r"^[A-Z][A-Z0-9_]*$")
_ISO_ALPHA2_RE = re.compile(r"^[A-Z]{2}$")


class CameraCountry(str):
    SERBIA: CameraCountry
    RUSSIA: CameraCountry
    SPAIN: CameraCountry
    ROMANIA: CameraCountry
    UK: CameraCountry
    FRANCE: CameraCountry
    NETHERLANDS: CameraCountry
    GERMANY: CameraCountry
    GEORGIA: CameraCountry
    ARMENIA: CameraCountry
    USA: CameraCountry
    SOUTH_KOREA: CameraCountry
    JAPAN: CameraCountry
    BUILT_IN: tuple[CameraCountry, ...]

    def __new__(cls, raw: str) -> CameraCountry:
        normalized = str(raw).strip().upper()
        if not _LOCATION_CODE_RE.match(normalized):
            raise ValueError(
                f"Invalid country code {raw!r}: expected UPPER_SNAKE_CASE (e.g. 'AUSTRIA')"
            )
        return super().__new__(cls, normalized)

    @property
    def value(self) -> str:
        return str(self)


CameraCountry.SERBIA = CameraCountry("SERBIA")
CameraCountry.RUSSIA = CameraCountry("RUSSIA")
CameraCountry.SPAIN = CameraCountry("SPAIN")
CameraCountry.ROMANIA = CameraCountry("ROMANIA")
CameraCountry.UK = CameraCountry("UK")
CameraCountry.FRANCE = CameraCountry("FRANCE")
CameraCountry.NETHERLANDS = CameraCountry("NETHERLANDS")
CameraCountry.GERMANY = CameraCountry("GERMANY")
CameraCountry.GEORGIA = CameraCountry("GEORGIA")
CameraCountry.ARMENIA = CameraCountry("ARMENIA")
CameraCountry.USA = CameraCountry("USA")
CameraCountry.SOUTH_KOREA = CameraCountry("SOUTH_KOREA")
CameraCountry.JAPAN = CameraCountry("JAPAN")
CameraCountry.BUILT_IN = (
    CameraCountry.SERBIA,
    CameraCountry.RUSSIA,
    CameraCountry.SPAIN,
    CameraCountry.ROMANIA,
    CameraCountry.UK,
    CameraCountry.FRANCE,
    CameraCountry.NETHERLANDS,
    CameraCountry.GERMANY,
    CameraCountry.GEORGIA,
    CameraCountry.ARMENIA,
    CameraCountry.USA,
    CameraCountry.SOUTH_KOREA,
    CameraCountry.JAPAN,
)


class CameraCity(str):
    BELGRADE: CameraCity
    ZEMUN: CameraCity
    NOVI_SAD: CameraCity
    SUBOTICA: CameraCity
    NIS: CameraCity
    MOSCOW: CameraCity
    SAINT_PETERSBURG: CameraCity
    ROSTOV_ON_DON: CameraCity
    TOMSK: CameraCity
    MAGADAN: CameraCity
    BARCELONA: CameraCity
    MADRID: CameraCity
    BUCHAREST: CameraCity
    LONDON: CameraCity
    PARIS: CameraCity
    AMSTERDAM: CameraCity
    BERLIN: CameraCity
    TBILISI: CameraCity
    YEREVAN: CameraCity
    NEW_YORK: CameraCity
    SEOUL: CameraCity
    TOKYO: CameraCity
    BUILT_IN: tuple[CameraCity, ...]

    def __new__(cls, raw: str) -> CameraCity:
        normalized = str(raw).strip().upper()
        if not _LOCATION_CODE_RE.match(normalized):
            raise ValueError(
                f"Invalid city code {raw!r}: expected UPPER_SNAKE_CASE (e.g. 'VIENNA')"
            )
        return super().__new__(cls, normalized)

    @property
    def value(self) -> str:
        return str(self)

    @property
    def default_country(self) -> CameraCountry | None:
        return _CITY_COUNTRY_MAP.get(self)


CameraCity.BELGRADE = CameraCity("BELGRADE")
CameraCity.ZEMUN = CameraCity("ZEMUN")
CameraCity.NOVI_SAD = CameraCity("NOVI_SAD")
CameraCity.SUBOTICA = CameraCity("SUBOTICA")
CameraCity.NIS = CameraCity("NIS")
CameraCity.MOSCOW = CameraCity("MOSCOW")
CameraCity.SAINT_PETERSBURG = CameraCity("SAINT_PETERSBURG")
CameraCity.ROSTOV_ON_DON = CameraCity("ROSTOV_ON_DON")
CameraCity.TOMSK = CameraCity("TOMSK")
CameraCity.MAGADAN = CameraCity("MAGADAN")
CameraCity.BARCELONA = CameraCity("BARCELONA")
CameraCity.MADRID = CameraCity("MADRID")
CameraCity.BUCHAREST = CameraCity("BUCHAREST")
CameraCity.LONDON = CameraCity("LONDON")
CameraCity.PARIS = CameraCity("PARIS")
CameraCity.AMSTERDAM = CameraCity("AMSTERDAM")
CameraCity.BERLIN = CameraCity("BERLIN")
CameraCity.TBILISI = CameraCity("TBILISI")
CameraCity.YEREVAN = CameraCity("YEREVAN")
CameraCity.NEW_YORK = CameraCity("NEW_YORK")
CameraCity.SEOUL = CameraCity("SEOUL")
CameraCity.TOKYO = CameraCity("TOKYO")
CameraCity.BUILT_IN = (
    CameraCity.BELGRADE,
    CameraCity.ZEMUN,
    CameraCity.NOVI_SAD,
    CameraCity.SUBOTICA,
    CameraCity.NIS,
    CameraCity.MOSCOW,
    CameraCity.SAINT_PETERSBURG,
    CameraCity.ROSTOV_ON_DON,
    CameraCity.TOMSK,
    CameraCity.MAGADAN,
    CameraCity.BARCELONA,
    CameraCity.MADRID,
    CameraCity.BUCHAREST,
    CameraCity.LONDON,
    CameraCity.PARIS,
    CameraCity.AMSTERDAM,
    CameraCity.BERLIN,
    CameraCity.TBILISI,
    CameraCity.YEREVAN,
    CameraCity.NEW_YORK,
    CameraCity.SEOUL,
    CameraCity.TOKYO,
)

_CITY_COUNTRY_MAP: dict[str, CameraCountry] = {
    CameraCity.BELGRADE: CameraCountry.SERBIA,
    CameraCity.ZEMUN: CameraCountry.SERBIA,
    CameraCity.NOVI_SAD: CameraCountry.SERBIA,
    CameraCity.SUBOTICA: CameraCountry.SERBIA,
    CameraCity.NIS: CameraCountry.SERBIA,
    CameraCity.MOSCOW: CameraCountry.RUSSIA,
    CameraCity.SAINT_PETERSBURG: CameraCountry.RUSSIA,
    CameraCity.ROSTOV_ON_DON: CameraCountry.RUSSIA,
    CameraCity.TOMSK: CameraCountry.RUSSIA,
    CameraCity.MAGADAN: CameraCountry.RUSSIA,
    CameraCity.BARCELONA: CameraCountry.SPAIN,
    CameraCity.MADRID: CameraCountry.SPAIN,
    CameraCity.BUCHAREST: CameraCountry.ROMANIA,
    CameraCity.LONDON: CameraCountry.UK,
    CameraCity.PARIS: CameraCountry.FRANCE,
    CameraCity.AMSTERDAM: CameraCountry.NETHERLANDS,
    CameraCity.BERLIN: CameraCountry.GERMANY,
    CameraCity.TBILISI: CameraCountry.GEORGIA,
    CameraCity.YEREVAN: CameraCountry.ARMENIA,
    CameraCity.NEW_YORK: CameraCountry.USA,
    CameraCity.SEOUL: CameraCountry.SOUTH_KOREA,
    CameraCity.TOKYO: CameraCountry.JAPAN,
}

SerbiaCity = CameraCity


class CameraCategory(StrEnum):
    BRIDGES = "BRIDGES"
    SQUARES = "SQUARES"
    BOULEVARDS = "BOULEVARDS"
    HIGHWAYS = "HIGHWAYS"


class VideoQuality(StrEnum):
    UHD_4K = "UHD_4K"
    UHD_2K = "UHD_2K"
    FHD_1080P = "FHD_1080P"
    HD_720P = "HD_720P"
    SD = "SD"

    @property
    def is_hd(self) -> bool:
        return self != VideoQuality.SD

    @property
    def sort_rank(self) -> int:
        if self == VideoQuality.UHD_4K:
            return 0
        if self == VideoQuality.UHD_2K:
            return 1
        if self == VideoQuality.FHD_1080P:
            return 2
        if self == VideoQuality.HD_720P:
            return 3
        return 4


class StreamTransportType(StrEnum):
    HLS = "HLS"
    YOUTUBE_LIVE = "YOUTUBE_LIVE"
    RUTUBE_LIVE = "RUTUBE_LIVE"
    IPCAMLIVE = "IPCAMLIVE"
    SNAPSHOT_POLL = "SNAPSHOT_POLL"

    @property
    def is_live_video(self) -> bool:
        return self != StreamTransportType.SNAPSHOT_POLL


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
    country_name: str | None = None
    country_iso: str | None = None
    city: CameraCity = CameraCity.BELGRADE
    city_name: str | None = None
    quality: VideoQuality = VideoQuality.SD
    featured: bool | None = None
    timezone: str = "Europe/Belgrade"
    poster_headers: dict[str, str] | None = None

    @property
    def resolved_country(self) -> CameraCountry:
        if self.country is not None:
            return self.country
        default = self.city.default_country
        if default is not None:
            return default
        raise ValueError(
            f"Camera {self.id!r} uses custom city {self.city!r} without explicit 'country'"
        )

    @property
    def is_featured(self) -> bool:
        return self.featured is True

    @property
    def is_live_video(self) -> bool:
        return bool(self.streams) and self.streams[0].type.is_live_video

    def to_dict(self) -> dict[str, Any]:
        payload: dict[str, Any] = {
            "id": self.id,
            "name": self.name,
        }
        if self.country is not None:
            payload["country"] = self.country.value
        if self.country_name is not None:
            payload["country_name"] = self.country_name
        if self.country_iso is not None:
            payload["country_iso"] = self.country_iso
        payload["city"] = self.city.value
        if self.city_name is not None:
            payload["city_name"] = self.city_name
        payload["quality"] = self.quality.value
        if self.featured is not None:
            payload["featured"] = self.featured
        payload.update(
            {
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
        raw_country_name = raw.get("country_name")
        country_name = str(raw_country_name).strip() if raw_country_name else None
        raw_country_iso = raw.get("country_iso")
        country_iso = str(raw_country_iso).strip().upper() if raw_country_iso else None
        if country_iso is not None and not _ISO_ALPHA2_RE.match(country_iso):
            raise ValueError(f"Invalid country_iso {country_iso!r}: expected 2-letter ISO code")
        raw_city = raw.get("city", CameraCity.BELGRADE.value)
        raw_city_name = raw.get("city_name")
        city_name = str(raw_city_name).strip() if raw_city_name else None
        raw_quality = raw.get("quality", VideoQuality.SD.value)
        raw_featured = raw.get("featured")
        featured = bool(raw_featured) if raw_featured is not None else None
        raw_timezone = raw.get("timezone", "Europe/Belgrade")
        return cls(
            id=str(raw["id"]),
            name=str(raw["name"]),
            country=country,
            country_name=country_name,
            country_iso=country_iso,
            city=CameraCity(str(raw_city)),
            city_name=city_name,
            quality=VideoQuality(str(raw_quality)),
            featured=featured,
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
