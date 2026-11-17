package rs.belgrade.tvcams.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.favorites.ViewerPreferences
import rs.belgrade.tvcams.domain.favorites.ZapScope

private val Context.viewerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "viewer_preferences",
)

class DataStoreFavoritesRepository(
    private val appContext: Context,
) : FavoritesRepository {

    override val preferences: Flow<ViewerPreferences> =
        appContext.viewerPreferencesDataStore.data.map { prefs ->
            val rawScope = prefs[KEY_ZAP_SCOPE]
            val scope = runCatching {
                if (rawScope != null) ZapScope.valueOf(rawScope) else ZapScope.ALL
            }.getOrDefault(ZapScope.ALL)

            ViewerPreferences(
                favoriteIds = prefs[KEY_FAVORITE_IDS].orEmpty(),
                lastCameraId = prefs[KEY_LAST_CAMERA_ID],
                zapScope = scope,
                autoHideHud = prefs[KEY_AUTO_HIDE_HUD] ?: true,
            )
        }

    override suspend fun setFavorites(favoriteIds: Set<String>) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_FAVORITE_IDS] = favoriteIds.toSet()
        }
    }

    override suspend fun setLastCamera(cameraId: String) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_LAST_CAMERA_ID] = cameraId
        }
    }

    override suspend fun setZapScope(scope: ZapScope) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_ZAP_SCOPE] = scope.name
        }
    }

    private companion object {
        val KEY_FAVORITE_IDS = stringSetPreferencesKey("favorite_camera_ids")
        val KEY_LAST_CAMERA_ID = stringPreferencesKey("last_camera_id")
        val KEY_ZAP_SCOPE = stringPreferencesKey("zap_scope")
        val KEY_AUTO_HIDE_HUD = booleanPreferencesKey("auto_hide_hud")
    }
}
