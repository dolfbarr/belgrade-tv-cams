from __future__ import annotations

from dataclasses import replace

from catalog_probe.models import (
    CameraCategory,
    CameraCity,
    CameraCountry,
    CameraEntry,
    GeoCoordinates,
    SerbiaCity,
    StreamDescriptor,
    StreamTransportType,
    VideoQuality,
)

NAXI_HEADERS: dict[str, str] = {
    "Referer": "https://www.naxi.rs/kamere",
}

TOLL4ALL_HEADERS: dict[str, str] = {
    "Referer": "https://kamere.toll4all.com/",
    "Origin": "https://kamere.toll4all.com",
}

OTT_24SEDAM_HEADERS: dict[str, str] = {
    "Referer": "https://kamere.24sedam.rs/",
    "Origin": "https://kamere.24sedam.rs",
    "User-Agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"
    ),
}


def _naxi_camera(
    *,
    cam_id: str,
    name: str,
    streets: tuple[str, ...],
    municipality: str,
    category: CameraCategory,
    direction: str,
    lat: float,
    lon: float,
    hls_slug: str,
    jpg_num: int,
) -> CameraEntry:
    jpg_url = f"https://stream.uzivobeograd.rs/live/cam_{jpg_num}.jpg"
    return CameraEntry(
        id=cam_id,
        name=name,
        city=SerbiaCity.BELGRADE,
        quality=VideoQuality.SD,
        streets=streets,
        municipality=municipality,
        category=category,
        direction=direction,
        poster_url=jpg_url,
        poster_headers=dict(NAXI_HEADERS),
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=(
            StreamDescriptor(
                type=StreamTransportType.HLS,
                url=f"https://streamer.devnet.rs/hls/{hls_slug}.m3u8",
                headers=dict(NAXI_HEADERS),
            ),
            StreamDescriptor(
                type=StreamTransportType.SNAPSHOT_POLL,
                url=jpg_url,
                interval_ms=1500,
                cache_buster_param="_",
                headers=dict(NAXI_HEADERS),
            ),
        ),
    )


def _ott_24sedam_camera(
    *,
    cam_id: str,
    name: str,
    city: SerbiaCity,
    streets: tuple[str, ...],
    municipality: str,
    category: CameraCategory,
    direction: str,
    lat: float,
    lon: float,
    ott_slug: str,
    poster_url: str,
    fallback_jpg_num: int | None = None,
) -> CameraEntry:
    streams: list[StreamDescriptor] = [
        StreamDescriptor(
            type=StreamTransportType.HLS,
            url=f"https://srbija-nadlanu.ott.solutions/srbija-nadlanu/{ott_slug}/playlist.m3u8",
            headers=dict(OTT_24SEDAM_HEADERS),
        ),
    ]
    if fallback_jpg_num is not None:
        streams.append(
            StreamDescriptor(
                type=StreamTransportType.SNAPSHOT_POLL,
                url=f"https://stream.uzivobeograd.rs/live/cam_{fallback_jpg_num}.jpg",
                interval_ms=1500,
                cache_buster_param="_",
                headers=dict(NAXI_HEADERS),
            )
        )
    return CameraEntry(
        id=cam_id,
        name=name,
        city=city,
        quality=VideoQuality.UHD_2K,
        streets=streets,
        municipality=municipality,
        category=category,
        direction=direction,
        poster_url=poster_url,
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=tuple(streams),
    )


def _toll4all_front_camera(
    *,
    cam_id: str,
    name: str,
    city: SerbiaCity,
    streets: tuple[str, ...],
    municipality: str,
    direction: str,
    lat: float,
    lon: float,
    pan_num: int,
    include_hls: bool = True,
) -> CameraEntry:
    base = f"https://cam.bitinfo.co.rs/front_pan_cam{pan_num}"
    jpg_url = f"{base}/index.jpg"
    streams: list[StreamDescriptor] = []
    if include_hls:
        streams.append(
            StreamDescriptor(
                type=StreamTransportType.HLS,
                url=f"{base}/index.m3u8",
                headers=dict(TOLL4ALL_HEADERS),
            )
        )
    streams.append(
        StreamDescriptor(
            type=StreamTransportType.SNAPSHOT_POLL,
            url=jpg_url,
            interval_ms=3000,
            cache_buster_param="_",
            headers=dict(TOLL4ALL_HEADERS),
        )
    )
    return CameraEntry(
        id=cam_id,
        name=name,
        city=city,
        quality=VideoQuality.HD_720P,
        streets=streets,
        municipality=municipality,
        category=CameraCategory.HIGHWAYS,
        direction=direction,
        poster_url=jpg_url,
        poster_headers=dict(TOLL4ALL_HEADERS),
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=tuple(streams),
    )


def _toll4all_side_camera(
    *,
    cam_id: str,
    name: str,
    city: SerbiaCity,
    streets: tuple[str, ...],
    municipality: str,
    direction: str,
    lat: float,
    lon: float,
    side_num: int,
    include_hls: bool = True,
) -> CameraEntry:
    jpg_url = f"https://jpps.bitinfo.co.rs/side_pan_cam{side_num}/index.jpg"
    streams: list[StreamDescriptor] = []
    if include_hls:
        streams.append(
            StreamDescriptor(
                type=StreamTransportType.HLS,
                url=f"https://cam.bitinfo.co.rs/side_pan_cam{side_num}/index.m3u8",
                headers=dict(TOLL4ALL_HEADERS),
            )
        )
    streams.append(
        StreamDescriptor(
            type=StreamTransportType.SNAPSHOT_POLL,
            url=jpg_url,
            interval_ms=3000,
            cache_buster_param="_",
            headers=dict(TOLL4ALL_HEADERS),
        )
    )
    return CameraEntry(
        id=cam_id,
        name=name,
        city=city,
        quality=VideoQuality.HD_720P,
        streets=streets,
        municipality=municipality,
        category=CameraCategory.HIGHWAYS,
        direction=direction,
        poster_url=jpg_url,
        poster_headers=dict(TOLL4ALL_HEADERS),
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=tuple(streams),
    )


def _mup_border_camera(
    *,
    cam_id: str,
    name: str,
    city: SerbiaCity,
    streets: tuple[str, ...],
    municipality: str,
    direction: str,
    lat: float,
    lon: float,
    hls_path: str,
    poster_slug: str,
) -> CameraEntry:
    return CameraEntry(
        id=cam_id,
        name=name,
        city=city,
        quality=VideoQuality.SD,
        streets=streets,
        municipality=municipality,
        category=CameraCategory.HIGHWAYS,
        direction=direction,
        poster_url=f"https://kameresrbije.rs/thumbnails/{poster_slug}.webp",
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=(
            StreamDescriptor(
                type=StreamTransportType.HLS,
                url=f"https://kamere.mup.gov.rs:4443/{hls_path}",
            ),
        ),
    )


def _rutube_live_camera(
    *,
    cam_id: str,
    name: str,
    city: CameraCity,
    timezone: str,
    streets: tuple[str, ...],
    municipality: str,
    category: CameraCategory,
    direction: str,
    lat: float,
    lon: float,
    hls_url: str,
    poster_url: str,
) -> CameraEntry:
    return CameraEntry(
        id=cam_id,
        name=name,
        country=city.default_country,
        city=city,
        quality=VideoQuality.FHD_1080P,
        timezone=timezone,
        streets=streets,
        municipality=municipality,
        category=category,
        direction=direction,
        poster_url=poster_url,
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=(
            StreamDescriptor(
                type=StreamTransportType.HLS,
                url=hls_url,
            ),
        ),
    )


def _windy_snapshot_camera(
    *,
    cam_id: str,
    name: str,
    city: CameraCity,
    quality: VideoQuality,
    timezone: str,
    streets: tuple[str, ...],
    municipality: str,
    category: CameraCategory,
    direction: str,
    lat: float,
    lon: float,
    windy_id: str,
    interval_ms: int = 60000,
) -> CameraEntry:
    snap_url = f"https://imgproxy.windy.com/_/full/plain/current/{windy_id}/original.jpg"
    return CameraEntry(
        id=cam_id,
        name=name,
        country=city.default_country,
        city=city,
        quality=quality,
        timezone=timezone,
        streets=streets,
        municipality=municipality,
        category=category,
        direction=direction,
        poster_url=snap_url,
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=(
            StreamDescriptor(
                type=StreamTransportType.SNAPSHOT_POLL,
                url=snap_url,
                interval_ms=interval_ms,
                cache_buster_param="v",
            ),
        ),
    )


def _madrid_camera(
    *,
    cam_id: str,
    name: str,
    streets: tuple[str, ...],
    municipality: str,
    category: CameraCategory,
    direction: str,
    lat: float,
    lon: float,
    cam_code: str,
) -> CameraEntry:
    snap_url = f"https://informo.madrid.es/cameras/Camara{cam_code}.jpg"
    return CameraEntry(
        id=cam_id,
        name=name,
        country=CameraCountry.SPAIN,
        city=CameraCity.MADRID,
        quality=VideoQuality.HD_720P,
        timezone="Europe/Madrid",
        streets=streets,
        municipality=municipality,
        category=category,
        direction=direction,
        poster_url=snap_url,
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=(
            StreamDescriptor(
                type=StreamTransportType.SNAPSHOT_POLL,
                url=snap_url,
                interval_ms=30000,
                cache_buster_param="v",
            ),
        ),
    )


def _tomsk_snapshot_camera(
    *,
    cam_id: str,
    name: str,
    streets: tuple[str, ...],
    municipality: str,
    category: CameraCategory,
    direction: str,
    lat: float,
    lon: float,
    slug: str,
    quality: VideoQuality = VideoQuality.FHD_1080P,
    windy_fallback_id: str | None = None,
) -> CameraEntry:
    primary_url = f"https://cdn08.vtomske.ru/stream/{slug}.jpg"
    streams: list[StreamDescriptor] = [
        StreamDescriptor(
            type=StreamTransportType.SNAPSHOT_POLL,
            url=primary_url,
            interval_ms=10000,
            cache_buster_param="_",
        ),
    ]
    if windy_fallback_id is not None:
        streams.append(
            StreamDescriptor(
                type=StreamTransportType.SNAPSHOT_POLL,
                url=(
                    f"https://imgproxy.windy.com/_/full/plain/current/{windy_fallback_id}"
                    "/original.jpg"
                ),
                interval_ms=60000,
                cache_buster_param="v",
            )
        )
    return CameraEntry(
        id=cam_id,
        name=name,
        country=CameraCountry.RUSSIA,
        city=CameraCity.TOMSK,
        quality=quality,
        timezone="Asia/Tomsk",
        streets=streets,
        municipality=municipality,
        category=category,
        direction=direction,
        poster_url=primary_url,
        coordinates=GeoCoordinates(lat=lat, lon=lon),
        streams=tuple(streams),
    )


def build_curated_cameras() -> list[CameraEntry]:
    country_order: dict[CameraCountry, int] = {
        CameraCountry.SERBIA: 0,
        CameraCountry.RUSSIA: 1,
        CameraCountry.SPAIN: 2,
        CameraCountry.ROMANIA: 3,
    }
    city_order: dict[CameraCity, int] = {
        CameraCity.BELGRADE: 0,
        CameraCity.ZEMUN: 1,
        CameraCity.NOVI_SAD: 2,
        CameraCity.SUBOTICA: 3,
        CameraCity.NIS: 4,
        CameraCity.SAINT_PETERSBURG: 5,
        CameraCity.ROSTOV_ON_DON: 6,
        CameraCity.TOMSK: 7,
        CameraCity.BARCELONA: 8,
        CameraCity.MADRID: 9,
        CameraCity.BUCHAREST: 10,
    }
    raw_cameras = [
        # =========================================================================
        # BELGRADE (40 cameras: 21 HD [11 2K QHD + 10 720p] + 19 SD)
        # =========================================================================
        _naxi_camera(
            cam_id="bg-brankov-most",
            name="Brankov most — Terazijski tunel",
            streets=("Brankov most", "Brankova", "Terazijski tunel", "Pop Lukina"),
            municipality="Stari Grad / Novi Beograd",
            category=CameraCategory.BRIDGES,
            direction="Pogled duž Brankove ulice ka Novom Beogradu i ulazu u Terazijski tunel",
            lat=44.8149,
            lon=20.4533,
            hls_slug="brankov",
            jpg_num=1,
        ),
        _naxi_camera(
            cam_id="bg-gazela",
            name="Most Gazela — Galerija & Beograd na vodi",
            streets=(
                "Most Gazela",
                "TC Galerija Belgrade",
                "Beograd na vodi",
                "Autoput E-75",
            ),
            municipality="Savski Venac / Novi Beograd",
            category=CameraCategory.BRIDGES,
            direction=(
                "Most Gazela preko Save sa direktnim pogledom na TC Galerija i Beograd na vodi"
            ),
            lat=44.8019,
            lon=20.4408,
            hls_slug="gazela",
            jpg_num=2,
        ),
        _naxi_camera(
            cam_id="bg-most-na-adi",
            name="Most na Adi — Novi Beograd",
            streets=("Most na Adi", "Bulevar Milutina Milankovića", "Jurija Gagarina"),
            municipality="Novi Beograd",
            category=CameraCategory.BRIDGES,
            direction="Severni prilaz Mostu na Adi sa novobeogradske petlje",
            lat=44.8054,
            lon=20.4152,
            hls_slug="ada",
            jpg_num=4,
        ),
        _ott_24sedam_camera(
            cam_id="bg-most-na-adi-topcider",
            name="Most na Adi — Radnička (Ada Mall) & Topčider",
            city=SerbiaCity.BELGRADE,
            streets=(
                "Most na Adi",
                "Radnička (Ada Mall)",
                "Bulevar vojvode Mišića",
                "Topčiderski park",
            ),
            municipality="Savski Venac / Čukarica",
            category=CameraCategory.BRIDGES,
            direction=(
                "2K QHD pogled na isključenje sa Mosta na Adi ka Radničkoj (Ada Mall), "
                "Topčideru i Hipodromu"
            ),
            lat=44.7891,
            lon=20.4294,
            ott_slug="adamost-mkonak",
            poster_url="https://kamere.24sedam.rs/js/cameras/adamost-mkonak.webp",
            fallback_jpg_num=4,
        ),
        _naxi_camera(
            cam_id="bg-pancevacki-most",
            name="Pančevački most",
            streets=("Pančevački most", "Bulevar despota Stefana", "Dragoslava Srejovića"),
            municipality="Palilula",
            category=CameraCategory.BRIDGES,
            direction="Gradski prilaz i uključenje na Pančevački most preko Dunava",
            lat=44.8229,
            lon=20.4856,
            hls_slug="pancevac",
            jpg_num=7,
        ),
        _naxi_camera(
            cam_id="bg-plavi-most",
            name="Plavi most — Autoput",
            streets=("Vojislava Ilića", "Autoput E-75", "Mokroluška"),
            municipality="Voždovac / Zvezdara",
            category=CameraCategory.BRIDGES,
            direction="Petlja Plavi most iznad autoputa Beograd–Niš",
            lat=44.7824,
            lon=20.5021,
            hls_slug="plavimost",
            jpg_num=14,
        ),
        # Belgrade Squares & Major Hubs (11)
        _naxi_camera(
            cam_id="bg-slavija",
            name="Trg Slavija",
            streets=("Trg Slavija", "Kralja Milana", "Beogradska", "Nemanjina", "Makenzijeva"),
            municipality="Vračar / Savski Venac",
            category=CameraCategory.SQUARES,
            direction="Kružni tok na Trgu Slavija i prilazne gradske arterije",
            lat=44.8025,
            lon=20.4664,
            hls_slug="slavija",
            jpg_num=8,
        ),
        _ott_24sedam_camera(
            cam_id="bg-kralja-milana-london",
            name="Kralja Milana — London",
            city=SerbiaCity.BELGRADE,
            streets=("Kralja Milana", "Kneza Miloša", "Dobrinjska"),
            municipality="Stari Grad / Vračar",
            category=CameraCategory.SQUARES,
            direction=(
                "2K QHD pogled duž Ulice kralja Milana od Londona ka Slaviji i Hramu Svetog Save"
            ),
            lat=44.8069,
            lon=20.4638,
            ott_slug="london-2",
            poster_url="https://kamere.24sedam.rs/js/cameras/london-2.webp",
        ),
        _ott_24sedam_camera(
            cam_id="bg-kneza-milosa-london",
            name="Kneza Miloša — London",
            city=SerbiaCity.BELGRADE,
            streets=("Kneza Miloša", "Kraljice Natalije", "Masarikova"),
            municipality="Savski Venac / Stari Grad",
            category=CameraCategory.SQUARES,
            direction="2K QHD pogled na raskrsnicu Kneza Miloša, Kraljice Natalije i Masarikove",
            lat=44.8065,
            lon=20.4631,
            ott_slug="london-1",
            poster_url="https://kamere.24sedam.rs/js/cameras/london-1.webp",
        ),
        _naxi_camera(
            cam_id="bg-autokomanda",
            name="Autokomanda — Kružni tok",
            streets=("Bulevar oslobođenja", "Vojvode Stepe", "Dr Milutina Ivkovića"),
            municipality="Voždovac / Savski Venac",
            category=CameraCategory.SQUARES,
            direction="Kružni tok Autokomanda ka Voždovcu i stadionu Rajko Mitić",
            lat=44.7894,
            lon=20.4648,
            hls_slug="autokomanda",
            jpg_num=3,
        ),
        _ott_24sedam_camera(
            cam_id="bg-autokomanda-frans",
            name="Autokomanda — Petlja Franš",
            city=SerbiaCity.BELGRADE,
            streets=(
                "Bulevar Franše d'Eperea",
                "Bulevar oslobođenja",
                "Južni bulevar",
                "Ustanička",
            ),
            municipality="Savski Venac / Voždovac",
            category=CameraCategory.SQUARES,
            direction="2K QHD pogled na petlju kod Franša, ulaz u Južni bulevar i Ustaničku",
            lat=44.7908,
            lon=20.4671,
            ott_slug="autokomanda",
            poster_url="https://kamere.24sedam.rs/js/cameras/autokomanda-petlja.webp",
            fallback_jpg_num=3,
        ),
        _naxi_camera(
            cam_id="bg-trg-republike",
            name="Trg Republike",
            streets=("Trg Republike", "Kolarčeva", "Francuska", "Vase Čarapića"),
            municipality="Stari Grad",
            category=CameraCategory.SQUARES,
            direction="Centralni gradski trg ispred Narodnog muzeja i Narodnog pozorišta",
            lat=44.8162,
            lon=20.4603,
            hls_slug="trgrepublike",
            jpg_num=18,
        ),
        _naxi_camera(
            cam_id="bg-trg-nikole-pasica",
            name="Trg Nikole Pašića",
            streets=("Trg Nikole Pašića", "Dečanska", "Bulevar kralja Aleksandra"),
            municipality="Stari Grad",
            category=CameraCategory.SQUARES,
            direction="Saobraćajnica duž Trga Nikole Pašića ka Domu Narodne skupštine",
            lat=44.8124,
            lon=20.4628,
            hls_slug="trgnikole",
            jpg_num=20,
        ),
        _naxi_camera(
            cam_id="bg-savski-trg",
            name="Savski trg — Beograd na vodi & Stefan Nemanja",
            streets=("Savski trg", "Beograd na vodi", "Nemanjina", "Karađorđeva"),
            municipality="Savski Venac",
            category=CameraCategory.SQUARES,
            direction=(
                "Savski trg, spomenik Stefanu Nemanji i ulaz u Beograd na vodi "
                "kod stare Železničke stanice"
            ),
            lat=44.8082,
            lon=20.4561,
            hls_slug="zeleznicka",
            jpg_num=19,
        ),
        _naxi_camera(
            cam_id="bg-bogoslovija",
            name="Bogoslovija",
            streets=("Mije Kovačevića", "Dragoslava Srejovića", "Severni bulevar"),
            municipality="Palilula",
            category=CameraCategory.SQUARES,
            direction="Kružni tok kod Bogoslovije ka Pančevačkom mostu i Zvezdari",
            lat=44.8152,
            lon=20.4918,
            hls_slug="bogoslovija",
            jpg_num=5,
        ),
        _naxi_camera(
            cam_id="bg-kruzni-tok-nbg",
            name="Kružni tok Novi Beograd",
            streets=("Bulevar Mihajla Pupina", "Omladinskih brigada", "Pariske komune"),
            municipality="Novi Beograd",
            category=CameraCategory.SQUARES,
            direction="Kružni tok kod Opštine Novi Beograd",
            lat=44.8206,
            lon=20.4139,
            hls_slug="opstinanbg",
            jpg_num=21,
        ),
        _naxi_camera(
            cam_id="bg-vukov-spomenik",
            name="Vukov spomenik",
            streets=("Bulevar kralja Aleksandra", "Ruzveltova", "Kraljice Marije"),
            municipality="Zvezdara / Palilula / Vračar",
            category=CameraCategory.SQUARES,
            direction="Raskrsnica kod Vukovog spomenika i Elektrotehničkog fakulteta",
            lat=44.8051,
            lon=20.4774,
            hls_slug="vuk",
            jpg_num=6,
        ),
        # Belgrade Boulevards & Urban Arteries (14)
        _naxi_camera(
            cam_id="bg-bulevar-mihajla-pupina",
            name="TC Ušće & Bulevar Mihajla Pupina",
            streets=(
                "TC Ušće",
                "Poslovni centar Ušće",
                "Bulevar Mihajla Pupina",
                "Palata Srbija",
            ),
            municipality="Novi Beograd",
            category=CameraCategory.BOULEVARDS,
            direction="Pogled pored TC Ušće, kula Ušće i Palate Srbija ka Brankovom mostu",
            lat=44.8178,
            lon=20.4319,
            hls_slug="pupina",
            jpg_num=9,
        ),
        _ott_24sedam_camera(
            cam_id="bg-sava-centar-usce",
            name="Sava Centar & Prilaz Ušću — Milentija Popovića",
            city=SerbiaCity.BELGRADE,
            streets=(
                "Bulevar Milentija Popovića",
                "Prilaz TC Ušće",
                "Sava Centar",
                "Blok 19",
            ),
            municipality="Novi Beograd",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "2K QHD pogled duž Milentija Popovića pored Sava Centra ka TC Ušće i Šest kaplara"
            ),
            lat=44.8089,
            lon=20.4314,
            ott_slug="sc-usce",
            poster_url="https://kamere.24sedam.rs/js/cameras/sc-usce.webp",
        ),
        _naxi_camera(
            cam_id="bg-nemanjina",
            name="Nemanjina ulica",
            streets=("Nemanjina", "Kneza Miloša", "Sarajevska"),
            municipality="Savski Venac",
            category=CameraCategory.BOULEVARDS,
            direction="Raskrsnica Nemanjine i Kneza Miloša između Savskog trga i Slavije",
            lat=44.8058,
            lon=20.4607,
            hls_slug="nemanjina",
            jpg_num=11,
        ),
        _naxi_camera(
            cam_id="bg-takovska",
            name="Takovska ulica",
            streets=("Takovska", "Bulevar kralja Aleksandra", "Kneza Miloša", "Svetogorska"),
            municipality="Palilula / Stari Grad",
            category=CameraCategory.BOULEVARDS,
            direction="Potez Takovske ulice od Glavne pošte i Skupštine ka Cvijićevoj",
            lat=44.8117,
            lon=20.4676,
            hls_slug="takovska",
            jpg_num=13,
        ),
        _ott_24sedam_camera(
            cam_id="bg-kraljice-marije-postanska",
            name="Kraljice Marije — Poštanska štedionica",
            city=SerbiaCity.BELGRADE,
            streets=("Kraljice Marije", "Starine Novaka", "27. marta"),
            municipality="Palilula",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "2K QHD pogled kod Gradske uprave i Tašmajdana na Kraljice Marije i Starine Novaka"
            ),
            lat=44.8095,
            lon=20.4732,
            ott_slug="postanska-stedionica",
            poster_url="https://kamere.24sedam.rs/js/cameras/postanska-stedionica.webp",
        ),
        _naxi_camera(
            cam_id="bg-bulevar-despota-stefana",
            name="Bulevar despota Stefana",
            streets=("Bulevar despota Stefana", "Cvijićeva", "Džordža Vašingtona"),
            municipality="Stari Grad / Palilula",
            category=CameraCategory.BOULEVARDS,
            direction="Saobraćajni koridor od centra grada ka Pančevačkom mostu",
            lat=44.8172,
            lon=20.4721,
            hls_slug="despotastefana",
            jpg_num=17,
        ),
        _ott_24sedam_camera(
            cam_id="bg-juzni-bulevar",
            name="Južni bulevar — Vračar",
            city=SerbiaCity.BELGRADE,
            streets=("Južni bulevar", "Maksima Gorkog", "Đerdapska"),
            municipality="Vračar",
            category=CameraCategory.BOULEVARDS,
            direction="2K QHD pogled duž Južnog bulevara od Autokomande ka Vračaru i Crvenom krstu",
            lat=44.7924,
            lon=20.4763,
            ott_slug="juzni_bulevar",
            poster_url="https://kamere.24sedam.rs/js/cameras/juzni-bulevar.webp",
        ),
        _naxi_camera(
            cam_id="bg-jurija-gagarina",
            name="Jurija Gagarina — Blokovi",
            streets=("Jurija Gagarina", "Omladinskih brigada", "Gandijeva"),
            municipality="Novi Beograd",
            category=CameraCategory.BOULEVARDS,
            direction="Novobeogradski blokovi — bulevar sa tramvajskim koridorom",
            lat=44.8038,
            lon=20.3982,
            hls_slug="gagarina",
            jpg_num=12,
        ),
        _ott_24sedam_camera(
            cam_id="bg-jurija-gagarina-belville",
            name="Jurija Gagarina — Delta City & Belville",
            city=SerbiaCity.BELGRADE,
            streets=(
                "Jurija Gagarina",
                "TC Delta City",
                "Antifašističke borbe",
                "Dr Agostina Neta",
            ),
            municipality="Novi Beograd",
            category=CameraCategory.BOULEVARDS,
            direction="2K QHD pogled na Jurija Gagarina kod TC Delta City i naselja Belville",
            lat=44.8052,
            lon=20.4068,
            ott_slug="jur1-gargarina",
            poster_url="https://kameresrbije.rs/thumbnails/beograd-jur1-170.webp",
            fallback_jpg_num=12,
        ),
        _ott_24sedam_camera(
            cam_id="bg-jurija-gagarina-ada",
            name="Jurija Gagarina — Prilaz Mostu na Adi",
            city=SerbiaCity.BELGRADE,
            streets=("Jurija Gagarina", "Marka Hristića", "Novi Beograd stanica"),
            municipality="Novi Beograd",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "2K QHD pogled na uključenje iz Jurija Gagarina ka Mostu na Adi "
                "i novoj autobuskoj stanici"
            ),
            lat=44.8069,
            lon=20.4158,
            ott_slug="jur2-gargarina",
            poster_url="https://kameresrbije.rs/thumbnails/beograd-jur2-171.webp",
            fallback_jpg_num=12,
        ),
        _naxi_camera(
            cam_id="bg-autoput-nbg-genex",
            name="Autoput Novi Beograd — Genex",
            streets=("Autoput E-75", "Bulevar Arsenija Čarnojevića", "Narodnih heroja"),
            municipality="Novi Beograd",
            category=CameraCategory.BOULEVARDS,
            direction="Autoput kroz Novi Beograd kod kule Genex (Zapadna kapija)",
            lat=44.8195,
            lon=20.4044,
            hls_slug="genex",
            jpg_num=10,
        ),
        _ott_24sedam_camera(
            cam_id="bg-rakovicki-bulevar",
            name="Bulevar patrijarha Pavla — Košutnjak",
            city=SerbiaCity.BELGRADE,
            streets=("Bulevar patrijarha Pavla", "Pere Velimirovića", "Stari Košutnjak"),
            municipality="Rakovica",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "2K QHD pogled na Bulevar patrijarha Pavla u Rakovici, "
                "skretanje ka Dedinju i Miljakovcu"
            ),
            lat=44.7591,
            lon=20.4438,
            ott_slug="rakovica",
            poster_url="https://kamere.24sedam.rs/js/cameras/rakovica.webp",
        ),
        _ott_24sedam_camera(
            cam_id="bg-trgovacka-zarkovo",
            name="Trgovačka ulica — Žarkovo",
            city=SerbiaCity.BELGRADE,
            streets=("Trgovačka", "Ace Joksimovića", "Vodovodska"),
            municipality="Čukarica",
            category=CameraCategory.BOULEVARDS,
            direction="2K QHD pogled duž Trgovačke ulice u Žarkovu ka Vidikovcu i Ceraku",
            lat=44.7584,
            lon=20.4159,
            ott_slug="zarkovo",
            poster_url="https://kamere.24sedam.rs/js/cameras/zarkovo.webp",
        ),
        # Belgrade Ring & Danube / Sava Corridors (10)
        _toll4all_front_camera(
            cam_id="bg-vrcin-ulaz",
            name="Beograd (Vrčin) — Ulaz na autoput",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A1 / E-75", "Bubanj Potok — Vrčin"),
            municipality="Grocka / Voždovac",
            direction="Čeona naplatna stanica Beograd–Vrčin, izlaz iz Beograda ka Nišu",
            lat=44.6961,
            lon=20.5731,
            pan_num=6,
        ),
        _toll4all_front_camera(
            cam_id="bg-vrcin-izlaz",
            name="Beograd (Vrčin) — Prilaz Beogradu",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A1 / E-75", "Vrčin — Bubanj Potok"),
            municipality="Grocka / Voždovac",
            direction="Čeona naplatna stanica Beograd–Vrčin, ulazak u Beograd iz smera Niša",
            lat=44.6965,
            lon=20.5725,
            pan_num=5,
        ),
        _toll4all_front_camera(
            cam_id="bg-obrenovac-ulaz",
            name="Obrenovac — Ulaz na Miloš Veliki",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A2 (Miloš Veliki)", "Surčin — Obrenovac"),
            municipality="Obrenovac",
            direction="Jugozapadni prilaz Beogradu na autoputu Miloš Veliki, smer ka Čačku",
            lat=44.6518,
            lon=20.2335,
            pan_num=4,
        ),
        _toll4all_front_camera(
            cam_id="bg-obrenovac-izlaz",
            name="Obrenovac — Prilaz Beogradu",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A2 (Miloš Veliki)", "Obrenovac — Surčin"),
            municipality="Obrenovac",
            direction="Jugozapadni prilaz Beogradu na autoputu Miloš Veliki, izlaz ka Surčinu",
            lat=44.6522,
            lon=20.2341,
            pan_num=3,
        ),
        _toll4all_side_camera(
            cam_id="bg-smederevo-ulaz",
            name="Podunavlje (Smederevo) — Ulaz na A1 ka Beogradu",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A1 / E-75", "Vodanj — Smederevo"),
            municipality="Grocka / Smederevo",
            direction=(
                "Istočni podunavski prilaz Beogradu na koridoru E-75 — uključenje ka Beogradu"
            ),
            lat=44.6012,
            lon=20.8965,
            side_num=10,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="bg-smederevo-izlaz",
            name="Podunavlje (Smederevo) — Izlaz sa autoputa A1",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A1 / E-75", "Ralja — Smederevo"),
            municipality="Grocka / Smederevo",
            direction="Istočni podunavski koridor E-75 — isključenje ka Smederevu i Dunavu",
            lat=44.6016,
            lon=20.8959,
            side_num=9,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="bg-pozarevac-ulaz",
            name="Dunavski koridor (Požarevac) — Ulaz na A1",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A1 / E-75", "Mala Krsna — Požarevac"),
            municipality="Podunavlje / Požarevac",
            direction=(
                "Jugoistočni koridor ka Beogradu — uključenje sa brze saobraćajnice "
                "Dunavski koridor"
            ),
            lat=44.5738,
            lon=21.0284,
            side_num=8,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="bg-pozarevac-izlaz",
            name="Dunavski koridor (Požarevac) — Izlaz sa A1",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A1 / E-75", "Požarevac — Veliko Gradište"),
            municipality="Podunavlje / Požarevac",
            direction="Isključenje sa autoputa A1 na brzu saobraćajnicu ka Požarevcu i Đerdapu",
            lat=44.5742,
            lon=21.0278,
            side_num=7,
            include_hls=True,
        ),
        _toll4all_front_camera(
            cam_id="bg-sabac-ulaz",
            name="Posavina (Šabac — Most na Savi) — Ulaz ka Beogradu",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A8", "Novi most na Savi — Ruma — Beograd"),
            municipality="Posavina / Šabac",
            direction=(
                "Čeona stanica kod novog mosta preko Save na autoputu A8 — smer ka Rumi i Beogradu"
            ),
            lat=44.7814,
            lon=19.6832,
            pan_num=10,
        ),
        _toll4all_front_camera(
            cam_id="bg-sabac-izlaz",
            name="Posavina (Šabac — Most na Savi) — Izlaz sa A8",
            city=SerbiaCity.BELGRADE,
            streets=("Autoput A8", "Ruma — Šabac — Loznica"),
            municipality="Posavina / Šabac",
            direction="Čeona stanica na autoputu A8 posle mosta preko Save — izlaz ka Šapcu",
            lat=44.7818,
            lon=19.6826,
            pan_num=9,
        ),
        # =========================================================================
        # ZEMUN (6 HD cameras: 2 urban 2K QHD + 4 Batajnica & Surčin/Dobanovci 720p)
        # =========================================================================
        _ott_24sedam_camera(
            cam_id="bg-lakat-nova-galenika",
            name="Zemun — Lakat krivina & Nova Galenika",
            city=SerbiaCity.ZEMUN,
            streets=("Batajnički drum", "Cara Dušana", "Prilaz Pupinovom mostu"),
            municipality="Zemun",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "2K QHD pogled na Lakat krivinu u Zemunu na spoju starog novosadskog puta "
                "i prilaza Pupinovom mostu"
            ),
            lat=44.8628,
            lon=20.3634,
            ott_slug="posta-lakat",
            poster_url="https://kamere.24sedam.rs/js/cameras/glavna-posta-2.webp",
        ),
        _ott_24sedam_camera(
            cam_id="bg-zmaj-autoput",
            name="Zemun — Zmaj & Tošin bunar (Autoput E-70)",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A3 / E-70", "Tošin bunar", "Zemun"),
            municipality="Zemun",
            category=CameraCategory.HIGHWAYS,
            direction=(
                "2K QHD pogled kod Zmaja u Zemunu na koridor E-70 ka Tošinom bunaru i aerodromu"
            ),
            lat=44.8286,
            lon=20.3824,
            ott_slug="zmaj-pumpa",
            poster_url="https://kamere.24sedam.rs/js/cameras/zmaj-pumpa.webp",
        ),
        _toll4all_front_camera(
            cam_id="bg-stara-pazova-izlaz",
            name="Zemun Sever (Batajnica / Stara Pazova) — Prilaz Zemunu",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A1 / E-75", "Stara Pazova — Batajnica — Zemun"),
            municipality="Zemun / Stara Pazova",
            direction="Severni prilaz Zemunu i Batajnici na koridoru E-75 iz smera Novog Sada",
            lat=44.9908,
            lon=20.2044,
            pan_num=1,
        ),
        _toll4all_front_camera(
            cam_id="bg-stara-pazova-ulaz",
            name="Zemun Sever (Batajnica / Stara Pazova) — Smer Novi Sad",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A1 / E-75", "Zemun — Batajnica — Stara Pazova"),
            municipality="Zemun / Stara Pazova",
            direction="Severni izlaz iz Zemuna i Batajnice na koridoru E-75 ka Novom Sadu",
            lat=44.9912,
            lon=20.2038,
            pan_num=2,
        ),
        _toll4all_front_camera(
            cam_id="bg-simanovci-izlaz",
            name="Zemun Zapad (Dobanovci / Šimanovci) — Prilaz Zemunu",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A3 / E-70", "Šimanovci — Dobanovci — Aerodrom Nikola Tesla"),
            municipality="Surčin / Zemun",
            direction=(
                "Zapadni prilaz petlji Dobanovci, aerodromu Nikola Tesla i Zemunu na koridoru E-70"
            ),
            lat=44.8694,
            lon=20.0991,
            pan_num=11,
        ),
        _toll4all_front_camera(
            cam_id="bg-simanovci-ulaz",
            name="Zemun Zapad (Dobanovci / Šimanovci) — Smer Srem",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A3 / E-70", "Zemun — Dobanovci — Šimanovci"),
            municipality="Surčin / Zemun",
            direction="Zapadni koridor E-70 od Zemuna i petlje Dobanovci ka Sremu",
            lat=44.8691,
            lon=20.0984,
            pan_num=12,
        ),
        _toll4all_front_camera(
            cam_id="zm-sid-izlaz",
            name="Koridor A3 Zapad (Šid) — Smer Ruma & Zemun",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A3 / E-70", "Šid — Ruma — Šimanovci — Zemun"),
            municipality="Zapadni koridor E-70",
            direction="Zapadni autoput A3 / E-70 iz smera Batrovaca ka Rumi, Dobanovcima i Zemunu",
            lat=45.0518,
            lon=19.2291,
            pan_num=13,
        ),
        _toll4all_front_camera(
            cam_id="zm-sid-ulaz",
            name="Koridor A3 Zapad (Šid) — Smer Batrovci",
            city=SerbiaCity.ZEMUN,
            streets=("Autoput A3 / E-70", "Zemun — Ruma — Šid — Batrovci"),
            municipality="Zapadni koridor E-70",
            direction="Zapadni autoput A3 / E-70 od Zemuna ka prelazu Batrovci",
            lat=45.0514,
            lon=19.2297,
            pan_num=14,
        ),
        # =========================================================================
        # NOVI SAD (8 HD cameras: 4 urban 2K QHD + 4 A1 & Fruškogorski koridor 720p)
        # =========================================================================
        _ott_24sedam_camera(
            cam_id="ns-pupinova-palata",
            name="Pupinova palata — Bulevar Mihajla Pupina",
            city=SerbiaCity.NOVI_SAD,
            streets=("Bulevar Mihajla Pupina", "Narodnih heroja", "Modene"),
            municipality="Stari Grad, Novi Sad",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "2K QHD pogled duž Bulevara Mihajla Pupina u centru Novog Sada ka Varadinskom mostu"
            ),
            lat=45.2543,
            lon=19.8457,
            ott_slug="posta-ns1",
            poster_url="https://kamere.24sedam.rs/js/cameras/posta-ns1.webp",
        ),
        _ott_24sedam_camera(
            cam_id="ns-bulevar-mihajla-pupina",
            name="Srpsko narodno pozorište & Centar",
            city=SerbiaCity.NOVI_SAD,
            streets=("Bulevar Mihajla Pupina", "Pozorišni trg", "Jevrejska"),
            municipality="Stari Grad, Novi Sad",
            category=CameraCategory.SQUARES,
            direction=(
                "2K QHD pogled ka Srpskom narodnom pozorištu, ulazu u pešačku zonu "
                "i Uspenskoj crkvi"
            ),
            lat=45.2551,
            lon=19.8432,
            ott_slug="posta-ns2",
            poster_url="https://kamere.24sedam.rs/js/cameras/posta-ns2.webp",
        ),
        _ott_24sedam_camera(
            cam_id="ns-varadinski-most",
            name="Varadinski most (Most Duga) — Dunav",
            city=SerbiaCity.NOVI_SAD,
            streets=("Varadinski most", "Kej žrtava racije", "Beogradski kej"),
            municipality="Novi Sad / Petrovaradin",
            category=CameraCategory.BRIDGES,
            direction=(
                "2K QHD pogled preko Dunava na Varadinski most i podgrađe Petrovaradinske tvrđave"
            ),
            lat=45.2548,
            lon=19.8576,
            ott_slug="petrovaradin",
            poster_url="https://kamere.24sedam.rs/js/cameras/petrovaradin.webp",
        ),
        _ott_24sedam_camera(
            cam_id="ns-miseluk",
            name="Mišeluk — Prilaz Mostu Slobode",
            city=SerbiaCity.NOVI_SAD,
            streets=("Put doktora Goldmana", "Tunel Mišeluk", "Most Slobode"),
            municipality="Petrovaradin / Sremska Kamenica",
            category=CameraCategory.BOULEVARDS,
            direction="2K QHD pogled na petlju Mišeluk i sremski prilaz tunelu i Mostu Slobode",
            lat=45.2294,
            lon=19.8519,
            ott_slug="juzni-bul1",
            poster_url="https://kamere.24sedam.rs/js/cameras/miseluk.webp",
        ),
        _toll4all_side_camera(
            cam_id="ns-jug-ulaz",
            name="Novi Sad Jug — Ulaz na autoput A1",
            city=SerbiaCity.NOVI_SAD,
            streets=("Autoput A1 / E-75", "Zrenjaninski put — Kaćka petlja"),
            municipality="Novi Sad",
            direction=(
                "Naplatna stanica Novi Sad Jug, uključenje na autoput E-75 ka Beogradu i Subotici"
            ),
            lat=45.2764,
            lon=19.8981,
            side_num=14,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="ns-jug-izlaz",
            name="Novi Sad Jug — Ulazak u Novi Sad",
            city=SerbiaCity.NOVI_SAD,
            streets=("Autoput A1 / E-75", "Bulevar Evrope / Kaćka petlja"),
            municipality="Novi Sad",
            direction=(
                "Naplatna stanica Novi Sad Jug, izlazak sa autoputa E-75 ka centru Novog Sada"
            ),
            lat=45.2768,
            lon=19.8975,
            side_num=13,
            include_hls=False,
        ),
        _toll4all_front_camera(
            cam_id="ns-ruma-izlaz",
            name="Fruškogorski koridor (Ruma) — Smer Irig & Novi Sad",
            city=SerbiaCity.NOVI_SAD,
            streets=("Autoput E-70 / M-21", "Ruma — Irig — Fruška gora — Novi Sad"),
            municipality="Ruma / Fruška gora",
            direction="Južni prilaz Fruškoj gori i Novom Sadu kod petlje Ruma",
            lat=44.9782,
            lon=19.8314,
            pan_num=7,
        ),
        _toll4all_front_camera(
            cam_id="ns-ruma-ulaz",
            name="Fruškogorski koridor (Ruma) — Ulaz na autoput E-70",
            city=SerbiaCity.NOVI_SAD,
            streets=("Autoput A3 / E-70", "Novi Sad — Irig — Ruma"),
            municipality="Ruma / Fruška gora",
            direction="Uključenje sa Fruškogorskog pravca na koridor E-70 kod Rume",
            lat=44.9778,
            lon=19.8321,
            pan_num=8,
        ),
        # =========================================================================
        # SUBOTICA (9 cameras: 1080p Gradski trg + 2 A1 720p + MZ Zorka + 5 border)
        # =========================================================================
        CameraEntry(
            id="su-gradski-trg-korzo",
            name="Subotica — Gradski trg & Narodno pozorište",
            city=SerbiaCity.SUBOTICA,
            quality=VideoQuality.FHD_1080P,
            streets=("Trg slobode", "Korzo", "Trg republike"),
            municipality="Centar, Subotica",
            category=CameraCategory.SQUARES,
            direction=(
                "Full HD 1080p pogled na centralni Gradski trg, Narodno pozorište "
                "i šetalište Korzo u Subotici"
            ),
            poster_url="https://kameresrbije.rs/thumbnails/subotica-gradski-trg-58.webp",
            coordinates=GeoCoordinates(lat=46.1003, lon=19.6656),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://g0.ipcamlive.com/player/snapshot.php?alias=kameratrg",
                    interval_ms=4000,
                    cache_buster_param="t",
                ),
            ),
        ),
        _toll4all_front_camera(
            cam_id="su-jug-ulaz",
            name="Subotica Jug — Ulaz na autoput A1",
            city=SerbiaCity.SUBOTICA,
            streets=("Autoput A1 / E-75", "Bikovo — Petlja Subotica Jug"),
            municipality="Subotica",
            direction="Čeona naplatna stanica Subotica Jug, uključenje ka Novom Sadu i Beogradu",
            lat=45.9892,
            lon=19.7285,
            pan_num=16,
        ),
        _toll4all_front_camera(
            cam_id="su-jug-izlaz",
            name="Subotica Jug — Prilaz Subotici i Paliću",
            city=SerbiaCity.SUBOTICA,
            streets=("Autoput A1 / E-75", "Subotica Jug — Palić"),
            municipality="Subotica",
            direction="Čeona naplatna stanica Subotica Jug, smer ka Subotici, Paliću i Horgošu",
            lat=45.9896,
            lon=19.7279,
            pan_num=15,
        ),
        CameraEntry(
            id="su-mz-zorka",
            name="Subotica — MZ Zorka (Panorama & Nebo)",
            city=SerbiaCity.SUBOTICA,
            quality=VideoQuality.SD,
            streets=("Proleterskih brigada", "Sutjeska", "MZ Zorka"),
            municipality="Subotica",
            category=CameraCategory.SQUARES,
            direction="Uživo meteorološka i panoramska kamera iznad severozapadnog dela Subotice",
            poster_url="https://sumeteo.info/webcam/cam_1.jpg",
            coordinates=GeoCoordinates(lat=46.1124, lon=19.6548),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://sumeteo.info/webcam/cam_1.jpg",
                    interval_ms=10000,
                    cache_buster_param="_",
                ),
            ),
        ),
        _mup_border_camera(
            cam_id="su-kelebija-ulaz",
            name="Subotica (GP Kelebija) — Ulaz u Srbiju",
            city=SerbiaCity.SUBOTICA,
            streets=("Put Edvarda Kardelja", "Y-krak obilaznica Subotica", "Kelebija"),
            municipality="Subotica",
            direction=(
                "Zapadni prilaz Subotici na graničnom prelazu Kelebija — ulaz u Republiku Srbiju"
            ),
            lat=46.1531,
            lon=19.5803,
            hls_path="Kelebija/kelebija1.m3u8",
            poster_slug="kelebija-30",
        ),
        _mup_border_camera(
            cam_id="su-kelebija-izlaz",
            name="Subotica (GP Kelebija) — Izlaz iz Srbije",
            city=SerbiaCity.SUBOTICA,
            streets=("Put Edvarda Kardelja", "Kelebija — Tompa"),
            municipality="Subotica",
            direction="Zapadni izlaz iz Subotice na graničnom prelazu Kelebija ka Mađarskoj",
            lat=46.1528,
            lon=19.5809,
            hls_path="Kelebija/kelebija2.m3u8",
            poster_slug="kelebija-31",
        ),
        _mup_border_camera(
            cam_id="su-horgos-ulaz",
            name="Autoput E-75 (GP Horgoš) — Severna kapija",
            city=SerbiaCity.SUBOTICA,
            streets=("Autoput A1 / E-75", "Horgoš — Palić — Subotica"),
            municipality="Horgoš / Subotica",
            direction="Glavni koridor E-75 severno od Palića i Subotice — ulaz u Republiku Srbiju",
            lat=46.1783,
            lon=19.6997,
            hls_path="Horgos/horgos1.m3u8",
            poster_slug="horgos-32",
        ),
        _mup_border_camera(
            cam_id="su-djala-ulaz",
            name="Potisje (GP Đala) — Ulaz u Srbiju",
            city=SerbiaCity.SUBOTICA,
            streets=("Državni put II A-104", "Đala — Novi Kneževac — Subotica"),
            municipality="Severni Banat / Potisje",
            direction="Severoistočni prilaz Potisju i Subotici na prelazu Đala — ulaz u Srbiju",
            lat=46.1583,
            lon=20.1162,
            hls_path="Djala/djala2.m3u8",
            poster_slug="djala-29",
        ),
        _mup_border_camera(
            cam_id="su-djala-izlaz",
            name="Potisje (GP Đala) — Izlaz iz Srbije",
            city=SerbiaCity.SUBOTICA,
            streets=("Državni put II A-104", "Novi Kneževac — Đala — Tisasiget"),
            municipality="Severni Banat / Potisje",
            direction="Izlaz iz Srbije na prelazu Đala duž reke Tise ka Segedinu",
            lat=46.1586,
            lon=20.1157,
            hls_path="Djala/djala1.m3u8",
            poster_slug="djala-28",
        ),
        # =========================================================================
        # NIŠ (11 cameras: 3K Vidojevica AllSky + 8 A1/A4 720p + 2 Gradina border)
        # =========================================================================
        CameraEntry(
            id="ni-vidojevica-allsky",
            name="Vidojevica (Opservatorija) — Nebo uživo (AllSky)",
            city=SerbiaCity.NIS,
            quality=VideoQuality.UHD_2K,
            streets=("Astronomska stanica Vidojevica", "Planina Vidojevica (1155 m)"),
            municipality="Toplica / Nišavski region",
            category=CameraCategory.SQUARES,
            direction=(
                "3K panoramska AllSky kamera Astronomske opservatorije na Vidojevici "
                "jugozapadno od Niša"
            ),
            poster_url="https://vidojevica.aob.rs/images/stories/allsky/ImageLastFTP_AllSKY.jpg",
            coordinates=GeoCoordinates(lat=43.1414, lon=21.5558),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://vidojevica.aob.rs/images/stories/allsky/ImageLastFTP_AllSKY.jpg",
                    interval_ms=60000,
                    cache_buster_param="_",
                ),
            ),
        ),
        _toll4all_side_camera(
            cam_id="ni-sever-ulaz",
            name="Niš Sever (Trupale) — Ulaz na autoput",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Bulevar 12. februar", "Čamurlijski put"),
            municipality="Crveni Krst, Niš",
            direction=(
                "Severni prilaz Nišu kod petlje Trupale i aerodroma Konstantin Veliki — ulaz na A1"
            ),
            lat=43.3551,
            lon=21.8483,
            side_num=4,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="ni-sever-izlaz",
            name="Niš Sever (Trupale) — Ulazak u Niš",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Bulevar 12. februar"),
            municipality="Crveni Krst, Niš",
            direction="Severni ulazak u Niš sa koridora E-75 ka Bulevaru 12. februar i Tvrđavi",
            lat=43.3555,
            lon=21.8478,
            side_num=3,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="ni-jug-ulaz",
            name="Niš Jug — Ulaz na autoput",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Bulevar Dimitrija Tucovića", "Novo Selo"),
            municipality="Palilula / Medijana, Niš",
            direction=(
                "Južni prilaz Nišu na koridoru E-75 — uključenje na autoput ka Beogradu i Leskovcu"
            ),
            lat=43.2684,
            lon=21.8252,
            side_num=6,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="ni-jug-izlaz",
            name="Niš Jug — Južni ulazak u Niš",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Južni prilaz Nišu"),
            municipality="Palilula / Medijana, Niš",
            direction="Južni izlaz sa autoputa A1 ka Nišu, Ledena Stena i centar grada",
            lat=43.2689,
            lon=21.8247,
            side_num=5,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="ni-leskovac-ulaz",
            name="Južna Morava (Leskovac) — Ulaz na A1 ka Nišu",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Leskovac — Brestovac — Niš"),
            municipality="Južni koridor A1",
            direction="Južni koridor E-75 — uključenje kod Leskovca u smeru ka Nišu",
            lat=43.0346,
            lon=21.9679,
            side_num=2,
            include_hls=True,
        ),
        _toll4all_side_camera(
            cam_id="ni-leskovac-izlaz",
            name="Južna Morava (Leskovac) — Izlaz sa autoputa A1",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Niš — Pečenjevce — Leskovac"),
            municipality="Južni koridor A1",
            direction="Južni koridor E-75 od Niša — isključenje sa autoputa A1 ka Leskovcu",
            lat=43.0342,
            lon=21.9685,
            side_num=1,
            include_hls=True,
        ),
        _toll4all_front_camera(
            cam_id="ni-dimitrovgrad-izlaz",
            name="Koridor A4 Istok (Dimitrovgrad) — Smer Pirot & Niš",
            city=SerbiaCity.NIS,
            streets=("Autoput A4 / E-80", "Dimitrovgrad — Pirot — Bela Palanka — Niš"),
            municipality="Istočni krak E-80",
            direction="Istočni krak Koridora 10 (A4 / E-80) u smeru ka Pirotu i Nišu",
            lat=43.0168,
            lon=22.7684,
            pan_num=17,
        ),
        _toll4all_front_camera(
            cam_id="ni-dimitrovgrad-ulaz",
            name="Koridor A4 Istok (Dimitrovgrad) — Smer Gradina",
            city=SerbiaCity.NIS,
            streets=("Autoput A4 / E-80", "Niš — Pirot — Dimitrovgrad"),
            municipality="Istočni krak E-80",
            direction="Istočni krak Koridora 10 (A4 / E-80) od Niša i Pirota ka Gradini",
            lat=43.0164,
            lon=22.7691,
            pan_num=18,
        ),
        _toll4all_front_camera(
            cam_id="ni-presevo-ulaz",
            name="Koridor A1 Jug (Preševo) — Smer Vranje & Niš",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Preševo — Vranje — Grdelica — Niš"),
            municipality="Južni krak E-75",
            direction="Južni krak Koridora 10 (A1 / E-75) u smeru ka Vranju, Grdelici i Nišu",
            lat=42.3624,
            lon=21.6884,
            pan_num=20,
            include_hls=True,
        ),
        _toll4all_front_camera(
            cam_id="ni-presevo-izlaz",
            name="Koridor A1 Jug (Preševo) — Južni izlaz sa A1",
            city=SerbiaCity.NIS,
            streets=("Autoput A1 / E-75", "Niš — Vranje — Preševo"),
            municipality="Južni krak E-75",
            direction="Južni krak Koridora 10 (A1 / E-75) od Niša i Vranja ka prelazu Preševo",
            lat=42.3620,
            lon=21.6890,
            pan_num=19,
            include_hls=False,
        ),
        _mup_border_camera(
            cam_id="ni-gradina-ulaz",
            name="Autoput E-80 (GP Gradina) — Ulaz ka Nišu",
            city=SerbiaCity.NIS,
            streets=("Autoput A4 / E-80", "Gradina — Pirot — Niš"),
            municipality="Dimitrovgrad / Istočni krak",
            direction="Istočna kapija Srbije na koridoru E-80 — ulazak u Srbiju ka Pirotu i Nišu",
            lat=42.9992,
            lon=22.8016,
            hls_path="Gradina/gradina1.m3u8",
            poster_slug="gradina-54",
        ),
        _mup_border_camera(
            cam_id="ni-gradina-izlaz",
            name="Autoput E-80 (GP Gradina) — Izlaz iz Srbije",
            city=SerbiaCity.NIS,
            streets=("Autoput A4 / E-80", "Niš — Dimitrovgrad — Kalotina"),
            municipality="Dimitrovgrad / Istočni krak",
            direction="Istočni krak E-80 — izlazak iz Republike Srbije na prelazu Gradina",
            lat=42.9988,
            lon=22.8022,
            hls_path="Gradina/gradina2.m3u8",
            poster_slug="gradina-55",
        ),
        # =========================================================================
        # SAINT PETERSBURG, RUSSIA (12 cameras: 10 live 1080p HLS + 2 live HD snaps)
        # =========================================================================
        _rutube_live_camera(
            cam_id="ru-spb-palace-bridge",
            name="Palace Bridge & Neva River Panorama",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Dvortsovy Bridge", "Admiralty Embankment", "Universitetskaya Embankment"),
            municipality="Tsentralny / Vasileostrovsky",
            category=CameraCategory.BRIDGES,
            direction=(
                "Live panorama over the Neva River, Palace Bridge drawspan, Spit of Vasilyevsky "
                "Island, and Rostral Columns"
            ),
            lat=59.9410,
            lon=30.3085,
            hls_url=(
                "https://bl.rutube.ru/livestream/86e2f45785378c866d212e7f5f5447b0/index.m3u8"
                "?s=1Px1aOQMP3RaCMhH5mZrvg&e=2095018226&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-05-24/5b/c8/5bc845ae94af4fbf678737923f35631f.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-admiralty",
            name="Admiralty Embankment & Peter and Paul Fortress",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Admiralteyskaya Embankment", "Neva River", "Zayachy Island"),
            municipality="Admiralteysky / Petrogradsky",
            category=CameraCategory.BRIDGES,
            direction=(
                "Rotating sightseeing camera across Admiralty Embankment, Peter and Paul Fortress, "
                "Kunstkamera, and St. Isaac's Cathedral"
            ),
            lat=59.9385,
            lon=30.3056,
            hls_url=(
                "https://bl.rutube.ru/livestream/af70989fe893a8bb4f16447936340e95/index.m3u8"
                "?s=a2hnBGfscGGfhxA184wBqg&e=2095017940&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2024-09-22/3a/97/3a9710c5ebb51907c995f62abc399b79.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-spilled-blood",
            name="Church of the Savior on Spilled Blood",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Griboyedov Canal Embankment", "Inzhenernaya Street", "Mikhailovsky Garden"),
            municipality="Tsentralny District",
            category=CameraCategory.SQUARES,
            direction=(
                "Elevated view toward the domes of the Church of the Savior on Spilled Blood "
                "along the Griboyedov Canal"
            ),
            lat=59.9401,
            lon=30.3289,
            hls_url=(
                "https://bl.rutube.ru/livestream/9dce2908a76efba4a82c2f95fa72efc6/index.m3u8"
                "?s=sg9x2xyRLf-FMc4PnJdCtA&e=2095018227&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-08-07/c0/ed/c0ed919e225d891155820e5ef183e5a7.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-nevsky-gostiny",
            name="Nevsky Prospekt — Gostiny Dvor",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Nevsky Prospekt", "Sadovaya Street", "Gostiny Dvor"),
            municipality="Tsentralny District",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "View over the Nevsky Prospekt and Sadovaya Street intersection beside "
                "Great Gostiny Dvor arcade"
            ),
            lat=59.9344,
            lon=30.3323,
            hls_url=(
                "https://bl.rutube.ru/livestream/1217bd14907ff59c5c64bcc4cbe1af41/index.m3u8"
                "?s=oyxldQwKIGsKItkr-D-_DQ&e=2095018226&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-09-05/ba/6d/ba6dce8c3abb9eed322c61f4e3adc247.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-nevsky-anichkov",
            name="Nevsky Prospekt — Anichkov Palace & Fontanka",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Nevsky Prospekt", "Fontanka River Embankment", "Ostrovsky Square"),
            municipality="Tsentralny District",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "Live view along Nevsky Prospekt at Anichkov Palace and Anichkov Bridge "
                "over the Fontanka River"
            ),
            lat=59.9332,
            lon=30.3430,
            hls_url=(
                "https://bl.rutube.ru/livestream/a996e6508b2ff81cd523dae2d14f7bf6/index.m3u8"
                "?s=l_MOvXl61kNKP0H3iiXueg&e=2095018227&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-05-10/3c/c7/3cc7a9b9143624b88fbab52b1d3d8a05.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-nevsky-panorama",
            name="Nevsky Prospekt — Main Avenue Traffic",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Nevsky Prospekt", "Liteyny Prospekt"),
            municipality="Tsentralny District",
            category=CameraCategory.BOULEVARDS,
            direction="Wide boulevard view along Nevsky Prospekt toward Admiralty Spire",
            lat=59.9327,
            lon=30.3489,
            hls_url=(
                "https://bl.rutube.ru/livestream/746aa7922a210086f18daff094a5e63d/index.m3u8"
                "?s=yLRtprP8c2bx2SWjHgASDw&e=2095018308&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-05-09/b4/22/b422cb51aae61c450e29574b06685ba1.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-griboyedov",
            name="Griboyedov Canal & Kazan Quarter",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Griboyedov Canal Embankment", "Kazanskaya Street", "Bank Bridge"),
            municipality="Tsentralny District",
            category=CameraCategory.BRIDGES,
            direction="Waterway and historic embankments along the Griboyedov Canal",
            lat=59.9358,
            lon=30.3259,
            hls_url=(
                "https://bl.rutube.ru/livestream/e2fae10f093223afb5376b280fcbb24a/index.m3u8"
                "?s=8KO6AMsHSkTHHqjXRAHoVA&e=2095018227&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-07-23/da/5a/da5a3a7274459f10201fa9c053489892.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-aurora",
            name="Cruiser Aurora — Petrogradskaya Embankment",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Petrogradskaya Embankment", "Bolshaya Nevka", "Sampsonievsky Bridge"),
            municipality="Petrogradsky District",
            category=CameraCategory.BRIDGES,
            direction=(
                "Live view of the historic Cruiser Aurora moored at Petrogradskaya Embankment "
                "on the Neva River"
            ),
            lat=59.9554,
            lon=30.3378,
            hls_url=(
                "https://bl.rutube.ru/livestream/2c5000420be8aa08a969a21614e2d33e/index.m3u8"
                "?s=SVasj1lIiRv6A9q64Cz0qg&e=2095018227&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-05-29/1c/cd/1ccd55cd3d235f8bcd0f7bb7dc707dc3.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-historic-rooftops",
            name="Historic Center Rooftops — Tchaikovskogo Street",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Tchaikovskogo Street", "Liteyny Prospekt", "Summer Garden"),
            municipality="Tsentralny District",
            category=CameraCategory.SQUARES,
            direction="360-degree rooftop skyline over historic Saint Petersburg domes and spires",
            lat=59.9472,
            lon=30.3468,
            hls_url=(
                "https://bl.rutube.ru/livestream/d7e9422167f785e111c880e44ad76428/index.m3u8"
                "?s=KzPhUMHJIEjoD7CCWlXpdA&e=2095018308&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2024-09-30/9c/ba/9cba3c11b2aeea2626df2b0d978843b9.jpg"
            ),
        ),
        _rutube_live_camera(
            cam_id="ru-spb-zagorodny",
            name="Zagorodny Prospekt — Five Corners",
            city=CameraCity.SAINT_PETERSBURG,
            timezone="Europe/Moscow",
            streets=("Zagorodny Prospekt", "Rubinshteyna Street", "Lomonosova Street"),
            municipality="Tsentralny District",
            category=CameraCategory.BOULEVARDS,
            direction="Street-level panorama along Zagorodny Prospekt near Five Corners",
            lat=59.9261,
            lon=30.3408,
            hls_url=(
                "https://bl.rutube.ru/livestream/92e79cc0d122fb94647b79d4e7778773/index.m3u8"
                "?s=3xjII2j4UzMhodWR-hJdYA&e=2095018308&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-08-23/0b/0e/0b0e398a6f8c698fb107b1019ea997bc.jpg"
            ),
        ),
        CameraEntry(
            id="ru-spb-sevkabel",
            name="Sevkabel Port — Gulf of Finland & ZSD Bridge",
            country=CameraCountry.RUSSIA,
            city=CameraCity.SAINT_PETERSBURG,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Moscow",
            streets=("Kozhevennaya Line, 40", "Sevkabel Port", "Western High-Speed Diameter"),
            municipality="Vasilyevsky Island",
            category=CameraCategory.BRIDGES,
            direction=(
                "Waterfront boardwalk at Sevkabel Port overlooking the Gulf of Finland "
                "and the cable-stayed ZSD bridge"
            ),
            poster_url="https://imgproxy.windy.com/_/full/plain/current/1729876918/original.jpg",
            coordinates=GeoCoordinates(lat=59.9242, lon=30.2407),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url=(
                        "https://openapi-alpha.ivideon.com/cameras/"
                        "100-VBPoWKrNcZ6SBhucwbvQCD:0/live_preview?op=GET&access_token=public&q=2"
                    ),
                    interval_ms=2500,
                    cache_buster_param="_",
                ),
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://imgproxy.windy.com/_/full/plain/current/1729876918/original.jpg",
                    interval_ms=60000,
                    cache_buster_param="v",
                ),
            ),
        ),
        _windy_snapshot_camera(
            cam_id="ru-spb-hermitage",
            name="State Hermitage Museum — Winter Palace Courtyard",
            city=CameraCity.SAINT_PETERSBURG,
            quality=VideoQuality.HD_720P,
            timezone="Europe/Moscow",
            streets=("Palace Square", "Palace Embankment, 32", "Winter Palace"),
            municipality="Tsentralny District",
            category=CameraCategory.SQUARES,
            direction="View inside the Great Courtyard garden of the Winter Palace (Hermitage)",
            lat=59.9398,
            lon=30.3146,
            windy_id="1793869112",
        ),
        # =========================================================================
        # ROSTOV-ON-DON, RUSSIA (1 camera: 1080p HD live rooftop skyline HLS)
        # =========================================================================
        _rutube_live_camera(
            cam_id="ru-rov-leninsky-skyline",
            name="Leninsky District — Central Station & Siversa Flyover",
            city=CameraCity.ROSTOV_ON_DON,
            timezone="Europe/Moscow",
            streets=("Privokzalnaya Square", "Prospekt Siversa", "Gvardeyskaya Square"),
            municipality="Leninsky District",
            category=CameraCategory.HIGHWAYS,
            direction=(
                "High-altitude skyline panorama over Rostov-Glavny Railway Station, "
                "Siversa Prospekt viaduct, and western Rostov-on-Don hills"
            ),
            lat=47.2194,
            lon=39.6908,
            hls_url=(
                "https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8"
                "?s=ZyjH13qCcKa5iAOa33NZlw&e=2095018072&scheme=https"
            ),
            poster_url=(
                "https://pic.rtbcdn.ru/video/2026-08-07/c7/35/c735cfed8162599365f1b12cba048a36.jpg"
            ),
        ),
        # =========================================================================
        # TOMSK, RUSSIA (10 cameras: 9 1080p HD [incl. live HLS] + 1 SD, Asia/Tomsk)
        # =========================================================================
        CameraEntry(
            id="ru-tof-yuzhnaya",
            name="Yuzhnaya Square — Mokrushinsky Crossing",
            country=CameraCountry.RUSSIA,
            city=CameraCity.TOMSK,
            quality=VideoQuality.FHD_1080P,
            timezone="Asia/Tomsk",
            streets=("Yuzhnaya Square", "Krasnoarmeyskaya Street", "Mokrushina Street"),
            municipality="Kirovsky District",
            category=CameraCategory.SQUARES,
            direction=(
                "Southern gateway to Tomsk at Yuzhnaya Square and the Mokrushinsky rail crossing"
            ),
            poster_url="https://cdn08.vtomske.ru/stream/21.jpg",
            coordinates=GeoCoordinates(lat=56.4510, lon=84.9823),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.HLS,
                    url="https://cdn08.vtomske.ru/hls/stream6.m3u8",
                ),
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://cdn08.vtomske.ru/stream/21.jpg",
                    interval_ms=8000,
                    cache_buster_param="_",
                ),
            ),
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-novosobornaya",
            name="Novosobornaya Square & City Administration",
            streets=("Prospekt Lenina", "Novosobornaya Square", "Sovetskaya Street"),
            municipality="Sovetsky District",
            category=CameraCategory.SQUARES,
            direction="Central civic plaza at Novosobornaya Square along Prospekt Lenina",
            lat=56.4755,
            lon=84.9509,
            slug="admcam1",
            windy_fallback_id="1623915502",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-tom-embankment",
            name="Tom River Embankment — River Station",
            streets=("Naberezhnaya Reki Tomi", "Prospekt Lenina", "River Terminal"),
            municipality="Leninsky District",
            category=CameraCategory.BRIDGES,
            direction="Panoramic view over the Tom River embankment and Communal Bridge approach",
            lat=56.4942,
            lon=84.9426,
            slug="23",
            windy_fallback_id="1618476507",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-lenina-prospekt",
            name="Prospekt Lenina — Tikhiy Lane",
            streets=("Prospekt Lenina", "Pereulok Tikhiy", "Old Town"),
            municipality="Leninsky District",
            category=CameraCategory.BOULEVARDS,
            direction="Main north-south avenue of historic Tomsk along Prospekt Lenina",
            lat=56.4891,
            lon=84.9482,
            slug="1",
        ),
        _windy_snapshot_camera(
            cam_id="ru-tof-krasnoarmeyskaya",
            name="Krasnoarmeyskaya Street — Yuzhnaya Approach",
            city=CameraCity.TOMSK,
            quality=VideoQuality.FHD_1080P,
            timezone="Asia/Tomsk",
            streets=("Krasnoarmeyskaya Street", "Yuzhnaya Square", "Bogatyevo Highway"),
            municipality="Kirovsky District",
            category=CameraCategory.BOULEVARDS,
            direction="Traffic corridor along Krasnoarmeyskaya Street approaching Yuzhnaya Square",
            lat=56.4535,
            lon=84.9781,
            windy_id="1429349224",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-transportnaya",
            name="Transportnaya Square",
            streets=("Transportnaya Square", "Krasnoarmeyskaya Street", "Prospekt Kirova"),
            municipality="Kirovsky District",
            category=CameraCategory.SQUARES,
            direction="Major arterial roundabout at Transportnaya Square in southern Tomsk",
            lat=56.4557,
            lon=84.9756,
            slug="stream8",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-tsu-parus",
            name="Tom River — Tomsk State University Campus",
            streets=("Pereulok Buyanovsky, 3A", "Tom River", "TSU Parus Residence"),
            municipality="Kirovsky District",
            category=CameraCategory.BRIDGES,
            direction="High rooftop view from TSU Parus across the Tom River bend and left bank",
            lat=56.4682,
            lon=84.9389,
            slug="tsu",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-vershinina",
            name="Tom River Panorama — Vershinina Street",
            streets=("Vershinina Street, 47", "Nahimova Street", "Lagerny Garden"),
            municipality="Kirovsky District",
            category=CameraCategory.BOULEVARDS,
            direction="Wide urban panorama across Kirovsky District toward the Tom River valley",
            lat=56.4628,
            lon=84.9620,
            slug="19",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-plekhanova",
            name="Plekhanova Lane — Historic Wooden Quarter",
            streets=("Pereulok Plekhanova, 4", "Prospekt Lenina", "Gagarina Street"),
            municipality="Sovetsky District",
            category=CameraCategory.BOULEVARDS,
            direction="Central tree-lined street along Plekhanova Lane near Prospekt Lenina",
            lat=56.4795,
            lon=84.9542,
            slug="admcam3",
        ),
        _tomsk_snapshot_camera(
            cam_id="ru-tof-tomsky-lane",
            name="Tomsky Lane — Northern District",
            streets=("Pereulok Tomsky", "Karl Marx Street"),
            municipality="Leninsky District",
            category=CameraCategory.BOULEVARDS,
            direction="Neighborhood street camera at Tomsky Lane in northern central Tomsk",
            lat=56.5015,
            lon=84.9612,
            slug="admcam4",
            quality=VideoQuality.SD,
        ),
        # =========================================================================
        # BARCELONA, SPAIN (10 cameras: 1080p 3Cat live HLS + 9 municipal/port cams)
        # =========================================================================
        CameraEntry(
            id="es-bcn-3cat-panorama",
            name="Barcelona & Catalonia Live Panorama (3Cat)",
            country=CameraCountry.SPAIN,
            city=CameraCity.BARCELONA,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Madrid",
            streets=("Port Olímpic", "Tibidabo", "Barcelona Coastline"),
            municipality="Barcelona / Catalunya",
            category=CameraCategory.SQUARES,
            direction=(
                "24/7 1080p broadcast camera rotation across Barcelona skyline, port, "
                "and Catalan landmarks by 3Cat"
            ),
            poster_url="https://imgproxy.windy.com/_/full/plain/current/1450823411/original.jpg",
            coordinates=GeoCoordinates(lat=41.3851, lon=2.1734),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.HLS,
                    url="https://directes-tv-int.3catdirectes.cat/live-content/beauties-hls/master.m3u8",
                ),
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://imgproxy.windy.com/_/full/plain/current/1450823411/original.jpg",
                    interval_ms=60000,
                    cache_buster_param="v",
                ),
            ),
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-port-olimpic",
            name="Port Olímpic, Barceloneta & W Hotel",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Moll de Gregal", "Platja de la Barceloneta", "Port Olímpic"),
            municipality="Sant Martí / Ciutat Vella",
            category=CameraCategory.BRIDGES,
            direction="Mediterranean marina view from Port Olímpic toward Barceloneta and W Hotel",
            lat=41.3858,
            lon=2.2003,
            windy_id="1450823411",
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-la-rambla",
            name="La Rambla — Arts Santa Mònica & Port Vell",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("La Rambla", "Plaça del Portal de la Pau", "Columbus Monument"),
            municipality="Ciutat Vella",
            category=CameraCategory.BOULEVARDS,
            direction="Pedestrian promenade at lower La Rambla beside Arts Santa Mònica",
            lat=41.3772,
            lon=2.1758,
            windy_id="1450823279",
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-diagonal-macia",
            name="Avinguda Diagonal — Plaça Francesc Macià",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Avinguda Diagonal", "Plaça de Francesc Macià", "Josep Tarradellas"),
            municipality="Sarrià-Sant Gervasi / Les Corts",
            category=CameraCategory.SQUARES,
            direction="Westbound avenue traffic at Plaça de Francesc Macià on Avinguda Diagonal",
            lat=41.3926,
            lon=2.1444,
            windy_id="1252945039",
            interval_ms=30000,
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-gran-via",
            name="Gran Via de les Corts Catalanes — Glòries",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Gran Via de les Corts Catalanes", "Plaça de les Glòries Catalanes"),
            municipality="Sant Martí",
            category=CameraCategory.BOULEVARDS,
            direction="Arterial traffic along Gran Via de les Corts Catalanes near Glòries",
            lat=41.4042,
            lon=2.1928,
            windy_id="1616152753",
            interval_ms=30000,
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-marina-pujades",
            name="Carrer de la Marina — Port Olímpic Towers",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Carrer de la Marina", "Carrer de Pujades", "Vila Olímpica"),
            municipality="Sant Martí",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "Southbound view down Carrer de la Marina toward Torre Mapfre and Hotel Arts"
            ),
            lat=41.3935,
            lon=2.1903,
            windy_id="1616152597",
            interval_ms=30000,
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-mitre-balmes",
            name="Ronda del General Mitre — Carrer de Balmes",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Ronda del General Mitre", "Carrer de Balmes"),
            municipality="Sarrià-Sant Gervasi",
            category=CameraCategory.BOULEVARDS,
            direction="Upper-city boulevard junction of Ronda del General Mitre and Balmes",
            lat=41.4048,
            lon=2.1404,
            windy_id="1252946532",
            interval_ms=30000,
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-placa-cerda",
            name="Plaça Cerdà — Gran Via South Gateway",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Plaça d'Ildefons Cerdà", "Gran Via Sud", "Passeig de la Zona Franca"),
            municipality="Sants-Montjuïc",
            category=CameraCategory.SQUARES,
            direction="Southern airport expressway approach into Barcelona at Plaça Cerdà",
            lat=41.3622,
            lon=2.1354,
            windy_id="1234692367",
            interval_ms=30000,
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-ronda-litoral",
            name="Ronda Litoral (B-10) — Poblenou Coast",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Ronda Litoral B-10", "Carrer de Badajoz", "Mar Bella"),
            municipality="Sant Martí",
            category=CameraCategory.HIGHWAYS,
            direction="Coastal ring-road traffic along Ronda Litoral B-10 near Poblenou",
            lat=41.3924,
            lon=2.2045,
            windy_id="1253346207",
            interval_ms=30000,
        ),
        _windy_snapshot_camera(
            cam_id="es-bcn-ronda-dalt",
            name="Ronda de Dalt (B-20) — Collserola Foothills",
            city=CameraCity.BARCELONA,
            quality=VideoQuality.SD,
            timezone="Europe/Madrid",
            streets=("Ronda de Dalt B-20", "Passeig de Valldaura"),
            municipality="Horta-Guinardó / Nou Barris",
            category=CameraCategory.HIGHWAYS,
            direction="Northern orbital highway Ronda de Dalt B-20 along Collserola slopes",
            lat=41.4401,
            lon=2.1678,
            windy_id="1253046256",
            interval_ms=30000,
        ),
        # =========================================================================
        # MADRID, SPAIN (12 cameras: all 720p HD municipal feeds, Europe/Madrid)
        # =========================================================================
        _madrid_camera(
            cam_id="es-mad-cibeles",
            name="Plaza de Cibeles & Paseo de Recoletos",
            streets=("Plaza de Cibeles", "Calle de Alcalá", "Paseo del Prado"),
            municipality="Centro / Retiro",
            category=CameraCategory.SQUARES,
            direction=(
                "Iconic view across Plaza de Cibeles, Palacio de Cibeles, and Paseo del Prado"
            ),
            lat=40.4190,
            lon=-3.6931,
            cam_code="01302",
        ),
        _madrid_camera(
            cam_id="es-mad-callao-gran-via",
            name="Plaza del Callao — Gran Vía",
            streets=("Gran Vía", "Plaza del Callao", "Calle Jacometrezo"),
            municipality="Centro",
            category=CameraCategory.SQUARES,
            direction="Heart of Gran Vía at Plaza del Callao and Carrión (Schweppes) landmark",
            lat=40.4201,
            lon=-3.7059,
            cam_code="01314",
        ),
        _madrid_camera(
            cam_id="es-mad-gran-via-alcala",
            name="Gran Vía — Calle de Alcalá (Metrópolis)",
            streets=("Gran Vía, 1", "Calle de Alcalá", "Edificio Metrópolis"),
            municipality="Centro",
            category=CameraCategory.BOULEVARDS,
            direction="Eastern gateway of Gran Vía where it meets Calle de Alcalá",
            lat=40.4193,
            lon=-3.6974,
            cam_code="01317",
        ),
        _madrid_camera(
            cam_id="es-mad-plaza-espana",
            name="Plaza de España — Gran Vía West",
            streets=("Plaza de España", "Gran Vía", "Calle de la Princesa"),
            municipality="Centro / Moncloa-Aravaca",
            category=CameraCategory.SQUARES,
            direction="Renovated Plaza de España civic esplanade at the western end of Gran Vía",
            lat=40.4232,
            lon=-3.7108,
            cam_code="09301",
        ),
        _madrid_camera(
            cam_id="es-mad-puerta-alcala",
            name="Puerta de Alcalá — Plaza de la Independencia",
            streets=("Plaza de la Independencia", "Calle de Alcalá", "Calle de Serrano"),
            municipality="Salamanca / Retiro",
            category=CameraCategory.SQUARES,
            direction="Plaza de la Independencia roundabout beside Puerta de Alcalá and El Retiro",
            lat=40.4200,
            lon=-3.6887,
            cam_code="04322",
        ),
        _madrid_camera(
            cam_id="es-mad-neptuno",
            name="Fuente de Neptuno — Paseo del Prado",
            streets=("Plaza Cánovas del Castillo", "Paseo del Prado", "Carrera de San Jerónimo"),
            municipality="Centro / Cortes",
            category=CameraCategory.SQUARES,
            direction=(
                "Tree-lined Paseo del Prado boulevard at Plaza Cánovas del Castillo (Neptuno)"
            ),
            lat=40.4153,
            lon=-3.6942,
            cam_code="01303",
        ),
        _madrid_camera(
            cam_id="es-mad-paseo-prado",
            name="Paseo del Prado — Museo del Prado & Huertas",
            streets=("Paseo del Prado", "Calle de las Huertas", "CaixaForum"),
            municipality="Centro / Retiro",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "Landscape of Light UNESCO corridor along Paseo del Prado near Museo del Prado"
            ),
            lat=40.4131,
            lon=-3.6934,
            cam_code="01323",
        ),
        _madrid_camera(
            cam_id="es-mad-plaza-colon",
            name="Plaza de Colón — Paseo de la Castellana",
            streets=("Plaza de Colón", "Paseo de la Castellana", "Calle de Goya"),
            municipality="Salamanca / Chamberí",
            category=CameraCategory.SQUARES,
            direction="Grand junction of Paseo de la Castellana and Paseo de Recoletos at Colón",
            lat=40.4253,
            lon=-3.6897,
            cam_code="01301",
        ),
        _madrid_camera(
            cam_id="es-mad-bernabeu-lima",
            name="Plaza de Lima — Santiago Bernabéu",
            streets=("Paseo de la Castellana", "Plaza de Lima", "Concha Espina"),
            municipality="Chamartín / Tetuán",
            category=CameraCategory.BOULEVARDS,
            direction="Paseo de la Castellana corridor directly in front of Santiago Bernabéu",
            lat=40.4519,
            lon=-3.6908,
            cam_code="06306",
        ),
        _madrid_camera(
            cam_id="es-mad-plaza-castilla",
            name="Plaza de Castilla — Puerta de Europa (Torres KIO)",
            streets=("Plaza de Castilla", "Paseo de la Castellana", "Avenida de Asturias"),
            municipality="Chamartín / Tetuán",
            category=CameraCategory.SQUARES,
            direction=(
                "Northern business hub at Plaza de Castilla framed by the inclined KIO Towers"
            ),
            lat=40.4661,
            lon=-3.6889,
            cam_code="06303",
        ),
        _madrid_camera(
            cam_id="es-mad-atocha",
            name="Glorieta del Emperador Carlos V — Atocha",
            streets=("Plaza del Emperador Carlos V", "Paseo de la Infanta Isabel", "Atocha"),
            municipality="Centro / Arganzuela",
            category=CameraCategory.SQUARES,
            direction="Main rail hub plaza outside Madrid Puerta de Atocha and Reina Sofía Museum",
            lat=40.4092,
            lon=-3.6922,
            cam_code="01304",
        ),
        _madrid_camera(
            cam_id="es-mad-bailen-mayor",
            name="Calle de Bailén — Calle Mayor & Almudena",
            streets=("Calle de Bailén", "Calle Mayor", "Catedral de la Almudena"),
            municipality="Centro (Palacio)",
            category=CameraCategory.BRIDGES,
            direction="Historic Bailén viaduct corridor beside Calle Mayor and Almudena Cathedral",
            lat=40.4153,
            lon=-3.7135,
            cam_code="01334",
        ),
        # =========================================================================
        # BUCHAREST, ROMANIA (5 cameras: 4 1080p HD + 1 720p HD, Europe/Bucharest)
        # =========================================================================
        _windy_snapshot_camera(
            cam_id="ro-buh-piata-romana",
            name="Piața Romană — ASE & Bulevardul Magheru",
            city=CameraCity.BUCHAREST,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Bucharest",
            streets=("Piața Romană", "Bulevardul Gheorghe Magheru", "Bulevardul Lascăr Catargiu"),
            municipality="Sector 1, Bucharest",
            category=CameraCategory.SQUARES,
            direction=(
                "High-angle view of Piața Romană roundabout, Bucharest Academy of Economic "
                "Studies dome, and Bulevardul Magheru"
            ),
            lat=44.4471,
            lon=26.0975,
            windy_id="1793909045",
        ),
        _windy_snapshot_camera(
            cam_id="ro-buh-bratianu-baratia",
            name="Bulevardul I. C. Brătianu — Bărăția Tower & Cocor",
            city=CameraCity.BUCHAREST,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Bucharest",
            streets=("Bulevardul Ion C. Brătianu", "Bărăția Church", "Old Town (Lipscani)"),
            municipality="Sector 3, Bucharest",
            category=CameraCategory.BOULEVARDS,
            direction=(
                "Central avenue connecting Piața Universității and Piața Unirii past historic "
                "Bărăția Tower and Cocor facade"
            ),
            lat=44.4309,
            lon=26.1038,
            windy_id="1793902097",
        ),
        _windy_snapshot_camera(
            cam_id="ro-buh-militari-skyline",
            name="Bucharest West Skyline — Militari & Uverturii",
            city=CameraCity.BUCHAREST,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Bucharest",
            streets=("Bulevardul Uverturii", "Militari", "Chiajna"),
            municipality="Sector 6 / Ilfov",
            category=CameraCategory.BOULEVARDS,
            direction="Elevated western skyline and evening traffic across Militari district",
            lat=44.4355,
            lon=25.9912,
            windy_id="1793906533",
        ),
        _windy_snapshot_camera(
            cam_id="ro-buh-aeroclub-clinceni",
            name="Bucharest Aeroclub — Clinceni Airfield",
            city=CameraCity.BUCHAREST,
            quality=VideoQuality.FHD_1080P,
            timezone="Europe/Bucharest",
            streets=("Aerodromul Clinceni", "DN6 Corridor", "Southwest Bucharest"),
            municipality="Clinceni / Bucharest Metro",
            category=CameraCategory.HIGHWAYS,
            direction="Wide airfield and weather horizon view at Aeroclubul României — București",
            lat=44.3606,
            lon=25.9317,
            windy_id="1727652470",
        ),
        CameraEntry(
            id="ro-buh-piata-unirii",
            name="Piața Unirii — Fountains & Bulevardul Unirii",
            country=CameraCountry.ROMANIA,
            city=CameraCity.BUCHAREST,
            quality=VideoQuality.HD_720P,
            timezone="Europe/Bucharest",
            streets=("Piața Unirii", "Bulevardul Unirii", "Splaiul Independenței"),
            municipality="Sector 3 / Sector 4, Bucharest",
            category=CameraCategory.SQUARES,
            direction=(
                "Central Bucharest plaza overlooking Piața Unirii fountains and grand boulevard"
            ),
            poster_url=(
                "https://webcamromania.ro/media/snapshots/"
                "01a0267e-d4e0-753a-8ec9-efaa321c0a78/latest.jpg"
            ),
            coordinates=GeoCoordinates(lat=44.4268, lon=26.1025),
            streams=(
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url=(
                        "https://webcamromania.ro/media/snapshots/"
                        "01a0267e-d4e0-753a-8ec9-efaa321c0a78/latest.jpg"
                    ),
                    interval_ms=30000,
                    cache_buster_param="t",
                ),
                StreamDescriptor(
                    type=StreamTransportType.SNAPSHOT_POLL,
                    url="https://imgproxy.windy.com/_/full/plain/current/1793907437/original.jpg",
                    interval_ms=60000,
                    cache_buster_param="v",
                ),
            ),
        ),
    ]
    enriched = [
        replace(cam, country=cam.resolved_country) if cam.country is None else cam
        for cam in raw_cameras
    ]
    return sorted(
        enriched,
        key=lambda cam: (
            country_order[cam.resolved_country],
            city_order[cam.city],
            cam.quality.sort_rank,
        ),
    )
