from __future__ import annotations

import html
import re
from dataclasses import dataclass, replace
from urllib.parse import urlsplit, urlunsplit

import httpx

from catalog_probe.models import CameraEntry, StreamDescriptor

_RUTUBE_LIVE_RE = re.compile(
    r"^https://bl\.rutube\.ru/livestream/(?P<video_id>[0-9a-f]{32})/index\.m3u8"
)

_NAXI_HQ_RE = re.compile(
    r'<li[^>]*data-id="camera-(?P<id>\d+)"[^>]*>.*?'
    r'<source[^>]*src="(?P<url>https://[^"]+\.m3u8)"[^>]*>.*?'
    r'<h2\s+class="camera__title">(?P<title>[^<]+)</h2>',
    re.DOTALL,
)

_NAXI_LQ_RE = re.compile(
    r'<li[^>]*data-id="camera-(?P<id>\d+)"[^>]*>.*?'
    r'data-url="(?P<url>https://[^"]+\.jpg[^"]*)"[^>]*>.*?'
    r'<h2\s+class="camera__title">(?P<title>[^<]+)</h2>',
    re.DOTALL,
)

_TOLL4ALL_RE = re.compile(
    r'<div\s+class="cam-item"\s+poster="(?P<poster>https://[^"]+)"\s+'
    r'src="(?P<src>https://[^"]+\.m3u8)"[^>]*>\s*'
    r"<span>(?P<title>[^<]+)</span>",
    re.DOTALL,
)


@dataclass(frozen=True, slots=True)
class ProbeResult:
    url: str
    reachable: bool
    status_code: int | None
    error: str | None = None


def normalize_snapshot_url(url: str) -> str:
    parts = urlsplit(url.strip())
    return urlunsplit((parts.scheme, parts.netloc, parts.path, "", ""))


def extract_naxi_hq_streams(page_html: str) -> dict[str, tuple[str, str]]:
    discovered: dict[str, tuple[str, str]] = {}
    for match in _NAXI_HQ_RE.finditer(page_html):
        cam_id = match.group("id").strip()
        title = html.unescape(match.group("title")).strip()
        url = match.group("url").strip()
        discovered[cam_id] = (title, url)
    return discovered


def extract_naxi_lq_snapshots(page_html: str) -> dict[str, tuple[str, str]]:
    discovered: dict[str, tuple[str, str]] = {}
    for match in _NAXI_LQ_RE.finditer(page_html):
        cam_id = match.group("id").strip()
        title = html.unescape(match.group("title")).strip()
        url = normalize_snapshot_url(match.group("url"))
        discovered[cam_id] = (title, url)
    return discovered


def extract_toll4all_streams(page_html: str) -> list[tuple[str, str, str]]:
    discovered: list[tuple[str, str, str]] = []
    for match in _TOLL4ALL_RE.finditer(page_html):
        title = html.unescape(match.group("title")).strip()
        src = match.group("src").strip()
        poster = normalize_snapshot_url(match.group("poster"))
        discovered.append((title, src, poster))
    return discovered


def apply_probe_ordering(
    camera: CameraEntry,
    results: dict[str, ProbeResult],
) -> CameraEntry:
    # Stable partition: reachable streams first, unreachable fallbacks retained in original order
    healthy = [
        s for s in camera.streams if results.get(s.url, ProbeResult(s.url, True, 200)).reachable
    ]
    degraded = [
        s for s in camera.streams if not results.get(s.url, ProbeResult(s.url, True, 200)).reachable
    ]
    ordered = tuple(healthy + degraded)
    if ordered == camera.streams:
        return camera
    return replace(camera, streams=ordered)


def refresh_rutube_live_urls(
    cameras: list[CameraEntry],
    timeout_seconds: float = 6.0,
    transport: httpx.BaseTransport | None = None,
) -> list[CameraEntry]:
    resolved_by_id: dict[str, str] = {}
    with httpx.Client(
        http2=False,
        timeout=timeout_seconds,
        follow_redirects=True,
        transport=transport,
    ) as client:
        updated_cameras: list[CameraEntry] = []
        for camera in cameras:
            changed = False
            new_streams: list[StreamDescriptor] = []
            for stream in camera.streams:
                match = _RUTUBE_LIVE_RE.match(stream.url)
                if match is None:
                    new_streams.append(stream)
                    continue
                video_id = match.group("video_id")
                if video_id not in resolved_by_id:
                    api_url = (
                        f"https://rutube.ru/api/play/options/{video_id}/"
                        "?no_404=true&referer=https%3A%2F%2Frutube.ru"
                    )
                    try:
                        resp = client.get(api_url)
                        if resp.status_code == 200:
                            payload = resp.json()
                            hls_list = payload.get("live_streams", {}).get("hls", [])
                            if hls_list and isinstance(hls_list[0].get("url"), str):
                                resolved_by_id[video_id] = hls_list[0]["url"]
                    except (httpx.HTTPError, ValueError, KeyError):
                        pass
                fresh_url = resolved_by_id.get(video_id)
                if fresh_url and fresh_url != stream.url:
                    new_streams.append(replace(stream, url=fresh_url))
                    changed = True
                else:
                    new_streams.append(stream)
            if changed:
                updated_cameras.append(replace(camera, streams=tuple(new_streams)))
            else:
                updated_cameras.append(camera)
    return updated_cameras


def probe_cameras_live(
    cameras: list[CameraEntry],
    timeout_seconds: float = 6.0,
    transport: httpx.BaseTransport | None = None,
) -> dict[str, ProbeResult]:
    results: dict[str, ProbeResult] = {}
    # Use HTTP/1.1 to avoid RFC 7540 connection-header errors on legacy CCTV/Rutube nginx nodes
    with httpx.Client(
        http2=False,
        timeout=timeout_seconds,
        follow_redirects=True,
        transport=transport,
    ) as client:
        for camera in cameras:
            for stream in camera.streams:
                if stream.url in results:
                    continue
                req_headers = stream.headers or {}
                try:
                    response = client.head(stream.url, headers=req_headers)
                    status_code = response.status_code
                    if not (200 <= status_code < 300):
                        # CDNs such as BunnyCDN reject HEAD on HLS; fall back to streaming GET
                        with client.stream("GET", stream.url, headers=req_headers) as get_resp:
                            status_code = get_resp.status_code
                    ok = 200 <= status_code < 300
                    results[stream.url] = ProbeResult(
                        url=stream.url,
                        reachable=ok,
                        status_code=status_code,
                    )
                except httpx.HTTPError as exc:
                    results[stream.url] = ProbeResult(
                        url=stream.url,
                        reachable=False,
                        status_code=None,
                        error=str(exc),
                    )
    return results
