package rs.belgrade.tvcams.data.currency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.belgrade.tvcams.domain.currency.CurrencyCacheStore
import rs.belgrade.tvcams.domain.currency.CurrencyUpstreamClient
import rs.belgrade.tvcams.domain.currency.ExchangeRateTable
import java.io.File
import java.io.IOException
import java.util.Locale

class MultiMirrorCurrencyClient(
    private val httpClient: OkHttpClient,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val jsDelivrBaseUrl: String = DEFAULT_JSDELIVR_BASE_URL,
    private val cloudflareBaseUrl: String = DEFAULT_CLOUDFLARE_BASE_URL,
    private val openErApiBaseUrl: String = DEFAULT_OPEN_ER_BASE_URL,
) : CurrencyUpstreamClient {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun fetchLatestRates(baseCurrency: String): ExchangeRateTable? =
        withContext(Dispatchers.IO) {
            val baseUpper = baseCurrency.trim().uppercase(Locale.ROOT)
            if (baseUpper.length != 3 || !baseUpper.all { it in 'A'..'Z' }) {
                return@withContext null
            }
            val baseLower = baseUpper.lowercase(Locale.ROOT)

            fetchFawazMirror(
                endpointUrl = "${jsDelivrBaseUrl.trimEnd('/')}/$baseLower.min.json",
                baseUpper = baseUpper,
                baseLower = baseLower,
            ) ?: fetchFawazMirror(
                endpointUrl = "${cloudflareBaseUrl.trimEnd('/')}/$baseLower.min.json",
                baseUpper = baseUpper,
                baseLower = baseLower,
            ) ?: fetchOpenErMirror(
                endpointUrl = "${openErApiBaseUrl.trimEnd('/')}/$baseUpper",
                baseUpper = baseUpper,
            )
        }

    private fun fetchFawazMirror(
        endpointUrl: String,
        baseUpper: String,
        baseLower: String,
    ): ExchangeRateTable? {
        val rawBody = executeGetJson(endpointUrl) ?: return null
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull() ?: return null
        val ratesObject = root[baseLower]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        val parsedRates = extractValidRates(ratesObject)
        if (parsedRates.isEmpty()) return null
        return ExchangeRateTable(
            baseCurrency = baseUpper,
            rates = parsedRates,
            fetchedAtEpochMs = clockMillis(),
        )
    }

    private fun fetchOpenErMirror(
        endpointUrl: String,
        baseUpper: String,
    ): ExchangeRateTable? {
        val rawBody = executeGetJson(endpointUrl) ?: return null
        val root = runCatching { json.parseToJsonElement(rawBody).jsonObject }.getOrNull() ?: return null
        val status = root["result"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (status != "success") return null
        val ratesObject = root["rates"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
        val parsedRates = extractValidRates(ratesObject)
        if (parsedRates.isEmpty()) return null
        return ExchangeRateTable(
            baseCurrency = baseUpper,
            rates = parsedRates,
            fetchedAtEpochMs = clockMillis(),
        )
    }

    private fun executeGetJson(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun extractValidRates(ratesObj: JsonObject): Map<String, Double> = buildMap {
        for ((rawKey, element) in ratesObj) {
            val code = rawKey.trim().uppercase(Locale.ROOT)
            if (code.length != 3 || !code.all { it in 'A'..'Z' }) continue
            val value = runCatching { element.jsonPrimitive.doubleOrNull }.getOrNull() ?: continue
            if (value.isFinite() && value > 0.0) {
                put(code, value)
            }
        }
    }

    companion object {
        const val DEFAULT_JSDELIVR_BASE_URL: String =
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies"
        const val DEFAULT_CLOUDFLARE_BASE_URL: String =
            "https://latest.currency-api.pages.dev/v1/currencies"
        const val DEFAULT_OPEN_ER_BASE_URL: String =
            "https://open.er-api.com/v6/latest"
    }
}

class FileCurrencyCacheStore(
    private val cacheDir: File,
) : CurrencyCacheStore {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun readCached(baseCurrency: String): ExchangeRateTable? =
        withContext(Dispatchers.IO) {
            val code = baseCurrency.trim().uppercase(Locale.ROOT)
            if (code.length != 3 || !code.all { it in 'A'..'Z' }) return@withContext null
            val target = File(cacheDir, "$code.json")
            if (!target.isFile) return@withContext null
            runCatching {
                json.decodeFromString(ExchangeRateTable.serializer(), target.readText(Charsets.UTF_8))
            }.getOrNull()
        }

    override suspend fun writeCached(table: ExchangeRateTable) {
        withContext(Dispatchers.IO) {
            val code = table.baseCurrency.trim().uppercase(Locale.ROOT)
            if (code.length != 3 || !code.all { it in 'A'..'Z' }) return@withContext
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val tempFile = File(cacheDir, "$code.json.tmp")
            val finalFile = File(cacheDir, "$code.json")
            val encoded = json.encodeToString(ExchangeRateTable.serializer(), table)
            tempFile.writeText(encoded, Charsets.UTF_8)
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        }
    }
}
