package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.model.Camera

data class HomeChannelSelection(
    val isFavoritesMode: Boolean,
    val channelTitle: String,
    val cameras: List<Camera>,
)

object HomeChannelSelector {
    private val FEATURED_IDS_ORDER: List<String> = listOf(
        "jp-tyo-shibuya-scramble",
        "us-nyc-times-square",
        "uk-lon-abbey-road",
        "fr-par-eiffel-tower",
        "nl-ams-dam-square",
        "de-ber-alexanderplatz",
        "kr-sel-hangang-banpo",
        "ru-mow-moskva-reka",
        "ru-spb-palace-bridge",
        "ru-gdX-nagaev-bay",
        "ge-tbs-freedom-square",
        "am-evn-republic-square",
        "es-bcn-3cat-panorama",
        "es-mad-cibeles",
        "ro-buh-calea-victoriei-live",
        "bg-kralja-milana-london",
        "bg-lakat-nova-galenika",
        "ns-pupinova-palata",
        "su-gradski-trg-korzo",
        "ni-vidojevica-allsky",
        "bg-sava-centar-usce",
        "ru-rov-leninsky-skyline",
        "ru-tof-yuzhnaya",
        "ro-buh-piata-romana",
        "bg-most-na-adi-topcider",
        "bg-zmaj-autoput",
        "ns-varadinski-most",
        "su-jug-izlaz",
        "ni-sever-izlaz",
        "bg-brankov-most",
    )

    fun selectChannelCameras(
        cameras: List<Camera>,
        favoriteIds: Set<String>,
        maxItems: Int = 16,
    ): HomeChannelSelection {
        val limit = maxItems.coerceAtLeast(1)
        val matchedFavorites = cameras.filter { favoriteIds.contains(it.id) }
        if (matchedFavorites.isNotEmpty()) {
            return HomeChannelSelection(
                isFavoritesMode = true,
                channelTitle = "World Live Cams — Favorites",
                cameras = matchedFavorites.take(limit),
            )
        }

        val byId = cameras.associateBy { it.id }
        val curated = ArrayList<Camera>(limit)
        for (id in FEATURED_IDS_ORDER) {
            val cam = byId[id]
            if (cam != null && curated.size < limit) {
                curated.add(cam)
            }
        }
        if (curated.size < limit) {
            for (cam in cameras) {
                if (cam.isFeatured && curated.none { it.id == cam.id } && curated.size < limit) {
                    curated.add(cam)
                }
            }
        }
        if (curated.size < limit) {
            for (cam in cameras) {
                if (curated.none { it.id == cam.id } && curated.size < limit) {
                    curated.add(cam)
                }
            }
        }

        return HomeChannelSelection(
            isFavoritesMode = false,
            channelTitle = "World Live Cams — Featured",
            cameras = curated,
        )
    }
}
