package rs.belgrade.tvcams

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import rs.belgrade.tvcams.data.catalog.AssetCatalogSeedSource
import rs.belgrade.tvcams.data.catalog.FileCatalogCacheStore
import rs.belgrade.tvcams.data.catalog.OkHttpRemoteCatalogClient
import rs.belgrade.tvcams.data.favorites.DataStoreFavoritesRepository
import rs.belgrade.tvcams.data.weather.OpenMeteoWeatherClient
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.weather.GridCachedWeatherRepository
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val favoritesRepository: FavoritesRepository = DataStoreFavoritesRepository(
        appContext = application.applicationContext,
    )

    val weatherRepository: WeatherRepository = GridCachedWeatherRepository(
        upstream = OpenMeteoWeatherClient(httpClient = httpClient),
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

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.httpClient }))
            }
            .build()
}
