package rs.belgrade.tvcams.domain.currency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.util.Currency
import java.util.Locale

@Serializable
enum class BaseCurrencyPreference {
    AUTO,
    EUR,
    USD,
    RSD,
    GBP,
    CHF,
    JPY,
    RUB,
    CAD,
    AUD,
    ;

    fun next(): BaseCurrencyPreference = when (this) {
        AUTO -> EUR
        EUR -> USD
        USD -> RSD
        RSD -> GBP
        GBP -> CHF
        CHF -> JPY
        JPY -> RUB
        RUB -> CAD
        CAD -> AUD
        AUD -> AUTO
    }

    fun resolveCurrencyCode(locale: Locale = Locale.getDefault()): String = when (this) {
        AUTO -> runCatching {
            val region = locale.country
            if (region.length != 2) {
                null
            } else {
                Currency.getInstance(locale).currencyCode.uppercase(Locale.ROOT)
            }
        }.getOrNull()?.takeIf { it.length == 3 && it != "XXX" } ?: DEFAULT_FALLBACK_CURRENCY

        EUR, USD, RSD, GBP, CHF, JPY, RUB, CAD, AUD -> name
    }

    fun displayLabel(locale: Locale = Locale.getDefault()): String = when (this) {
        AUTO -> "Auto (${resolveCurrencyCode(locale)})"
        EUR, USD, RSD, GBP, CHF, JPY, RUB, CAD, AUD -> name
    }

    companion object {
        const val DEFAULT_FALLBACK_CURRENCY: String = "EUR"
    }
}

object CountryCurrencyResolver {
    private val EXPLICIT_COUNTRY_CURRENCIES: Map<String, List<String>> = mapOf(
        "RS" to listOf("RSD"),
        "RU" to listOf("RUB"),
        "ES" to listOf("EUR"),
        "FR" to listOf("EUR"),
        "NL" to listOf("EUR"),
        "DE" to listOf("EUR"),
        "IT" to listOf("EUR"),
        "PT" to listOf("EUR"),
        "AT" to listOf("EUR"),
        "IE" to listOf("EUR"),
        "HR" to listOf("EUR"),
        "FI" to listOf("EUR"),
        "GR" to listOf("EUR"),
        "BE" to listOf("EUR"),
        "ME" to listOf("EUR"),
        "RO" to listOf("RON"),
        "GB" to listOf("GBP"),
        "UK" to listOf("GBP"),
        "GE" to listOf("GEL"),
        "AM" to listOf("AMD"),
        "US" to listOf("USD"),
        "KR" to listOf("KRW"),
        "JP" to listOf("JPY"),
        "CH" to listOf("CHF"),
        "CZ" to listOf("CZK"),
        "IS" to listOf("ISK"),
        "NO" to listOf("NOK"),
        "TW" to listOf("TWD"),
        "HK" to listOf("HKD"),
        "SG" to listOf("SGD"),
        "TH" to listOf("THB"),
        "AU" to listOf("AUD"),
        "IL" to listOf("ILS"),
        "CA" to listOf("CAD"),
        "BR" to listOf("BRL"),
        "SX" to listOf("XCG", "ANG"),
        "CW" to listOf("XCG", "ANG"),
        "MX" to listOf("MXN"),
        "PL" to listOf("PLN"),
        "HU" to listOf("HUF"),
        "BG" to listOf("BGN"),
        "TR" to listOf("TRY"),
        "SE" to listOf("SEK"),
        "DK" to listOf("DKK"),
        "UA" to listOf("UAH"),
        "AE" to listOf("AED"),
    )

    fun resolveCurrencyCandidates(countryIsoAlpha2: String?): List<String> {
        val iso = countryIsoAlpha2?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (iso.length != 2 || !iso.all { it in 'A'..'Z' }) return emptyList()
        EXPLICIT_COUNTRY_CURRENCIES[iso]?.let { return it }

        val dynamicCode = runCatching {
            val regionLocale = Locale.Builder().setRegion(iso).build()
            Currency.getInstance(regionLocale).currencyCode.uppercase(Locale.ROOT)
        }.getOrNull()

        return if (dynamicCode != null && dynamicCode.length == 3 && dynamicCode != "XXX") {
            listOf(dynamicCode)
        } else {
            emptyList()
        }
    }

    fun resolvePrimaryCurrency(countryIsoAlpha2: String?): String? =
        resolveCurrencyCandidates(countryIsoAlpha2).firstOrNull()
}

@Serializable
data class ExchangeRateTable(
    val baseCurrency: String,
    val rates: Map<String, Double>,
    val fetchedAtEpochMs: Long,
)

data class CurrencyQuote(
    val baseCurrency: String,
    val localCurrency: String,
    val localPerBaseRate: Double,
    val compactLabel: String,
    val detailLabel: String,
    val fetchedAtEpochMs: Long,
)

object CurrencyQuoteFormatter {
    fun format(
        baseCurrency: String,
        localCurrency: String,
        localPerBaseRate: Double,
        fetchedAtEpochMs: Long = 0L,
    ): CurrencyQuote? {
        val base = baseCurrency.trim().uppercase(Locale.ROOT)
        val local = localCurrency.trim().uppercase(Locale.ROOT)
        if (base.length != 3 || local.length != 3 || base == local) return null
        if (!localPerBaseRate.isFinite() || localPerBaseRate <= 0.0) return null

        val stronger: String
        val weaker: String
        val factor: Double
        if (localPerBaseRate >= 1.0) {
            stronger = base
            weaker = local
            factor = localPerBaseRate
        } else {
            stronger = local
            weaker = base
            factor = 1.0 / localPerBaseRate
        }
        if (!factor.isFinite() || factor <= 0.0) return null

        val formattedFactor = when {
            factor < 10.0 -> String.format(Locale.US, "%.2f", factor)
            factor < 1_000.0 -> String.format(Locale.US, "%.1f", factor)
            else -> String.format(Locale.US, "%,.0f", factor)
        }
        val compact = "1 $stronger = $formattedFactor $weaker"
        return CurrencyQuote(
            baseCurrency = base,
            localCurrency = local,
            localPerBaseRate = localPerBaseRate,
            compactLabel = compact,
            detailLabel = "FX · $compact",
            fetchedAtEpochMs = fetchedAtEpochMs,
        )
    }
}

interface CurrencyUpstreamClient {
    suspend fun fetchLatestRates(baseCurrency: String): ExchangeRateTable?
}

interface CurrencyCacheStore {
    suspend fun readCached(baseCurrency: String): ExchangeRateTable?
    suspend fun writeCached(table: ExchangeRateTable)
}

interface CurrencyRepository {
    suspend fun getQuoteForCountry(
        baseCurrency: String,
        countryIsoAlpha2: String?,
    ): CurrencyQuote?
}

class CachedCurrencyRepository(
    private val upstream: CurrencyUpstreamClient,
    private val cacheStore: CurrencyCacheStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val staleGraceMillis: Long = DEFAULT_STALE_GRACE_MS,
) : CurrencyRepository {

    private val mutex = Mutex()
    private val memoryEntries = HashMap<String, ExchangeRateTable>()

    override suspend fun getQuoteForCountry(
        baseCurrency: String,
        countryIsoAlpha2: String?,
    ): CurrencyQuote? {
        val base = baseCurrency.trim().uppercase(Locale.ROOT)
        if (base.length != 3) return null

        val candidates = CountryCurrencyResolver.resolveCurrencyCandidates(countryIsoAlpha2)
        if (candidates.isEmpty() || candidates.any { it == base }) {
            return null
        }

        val table = resolveRateTable(base) ?: return null
        for (target in candidates) {
            val rate = table.rates[target]
            if (rate != null && rate.isFinite() && rate > 0.0) {
                return CurrencyQuoteFormatter.format(
                    baseCurrency = base,
                    localCurrency = target,
                    localPerBaseRate = rate,
                    fetchedAtEpochMs = table.fetchedAtEpochMs,
                )
            }
        }
        return null
    }

    private suspend fun resolveRateTable(base: String): ExchangeRateTable? = mutex.withLock {
        val now = clockMillis()
        val existing = memoryEntries[base] ?: loadValidDiskTable(base)?.also { loaded ->
            memoryEntries[base] = loaded
        }

        if (existing != null && now - existing.fetchedAtEpochMs in 0 until ttlMillis) {
            return@withLock existing
        }

        val fresh = runCatching { upstream.fetchLatestRates(base) }.getOrNull()
            ?.let { sanitizeTable(it, fallbackEpochMs = now) }
        if (fresh != null) {
            memoryEntries[base] = fresh
            runCatching { cacheStore.writeCached(fresh) }
            return@withLock fresh
        }

        if (existing != null && now - existing.fetchedAtEpochMs < staleGraceMillis) {
            existing
        } else {
            null
        }
    }

    private suspend fun loadValidDiskTable(base: String): ExchangeRateTable? {
        val cached = runCatching { cacheStore.readCached(base) }.getOrNull() ?: return null
        return sanitizeTable(cached, fallbackEpochMs = cached.fetchedAtEpochMs)
    }

    private fun sanitizeTable(
        raw: ExchangeRateTable,
        fallbackEpochMs: Long,
    ): ExchangeRateTable? {
        val normalizedBase = raw.baseCurrency.trim().uppercase(Locale.ROOT)
        if (normalizedBase.length != 3) return null
        val cleanedRates = buildMap {
            for ((code, value) in raw.rates) {
                val key = code.trim().uppercase(Locale.ROOT)
                if (key.length == 3 && value.isFinite() && value > 0.0) {
                    put(key, value)
                }
            }
        }
        if (cleanedRates.isEmpty()) return null
        val timestamp = if (raw.fetchedAtEpochMs > 0L) raw.fetchedAtEpochMs else fallbackEpochMs
        return ExchangeRateTable(
            baseCurrency = normalizedBase,
            rates = cleanedRates,
            fetchedAtEpochMs = timestamp,
        )
    }

    companion object {
        const val DEFAULT_TTL_MS: Long = 12L * 60L * 60L * 1_000L
        const val DEFAULT_STALE_GRACE_MS: Long = 7L * 24L * 60L * 60L * 1_000L
    }
}
