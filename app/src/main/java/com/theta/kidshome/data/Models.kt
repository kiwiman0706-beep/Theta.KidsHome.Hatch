package com.theta.kidshome.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** CALL：Google Meet などの通話リンク（どうがロック中も押せる） */
enum class TileType { APP, VIDEO, DRAWING, CALL, INSTRUMENT, EDUPAD, WORDPRO, PUZZLE, TYPING, MOJI, KUKU, MAP, TASU, DICT, SOROBAN, HISSAN, JAPAN }

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
    /** じかんわり：[げつ, か, すい, もく, きん, ど] ごとの かもく（1 じかんめ から） */
    val timetable: List<List<String>> = List(6) { emptyList() },
    /** ようび ごとの もちもの */
    val belongings: List<String> = List(6) { "" },
    /** ホームに はった ふせん（おえかき・わーぷろ から） */
    val stickers: List<Sticker> = emptyList(),
    /** 学年（0 = 小学校まえ）。gradeYear はそれを決めた年度（4 月に 自動で 1 つ上がる） */
    val grade: Int = 0,
    val gradeYear: Int = 0,
) {
    val hasTimetable get() = timetable.any { d -> d.any { it.isNotBlank() } }
}

enum class LockReason(val emoji: String, val title: String) {
    STUDY("📚", "べんきょうタイム"),
    MEAL("🍚", "ごはんタイム"),
    SLEEP("😴", "おやすみタイム"),
    REST("👀", "おめめ やすめタイム"),
}

data class AppConfig(
    val profiles: List<Profile>,
    val currentProfileId: String,
    /** おうちのひとゲートのかけ算：false = 2けた×1けた、true = 2けた×2けた */
    val hardGate: Boolean = false,
    val videoLockFrom: Long = 0L,
    val videoLockUntil: Long = 0L,
    val lockReason: LockReason = LockReason.STUDY,
    val autoUpdate: Boolean = true,
    /** 天気を出す場所（null なら天気を出さない） */
    val weather: WeatherPlace? = null,
    /** 「ウィジェット」タブに置いた Android のウィジェット */
    val widgetIds: List<Int> = emptyList(),
    /** おんがくの「ひらく」で起動するアプリ */
    val musicPackage: String? = null,
    /** おうちのひとからの呼びかけ（ntfy のトピック名） */
    val remoteTopic: String? = null,
    val remoteOn: Boolean = false,
    /** アプリの仕分け先 */
    val categories: List<AppCategory> = emptyList(),
    /** パッケージ名 → カテゴリーの id（または Categories.HIDDEN）。無いものは未分類（出さない） */
    val appCategory: Map<String, String> = emptyMap(),
    /** 親のスマホから設定するスプレッドシート（リンクを知っている全員が閲覧可） */
    val sheetUrl: String? = null,
    /** 端末の動画・音楽・おはなし のタブを出すか */
    val showVideos: Boolean = true,
    val showMusic: Boolean = true,
    val showBooks: Boolean = true,
    /** 子どもに見せないフォルダ（カメラで撮った動画など） */
    val hiddenFolders: Set<String> = setOf("Camera", "Screenshots", "Screen recordings", "WhatsApp Video", "LINE"),
    /** ピアノの れんしゅう に足した曲 */
    val songs: List<com.theta.kidshome.media.Song> = emptyList(),
    /** Google カレンダーなどの iCal の URL（ログインなしで予定を出す） */
    val calendarUrls: List<String> = emptyList(),
    /** ホームの かたち："room"（へや）か "tabs"（タブ） */
    val homeStyle: String = "room",
    /** よる（19 時〜6 時）は へや を くらくして「おやすみ」と いう（車で 見るなら オフ） */
    val nightDim: Boolean = false,
    /** 出さない 端末カレンダー（CalendarContract の id） */
    val hiddenCalendars: Set<String> = emptySet(),
    /** じしょ：子どもに 見せる 辞書 の レベル（1 こども向け・2 もっと くわしく・3 おとな向け も） */
    val dictLevel: Int = 3,
) {
    fun categoryOf(packageName: String): AppCategory? =
        appCategory[packageName]?.let { id -> categories.firstOrNull { it.id == id } }

    val currentProfile: Profile
        get() = profiles.firstOrNull { it.id == currentProfileId } ?: profiles.first()

    fun isVideoLocked(now: Long = System.currentTimeMillis()) = videoLockUntil > now && !Beta.on

    companion object {
        fun drawingTile() = Tile(type = TileType.DRAWING, label = "おえかき", emoji = "🎨")
        fun instrumentTile() = Tile(type = TileType.INSTRUMENT, label = "がっき", emoji = "🎹")
        fun edupadTile() = Tile(type = TileType.EDUPAD, label = "くるくる思考パッド", emoji = "🌀")
        fun wordproTile() = Tile(type = TileType.WORDPRO, label = "わーぷろ", emoji = "📝")
        fun puzzleTile() = Tile(type = TileType.PUZZLE, label = "パズル", emoji = "🧩")
        fun typingTile() = Tile(type = TileType.TYPING, label = "タイピング", emoji = "⌨️")
        fun mojiTile() = Tile(type = TileType.MOJI, label = "もじ れんしゅう", emoji = "✍️")
        fun mapTile() = Tile(type = TileType.MAP, label = "ちず", emoji = "🗺️")
        fun dictTile() = Tile(type = TileType.DICT, label = "じしょ", emoji = "📖")
        fun tasuTile() = Tile(type = TileType.TASU, label = "たしざん の ひょう", emoji = "➕")
        fun sorobanTile() = Tile(type = TileType.SOROBAN, label = "そろばん", emoji = "🧮")
        fun japanTile() = Tile(type = TileType.JAPAN, label = "にっぽん パズル", emoji = "🗾")
        fun hissanTile() = Tile(type = TileType.HISSAN, label = "ひっさん でんたく", emoji = "🔢")
        fun kukuTile() = Tile(type = TileType.KUKU, label = "くくの ひょう", emoji = "✖️")

        fun default(): AppConfig {
            val p1 = Profile(name = "おねえちゃん", themeId = "usagi", tiles = listOf(edupadTile(), drawingTile(), wordproTile(), puzzleTile(), instrumentTile()))
            val p2 = Profile(name = "いもうと", themeId = "neko", tiles = listOf(edupadTile(), drawingTile(), wordproTile(), puzzleTile(), instrumentTile()))
            return AppConfig(profiles = listOf(p1, p2), currentProfileId = p1.id)
        }
    }
}

/** ホームに はる ふせん。image は おえかき の絵（アプリの中のファイル名）、なければ text */
data class Sticker(
    val id: String = UUID.randomUUID().toString(),
    val image: String? = null,
    val text: String = "",
    /** 画面の中の位置（0〜1） */
    val x: Float = 0.5f,
    val y: Float = 0.6f,
    val rotation: Float = 0f,
    val color: Int = 0xFFFFF3A0.toInt(),
    /** スタンプライブラリの シール（うかぶ）。あれば image・text より先 */
    val stamp: String? = null,
)

fun Sticker.toJson(): JSONObject = JSONObject().apply {
    put("id", id); putOpt("image", image); put("text", text)
    put("x", x.toDouble()); put("y", y.toDouble()); put("rotation", rotation.toDouble()); put("color", color); putOpt("stamp", stamp)
}

fun stickerFromJson(o: JSONObject) = Sticker(
    id = o.getString("id"), image = if (o.has("image") && !o.isNull("image")) o.getString("image") else null,
    text = o.optString("text"), x = o.optDouble("x", 0.5).toFloat(), y = o.optDouble("y", 0.6).toFloat(),
    rotation = o.optDouble("rotation").toFloat(), color = o.optInt("color", 0xFFFFF3A0.toInt()),
    stamp = if (o.has("stamp") && !o.isNull("stamp")) o.getString("stamp") else null,
)

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
    put("timetable", JSONArray(timetable.map { JSONArray(it) }))
    put("belongings", JSONArray(belongings))
    put("stickers", JSONArray(stickers.map { it.toJson() }))
    put("grade", grade)
    put("gradeYear", gradeYear)
}

fun profileFromJson(o: JSONObject): Profile {
    val arr = o.optJSONArray("tiles") ?: JSONArray()
    return Profile(
        id = o.getString("id"),
        name = o.optString("name"),
        themeId = o.optString("themeId", "usagi"),
        tiles = (0 until arr.length()).map { tileFromJson(arr.getJSONObject(it)) },
        timetable = List(6) { d ->
            o.optJSONArray("timetable")?.optJSONArray(d)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        },
        belongings = List(6) { d -> o.optJSONArray("belongings")?.optString(d).orEmpty() },
        stickers = o.optJSONArray("stickers")?.let { a -> (0 until a.length()).map { stickerFromJson(a.getJSONObject(it)) } } ?: emptyList(),
        grade = o.optInt("grade"),
        gradeYear = o.optInt("gradeYear"),
    )
}

fun AppConfig.toJson(): JSONObject = JSONObject().apply {
    put("profiles", JSONArray(profiles.map { it.toJson() }))
    put("currentProfileId", currentProfileId)
    put("hardGate", hardGate)
    put("videoLockFrom", videoLockFrom)
    put("videoLockUntil", videoLockUntil)
    put("autoUpdate", autoUpdate)
    put("lockReason", lockReason.name)
    weather?.let { put("weather", JSONObject().put("name", it.name).put("lat", it.lat).put("lon", it.lon).put("loc", it.useLocation)) }
    put("widgetIds", JSONArray(widgetIds))
    putOpt("musicPackage", musicPackage)
    putOpt("remoteTopic", remoteTopic)
    put("remoteOn", remoteOn)
    put("categories", JSONArray(categories.map { c ->
        JSONObject().put("id", c.id).put("name", c.name).put("emoji", c.emoji)
            .put("visibleTo", JSONArray(c.visibleTo.toList())).put("isVideo", c.isVideo)
    }))
    put("appCategory", JSONObject(appCategory))
    putOpt("sheetUrl", sheetUrl)
    put("showVideos", showVideos)
    put("showMusic", showMusic)
    put("showBooks", showBooks)
    put("hiddenFolders", JSONArray(hiddenFolders.toList()))
    put("songs", JSONArray(songs.map { JSONObject().put("title", it.title).put("notes", it.notes) }))
    put("calendarUrls", JSONArray(calendarUrls))
    put("homeStyle", homeStyle)
    put("nightDim", nightDim)
    put("hiddenCalendars", JSONArray(hiddenCalendars.toList()))
    put("dictLevel", dictLevel)
}

fun appConfigFromJson(o: JSONObject): AppConfig {
    val arr = o.getJSONArray("profiles")
    val profiles = (0 until arr.length()).map { profileFromJson(arr.getJSONObject(it)) }
    require(profiles.isNotEmpty())
    return AppConfig(
        profiles = profiles,
        currentProfileId = o.optString("currentProfileId", profiles.first().id),
        hardGate = o.optBoolean("hardGate"),
        videoLockFrom = o.optLong("videoLockFrom"),
        videoLockUntil = o.optLong("videoLockUntil"),
        autoUpdate = o.optBoolean("autoUpdate", true),
        lockReason = runCatching { LockReason.valueOf(o.getString("lockReason")) }
            .getOrDefault(LockReason.STUDY),
        weather = o.optJSONObject("weather")?.let {
            WeatherPlace(it.optString("name"), it.optDouble("lat"), it.optDouble("lon"), it.optBoolean("loc"))
        },
        widgetIds = o.optJSONArray("widgetIds")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList(),
        musicPackage = o.str("musicPackage"),
        remoteTopic = o.str("remoteTopic"),
        remoteOn = o.optBoolean("remoteOn"),
        categories = o.optJSONArray("categories")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map { c ->
                val v = c.optJSONArray("visibleTo")
                AppCategory(
                    id = c.getString("id"), name = c.optString("name"), emoji = c.optString("emoji", "⭐"),
                    visibleTo = v?.let { (0 until it.length()).map { i -> it.getString(i) }.toSet() } ?: emptySet(),
                    isVideo = c.optBoolean("isVideo"),
                )
            }
        } ?: emptyList(),
        appCategory = o.optJSONObject("appCategory")?.let { m -> m.keys().asSequence().associateWith { m.getString(it) } } ?: emptyMap(),
        sheetUrl = o.str("sheetUrl"),
        showVideos = o.optBoolean("showVideos", true),
        showMusic = o.optBoolean("showMusic", true),
        showBooks = o.optBoolean("showBooks", true),
        hiddenFolders = o.optJSONArray("hiddenFolders")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            ?: AppConfig(emptyList(), "").hiddenFolders,
        songs = o.optJSONArray("songs")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map { com.theta.kidshome.media.Song(it.optString("title"), it.optString("notes")) }
        } ?: emptyList(),
        calendarUrls = o.optJSONArray("calendarUrls")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        homeStyle = o.optString("homeStyle", "room"),
        nightDim = o.optBoolean("nightDim", false),
        dictLevel = o.optInt("dictLevel", 3),
        hiddenCalendars = o.optJSONArray("hiddenCalendars")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
    )
}
