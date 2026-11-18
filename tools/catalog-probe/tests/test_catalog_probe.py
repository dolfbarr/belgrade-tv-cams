from __future__ import annotations

import json

import httpx

from catalog_probe.curated import build_curated_cameras
from catalog_probe.models import (
    CameraCategory,
    CatalogManifest,
    SerbiaCity,
    StreamTransportType,
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
)


def test_normalize_snapshot_url_strips_stale_cache_buster() -> None:
    raw = "https://stream.uzivobeograd.rs/live/cam_14.jpg?rand=0.2772828567751485"
    assert normalize_snapshot_url(raw) == "https://stream.uzivobeograd.rs/live/cam_14.jpg"


def test_curated_v2_serbia_catalog_invariants() -> None:
    cameras = build_curated_cameras()
    assert len(cameras) == 56

    seen_ids: set[str] = set()
    categories: set[CameraCategory] = set()
    city_counts: dict[SerbiaCity, int] = {city: 0 for city in SerbiaCity}

    for cam in cameras:
        assert cam.id not in seen_ids, f"Duplicate camera ID: {cam.id}"
        seen_ids.add(cam.id)
        assert cam.name.strip() != ""
        assert cam.timezone == "Europe/Belgrade"
        assert len(cam.streets) >= 1
        assert cam.municipality.strip() != ""
        assert cam.direction.strip() != ""
        assert cam.poster_url.startswith("https://")
        assert "?" not in cam.poster_url
        categories.add(cam.category)
        city_counts[cam.city] += 1

        # Verify city coordinate accuracy so Open-Meteo returns true local weather per city
        if cam.city == SerbiaCity.BELGRADE:
            assert 44.55 <= cam.coordinates.lat <= 45.05
            assert 20.00 <= cam.coordinates.lon <= 20.70
        elif cam.city == SerbiaCity.NOVI_SAD:
            assert 45.15 <= cam.coordinates.lat <= 45.40
            assert 19.70 <= cam.coordinates.lon <= 20.05
        elif cam.city == SerbiaCity.SUBOTICA:
            assert 45.95 <= cam.coordinates.lat <= 46.25
            assert 19.50 <= cam.coordinates.lon <= 19.85
        elif cam.city == SerbiaCity.NIS:
            assert 43.20 <= cam.coordinates.lat <= 43.45
            assert 21.75 <= cam.coordinates.lon <= 22.05

        assert len(cam.streams) >= 1
        for stream in cam.streams:
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

    assert city_counts == {
        SerbiaCity.BELGRADE: 40,
        SerbiaCity.NOVI_SAD: 6,
        SerbiaCity.SUBOTICA: 6,
        SerbiaCity.NIS: 4,
    }
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
    camera = build_curated_cameras()[0]
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


def test_whole_graph_json_round_trip_fixed_point() -> None:
    manifest = CatalogManifest(
        schema_version=2,
        revision=2026111801,
        updated_at="2026-11-18T01:45:00Z",
        cameras=tuple(build_curated_cameras()),
    )
    first_json = manifest_to_json(manifest)
    raw_obj = json.loads(first_json)

    assert raw_obj["schema_version"] == 2
    first_cam = raw_obj["cameras"][0]
    # Verify exact key order matching Kotlin kotlinx.serialization CatalogJsonCodec
    assert list(first_cam.keys())[:4] == ["id", "name", "city", "timezone"]

    first_hls_stream = first_cam["streams"][0]
    assert "interval_ms" not in first_hls_stream
    assert "cache_buster_param" not in first_hls_stream

    reloaded = manifest_from_json(first_json)
    second_json = manifest_to_json(reloaded)
    assert second_json == first_json
    assert reloaded == manifest
