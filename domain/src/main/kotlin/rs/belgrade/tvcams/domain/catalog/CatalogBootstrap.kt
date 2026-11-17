package rs.belgrade.tvcams.domain.catalog

import rs.belgrade.tvcams.domain.model.CatalogJsonCodec
import rs.belgrade.tvcams.domain.model.CatalogManifest

interface CatalogSeedSource {
    suspend fun readSeed(): CatalogManifest
}

interface CatalogCacheStore {
    suspend fun readCached(): CatalogManifest?
    suspend fun readETag(): String?
    suspend fun writeCached(manifest: CatalogManifest, etag: String?)
}

sealed interface RemoteCatalogResult {
    data class Updated(val manifest: CatalogManifest, val etag: String?) : RemoteCatalogResult
    data object NotModified : RemoteCatalogResult
    data class Unreachable(val reason: String) : RemoteCatalogResult
}

interface RemoteCatalogClient {
    suspend fun fetchCatalog(ifNoneMatch: String?): RemoteCatalogResult
}

sealed interface CatalogSyncOutcome {
    data class Applied(val manifest: CatalogManifest) : CatalogSyncOutcome
    data object UpToDate : CatalogSyncOutcome
    data class Rejected(val reason: String) : CatalogSyncOutcome
    data class Offline(val reason: String) : CatalogSyncOutcome
}

class CatalogBootstrapCoordinator(
    private val seedSource: CatalogSeedSource,
    private val cacheStore: CatalogCacheStore,
    private val remoteClient: RemoteCatalogClient,
) {
    suspend fun loadInitial(): CatalogManifest {
        val seed = seedSource.readSeed()
        val cached = runCatching { cacheStore.readCached() }.getOrNull()
        if (cached != null && isValidManifest(cached) && cached.revision >= seed.revision) {
            return cached
        }
        return seed
    }

    suspend fun syncRemote(currentRevision: Long): CatalogSyncOutcome {
        val previousEtag = runCatching { cacheStore.readETag() }.getOrNull()
        return when (val result = remoteClient.fetchCatalog(ifNoneMatch = previousEtag)) {
            is RemoteCatalogResult.NotModified -> CatalogSyncOutcome.UpToDate
            is RemoteCatalogResult.Unreachable -> CatalogSyncOutcome.Offline(result.reason)
            is RemoteCatalogResult.Updated -> {
                val incoming = result.manifest
                when {
                    !isValidManifest(incoming) ->
                        CatalogSyncOutcome.Rejected("Remote manifest failed schema or non-empty camera check")
                    incoming.revision < currentRevision ->
                        CatalogSyncOutcome.Rejected("Remote revision ${incoming.revision} older than $currentRevision")
                    else -> {
                        cacheStore.writeCached(incoming, result.etag)
                        CatalogSyncOutcome.Applied(incoming)
                    }
                }
            }
        }
    }

    private fun isValidManifest(manifest: CatalogManifest): Boolean =
        manifest.schemaVersion == CatalogJsonCodec.SUPPORTED_SCHEMA_VERSION &&
            manifest.cameras.isNotEmpty() &&
            manifest.cameras.all { it.id.isNotBlank() && it.streams.isNotEmpty() }
}
