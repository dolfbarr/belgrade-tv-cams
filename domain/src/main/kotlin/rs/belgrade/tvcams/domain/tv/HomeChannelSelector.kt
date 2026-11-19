package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.model.Camera

data class HomeChannelSelection(
    val isFavoritesMode: Boolean,
    val channelTitle: String,
    val cameras: List<Camera>,
)

object HomeChannelSelector {
    private val CHANNEL_RANK_COMPARATOR: Comparator<Camera> =
        compareByDescending<Camera> { it.isFeatured }
            .thenByDescending { it.isLiveVideo }
            .thenBy { it.resolvedQuality.sortRank }

    /**
     * Generalized multi-country, multi-city round-robin featured selector:
     * 1. One top-ranked camera per distinct country,
     * 2. One top-ranked camera per distinct remaining city,
     * 3. Highest-ranked remaining cameras up to [maxItems].
     * Zero hardcoded camera IDs required when community PRs add new cities or countries.
     */
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

        val curated = ArrayList<Camera>(limit)
        val selectedIds = HashSet<String>()

        // Pass 1: Prioritize newly contributed countries first so community additions surface immediately,
        // followed by built-in countries in canonical order (1 top camera per country).
        val countriesOrdered = cameras
            .map { it.resolvedCountry }
            .distinct()
            .sortedWith(
                compareBy<rs.belgrade.tvcams.domain.model.CameraCountry> {
                    if (it.sortOrder == Int.MAX_VALUE) 0 else 1
                }
                    .thenBy { it.sortOrder }
                    .thenBy { it.code },
            )

        for (country in countriesOrdered) {
            if (curated.size >= limit) break
            val bestInCountry = cameras
                .filter { it.resolvedCountry == country && it.id !in selectedIds }
                .minWithOrNull(CHANNEL_RANK_COMPARATOR)
            if (bestInCountry != null) {
                curated.add(bestInCountry)
                selectedIds.add(bestInCountry.id)
            }
        }

        // Pass 2: Cover remaining distinct cities (1 top camera per unrepresented city).
        val representedCities = curated.map { it.city }.toHashSet()
        val remainingCities = cameras
            .map { it.city }
            .distinct()
            .filter { it !in representedCities }
            .sortedWith(compareBy { it.sortOrder })

        for (city in remainingCities) {
            if (curated.size >= limit) break
            val bestInCity = cameras
                .filter { it.city == city && it.id !in selectedIds }
                .minWithOrNull(CHANNEL_RANK_COMPARATOR)
            if (bestInCity != null) {
                curated.add(bestInCity)
                selectedIds.add(bestInCity.id)
            }
        }

        // Pass 3: Fill remaining capacity with best overall remaining cameras.
        if (curated.size < limit) {
            val remaining = cameras
                .filter { it.id !in selectedIds }
                .sortedWith(CHANNEL_RANK_COMPARATOR)
            for (cam in remaining) {
                if (curated.size >= limit) break
                curated.add(cam)
                selectedIds.add(cam.id)
            }
        }

        return HomeChannelSelection(
            isFavoritesMode = false,
            channelTitle = "World Live Cams — Featured",
            cameras = curated,
        )
    }
}
