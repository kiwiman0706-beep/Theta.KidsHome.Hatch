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
        // 0.7.0：くるくる思考パッド を内蔵したので、前からの端末にも 1 回だけ置く
        if (!prefs.getBoolean("added:edupad", false)) {
            if (config.profiles.none { p -> p.tiles.any { it.type == TileType.EDUPAD } }) {
                update { c -> c.copy(profiles = c.profiles.map { it.copy(tiles = listOf(AppConfig.edupadTile()) + it.tiles) }) }
            }
            prefs.edit().putBoolean("added:edupad", true).apply()
        }
        // 0.9.0：わーぷろ
        if (!prefs.getBoolean("added:wordpro", false)) {
            if (config.profiles.none { p -> p.tiles.any { it.type == TileType.WORDPRO } }) {
                update { c -> c.copy(profiles = c.profiles.map { it.copy(tiles = it.tiles + AppConfig.wordproTile()) }) }
            }
            prefs.edit().putBoolean("added:wordpro", true).apply()
        }
        if (!prefs.getBoolean("added:puzzle", false)) {
            if (config.profiles.none { p -> p.tiles.any { it.type == TileType.PUZZLE } }) {
                update { c -> c.copy(profiles = c.profiles.map { it.copy(tiles = it.tiles + AppConfig.puzzleTile()) }) }
            }
            prefs.edit().putBoolean("added:puzzle", true).apply()
        }
        if (!prefs.getBoolean("added:typing", false)) {
            if (config.profiles.none { p -> p.tiles.any { it.type == TileType.TYPING } }) {
                update { c -> c.copy(profiles = c.profiles.map { it.copy(tiles = it.tiles + AppConfig.typingTile()) }) }
            }
            prefs.edit().putBoolean("added:typing", true).apply()
        }
    }

    /** いまの子のホームに ふせん をはる（少しずらして、少しかたむける） */
    fun addSticker(sticker: Sticker) = updateProfile(config.currentProfileId) { p ->
        val n = p.stickers.size
        p.copy(stickers = (p.stickers + sticker.copy(
            x = 0.38f + (n % 4) * 0.12f, y = 0.58f + (n / 4 % 3) * 0.1f,
            rotation = listOf(-6f, 4f, -2f, 7f, -5f)[n % 5],
        )).takeLast(12))
    }

    fun moveSticker(id: String, x: Float, y: Float) = updateProfile(config.currentProfileId) { p ->
        p.copy(stickers = p.stickers.map { if (it.id == id) it.copy(x = x.coerceIn(0f, 0.95f), y = y.coerceIn(0f, 0.95f)) else it })
    }

    fun removeSticker(context: Context, id: String) = updateProfile(config.currentProfileId) { p ->
        p.stickers.firstOrNull { it.id == id }?.image?.let { java.io.File(java.io.File(context.filesDir, "stickers"), it).delete() }
        p.copy(stickers = p.stickers.filter { it.id != id })
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
