from __future__ import annotations

import json

from catalog_probe.curated import build_curated_cameras
from catalog_probe.models import (
    CameraCategory,
    CatalogManifest,
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
)


def test_normalize_snapshot_url_strips_stale_cache_buster() -> None:
    raw = "https://stream.uzivobeograd.rs/live/cam_14.jpg?rand=0.2772828567751485"
    assert normalize_snapshot_url(raw) == "https://stream.uzivobeograd.rs/live/cam_14.jpg"


def test_curated_catalog_invariants() -> None:
    cameras = build_curated_cameras()
    assert len(cameras) == 27

    seen_ids: set[str] = set()
    categories: set[CameraCategory] = set()

    for cam in cameras:
        assert cam.id not in seen_ids, f"Duplicate camera ID: {cam.id}"
        seen_ids.add(cam.id)
        assert cam.name.strip() != ""
        assert len(cam.streets) >= 1
        assert cam.municipality.strip() != ""
        assert cam.direction.strip() != ""
        assert cam.poster_url.startswith("https://")
        assert "?" not in cam.poster_url
        categories.add(cam.category)

        assert len(cam.streams) == 2
        assert cam.streams[0].type == StreamTransportType.HLS
        assert cam.streams[1].type == StreamTransportType.SNAPSHOT_POLL
        assert "?" not in cam.streams[1].url

        if "cam.bitinfo.co.rs" in cam.streams[0].url:
            assert cam.streams[0].headers is not None
            assert cam.streams[0].headers.get("Referer") == "https://kamere.toll4all.com/"
            assert cam.streams[0].headers.get("Origin") == "https://kamere.toll4all.com"

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


def test_whole_graph_json_round_trip_fixed_point() -> None:
    manifest = CatalogManifest(
        schema_version=1,
        revision=2026111701,
        updated_at="2026-11-17T21:30:00Z",
        cameras=tuple(build_curated_cameras()),
    )
    first_json = manifest_to_json(manifest)
    raw_obj = json.loads(first_json)

    # Verify optional fields are omitted rather than null when absent (Wire Invariant)
    first_hls_stream = raw_obj["cameras"][0]["streams"][0]
    assert "interval_ms" not in first_hls_stream
    assert "cache_buster_param" not in first_hls_stream

    reloaded = manifest_from_json(first_json)
    second_json = manifest_to_json(reloaded)
    assert second_json == first_json
    assert reloaded == manifest
