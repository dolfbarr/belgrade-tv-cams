package rs.belgrade.tvcams

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Path.Companion.toOkioPath
import rs.belgrade.tvcams.data.airquality.OpenMeteoAirQualityClient
import rs.belgrade.tvcams.data.catalog.AssetCatalogSeedSource
import rs.belgrade.tvcams.data.catalog.FileCatalogCacheStore
import rs.belgrade.tvcams.data.catalog.OkHttpRemoteCatalogClient
import rs.belgrade.tvcams.data.favorites.DataStoreFavoritesRepository
import rs.belgrade.tvcams.data.stream.OkHttpStreamUrlResolver
import rs.belgrade.tvcams.data.weather.OpenMeteoWeatherClient
import rs.belgrade.tvcams.domain.airquality.AirQualityRepository
import rs.belgrade.tvcams.domain.airquality.GridCachedAirQualityRepository
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.weather.GridCachedWeatherRepository
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import rs.belgrade.tvcams.tvchannel.TvHomeChannelSynchronizer
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Dedicated HTTP/1.1 client for HLS/snapshot origins (e.g. bl.rutube.ru / CCTV gateways)
    // that emit HTTP/1 Keep-Alive headers over HTTP/2 and trigger RFC 7540 stream errors.
    val mediaHttpClient: OkHttpClient = httpClient.newBuilder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .build()

    val streamUrlResolver: OkHttpStreamUrlResolver = OkHttpStreamUrlResolver(
        generalHttpClient = httpClient,
        mediaHttpClient = mediaHttpClient,
    )

    val favoritesRepository: FavoritesRepository = DataStoreFavoritesRepository(
        appContext = application.applicationContext,
    )

    val weatherRepository: WeatherRepository = GridCachedWeatherRepository(
        upstream = OpenMeteoWeatherClient(httpClient = httpClient),
    )

    val airQualityRepository: AirQualityRepository = GridCachedAirQualityRepository(
        upstream = OpenMeteoAirQualityClient(httpClient = httpClient),
    )

    val homeChannelSynchronizer: TvHomeChannelSynchronizer = TvHomeChannelSynchronizer(
        appContext = application.applicationContext,
    )

    val catalogCoordinator: CatalogBootstrapCoordinator = CatalogBootstrapCoordinator(
        seedSource = AssetCatalogSeedSource(application.applicationContext),
        cacheStore = FileCatalogCacheStore(File(application.filesDir, "catalog_cache")),
        remoteClient = OkHttpRemoteCatalogClient(
            httpClient = httpClient,
            catalogUrl = BuildConfig.DEFAULT_CATALOG_URL,
        ),
    )
}

class BelgradeTvApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val isLowRam = (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.isLowRamDevice == true
        val memPercent = if (isLowRam) 0.12 else 0.18
        return ImageLoader.Builder(context)
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_poster_cache").toOkioPath())
                    .maxSizeBytes(48L * 1024L * 1024L)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.mediaHttpClient }))
            }
            .build()
    }
}
