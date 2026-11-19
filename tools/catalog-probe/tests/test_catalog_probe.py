from __future__ import annotations

import json
from dataclasses import replace
from pathlib import Path

import httpx
import pytest

from catalog_probe.cli import main as cli_main
from catalog_probe.contributions import parse_contribution_file
from catalog_probe.curated import build_curated_cameras
from catalog_probe.models import (
    CameraCategory,
    CameraCity,
    CameraCountry,
    CameraEntry,
    CatalogManifest,
    GeoCoordinates,
    StreamDescriptor,
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
    extract_youtube_channel_live_url,
    extract_youtube_video_id,
    normalize_snapshot_url,
    probe_cameras_live,
    refresh_rostelecom_live_urls,
    refresh_rutube_live_urls,
    refresh_youtube_live_urls,
)


def test_normalize_snapshot_url_strips_stale_cache_buster() -> None:
    raw = "https://stream.uzivobeograd.rs/live/cam_14.jpg?rand=0.2772828567751485"
    assert normalize_snapshot_url(raw) == "https://stream.uzivobeograd.rs/live/cam_14.jpg"


def test_extract_youtube_video_id_variants() -> None:
    assert extract_youtube_video_id("https://www.youtube.com/watch?v=ZxCeWQ3Lcig") == "ZxCeWQ3Lcig"
    assert extract_youtube_video_id("https://youtu.be/dfVK7ld38Ys?t=5") == "dfVK7ld38Ys"
    assert extract_youtube_video_id("https://www.youtube.com/live/RVvYQghFCXk") == "RVvYQghFCXk"
    assert extract_youtube_video_id("https://example.com/watch?v=ZxCeWQ3Lcig") is None


def test_curated_v5_world_catalog_invariants() -> None:
    cameras = build_curated_cameras()
    assert len(cameras) == 225

    seen_ids: set[str] = set()
    categories: set[CameraCategory] = set()
    country_counts: dict[CameraCountry, int] = {country: 0 for country in CameraCountry.BUILT_IN}
    city_counts: dict[CameraCity, int] = {city: 0 for city in CameraCity.BUILT_IN}
    quality_counts: dict[VideoQuality, int] = {q: 0 for q in VideoQuality}

    expected_timezones: dict[CameraCity, str] = {
        CameraCity.BELGRADE: "Europe/Belgrade",
        CameraCity.ZEMUN: "Europe/Belgrade",
        CameraCity.NOVI_SAD: "Europe/Belgrade",
        CameraCity.SUBOTICA: "Europe/Belgrade",
        CameraCity.NIS: "Europe/Belgrade",
        CameraCity.MOSCOW: "Europe/Moscow",
        CameraCity.SAINT_PETERSBURG: "Europe/Moscow",
        CameraCity.ROSTOV_ON_DON: "Europe/Moscow",
        CameraCity.TOMSK: "Asia/Tomsk",
        CameraCity.MAGADAN: "Asia/Magadan",
        CameraCity.BARCELONA: "Europe/Madrid",
        CameraCity.MADRID: "Europe/Madrid",
        CameraCity.BUCHAREST: "Europe/Bucharest",
        CameraCity.LONDON: "Europe/London",
        CameraCity.PARIS: "Europe/Paris",
        CameraCity.AMSTERDAM: "Europe/Amsterdam",
        CameraCity.BERLIN: "Europe/Berlin",
        CameraCity.TBILISI: "Asia/Tbilisi",
        CameraCity.YEREVAN: "Asia/Yerevan",
        CameraCity.NEW_YORK: "America/New_York",
        CameraCity.SEOUL: "Asia/Seoul",
        CameraCity.TOKYO: "Asia/Tokyo",
    }

    city_bounds: dict[CameraCity, tuple[float, float, float, float]] = {
        CameraCity.BELGRADE: (44.55, 45.05, 19.65, 21.15),
        CameraCity.ZEMUN: (44.80, 45.08, 19.20, 20.43),
        CameraCity.NOVI_SAD: (44.95, 45.40, 19.70, 20.05),
        CameraCity.SUBOTICA: (45.95, 46.25, 19.50, 20.20),
        CameraCity.NIS: (42.30, 43.45, 21.50, 22.85),
        CameraCity.MOSCOW: (55.50, 56.00, 37.30, 38.00),
        CameraCity.SAINT_PETERSBURG: (59.80, 60.15, 29.70, 30.60),
        CameraCity.ROSTOV_ON_DON: (47.10, 47.40, 39.50, 39.95),
        CameraCity.TOMSK: (56.40, 56.60, 84.85, 85.15),
        CameraCity.MAGADAN: (59.50, 60.05, 150.60, 151.00),
        CameraCity.BARCELONA: (41.20, 41.50, 1.75, 2.28),
        CameraCity.MADRID: (40.35, 40.95, -3.95, -3.55),
        CameraCity.BUCHAREST: (44.30, 44.65, 25.90, 26.25),
        CameraCity.LONDON: (51.40, 51.65, -0.55, 0.10),
        CameraCity.PARIS: (48.75, 48.95, 2.15, 2.45),
        CameraCity.AMSTERDAM: (52.30, 52.45, 4.80, 5.05),
        CameraCity.BERLIN: (52.38, 52.62, 13.15, 13.72),
        CameraCity.TBILISI: (41.50, 41.80, 44.65, 45.05),
        CameraCity.YEREVAN: (40.10, 40.60, 44.40, 44.80),
        CameraCity.NEW_YORK: (40.65, 40.85, -74.10, -73.90),
        CameraCity.SEOUL: (37.45, 37.65, 126.85, 127.15),
        CameraCity.TOKYO: (35.50, 35.80, 139.60, 139.90),
    }

    query_allowed_hosts = (
        "ipcamlive.com",
        "openapi-alpha.ivideon.com",
        "rec.maglan.ru",
        "youtube.com",
        "camera.rt.ru",
        "bl.rutube.ru",
    )

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
        if not any(host in cam.poster_url for host in query_allowed_hosts):
            assert "?" not in cam.poster_url, f"Unexpected query in poster_url: {cam.poster_url}"
        categories.add(cam.category)
        country_counts[cam.country] += 1
        city_counts[cam.city] += 1
        quality_counts[cam.quality] += 1

        lat_min, lat_max, lon_min, lon_max = city_bounds[cam.city]
        assert lat_min <= cam.coordinates.lat <= lat_max, f"{cam.id} lat out of {cam.city} bounds"
        assert lon_min <= cam.coordinates.lon <= lon_max, f"{cam.id} lon out of {cam.city} bounds"

        assert len(cam.streams) >= 1
        for stream in cam.streams:
            if not any(host in stream.url for host in query_allowed_hosts):
                assert "?" not in stream.url, f"Unexpected query in stream url: {stream.url}"
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
        CameraCountry.RUSSIA: 35,
        CameraCountry.SPAIN: 29,
        CameraCountry.ROMANIA: 8,
        CameraCountry.UK: 9,
        CameraCountry.FRANCE: 8,
        CameraCountry.NETHERLANDS: 8,
        CameraCountry.GERMANY: 8,
        CameraCountry.GEORGIA: 4,
        CameraCountry.ARMENIA: 3,
        CameraCountry.USA: 13,
        CameraCountry.SOUTH_KOREA: 10,
        CameraCountry.JAPAN: 12,
    }
    assert city_counts == {
        CameraCity.BELGRADE: 40,
        CameraCity.ZEMUN: 8,
        CameraCity.NOVI_SAD: 8,
        CameraCity.SUBOTICA: 9,
        CameraCity.NIS: 13,
        CameraCity.MOSCOW: 7,
        CameraCity.SAINT_PETERSBURG: 12,
        CameraCity.ROSTOV_ON_DON: 3,
        CameraCity.TOMSK: 10,
        CameraCity.MAGADAN: 3,
        CameraCity.BARCELONA: 14,
        CameraCity.MADRID: 15,
        CameraCity.BUCHAREST: 8,
        CameraCity.LONDON: 9,
        CameraCity.PARIS: 8,
        CameraCity.AMSTERDAM: 8,
        CameraCity.BERLIN: 8,
        CameraCity.TBILISI: 4,
        CameraCity.YEREVAN: 3,
        CameraCity.NEW_YORK: 13,
        CameraCity.SEOUL: 10,
        CameraCity.TOKYO: 12,
    }

    # Every single one of the 22 cities must have >= 1 live motion video camera,
    # and live video cameras must be sorted ahead of snapshots (then highest resolution first).
    for city in CameraCity.BUILT_IN:
        city_cams = [c for c in cameras if c.city == city]
        live_count = sum(1 for c in city_cams if c.is_live_video)
        assert live_count >= 1, f"City {city} has zero live video streams!"
        sort_keys = [(0 if c.is_live_video else 1, c.quality.sort_rank) for c in city_cams]
        assert sort_keys == sorted(sort_keys), f"City {city} violated live-first/HD-first order"

    assert quality_counts[VideoQuality.UHD_4K] >= 20
    assert sum(1 for c in cameras if c.is_featured) >= 24

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
    camera = next(
        c
        for c in build_curated_cameras()
        if len(c.streams) >= 2
        and c.streams[0].type == StreamTransportType.HLS
        and c.streams[1].type == StreamTransportType.SNAPSHOT_POLL
    )
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


def test_prober_supports_youtube_rutube_ipcamlive_and_windy_content_type() -> None:
    sample_cameras = [
        CameraEntry(
            id="t-yt",
            name="YT",
            country=CameraCountry.JAPAN,
            city=CameraCity.TOKYO,
            quality=VideoQuality.UHD_4K,
            featured=True,
            timezone="Asia/Tokyo",
            streets=("St",),
            municipality="Minato",
            category=CameraCategory.SQUARES,
            direction="Dir",
            poster_url="https://i.ytimg.com/vi/ZxCeWQ3Lcig/hqdefault.jpg",
            coordinates=GeoCoordinates(35.68, 139.76),
            streams=(
                StreamDescriptor(
                    StreamTransportType.YOUTUBE_LIVE,
                    "https://www.youtube.com/watch?v=ZxCeWQ3Lcig",
                ),
            ),
        ),
        CameraEntry(
            id="t-rt",
            name="RT",
            country=CameraCountry.RUSSIA,
            city=CameraCity.MOSCOW,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Moscow",
            streets=("St",),
            municipality="Center",
            category=CameraCategory.BRIDGES,
            direction="Dir",
            poster_url="https://pic.rtbcdn.ru/video/x.jpg",
            coordinates=GeoCoordinates(55.75, 37.62),
            streams=(
                StreamDescriptor(
                    StreamTransportType.RUTUBE_LIVE,
                    "https://rutube.ru/video/849eda0a248516e770a6172f10a14308/",
                ),
            ),
        ),
        CameraEntry(
            id="t-ipc",
            name="IPC",
            country=CameraCountry.SERBIA,
            city=CameraCity.SUBOTICA,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Belgrade",
            streets=("St",),
            municipality="Subotica",
            category=CameraCategory.SQUARES,
            direction="Dir",
            poster_url="https://g0.ipcamlive.com/player/snapshot.php?alias=kameratrg",
            coordinates=GeoCoordinates(46.10, 19.66),
            streams=(
                StreamDescriptor(
                    StreamTransportType.IPCAMLIVE,
                    "https://ipcamlive.com/kameratrg",
                ),
                StreamDescriptor(
                    StreamTransportType.SNAPSHOT_POLL,
                    "https://imgproxy.windy.com/_/full/plain/current/999999/original.jpg",
                    interval_ms=60000,
                    cache_buster_param="v",
                ),
            ),
        ),
    ]

    sample_cameras.append(
        CameraEntry(
            id="t-yt-offline",
            name="YT Offline",
            country=CameraCountry.USA,
            city=CameraCity.NEW_YORK,
            quality=VideoQuality.FHD_1080P,
            timezone="America/New_York",
            streets=("Broadway",),
            municipality="Manhattan",
            category=CameraCategory.SQUARES,
            direction="Dir",
            poster_url="https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            coordinates=GeoCoordinates(40.75, -73.98),
            streams=(
                StreamDescriptor(
                    StreamTransportType.YOUTUBE_LIVE,
                    "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                ),
            ),
        )
    )

    def handler(request: httpx.Request) -> httpx.Response:
        url = str(request.url)
        if "youtubei/v1/player" in url and request.method == "POST":
            assert request.headers.get("x-youtube-client-name") == "3"
            assert "com.google.android.youtube/" in request.headers.get("user-agent", "")
            payload = json.loads(request.content.decode("utf-8"))
            assert payload["context"]["client"]["clientName"] == "ANDROID"
            vid = payload.get("videoId")
            if vid == "ZxCeWQ3Lcig":
                return httpx.Response(
                    200,
                    json={
                        "playabilityStatus": {"status": "OK"},
                        "videoDetails": {"videoId": vid, "isLive": True},
                        "streamingData": {
                            "hlsManifestUrl": (
                                "https://manifest.googlevideo.com/api/manifest/hls_variant/"
                                "id/ZxCeWQ3Lcig.1/file/index.m3u8"
                            )
                        },
                    },
                )
            return httpx.Response(
                200,
                json={
                    "playabilityStatus": {
                        "status": "LIVE_STREAM_OFFLINE",
                        "reason": "This live stream recording is not available.",
                    }
                },
            )
        if "rutube.ru/api/play/options/" in url:
            return httpx.Response(
                200,
                json={"live_streams": {"hls": [{"url": "https://bl.rutube.ru/live/index.m3u8"}]}},
            )
        if "ipcamlive.com/ajax/getcamerastreamstate.php" in url:
            return httpx.Response(200, json={"details": {"streamavailable": "1"}})
        if "imgproxy.windy.com" in url:
            # Simulate Windy retired-ID false 200 text/plain response
            return httpx.Response(
                200,
                headers={"content-type": "text/plain"},
                text="Image does not exist",
            )
        return httpx.Response(404)

    results = probe_cameras_live(
        sample_cameras,
        timeout_seconds=2.0,
        transport=httpx.MockTransport(handler),
    )
    assert results["https://www.youtube.com/watch?v=ZxCeWQ3Lcig"].reachable is True
    assert results["https://www.youtube.com/watch?v=dQw4w9WgXcQ"].reachable is False
    assert results["https://rutube.ru/video/849eda0a248516e770a6172f10a14308/"].reachable is True
    assert results["https://ipcamlive.com/kameratrg"].reachable is True
    assert (
        results["https://imgproxy.windy.com/_/full/plain/current/999999/original.jpg"].reachable
        is False
    )

    hls_cameras = [
        CameraEntry(
            id="t-hls-live",
            name="HLS Live",
            country=CameraCountry.GERMANY,
            city=CameraCity.BERLIN,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Berlin",
            streets=("St",),
            municipality="Mitte",
            category=CameraCategory.SQUARES,
            direction="Dir",
            poster_url="https://example.com/p.jpg",
            coordinates=GeoCoordinates(52.52, 13.40),
            streams=(
                StreamDescriptor(
                    StreamTransportType.HLS, "https://cdn.example.com/live/master.m3u8"
                ),
                StreamDescriptor(
                    StreamTransportType.HLS, "https://cdn.example.com/dead/master.m3u8"
                ),
            ),
        )
    ]

    def hls_handler(request: httpx.Request) -> httpx.Response:
        url = str(request.url)
        if url == "https://cdn.example.com/live/master.m3u8":
            return httpx.Response(
                200, text="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2000000\nchunks.m3u8\n"
            )
        if url == "https://cdn.example.com/live/chunks.m3u8":
            return httpx.Response(200, text="#EXTM3U\n#EXTINF:4.0,\nseg01.ts\n")
        if url == "https://cdn.example.com/dead/master.m3u8":
            return httpx.Response(
                200, text="#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1024\nempty.m3u8\n"
            )
        if url == "https://cdn.example.com/dead/empty.m3u8":
            return httpx.Response(200, text="#EXTM3U\n#EXT-X-VERSION:3\n")
        return httpx.Response(404)

    hls_results = probe_cameras_live(
        hls_cameras,
        timeout_seconds=2.0,
        transport=httpx.MockTransport(hls_handler),
    )
    assert hls_results["https://cdn.example.com/live/master.m3u8"].reachable is True
    assert hls_results["https://cdn.example.com/dead/master.m3u8"].reachable is False


def test_refresh_rutube_and_rostelecom_live_urls() -> None:
    legacy_rutube_cam = CameraEntry(
        id="legacy-rt",
        name="Legacy RT",
        country=CameraCountry.RUSSIA,
        city=CameraCity.ROSTOV_ON_DON,
        quality=VideoQuality.FHD_1080P,
        timezone="Europe/Moscow",
        streets=("St",),
        municipality="Leninsky",
        category=CameraCategory.HIGHWAYS,
        direction="Dir",
        poster_url="https://pic.rtbcdn.ru/video/x.jpg",
        coordinates=GeoCoordinates(47.22, 39.69),
        streams=(
            StreamDescriptor(
                StreamTransportType.HLS,
                "https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8?s=old",
            ),
        ),
    )
    expected_rt = (
        "https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8"
        "?s=freshsig&e=2099999999&scheme=https"
    )
    updated_rt = refresh_rutube_live_urls(
        [legacy_rutube_cam],
        timeout_seconds=2.0,
        transport=httpx.MockTransport(
            lambda req: httpx.Response(200, json={"live_streams": {"hls": [{"url": expected_rt}]}})
        ),
    )
    assert updated_rt[0].streams[0].url == expected_rt

    gdx_cam = next(c for c in build_curated_cameras() if c.id == "ru-gdx-sokol-panorama")
    expected_rostelecom = (
        "https://live-msk1.camera.rt.ru/public/master.m3u8?sid=3374004d-7835-40d0-88c8-515ad29c4dcd"
    )
    updated_gdx = refresh_rostelecom_live_urls(
        [gdx_cam],
        timeout_seconds=2.0,
        transport=httpx.MockTransport(
            lambda req: httpx.Response(
                200,
                json={"status": "ok", "camera": {"is_alive": True, "hls_url": expected_rostelecom}},
            )
        ),
    )
    assert updated_gdx[0].streams[0].url == expected_rostelecom


def test_self_healing_single_cam_youtube_channel_refresh_and_channel_probing(
    tmp_path: Path,
) -> None:
    assert (
        extract_youtube_channel_live_url(
            "https://www.youtube.com/channel/UC3DrGPmCHsrionkmduqX0qg/live"
        )
        == "https://www.youtube.com/channel/UC3DrGPmCHsrionkmduqX0qg/live"
    )
    assert extract_youtube_channel_live_url("https://www.youtube.com/@MadridSkyLive/live") == (
        "https://www.youtube.com/@MadridSkyLive/live"
    )
    assert extract_youtube_channel_live_url("https://www.youtube.com/watch?v=Jw9tsexipts") is None

    rotated_single_cam = CameraEntry(
        id="jp-tyo-skytree-test",
        name="Skytree",
        country=CameraCountry.JAPAN,
        city=CameraCity.TOKYO,
        quality=VideoQuality.UHD_4K,
        featured=True,
        timezone="Asia/Tokyo",
        streets=("Sumida",),
        municipality="Sumida-ku",
        category=CameraCategory.BRIDGES,
        direction="Dir",
        poster_url="https://i.ytimg.com/vi/iycgnGq-h14/hqdefault.jpg",
        coordinates=GeoCoordinates(35.71, 139.81),
        streams=(
            StreamDescriptor(
                StreamTransportType.YOUTUBE_LIVE,
                "https://www.youtube.com/watch?v=iycgnGq-h14",
            ),
            StreamDescriptor(
                StreamTransportType.YOUTUBE_LIVE,
                "https://www.youtube.com/channel/UC3DrGPmCHsrionkmduqX0qg/live",
            ),
        ),
    )
    multicam_no_channel_fallback = CameraEntry(
        id="us-nyc-earthcam-test",
        name="Times Square",
        country=CameraCountry.USA,
        city=CameraCity.NEW_YORK,
        quality=VideoQuality.UHD_4K,
        timezone="America/New_York",
        streets=("Broadway",),
        municipality="Manhattan",
        category=CameraCategory.SQUARES,
        direction="Dir",
        poster_url="https://i.ytimg.com/vi/ZxCeWQ3Lcig/hqdefault.jpg",
        coordinates=GeoCoordinates(40.75, -73.98),
        streams=(
            StreamDescriptor(
                StreamTransportType.YOUTUBE_LIVE,
                "https://www.youtube.com/watch?v=ZxCeWQ3Lcig",
            ),
        ),
    )

    def yt_handler(request: httpx.Request) -> httpx.Response:
        url = str(request.url)
        if "/channel/UC3DrGPmCHsrionkmduqX0qg/live" in url:
            html_body = (
                '<link rel="canonical" href="https://www.youtube.com/watch?v=Jw9tsexipts">'
                '<script>var yt={"isLive":true};</script>'
            )
            return httpx.Response(200, text=html_body)
        if "youtubei/v1/player" in url and request.method == "POST":
            vid = json.loads(request.content.decode("utf-8")).get("videoId")
            if vid == "Jw9tsexipts":
                return httpx.Response(
                    200,
                    json={
                        "playabilityStatus": {"status": "OK"},
                        "videoDetails": {"videoId": vid, "isLive": True},
                        "streamingData": {
                            "hlsManifestUrl": (
                                "https://manifest.googlevideo.com/api/manifest/hls_variant/"
                                "id/Jw9tsexipts.1/file/index.m3u8"
                            )
                        },
                    },
                )
            return httpx.Response(
                200,
                json={
                    "playabilityStatus": {"status": "OK"},
                    "videoDetails": {"videoId": vid, "isLive": False},
                    "streamingData": {},
                },
            )
        return httpx.Response(404)

    transport = httpx.MockTransport(yt_handler)
    refreshed = refresh_youtube_live_urls(
        [rotated_single_cam, multicam_no_channel_fallback],
        timeout_seconds=2.0,
        transport=transport,
    )
    assert refreshed[0].streams[0].url == "https://www.youtube.com/watch?v=Jw9tsexipts"
    assert refreshed[0].poster_url == "https://i.ytimg.com/vi/Jw9tsexipts/hqdefault.jpg"
    # Multi-cam entry without single_cam_channel_id must never be rewritten
    assert refreshed[1] == multicam_no_channel_fallback

    probe_res = probe_cameras_live([refreshed[0]], timeout_seconds=2.0, transport=transport)
    assert probe_res["https://www.youtube.com/watch?v=Jw9tsexipts"].reachable is True
    assert (
        probe_res["https://www.youtube.com/channel/UC3DrGPmCHsrionkmduqX0qg/live"].reachable is True
    )

    out_manifest = tmp_path / "out_v5.json"
    assert cli_main([]) == 2
    assert cli_main(["--offline", "--output", str(out_manifest)]) == 0
    assert out_manifest.exists()


def test_whole_graph_json_round_trip_fixed_point() -> None:
    manifest = CatalogManifest(
        schema_version=5,
        revision=2026111820,
        updated_at="2026-11-18T20:55:00Z",
        cameras=tuple(build_curated_cameras()),
    )
    first_json = manifest_to_json(manifest)
    raw_obj = json.loads(first_json)

    assert raw_obj["schema_version"] == 5
    featured_cam = next(item for item in raw_obj["cameras"] if item.get("featured") is True)
    assert list(featured_cam.keys())[:7] == [
        "id",
        "name",
        "country",
        "city",
        "quality",
        "featured",
        "timezone",
    ]

    non_featured_cam = next(item for item in raw_obj["cameras"] if "featured" not in item)
    assert list(non_featured_cam.keys())[:6] == [
        "id",
        "name",
        "country",
        "city",
        "quality",
        "timezone",
    ]

    reloaded = manifest_from_json(first_json)
    second_json = manifest_to_json(reloaded)
    assert second_json == first_json
    assert reloaded == manifest

    # Verify v3 backward compatibility when country is omitted (None)
    legacy_cam = replace(manifest.cameras[0], country=None, featured=None)
    legacy_dict = legacy_cam.to_dict()
    assert "country" not in legacy_dict
    assert "featured" not in legacy_dict
    assert list(legacy_dict.keys())[:5] == ["id", "name", "city", "quality", "timezone"]


def test_drop_in_community_contribution_file_loads_and_validates(tmp_path: Path) -> None:
    contrib_dir = tmp_path / "d"
    contrib_dir.mkdir()

    # Template files starting with '_' are ignored
    (contrib_dir / "_ignored.json").write_text("{}", encoding="utf-8")

    vienna_file = contrib_dir / "austria-vienna.json"
    vienna_file.write_text(
        json.dumps(
            {
                "country": "AUSTRIA",
                "country_name": "Austria",
                "country_iso": "AT",
                "city": "VIENNA",
                "city_name": "Vienna",
                "timezone": "Europe/Vienna",
                "cameras": [
                    {
                        "id": "at-vie-stephansplatz",
                        "name": "Stephansplatz & St. Stephen's Cathedral",
                        "quality": "FHD_1080P",
                        "featured": True,
                        "streets": ["Stephansplatz", "Kärntner Straße"],
                        "municipality": "Innere Stadt",
                        "category": "SQUARES",
                        "direction": "Live panorama over Stephansplatz",
                        "poster_url": "https://example.com/stephansplatz.jpg",
                        "coordinates": {"lat": 48.2082, "lon": 16.3738},
                        "streams": [
                            {"type": "HLS", "url": "https://example.com/stephansplatz.m3u8"}
                        ],
                    }
                ],
            }
        ),
        encoding="utf-8",
    )

    merged = build_curated_cameras(contributions_dir=contrib_dir)
    assert len(merged) == 226
    vienna = next(c for c in merged if c.id == "at-vie-stephansplatz")
    assert vienna.country == CameraCountry("AUSTRIA")
    assert vienna.country_name == "Austria"
    assert vienna.country_iso == "AT"
    assert vienna.city == CameraCity("VIENNA")
    assert vienna.city_name == "Vienna"
    assert vienna.timezone == "Europe/Vienna"

    exit_code = cli_main(
        ["--check-contributions", "--offline", "--contributions-dir", str(contrib_dir)]
    )
    assert exit_code == 0

    bad_tz_file = tmp_path / "bad-tz.json"
    bad_tz_file.write_text(
        json.dumps(
            {
                "country": "AUSTRIA",
                "city": "VIENNA",
                "timezone": "Mars/Olympus_Mons",
                "cameras": [
                    {
                        "id": "at-vie-bad-tz",
                        "name": "Bad TZ",
                        "quality": "SD",
                        "streets": ["Main"],
                        "municipality": "Center",
                        "category": "SQUARES",
                        "direction": "North",
                        "poster_url": "https://example.com/p.jpg",
                        "coordinates": {"lat": 48.2, "lon": 16.3},
                        "streams": [{"type": "HLS", "url": "https://example.com/s.m3u8"}],
                    }
                ],
            }
        ),
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="invalid IANA timezone"):
        parse_contribution_file(bad_tz_file)
