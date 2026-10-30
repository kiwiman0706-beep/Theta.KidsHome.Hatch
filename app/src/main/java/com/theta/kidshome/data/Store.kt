package com.theta.kidshome.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** 設定の保存先。Compose から直接読めるように State で持つ */
object Store {
    private const val KEY = "config"
    private lateinit var prefs: SharedPreferences

    var config by mutableStateOf(AppConfig.default())
        private set

    /** レスキュー画面から「セーフモード」で開いたとき：アニメーションや遊びを止める（保存しない） */
    var safeMode by mutableStateOf(false)

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("kidshome", Context.MODE_PRIVATE)
        config = prefs.getString(KEY, null)
            ?.let { runCatching { appConfigFromJson(JSONObject(it)) }.getOrNull() }
            ?: AppConfig.default()
        // 0.6.0：がっき を足したので、前からの端末にも 1 回だけ置く
        if (!prefs.getBoolean("added:instrument", false)) {
            if (config.profiles.none { p -> p.tiles.any { it.type == TileType.INSTRUMENT } }) {
                update { c -> c.copy(profiles = c.profiles.map { it.copy(tiles = it.tiles + AppConfig.instrumentTile()) }) }
            }
            prefs.edit().putBoolean("added:instrument", true).apply()
        }
    }

    fun update(transform: (AppConfig) -> AppConfig) {
        config = transform(config)
        prefs.edit().putString(KEY, config.toJson().toString()).apply()
    }

    fun updateProfile(profileId: String, transform: (Profile) -> Profile) = update { c ->
        c.copy(profiles = c.profiles.map { if (it.id == profileId) transform(it) else it })
    }

    fun addTiles(profileId: String, tiles: List<Tile>) =
        updateProfile(profileId) { it.copy(tiles = it.tiles + tiles) }
}
