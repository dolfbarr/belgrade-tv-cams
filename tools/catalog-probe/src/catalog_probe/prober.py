from __future__ import annotations

import html
import re
import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
from urllib.parse import parse_qs, urlsplit, urlunsplit

import httpx

from catalog_probe.models import CameraEntry, StreamDescriptor, StreamTransportType

_RUTUBE_LIVE_RE = re.compile(
    r"^https://bl\.rutube\.ru/livestream/(?P<video_id>[0-9a-f]{32})/index\.m3u8"
)
_RUTUBE_PAGE_RE = re.compile(r"rutube\.ru/(?:video|live)/(?P<video_id>[0-9a-f]{32})")
_YOUTUBE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
_IPCAMLIVE_ALIAS_RE = re.compile(
    r"ipcamlive\.com/(?:player/player\.php\?alias=)?(?P<alias>[A-Za-z0-9_-]+)"
)
_ROSTELECOM_HLS_RE = re.compile(
    r"^https://live-[a-z0-9-]+\.camera\.rt\.ru/public/master\.m3u8\?sid=(?P<uid>[0-9a-f-]{36})$"
)

_YOUTUBE_PROBE_HEADERS: dict[str, str] = {
    "User-Agent": (
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) "
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1"
    ),
    "Accept-Language": "en-US,en;q=0.9",
    "Cookie": (
        "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVy"
        "XzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnPpwY"
    ),
}

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


def extract_youtube_video_id(url: str) -> str | None:
    trimmed = url.strip()
    if _YOUTUBE_ID_RE.match(trimmed):
        return trimmed
    parts = urlsplit(trimmed)
    host = parts.netloc.lower()
    if host == "youtu.be":
        candidate = parts.path.strip("/").split("/")[0]
        return candidate if _YOUTUBE_ID_RE.match(candidate) else None
    if host.endswith("youtube.com"):
        qs = parse_qs(parts.query)
        if "v" in qs and qs["v"]:
            candidate = qs["v"][0]
            return candidate if _YOUTUBE_ID_RE.match(candidate) else None
        segments = [seg for seg in parts.path.split("/") if seg]
        if len(segments) >= 2 and segments[0] in ("live", "embed", "shorts"):
            candidate = segments[1]
            return candidate if _YOUTUBE_ID_RE.match(candidate) else None
    return None


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


def refresh_rostelecom_live_urls(
    cameras: list[CameraEntry],
    timeout_seconds: float = 6.0,
    transport: httpx.BaseTransport | None = None,
) -> list[CameraEntry]:
    resolved_by_uid: dict[str, str] = {}
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
                match = _ROSTELECOM_HLS_RE.match(stream.url)
                if match is None:
                    new_streams.append(stream)
                    continue
                uid = match.group("uid")
                if uid not in resolved_by_uid:
                    api_url = f"https://camera.rt.ru/api/v1/vc/cameras/{uid}.json"
                    try:
                        resp = client.get(api_url)
                        if resp.status_code == 200:
                            cam_obj = resp.json().get("camera", {})
                            hls_url = cam_obj.get("hls_url")
                            if cam_obj.get("is_alive") and isinstance(hls_url, str) and hls_url:
                                resolved_by_uid[uid] = hls_url
                    except (httpx.HTTPError, ValueError, KeyError):
                        pass
                fresh_url = resolved_by_uid.get(uid)
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


def _probe_single_stream(
    client: httpx.Client,
    stream: StreamDescriptor,
    youtube_sem: threading.Semaphore,
) -> ProbeResult:
    try:
        if stream.type == StreamTransportType.YOUTUBE_LIVE:
            video_id = extract_youtube_video_id(stream.url)
            if video_id is None:
                return ProbeResult(stream.url, False, None, "Invalid YouTube video ID")
            with youtube_sem:
                resp = client.get(
                    f"https://m.youtube.com/watch?v={video_id}",
                    headers=_YOUTUBE_PROBE_HEADERS,
                )
            ok = 200 <= resp.status_code < 300 and '"hlsManifestUrl"' in resp.text
            return ProbeResult(url=stream.url, reachable=ok, status_code=resp.status_code)

        if stream.type == StreamTransportType.RUTUBE_LIVE:
            match = _RUTUBE_PAGE_RE.search(stream.url)
            if match is None:
                return ProbeResult(stream.url, False, None, "Invalid Rutube URL")
            video_id = match.group("video_id")
            api_url = (
                f"https://rutube.ru/api/play/options/{video_id}/"
                "?no_404=true&referer=https%3A%2F%2Frutube.ru"
            )
            resp = client.get(api_url)
            ok = False
            if resp.status_code == 200:
                hls_list = resp.json().get("live_streams", {}).get("hls", [])
                ok = bool(hls_list and isinstance(hls_list[0].get("url"), str))
            return ProbeResult(url=stream.url, reachable=ok, status_code=resp.status_code)

        if stream.type == StreamTransportType.IPCAMLIVE:
            match = _IPCAMLIVE_ALIAS_RE.search(stream.url)
            if match is None:
                return ProbeResult(stream.url, False, None, "Invalid IpCamLive URL")
            alias = match.group("alias")
            resp = client.get(
                f"https://ipcamlive.com/ajax/getcamerastreamstate.php?cameraalias={alias}",
                headers={"Referer": "https://ipcamlive.com/"},
            )
            ok = False
            if resp.status_code == 200:
                details = resp.json().get("details", {})
                ok = str(details.get("streamavailable")) == "1"
            return ProbeResult(url=stream.url, reachable=ok, status_code=resp.status_code)

        req_headers = stream.headers or {}
        if "imgproxy.windy.com" in stream.url:
            with client.stream("GET", stream.url, headers=req_headers) as get_resp:
                status_code = get_resp.status_code
                ctype = get_resp.headers.get("content-type", "").lower()
                ok = 200 <= status_code < 300 and ctype.startswith("image/")
            return ProbeResult(url=stream.url, reachable=ok, status_code=status_code)

        response = client.head(stream.url, headers=req_headers)
        status_code = response.status_code
        if not (200 <= status_code < 300):
            # CDNs such as BunnyCDN reject HEAD on HLS; fall back to streaming GET
            with client.stream("GET", stream.url, headers=req_headers) as get_resp:
                status_code = get_resp.status_code
        ok = 200 <= status_code < 300
        return ProbeResult(url=stream.url, reachable=ok, status_code=status_code)
    except (httpx.HTTPError, ValueError, KeyError) as exc:
        return ProbeResult(
            url=stream.url,
            reachable=False,
            status_code=None,
            error=str(exc),
        )


def probe_cameras_live(
    cameras: list[CameraEntry],
    timeout_seconds: float = 6.0,
    transport: httpx.BaseTransport | None = None,
) -> dict[str, ProbeResult]:
    unique_streams: dict[str, StreamDescriptor] = {}
    for camera in cameras:
        for stream in camera.streams:
            if stream.url not in unique_streams:
                unique_streams[stream.url] = stream

    youtube_sem = threading.Semaphore(2)
    # Use HTTP/1.1 to avoid RFC 7540 connection-header errors on legacy CCTV/Rutube nginx nodes
    with (
        httpx.Client(
            http2=False,
            timeout=timeout_seconds,
            follow_redirects=True,
            transport=transport,
        ) as client,
        ThreadPoolExecutor(max_workers=10) as pool,
    ):
        futures = {
            url: pool.submit(_probe_single_stream, client, descriptor, youtube_sem)
            for url, descriptor in unique_streams.items()
        }
        return {url: future.result() for url, future in futures.items()}
