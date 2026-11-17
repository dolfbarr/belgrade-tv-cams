from __future__ import annotations

import html
import re
from dataclasses import dataclass, replace
from urllib.parse import urlsplit, urlunsplit

import httpx

from catalog_probe.models import CameraEntry

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


def probe_cameras_live(
    cameras: list[CameraEntry],
    timeout_seconds: float = 6.0,
) -> dict[str, ProbeResult]:
    results: dict[str, ProbeResult] = {}
    with httpx.Client(http2=True, timeout=timeout_seconds, follow_redirects=True) as client:
        for camera in cameras:
            for stream in camera.streams:
                if stream.url in results:
                    continue
                try:
                    response = client.head(stream.url, headers=stream.headers or {})
                    ok = 200 <= response.status_code < 300
                    results[stream.url] = ProbeResult(
                        url=stream.url,
                        reachable=ok,
                        status_code=response.status_code,
                    )
                except httpx.HTTPError as exc:
                    results[stream.url] = ProbeResult(
                        url=stream.url,
                        reachable=False,
                        status_code=None,
                        error=str(exc),
                    )
    return results
