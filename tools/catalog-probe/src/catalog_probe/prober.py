from __future__ import annotations

import html
import re
import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
from urllib.parse import parse_qs, urljoin, urlsplit, urlunsplit

import httpx

from catalog_probe.models import CameraEntry, StreamDescriptor, StreamTransportType

_RUTUBE_LIVE_RE = re.compile(
    r"^https://bl\.rutube\.ru/livestream/(?P<video_id>[0-9a-f]{32})/index\.m3u8"
)
_RUTUBE_PAGE_RE = re.compile(r"rutube\.ru/(?:video|live)/(?P<video_id>[0-9a-f]{32})")
_YOUTUBE_ID_RE = re.compile(r"^[A-Za-z0-9_-]{11}$")
_YOUTUBE_CHANNEL_LIVE_PATH_RE = re.compile(
    r"^/(?:channel/UC[A-Za-z0-9_-]{22}|@[A-Za-z0-9_.-]+)/live/?$"
)
_YOUTUBE_CHANNEL_STREAMS_PATH_RE = re.compile(
    r"^/(?:channel/UC[A-Za-z0-9_-]{22}|@[A-Za-z0-9_.-]+)/streams/?$"
)
_YOUTUBE_LOCKUP_VID_RE = re.compile(
    r'"(?:watchEndpoint":\{"videoId|addedVideoId|contentId|animationActivationTargetId)'
    r'":"(?P<vid>[A-Za-z0-9_-]{11})"'
)
_UNICODE_ESCAPE_RE = re.compile(r"\\u([0-9a-fA-F]{4})")
_YOUTUBE_CANONICAL_WATCH_RE = re.compile(
    r'rel="canonical"\s+href="https://www\.youtube\.com/watch\?v=(?P<vid>[A-Za-z0-9_-]{11})"'
)
_IPCAMLIVE_ALIAS_RE = re.compile(
    r"ipcamlive\.com/(?:player/player\.php\?alias=)?(?P<alias>[A-Za-z0-9_-]+)"
)
_ROSTELECOM_HLS_RE = re.compile(
    r"^https://live-[a-z0-9-]+\.camera\.rt\.ru/public/master\.m3u8\?sid=(?P<uid>[0-9a-f-]{36})$"
)

_YOUTUBE_ANDROID_CLIENT_VERSION = "21.26.364"
_YOUTUBE_ANDROID_UA = (
    f"com.google.android.youtube/{_YOUTUBE_ANDROID_CLIENT_VERSION} (Linux; U; Android 11) gzip"
)
_YOUTUBE_INNERTUBE_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
_YOUTUBE_INNERTUBE_HEADERS: dict[str, str] = {
    "User-Agent": _YOUTUBE_ANDROID_UA,
    "X-YouTube-Client-Name": "3",
    "X-YouTube-Client-Version": _YOUTUBE_ANDROID_CLIENT_VERSION,
    "Origin": "https://www.youtube.com",
    "Accept-Language": "en-US,en;q=0.9",
}
_YOUTUBE_WEB_CONSENT_HEADERS: dict[str, str] = {
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    ),
    "Cookie": (
        "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVy"
        "XzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnPpwY"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}


def build_youtube_innertube_payload(video_id: str) -> dict[str, object]:
    return {
        "videoId": video_id,
        "context": {
            "client": {
                "clientName": "ANDROID",
                "clientVersion": _YOUTUBE_ANDROID_CLIENT_VERSION,
                "androidSdkVersion": 30,
                "userAgent": _YOUTUBE_ANDROID_UA,
                "osName": "Android",
                "osVersion": "11",
                "hl": "en",
                "gl": "US",
            }
        },
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


def extract_youtube_channel_live_url(url: str) -> str | None:
    trimmed = url.strip()
    parts = urlsplit(trimmed)
    host = parts.netloc.lower()
    if not host.endswith("youtube.com"):
        return None
    if not _YOUTUBE_CHANNEL_LIVE_PATH_RE.match(parts.path):
        return None
    normalized_path = parts.path.rstrip("/")
    return f"https://www.youtube.com{normalized_path}"


def extract_youtube_streams_match_spec(url: str) -> tuple[str, str] | None:
    trimmed = url.strip()
    parts = urlsplit(trimmed)
    host = parts.netloc.lower()
    if not host.endswith("youtube.com"):
        return None
    if not _YOUTUBE_CHANNEL_STREAMS_PATH_RE.match(parts.path):
        return None
    qs = parse_qs(parts.query)
    match_vals = qs.get("match", [])
    if not match_vals or not match_vals[0].strip():
        return None
    normalized_path = parts.path.rstrip("/")
    return f"https://www.youtube.com{normalized_path}", match_vals[0].strip()


def extract_live_videos_from_streams_html(html_text: str) -> list[tuple[str, str]]:
    discovered: list[tuple[str, str]] = []
    seen: set[str] = set()

    # 1. Modern YouTube Desktop Web schema (lockupViewModel)
    for raw_chunk in html_text.split('"lockupViewModel":{')[1:]:
        window = raw_chunk[:12_000]
        if '"THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE"' not in window:
            continue
        vid_match = _YOUTUBE_LOCKUP_VID_RE.search(window)
        if vid_match is None:
            continue
        vid = vid_match.group("vid")
        if not _YOUTUBE_ID_RE.match(vid) or vid in seen:
            continue
        title_marker = '"lockupMetadataViewModel":{"title":{"content":"'
        t_idx = window.find(title_marker)
        if t_idx >= 0:
            start = t_idx + len(title_marker)
            end = window.find('"}', start)
            title_slice = window[start:end] if end > start else window[start : start + 500]
        else:
            title_slice = window[:700]
        decoded_title = _UNICODE_ESCAPE_RE.sub(
            lambda m: chr(int(m.group(1), 16)),
            title_slice,
        )
        seen.add(vid)
        discovered.append((vid, decoded_title))

    # 2. Legacy / fallback YouTube Web schema (videoRenderer)
    blocks = html_text.split('"videoRenderer":{"videoId":"')
    for chunk in blocks[1:]:
        window = chunk[:5_000]
        vid = window[:11]
        if not _YOUTUBE_ID_RE.match(vid) or vid in seen:
            continue
        if '"style":"LIVE"' not in window and '"BADGE_STYLE_TYPE_LIVE_NOW"' not in window:
            continue
        title_idx = window.find('"title":')
        title_slice = window[title_idx : title_idx + 700] if title_idx >= 0 else window[:700]
        decoded_title = _UNICODE_ESCAPE_RE.sub(
            lambda m: chr(int(m.group(1), 16)),
            title_slice,
        )
        seen.add(vid)
        discovered.append((vid, decoded_title))
    return discovered


def _resolve_youtube_streams_match_id(
    client: httpx.Client,
    streams_url: str,
    match_keyword: str,
    streams_cache: dict[str, list[tuple[str, str]]] | None = None,
) -> str | None:
    if streams_cache is not None and streams_url in streams_cache:
        live_items = streams_cache[streams_url]
    else:
        resp = client.get(streams_url, headers=_YOUTUBE_WEB_CONSENT_HEADERS)
        if not (200 <= resp.status_code < 300):
            if streams_cache is not None:
                streams_cache[streams_url] = []
            return None
        live_items = extract_live_videos_from_streams_html(resp.text)
        if streams_cache is not None:
            streams_cache[streams_url] = live_items

    needles = [part.strip().lower() for part in match_keyword.split("|") if part.strip()]
    if not needles:
        return None
    for vid, title_text in live_items:
        lowered_title = title_text.lower()
        if any(needle in lowered_title for needle in needles):
            return vid
    return None


def _resolve_youtube_canonical_live_id(
    client: httpx.Client,
    channel_live_url: str,
) -> str | None:
    resp = client.get(channel_live_url, headers=_YOUTUBE_WEB_CONSENT_HEADERS)
    if not (200 <= resp.status_code < 300):
        return None
    if '"isLive":true' not in resp.text:
        return None
    match = _YOUTUBE_CANONICAL_WATCH_RE.search(resp.text)
    return match.group("vid") if match is not None else None


def _probe_youtube_video_id(
    client: httpx.Client,
    video_id: str,
    stream_url: str,
) -> ProbeResult:
    resp = client.post(
        _YOUTUBE_INNERTUBE_PLAYER_URL,
        headers=_YOUTUBE_INNERTUBE_HEADERS,
        json=build_youtube_innertube_payload(video_id),
    )
    if not (200 <= resp.status_code < 300):
        return ProbeResult(
            url=stream_url,
            reachable=False,
            status_code=resp.status_code,
            error=f"HTTP {resp.status_code}",
        )
    payload = resp.json()
    playability = payload.get("playabilityStatus", {})
    status = playability.get("status")
    video_details = payload.get("videoDetails", {})
    is_live_ok = video_details.get("isLive", True) is True
    hls_url = payload.get("streamingData", {}).get("hlsManifestUrl")
    has_playable_hls = (
        isinstance(hls_url, str)
        and hls_url.startswith("https://")
        and ".m3u8" in hls_url
        and "/n/" not in hls_url
    )
    ok = status == "OK" and is_live_ok and has_playable_hls
    err = None
    if not ok:
        reason = playability.get("reason") or ("not live" if not is_live_ok else "no HLS")
        err = f"status={status} ({reason})"
    return ProbeResult(
        url=stream_url,
        reachable=ok,
        status_code=resp.status_code,
        error=err,
    )


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


def refresh_youtube_live_urls(
    cameras: list[CameraEntry],
    timeout_seconds: float = 6.0,
    transport: httpx.BaseTransport | None = None,
) -> list[CameraEntry]:
    channel_live_cache: dict[str, str | None] = {}
    streams_tab_cache: dict[str, list[tuple[str, str]]] = {}
    vid_health_cache: dict[str, bool] = {}

    with httpx.Client(
        http2=False,
        timeout=timeout_seconds,
        follow_redirects=True,
        transport=transport,
    ) as client:

        def _is_vid_healthy(vid: str, watch_url: str) -> bool:
            if vid not in vid_health_cache:
                try:
                    res = _probe_youtube_video_id(client, vid, watch_url)
                    vid_health_cache[vid] = res.reachable
                except (httpx.HTTPError, ValueError, KeyError):
                    vid_health_cache[vid] = False
            return vid_health_cache[vid]

        def _resolve_channel_descriptor(url: str) -> str | None:
            channel_live_url = extract_youtube_channel_live_url(url)
            if channel_live_url is not None:
                if channel_live_url not in channel_live_cache:
                    try:
                        channel_live_cache[channel_live_url] = _resolve_youtube_canonical_live_id(
                            client, channel_live_url
                        )
                    except (httpx.HTTPError, ValueError, KeyError):
                        channel_live_cache[channel_live_url] = None
                return channel_live_cache[channel_live_url]

            match_spec = extract_youtube_streams_match_spec(url)
            if match_spec is not None:
                streams_url, keyword = match_spec
                try:
                    return _resolve_youtube_streams_match_id(
                        client,
                        streams_url,
                        keyword,
                        streams_tab_cache,
                    )
                except (httpx.HTTPError, ValueError, KeyError):
                    return None
            return None

        updated_cameras: list[CameraEntry] = []
        for camera in cameras:
            if not camera.streams:
                updated_cameras.append(camera)
                continue
            primary = camera.streams[0]
            if primary.type != StreamTransportType.YOUTUBE_LIVE:
                updated_cameras.append(camera)
                continue

            primary_vid = extract_youtube_video_id(primary.url)
            if primary_vid is None:
                # Primary descriptor is a dynamic /live or /streams?match=... channel URL
                promoted_vid: str | None = None
                for s in camera.streams:
                    if s.type != StreamTransportType.YOUTUBE_LIVE:
                        continue
                    cand_vid = _resolve_channel_descriptor(s.url) or extract_youtube_video_id(s.url)
                    if cand_vid and _is_vid_healthy(
                        cand_vid,
                        f"https://www.youtube.com/watch?v={cand_vid}",
                    ):
                        promoted_vid = cand_vid
                        break

                if promoted_vid is None:
                    updated_cameras.append(camera)
                    continue

                watch_desc = StreamDescriptor(
                    type=StreamTransportType.YOUTUBE_LIVE,
                    url=f"https://www.youtube.com/watch?v={promoted_vid}",
                )
                dedup_tail = tuple(s for s in camera.streams if s.url != watch_desc.url)
                new_poster = camera.poster_url
                if new_poster.startswith(("https://i.ytimg.com/", "https://www.youtube.com/")):
                    new_poster = f"https://i.ytimg.com/vi/{promoted_vid}/hqdefault.jpg"
                updated_cameras.append(
                    replace(
                        camera,
                        poster_url=new_poster,
                        streams=(watch_desc, *dedup_tail),
                    )
                )
                continue

            if len(camera.streams) < 2:
                updated_cameras.append(camera)
                continue

            has_channel_fallback = any(
                s.type == StreamTransportType.YOUTUBE_LIVE
                and (
                    extract_youtube_channel_live_url(s.url) is not None
                    or extract_youtube_streams_match_spec(s.url) is not None
                )
                for s in camera.streams[1:]
            )
            if not has_channel_fallback:
                updated_cameras.append(camera)
                continue

            if _is_vid_healthy(primary_vid, primary.url):
                updated_cameras.append(camera)
                continue

            fresh_vid: str | None = None
            for s in camera.streams[1:]:
                if s.type != StreamTransportType.YOUTUBE_LIVE:
                    continue
                cand_vid = _resolve_channel_descriptor(s.url)
                if (
                    cand_vid is not None
                    and cand_vid != primary_vid
                    and _is_vid_healthy(
                        cand_vid,
                        f"https://www.youtube.com/watch?v={cand_vid}",
                    )
                ):
                    fresh_vid = cand_vid
                    break

            if fresh_vid is None:
                updated_cameras.append(camera)
                continue

            new_primary = replace(primary, url=f"https://www.youtube.com/watch?v={fresh_vid}")
            new_streams = (new_primary, *camera.streams[1:])
            new_poster = camera.poster_url.replace(
                f"https://i.ytimg.com/vi/{primary_vid}/",
                f"https://i.ytimg.com/vi/{fresh_vid}/",
            )
            updated_cameras.append(replace(camera, poster_url=new_poster, streams=new_streams))
    return updated_cameras


def _probe_single_stream(
    client: httpx.Client,
    stream: StreamDescriptor,
    youtube_sem: threading.Semaphore,
) -> ProbeResult:
    try:
        if stream.type == StreamTransportType.YOUTUBE_LIVE:
            video_id = extract_youtube_video_id(stream.url)
            if video_id is not None:
                with youtube_sem:
                    return _probe_youtube_video_id(client, video_id, stream.url)
            channel_live_url = extract_youtube_channel_live_url(stream.url)
            if channel_live_url is not None:
                with youtube_sem:
                    resolved_id = _resolve_youtube_canonical_live_id(client, channel_live_url)
                    if resolved_id is None:
                        return ProbeResult(
                            stream.url,
                            False,
                            200,
                            "No active live broadcast on channel",
                        )
                    return _probe_youtube_video_id(client, resolved_id, stream.url)
            match_spec = extract_youtube_streams_match_spec(stream.url)
            if match_spec is not None:
                streams_url, keyword = match_spec
                with youtube_sem:
                    resolved_id = _resolve_youtube_streams_match_id(client, streams_url, keyword)
                    if resolved_id is None:
                        return ProbeResult(
                            stream.url,
                            False,
                            200,
                            f"No active live broadcast matching '{keyword}'",
                        )
                    return _probe_youtube_video_id(client, resolved_id, stream.url)
            return ProbeResult(stream.url, False, None, "Invalid YouTube video ID")

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
        if stream.type == StreamTransportType.HLS:
            return _probe_hls_stream(client, stream.url, req_headers)

        if "imgproxy.windy.com" in stream.url:
            with client.stream("GET", stream.url, headers=req_headers) as get_resp:
                status_code = get_resp.status_code
                ctype = get_resp.headers.get("content-type", "").lower()
                ok = 200 <= status_code < 300 and ctype.startswith("image/")
            return ProbeResult(url=stream.url, reachable=ok, status_code=status_code)

        response = client.head(stream.url, headers=req_headers)
        status_code = response.status_code
        if not (200 <= status_code < 300):
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


def _extract_first_hls_variant_uri(master_body: str) -> str | None:
    expect_variant = False
    for raw in master_body.splitlines():
        line = raw.strip()
        if not line:
            continue
        if line.startswith("#EXT-X-STREAM-INF"):
            expect_variant = True
            continue
        if expect_variant and not line.startswith("#"):
            return line
    return None


def _probe_hls_stream(
    client: httpx.Client,
    stream_url: str,
    headers: dict[str, str],
) -> ProbeResult:
    outer = client.get(stream_url, headers=headers)
    if not (200 <= outer.status_code < 300):
        return ProbeResult(
            url=stream_url,
            reachable=False,
            status_code=outer.status_code,
            error=f"HTTP {outer.status_code}",
        )
    outer_body = outer.text.lstrip()
    if not outer_body.startswith("#EXTM3U"):
        return ProbeResult(
            url=stream_url,
            reachable=False,
            status_code=outer.status_code,
            error="Not an #EXTM3U playlist",
        )
    if "#EXT-X-STREAM-INF" in outer_body:
        variant_uri = _extract_first_hls_variant_uri(outer_body)
        if variant_uri is None:
            return ProbeResult(
                url=stream_url,
                reachable=False,
                status_code=outer.status_code,
                error="Master playlist missing variant URI",
            )
        variant_url = urljoin(str(outer.url), variant_uri)
        variant = client.get(variant_url, headers=headers)
        if not (200 <= variant.status_code < 300):
            return ProbeResult(
                url=stream_url,
                reachable=False,
                status_code=variant.status_code,
                error=f"Variant HTTP {variant.status_code}",
            )
        media_body = variant.text.lstrip()
        if not media_body.startswith("#EXTM3U"):
            return ProbeResult(
                url=stream_url,
                reachable=False,
                status_code=variant.status_code,
                error="Variant not #EXTM3U",
            )
    else:
        media_body = outer_body

    has_segments = any(
        (stripped := raw.strip()) != "" and not stripped.startswith("#")
        for raw in media_body.splitlines()
    )
    if not has_segments:
        return ProbeResult(
            url=stream_url,
            reachable=False,
            status_code=200,
            error="Media playlist contains 0 segments",
        )
    return ProbeResult(url=stream_url, reachable=True, status_code=outer.status_code)


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
