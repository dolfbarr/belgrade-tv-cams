package rs.belgrade.tvcams.data.catalog

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.belgrade.tvcams.domain.catalog.CatalogCacheStore
import rs.belgrade.tvcams.domain.catalog.CatalogSeedSource
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogClient
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogResult
import rs.belgrade.tvcams.domain.model.CatalogJsonCodec
import rs.belgrade.tvcams.domain.model.CatalogManifest
import java.io.File
import java.io.IOException

class AssetCatalogSeedSource(
    private val appContext: Context,
    private val assetFileName: String = "seed_catalog.v5.json",
) : CatalogSeedSource {
    override suspend fun readSeed(): CatalogManifest = withContext(Dispatchers.IO) {
        val raw = appContext.assets.open(assetFileName).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
        CatalogJsonCodec.decode(raw)
    }
}

class FileCatalogCacheStore(
    private val directory: File,
) : CatalogCacheStore {
    private val manifestFile = File(directory, "cached_catalog.v5.json")
    private val etagFile = File(directory, "cached_catalog.v5.etag")

    override suspend fun readCached(): CatalogManifest? = withContext(Dispatchers.IO) {
        if (!manifestFile.exists()) return@withContext null
        val raw = manifestFile.readText(Charsets.UTF_8)
        CatalogJsonCodec.decode(raw)
    }

    override suspend fun readETag(): String? = withContext(Dispatchers.IO) {
        if (!etagFile.exists()) return@withContext null
        etagFile.readText(Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    override suspend fun writeCached(manifest: CatalogManifest, etag: String?) {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val encoded = CatalogJsonCodec.encode(manifest)
            val tmpManifest = File(directory, "cached_catalog.v5.json.tmp")
            tmpManifest.writeText(encoded, Charsets.UTF_8)
            if (!tmpManifest.renameTo(manifestFile)) {
                manifestFile.writeText(encoded, Charsets.UTF_8)
                tmpManifest.delete()
            }

            if (etag != null && etag.isNotBlank()) {
                val tmpEtag = File(directory, "cached_catalog.v5.etag.tmp")
                tmpEtag.writeText(etag.trim(), Charsets.UTF_8)
                if (!tmpEtag.renameTo(etagFile)) {
                    etagFile.writeText(etag.trim(), Charsets.UTF_8)
                    tmpEtag.delete()
                }
            }
        }
    }
}

class OkHttpRemoteCatalogClient(
    private val httpClient: OkHttpClient,
    private val catalogUrl: String,
) : RemoteCatalogClient {
    override suspend fun fetchCatalog(ifNoneMatch: String?): RemoteCatalogResult =
        withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder()
                .url(catalogUrl)
                .get()
                .header("Accept", "application/json")
            if (!ifNoneMatch.isNullOrBlank()) {
                requestBuilder.header("If-None-Match", ifNoneMatch)
            }

            try {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    when {
                        response.code == 304 -> RemoteCatalogResult.NotModified
                        response.isSuccessful -> {
                            val bodyText = response.body?.string()
                                ?: return@withContext RemoteCatalogResult.Unreachable("Empty response body")
                            val decoded = runCatching { CatalogJsonCodec.decode(bodyText) }.getOrElse { err ->
                                return@withContext RemoteCatalogResult.Unreachable(
                                    "Invalid catalog JSON: ${err.message}",
                                )
                            }
                            RemoteCatalogResult.Updated(
                                manifest = decoded,
                                etag = response.header("ETag"),
                            )
                        }
                        else -> RemoteCatalogResult.Unreachable("HTTP ${response.code}")
                    }
                }
            } catch (ioe: IOException) {
                RemoteCatalogResult.Unreachable(ioe.message ?: "Network error")
            }
        }
}
