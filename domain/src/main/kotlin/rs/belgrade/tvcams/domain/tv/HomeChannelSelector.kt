package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.model.Camera

data class HomeChannelSelection(
    val isFavoritesMode: Boolean,
    val channelTitle: String,
    val cameras: List<Camera>,
)

object HomeChannelSelector {
    private val FEATURED_IDS_ORDER: List<String> = listOf(
        "bg-kralja-milana-london",
        "bg-most-na-adi-topcider",
        "bg-lakat-nova-galenika",
        "bg-zmaj-autoput",
        "ns-pupinova-palata",
        "ns-varadinski-most",
        "su-gradski-trg-korzo",
        "su-jug-izlaz",
        "ni-vidojevica-allsky",
        "ni-sever-izlaz",
        "bg-sava-centar-usce",
        "bg-brankov-most",
    )

    fun selectChannelCameras(
        cameras: List<Camera>,
        favoriteIds: Set<String>,
        maxItems: Int = 12,
    ): HomeChannelSelection {
        val limit = maxItems.coerceAtLeast(1)
        val matchedFavorites = cameras.filter { favoriteIds.contains(it.id) }
        if (matchedFavorites.isNotEmpty()) {
            return HomeChannelSelection(
                isFavoritesMode = true,
                channelTitle = "Serbia live cams — Favorites",
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
                if (curated.none { it.id == cam.id } && curated.size < limit) {
                    curated.add(cam)
                }
            }
        }

        return HomeChannelSelection(
            isFavoritesMode = false,
            channelTitle = "Serbia live cams — Featured",
            cameras = curated,
        )
    }
}
