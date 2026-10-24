package com.theta.kidshome.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class TileType { APP, VIDEO, DRAWING }

/** ホーム画面に並ぶ1つのボタン */
data class Tile(
    val id: String = UUID.randomUUID().toString(),
    val type: TileType,
    val label: String,
    /** APP: 起動するアプリ / VIDEO: 優先して開くアプリ(nullならOSにおまかせ) */
    val packageName: String? = null,
    /** VIDEO: 作品ページのURL */
    val url: String? = null,
    val emoji: String = "⭐",
    /** true なら「どうがロック」中は押せない */
    val isVideo: Boolean = false,
)

data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val themeId: String,
    val tiles: List<Tile>,
)

enum class LockReason(val emoji: String, val title: String) {
    STUDY("📚", "べんきょうタイム"),
    MEAL("🍚", "ごはんタイム"),
    SLEEP("😴", "おやすみタイム"),
    REST("👀", "おめめ やすめタイム"),
}

data class AppConfig(
    val profiles: List<Profile>,
    val currentProfileId: String,
    val pin: String? = null,
    val videoLockUntil: Long = 0L,
    val lockReason: LockReason = LockReason.STUDY,
) {
    val currentProfile: Profile
        get() = profiles.firstOrNull { it.id == currentProfileId } ?: profiles.first()

    fun isVideoLocked(now: Long = System.currentTimeMillis()) = videoLockUntil > now

    companion object {
        fun drawingTile() = Tile(type = TileType.DRAWING, label = "おえかき", emoji = "🎨")

        fun default(): AppConfig {
            val p1 = Profile(name = "おねえちゃん", themeId = "usagi", tiles = listOf(drawingTile()))
            val p2 = Profile(name = "いもうと", themeId = "neko", tiles = listOf(drawingTile()))
            return AppConfig(profiles = listOf(p1, p2), currentProfileId = p1.id)
        }
    }
}

// ---- JSON 変換 ----

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

fun Tile.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("type", type.name)
    put("label", label)
    putOpt("packageName", packageName)
    putOpt("url", url)
    put("emoji", emoji)
    put("isVideo", isVideo)
}

fun tileFromJson(o: JSONObject) = Tile(
    id = o.getString("id"),
    type = runCatching { TileType.valueOf(o.getString("type")) }.getOrDefault(TileType.APP),
    label = o.optString("label"),
    packageName = o.str("packageName"),
    url = o.str("url"),
    emoji = o.optString("emoji", "⭐"),
    isVideo = o.optBoolean("isVideo"),
)

fun Profile.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("themeId", themeId)
    put("tiles", JSONArray(tiles.map { it.toJson() }))
}

fun profileFromJson(o: JSONObject): Profile {
    val arr = o.optJSONArray("tiles") ?: JSONArray()
    return Profile(
        id = o.getString("id"),
        name = o.optString("name"),
        themeId = o.optString("themeId", "usagi"),
        tiles = (0 until arr.length()).map { tileFromJson(arr.getJSONObject(it)) },
    )
}

fun AppConfig.toJson(): JSONObject = JSONObject().apply {
    put("profiles", JSONArray(profiles.map { it.toJson() }))
    put("currentProfileId", currentProfileId)
    putOpt("pin", pin)
    put("videoLockUntil", videoLockUntil)
    put("lockReason", lockReason.name)
}

fun appConfigFromJson(o: JSONObject): AppConfig {
    val arr = o.getJSONArray("profiles")
    val profiles = (0 until arr.length()).map { profileFromJson(arr.getJSONObject(it)) }
    require(profiles.isNotEmpty())
    return AppConfig(
        profiles = profiles,
        currentProfileId = o.optString("currentProfileId", profiles.first().id),
        pin = o.str("pin"),
        videoLockUntil = o.optLong("videoLockUntil"),
        lockReason = runCatching { LockReason.valueOf(o.getString("lockReason")) }
            .getOrDefault(LockReason.STUDY),
    )
}
