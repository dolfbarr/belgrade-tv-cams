from __future__ import annotations

import json
from dataclasses import replace

import httpx

from catalog_probe.curated import build_curated_cameras
from catalog_probe.models import (
    CameraCategory,
    CameraCity,
    CameraCountry,
    CatalogManifest,
    StreamTransportType,
    VideoQuality,
    manifest_from_json,
    manifest_to_json,
)
from catalog_probe.prober import (
    ProbeResult,
    apply_probe_ordering,
    extract_naxi_hq_streams,
    extract_naxi_lq_snapshots,
    extract_toll4all_streams,
    normalize_snapshot_url,
    probe_cameras_live,
    refresh_rutube_live_urls,
)


def test_normalize_snapshot_url_strips_stale_cache_buster() -> None:
    raw = "https://stream.uzivobeograd.rs/live/cam_14.jpg?rand=0.2772828567751485"
    assert normalize_snapshot_url(raw) == "https://stream.uzivobeograd.rs/live/cam_14.jpg"


def test_curated_v4_world_catalog_invariants() -> None:
    cameras = build_curated_cameras()
    assert len(cameras) == 128

    seen_ids: set[str] = set()
    categories: set[CameraCategory] = set()
    country_counts: dict[CameraCountry, int] = {country: 0 for country in CameraCountry}
    city_counts: dict[CameraCity, int] = {city: 0 for city in CameraCity}
    quality_counts: dict[VideoQuality, int] = {q: 0 for q in VideoQuality}

    expected_timezones: dict[CameraCity, str] = {
        CameraCity.BELGRADE: "Europe/Belgrade",
        CameraCity.ZEMUN: "Europe/Belgrade",
        CameraCity.NOVI_SAD: "Europe/Belgrade",
        CameraCity.SUBOTICA: "Europe/Belgrade",
        CameraCity.NIS: "Europe/Belgrade",
        CameraCity.SAINT_PETERSBURG: "Europe/Moscow",
        CameraCity.ROSTOV_ON_DON: "Europe/Moscow",
        CameraCity.TOMSK: "Asia/Tomsk",
        CameraCity.BARCELONA: "Europe/Madrid",
        CameraCity.MADRID: "Europe/Madrid",
        CameraCity.BUCHAREST: "Europe/Bucharest",
    }

    for cam in cameras:
        assert cam.id not in seen_ids, f"Duplicate camera ID: {cam.id}"
        seen_ids.add(cam.id)
        assert cam.name.strip() != ""
        assert cam.country is not None
        assert cam.country == cam.city.default_country
        assert cam.timezone == expected_timezones[cam.city]
        assert len(cam.streets) >= 1
        assert cam.municipality.strip() != ""
        assert cam.direction.strip() != ""
        assert cam.poster_url.startswith("https://")
        assert "?" not in cam.poster_url
        categories.add(cam.category)
        country_counts[cam.country] += 1
        city_counts[cam.city] += 1
        quality_counts[cam.quality] += 1

        # Verify city coordinate accuracy so Open-Meteo returns true local weather per city
        if cam.city == CameraCity.BELGRADE:
            assert 44.55 <= cam.coordinates.lat <= 45.05
            assert 19.65 <= cam.coordinates.lon <= 21.15
        elif cam.city == CameraCity.ZEMUN:
            assert 44.80 <= cam.coordinates.lat <= 45.08
            assert 19.20 <= cam.coordinates.lon <= 20.43
        elif cam.city == CameraCity.NOVI_SAD:
            assert 44.95 <= cam.coordinates.lat <= 45.40
            assert 19.70 <= cam.coordinates.lon <= 20.05
        elif cam.city == CameraCity.SUBOTICA:
            assert 45.95 <= cam.coordinates.lat <= 46.25
            assert 19.50 <= cam.coordinates.lon <= 20.20
        elif cam.city == CameraCity.NIS:
            assert 42.30 <= cam.coordinates.lat <= 43.45
            assert 21.50 <= cam.coordinates.lon <= 22.85
        elif cam.city == CameraCity.SAINT_PETERSBURG:
            assert 59.80 <= cam.coordinates.lat <= 60.15
            assert 29.70 <= cam.coordinates.lon <= 30.60
        elif cam.city == CameraCity.ROSTOV_ON_DON:
            assert 47.10 <= cam.coordinates.lat <= 47.40
            assert 39.50 <= cam.coordinates.lon <= 39.95
        elif cam.city == CameraCity.TOMSK:
            assert 56.40 <= cam.coordinates.lat <= 56.60
            assert 84.85 <= cam.coordinates.lon <= 85.15
        elif cam.city == CameraCity.BARCELONA:
            assert 41.30 <= cam.coordinates.lat <= 41.50
            assert 2.05 <= cam.coordinates.lon <= 2.28
        elif cam.city == CameraCity.MADRID:
            assert 40.35 <= cam.coordinates.lat <= 40.55
            assert -3.80 <= cam.coordinates.lon <= -3.55
        elif cam.city == CameraCity.BUCHAREST:
            assert 44.30 <= cam.coordinates.lat <= 44.60
            assert 25.90 <= cam.coordinates.lon <= 26.25

        assert len(cam.streams) >= 1
        for stream in cam.streams:
            if not any(
                host in stream.url
                for host in ("ipcamlive.com", "openapi-alpha.ivideon.com", "bl.rutube.ru")
            ):
                assert "?" not in stream.url
            if "bitinfo.co.rs" in stream.url:
                assert stream.headers is not None
                assert stream.headers.get("Referer") == "https://kamere.toll4all.com/"
                assert stream.headers.get("Origin") == "https://kamere.toll4all.com"
            if "srbija-nadlanu.ott.solutions" in stream.url:
                assert stream.headers is not None
                assert stream.headers.get("Referer") == "https://kamere.24sedam.rs/"
                assert stream.headers.get("Origin") == "https://kamere.24sedam.rs"
                assert "Mozilla/5.0" in stream.headers.get("User-Agent", "")

    assert country_counts == {
        CameraCountry.SERBIA: 78,
        CameraCountry.RUSSIA: 23,
        CameraCountry.SPAIN: 22,
        CameraCountry.ROMANIA: 5,
    }
    assert city_counts == {
        CameraCity.BELGRADE: 40,
        CameraCity.ZEMUN: 8,
        CameraCity.NOVI_SAD: 8,
        CameraCity.SUBOTICA: 9,
        CameraCity.NIS: 13,
        CameraCity.SAINT_PETERSBURG: 12,
        CameraCity.ROSTOV_ON_DON: 1,
        CameraCity.TOMSK: 10,
        CameraCity.BARCELONA: 10,
        CameraCity.MADRID: 12,
        CameraCity.BUCHAREST: 5,
    }
    assert quality_counts == {
        VideoQuality.UHD_2K: 18,
        VideoQuality.FHD_1080P: 27,
        VideoQuality.HD_720P: 46,
        VideoQuality.SD: 37,
    }
    assert sum(1 for c in cameras if c.quality.is_hd) == 91
    assert sum(1 for c in cameras if not c.quality.is_hd) == 37

    # Verify HD-before-SD ordering invariant within every city
    for city in CameraCity:
        city_cams = [c for c in cameras if c.city == city]
        ranks = [c.quality.sort_rank for c in city_cams]
        assert ranks == sorted(ranks), f"City {city} cameras are not sorted HD-first: {ranks}"

    assert categories == {
        CameraCategory.BRIDGES,
        CameraCategory.SQUARES,
        CameraCategory.BOULEVARDS,
        CameraCategory.HIGHWAYS,
    }


def test_extract_upstream_html_parsers() -> None:
    naxi_hq_html = """
    <li class="camera" data-id="camera-1">
      <div class="camera__video" id="cont1">
        <video-js id="vid1">
          <source id="hd" src="https://streamer.devnet.rs/hls/brankov.m3u8" />
        </video-js>
      </div>
      <div class="camera__data">
        <h2 class="camera__title">Brankov most - Terazijski tunel</h2>
      </div>
    </li>
    """
    hq = extract_naxi_hq_streams(naxi_hq_html)
    assert hq == {
        "1": ("Brankov most - Terazijski tunel", "https://streamer.devnet.rs/hls/brankov.m3u8")
    }

    naxi_lq_html = """
    <li class="camera" data-id="camera-12">
      <div class="camera__video img-placeholder" data-url="https://stream.uzivobeograd.rs/live/cam_14.jpg?rand=0.2772">
      </div>
      <div class="camera__data">
        <h2 class="camera__title">Plavi most - Autoput</h2>
      </div>
    </li>
    """
    lq = extract_naxi_lq_snapshots(naxi_lq_html)
    assert lq == {"12": ("Plavi most - Autoput", "https://stream.uzivobeograd.rs/live/cam_14.jpg")}

    toll_html = """
    <div class="cam-item" poster="https://cam.bitinfo.co.rs/front_pan_cam5/index.jpg" src="https://cam.bitinfo.co.rs/front_pan_cam5/index.m3u8">
      <span>Vr&#269;in Izlaz</span>
    </div>
    """
    toll = extract_toll4all_streams(toll_html)
    assert toll == [
        (
            "Vrčin Izlaz",
            "https://cam.bitinfo.co.rs/front_pan_cam5/index.m3u8",
            "https://cam.bitinfo.co.rs/front_pan_cam5/index.jpg",
        )
    ]


def test_apply_probe_ordering_promotes_healthy_fallback() -> None:
    camera = next(c for c in build_curated_cameras() if len(c.streams) >= 2)
    hls_url = camera.streams[0].url
    snap_url = camera.streams[1].url

    results = {
        hls_url: ProbeResult(url=hls_url, reachable=False, status_code=404),
        snap_url: ProbeResult(url=snap_url, reachable=True, status_code=200),
    }
    reordered = apply_probe_ordering(camera, results)
    assert len(reordered.streams) == 2
    assert reordered.streams[0].type == StreamTransportType.SNAPSHOT_POLL
    assert reordered.streams[1].type == StreamTransportType.HLS


def test_prober_falls_back_to_get_when_head_returns_403() -> None:
    seen_methods: list[str] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen_methods.append(request.method)
        if request.method == "HEAD":
            return httpx.Response(403, text="Forbidden on HEAD")
        return httpx.Response(200, text="#EXTM3U\n")

    cam = build_curated_cameras()[0]
    transport = httpx.MockTransport(handler)
    results = probe_cameras_live([cam], timeout_seconds=2.0, transport=transport)
    assert results[cam.streams[0].url].reachable is True
    assert "HEAD" in seen_methods
    assert "GET" in seen_methods


def test_refresh_rutube_live_urls_updates_signed_hls_url() -> None:
    rutube_cam = next(c for c in build_curated_cameras() if c.id == "ru-rov-leninsky-skyline")
    expected_fresh = (
        "https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8"
        "?s=freshsig&e=2099999999&scheme=https"
    )

    def handler(request: httpx.Request) -> httpx.Response:
        assert "eba7bc75dd7994f0d7e22e1ad9e76a87" in str(request.url)
        return httpx.Response(200, json={"live_streams": {"hls": [{"url": expected_fresh}]}})

    updated = refresh_rutube_live_urls(
        [rutube_cam],
        timeout_seconds=2.0,
        transport=httpx.MockTransport(handler),
    )
    assert updated[0].streams[0].url == expected_fresh


def test_whole_graph_json_round_trip_fixed_point() -> None:
    manifest = CatalogManifest(
        schema_version=4,
        revision=2026111817,
        updated_at="2026-11-18T17:20:00Z",
        cameras=tuple(build_curated_cameras()),
    )
    first_json = manifest_to_json(manifest)
    raw_obj = json.loads(first_json)

    assert raw_obj["schema_version"] == 4
    first_cam = raw_obj["cameras"][0]
    # Verify exact key order matching Kotlin kotlinx.serialization CatalogJsonCodec
    assert list(first_cam.keys())[:6] == [
        "id",
        "name",
        "country",
        "city",
        "quality",
        "timezone",
    ]

    first_hls_stream = first_cam["streams"][0]
    assert "interval_ms" not in first_hls_stream
    assert "cache_buster_param" not in first_hls_stream

    reloaded = manifest_from_json(first_json)
    second_json = manifest_to_json(reloaded)
    assert second_json == first_json
    assert reloaded == manifest

    # Verify v3 backward compatibility when country is omitted (None)
    legacy_cam = replace(manifest.cameras[0], country=None)
    legacy_dict = legacy_cam.to_dict()
    assert "country" not in legacy_dict
    assert list(legacy_dict.keys())[:5] == ["id", "name", "city", "quality", "timezone"]
