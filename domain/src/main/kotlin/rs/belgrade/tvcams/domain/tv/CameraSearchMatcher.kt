package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.model.Camera
import java.text.Normalizer
import java.util.Locale

object CameraSearchMatcher {
    private val COMBINING_MARKS_RE = Regex("\\p{Mn}+")
    private val NON_ALPHANUM_RE = Regex("[^a-z0-9]+")

    fun normalizeForSearch(raw: String): String {
        if (raw.isBlank()) return ""
        val preMapped = buildString(raw.length + 4) {
            for (ch in raw) {
                when (ch) {
                    'đ', 'Đ' -> append('d')
                    'ł', 'Ł' -> append('l')
                    'ø', 'Ø' -> append('o')
                    'ß' -> append("ss")
                    'æ', 'Æ' -> append("ae")
                    'œ', 'Œ' -> append("oe")
                    else -> append(ch)
                }
            }
        }
        val decomposed = Normalizer.normalize(preMapped, Normalizer.Form.NFD)
        val ascii = COMBINING_MARKS_RE.replace(decomposed, "")
            .lowercase(Locale.ROOT)
        return NON_ALPHANUM_RE.replace(ascii, " ").trim()
    }

    private fun expandSerbianDigraphs(raw: String): String =
        raw.replace("đ", "dj").replace("Đ", "dj")

    fun filterCameras(
        cameras: List<Camera>,
        query: String,
        state: TvStageState? = null,
    ): List<Camera> {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) {
            val featured = cameras.filter { it.isFeatured }
            val seed = featured.ifEmpty { cameras.take(24) }
            return seed.sortedWith(
                compareBy<Camera> { it.resolvedCountry.sortOrder }
                    .thenBy { it.resolvedCountry.code }
                    .thenByDescending { it.isLiveVideo }
                    .thenBy { it.resolvedQuality.sortRank },
            )
        }

        val tokens = normalizedQuery.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        data class ScoredMatch(
            val camera: Camera,
            val tier: Int,
        )

        val matches = ArrayList<ScoredMatch>()
        for (cam in cameras) {
            val cityLabel = state?.cityDisplayLabel(cam.city) ?: cam.resolvedCityLabel
            val countryLabel = state?.countryDisplayLabel(cam.resolvedCountry) ?: cam.resolvedCountryLabel

            val primaryTitle = normalizeForSearch(cam.name)
            val primaryCity = normalizeForSearch(cityLabel)
            val primaryCountry = normalizeForSearch(countryLabel)
            val primaryHead = "$primaryTitle $primaryCity $primaryCountry"

            val rawCorpus = buildString {
                append(cam.name).append(' ')
                append(cityLabel).append(' ')
                append(countryLabel).append(' ')
                cam.streets.forEach { append(it).append(' ') }
                append(cam.municipality).append(' ')
                append(cam.direction).append(' ')
                append(cam.id).append(' ')
                append(cam.resolvedQuality.shortBadgeLabel())
            }
            val fullCorpus = normalizeForSearch(rawCorpus) + " " +
                normalizeForSearch(expandSerbianDigraphs(rawCorpus))

            val allTokensMatch = tokens.all { token -> fullCorpus.contains(token) }
            if (!allTokensMatch) continue

            val tier = when {
                primaryTitle.startsWith(normalizedQuery) || primaryCity.startsWith(normalizedQuery) -> 0
                tokens.all { token -> primaryHead.contains(token) } -> 1
                else -> 2
            }
            matches.add(ScoredMatch(cam, tier))
        }

        return matches
            .sortedWith(
                compareBy<ScoredMatch> { it.tier }
                    .thenByDescending { it.camera.isLiveVideo }
                    .thenBy { it.camera.resolvedQuality.sortRank }
                    .thenBy { it.camera.resolvedCountry.sortOrder }
                    .thenBy { it.camera.name },
            )
            .map { it.camera }
    }
}
