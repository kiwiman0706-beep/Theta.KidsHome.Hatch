package com.theta.kidshome.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 親のスマホから設定する：Google スプレッドシート（「リンクを知っている全員」が閲覧可）を読んで、ホームに反映する。
 * タブレットはログインしない。シートを書き換えられるのは、シートの持ち主（親）だけ。
 * シートが無い・読めない表は、タブレットの今の設定のまま。
 */
object SheetSync {
    /** シートの名前と、1 行目（見出し） */
    val CHILDREN = listOf("なまえ", "テーマ")
    val BUTTONS = listOf("こども", "しゅるい", "なまえ", "URL・アプリ", "えもじ", "どうが")
    val CATEGORIES = listOf("なまえ", "えもじ", "みせる", "どうが")
    val APPS = listOf("アプリ名", "パッケージ", "カテゴリー")
    val SETTINGS = listOf("こうもく", "あたい")
    val TIMETABLE = listOf("こども", "ようび", "1", "2", "3", "4", "5", "6", "もちもの")
    val sheets = linkedMapOf(
        "こども" to CHILDREN, "ボタン" to BUTTONS, "カテゴリー" to CATEGORIES, "アプリ" to APPS, "せってい" to SETTINGS,
        "じかんわり" to TIMETABLE,
    )
    private val everyone = setOf("ぜんいん", "みんな", "全員", "")
    private val yes = setOf("○", "◯", "はい", "yes", "true", "1", "o", "✓", "✔")

    data class State(val syncing: Boolean = false, val syncedAt: Long = 0L, val error: String? = null, val note: String? = null)

    var state by mutableStateOf(State())
        private set
    private val scope = MainScope()

    fun sheetId(url: String): String? = Regex("/spreadsheets/d/([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)

    /** 15 分に 1 回まで取り直す */
    fun sync(context: Context, force: Boolean = false) {
        val url = Store.config.sheetUrl ?: return
        val id = sheetId(url) ?: run { state = State(error = "スプレッドシートの URL ではありません"); return }
        if (state.syncing || (!force && System.currentTimeMillis() - state.syncedAt < 15 * 60_000)) return
        state = state.copy(syncing = true, error = null)
        val app = context.applicationContext
        scope.launch {
            val result = runCatching {
                val data = withContext(Dispatchers.IO) {
                    sheets.mapValues { (name, header) -> runCatching { fetch(id, name, header) }.getOrNull() }
                }
                if (data.values.all { it == null }) throw IOException("シートを読めませんでした（共有が「リンクを知っている全員」になっているか確かめてください）")
                val weatherName = data["せってい"]?.firstOrNull { it.getOrNull(0) == "てんき" }?.getOrNull(1)?.trim()
                val place = resolveWeather(weatherName)
                val applied = apply(Store.config, data, place) { resolvePackage(app, it) }
                Store.update { applied }
                if (place != null || weatherName != null) Weather.refresh(app, force = true)
                data.filterValues { it == null }.keys
            }
            state = State(
                syncedAt = System.currentTimeMillis(),
                error = result.exceptionOrNull()?.message,
                note = result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { "読めなかった表：${it.joinToString("・")}（そのままにしました）" },
            )
        }
    }

    private suspend fun resolveWeather(name: String?): WeatherPlace? {
        if (name.isNullOrBlank() || name == Store.config.weather?.name) return Store.config.weather
        return when (name) {
            "なし" -> null
            "いまいるところ", "現在地" -> WeatherPlace("いまいる ところ", 35.68, 139.76, useLocation = true)
            else -> runCatching { Weather.search(name).firstOrNull() }.getOrNull()?.copy(name = name) ?: Store.config.weather
        }
    }

    /** アプリ名でも書けるように：パッケージ名でなければ、入っているアプリの名前から探す */
    private fun resolvePackage(context: Context, value: String): String? {
        val v = value.trim()
        if (v.isEmpty()) return null
        if (v.contains('.') && !v.contains(' ')) return v
        return Launcher.listApps(context).firstOrNull { it.label == v }?.packageName
            ?: Launcher.listApps(context).firstOrNull { it.label.contains(v) }?.packageName
    }

    // ---------------------------------------------------------------- 読む

    private fun fetch(id: String, name: String, header: List<String>): List<List<String>>? {
        val url = "https://docs.google.com/spreadsheets/d/$id/gviz/tq?tqx=out:csv&headers=1&sheet=" + URLEncoder.encode(name, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        if (c.responseCode / 100 != 2) throw IOException("HTTP ${c.responseCode}")
        if (c.contentType?.contains("csv") != true) throw IOException("共有されていません")
        val rows = parseCsv(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        // 名前の違うシートを頼むと、Google は先頭のシートを返すので、見出しで確かめる
        val head = rows.firstOrNull()?.map { it.trim() } ?: return null
        if (head.getOrNull(0) != header[0] || head.getOrNull(1) != header[1]) return null
        return rows.drop(1).filter { r -> r.any { it.isNotBlank() } }
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(ch)
            } else when (ch) {
                '"' -> quoted = true
                ',' -> { row += cell.toString(); cell.clear() }
                '\r' -> {}
                '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = mutableListOf() }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows
    }

    // ---------------------------------------------------------------- 反映する

    private fun stableId(vararg parts: String) = UUID.nameUUIDFromBytes(parts.joinToString("\u0001").toByteArray()).toString()

    private fun themeIdOf(value: String, fallback: String): String {
        val v = value.trim()
        return com.theta.kidshome.ui.KidThemes.firstOrNull { it.name == v || it.id == v }?.id ?: fallback
    }

    /** シートの中身を設定に重ねる（null の表はそのまま） */
    fun apply(
        config: AppConfig,
        data: Map<String, List<List<String>>?>,
        weather: WeatherPlace? = config.weather,
        resolvePackage: (String) -> String? = { it },
    ): AppConfig {
        var c = config
        fun List<String>.at(i: Int) = getOrNull(i)?.trim().orEmpty()

        data["こども"]?.takeIf { it.isNotEmpty() }?.let { rows ->
            val used = mutableSetOf<String>()
            val profiles = rows.mapIndexedNotNull { i, r ->
                val name = r.at(0).ifEmpty { return@mapIndexedNotNull null }
                val old = c.profiles.firstOrNull { it.name == name && it.id !in used }
                    ?: c.profiles.getOrNull(i)?.takeIf { it.id !in used }
                val p = (old ?: Profile(name = name, themeId = "usagi", tiles = listOf(AppConfig.drawingTile())))
                    .let { it.copy(name = name, themeId = themeIdOf(r.at(1), it.themeId)) }
                used += p.id
                p
            }
            if (profiles.isNotEmpty()) {
                c = c.copy(
                    profiles = profiles,
                    currentProfileId = profiles.firstOrNull { it.id == c.currentProfileId }?.id ?: profiles.first().id,
                )
            }
        }

        data["ボタン"]?.let { rows ->
            c = c.copy(profiles = c.profiles.map { p ->
                val tiles = rows.mapIndexedNotNull { i, r ->
                    val who = r.at(0)
                    if (who !in everyone && who.split(',', '、', '，').map { it.trim() }.none { it == p.name }) return@mapIndexedNotNull null
                    val kind = r.at(1)
                    val label = r.at(2)
                    val target = r.at(3)
                    val emoji = r.at(4).ifEmpty { null }
                    val video = r.at(5).lowercase() in yes
                    val id = stableId(p.id, i.toString(), kind, label, target)
                    when (kind) {
                        "どうが" -> if (target.isEmpty()) null else Tile(
                            id = id, type = TileType.VIDEO, label = label.ifEmpty { "どうが" }, url = target,
                            packageName = Launcher.detectService(target)?.packageName, emoji = emoji ?: "🎬", isVideo = true,
                        )
                        "アプリ" -> resolvePackage(target)?.let { pkg ->
                            Tile(id = id, type = TileType.APP, label = label.ifEmpty { target }, packageName = pkg, emoji = emoji ?: "⭐",
                                isVideo = video || pkg in Launcher.videoPackages)
                        }
                        "おえかき" -> Tile(id = id, type = TileType.DRAWING, label = label.ifEmpty { "おえかき" }, emoji = emoji ?: "🎨")
                        "がっき" -> Tile(id = id, type = TileType.INSTRUMENT, label = label.ifEmpty { "がっき" }, emoji = emoji ?: "🎹")
                        "ちず" -> Tile(id = id, type = TileType.MAP, label = label.ifEmpty { "ちず" }, emoji = emoji ?: "🗺️")
                        "たしざん" -> Tile(id = id, type = TileType.TASU, label = label.ifEmpty { "たしざん の ひょう" }, emoji = emoji ?: "➕")
                        "くく" -> Tile(id = id, type = TileType.KUKU, label = label.ifEmpty { "くくの ひょう" }, emoji = emoji ?: "✖️")
                        "もじ" -> Tile(id = id, type = TileType.MOJI, label = label.ifEmpty { "もじ れんしゅう" }, emoji = emoji ?: "✍️")
                        "タイピング" -> Tile(id = id, type = TileType.TYPING, label = label.ifEmpty { "タイピング" }, emoji = emoji ?: "⌨️")
                        "パズル" -> Tile(id = id, type = TileType.PUZZLE, label = label.ifEmpty { "パズル" }, emoji = emoji ?: "🧩")
                        "わーぷろ" -> Tile(id = id, type = TileType.WORDPRO, label = label.ifEmpty { "わーぷろ" }, emoji = emoji ?: "📝")
                        "くるくる" -> Tile(id = id, type = TileType.EDUPAD, label = label.ifEmpty { "くるくる思考パッド" }, emoji = emoji ?: "🧠")
                        "でんわ" -> if (target.isEmpty()) null else Tile(
                            id = id, type = TileType.CALL, label = label.ifEmpty { "でんわ" }, url = target, emoji = emoji ?: "📞",
                        )
                        else -> null
                    }
                }
                p.copy(tiles = tiles)
            })
        }

        data["じかんわり"]?.let { rows ->
            c = c.copy(profiles = c.profiles.map { p ->
                val table = MutableList(6) { emptyList<String>() }
                val bring = MutableList(6) { "" }
                rows.filter { it.at(0) == p.name || it.at(0) in everyone }.forEach { r ->
                    val d = com.theta.kidshome.ui.SchoolDays.indexOf(r.at(1).removeSuffix("よう").removeSuffix("ようび"))
                        .takeIf { it >= 0 } ?: listOf("月", "火", "水", "木", "金", "土").indexOf(r.at(1).take(1))
                    if (d < 0) return@forEach
                    table[d] = (2..7).map { r.at(it) }.dropLastWhile { it.isBlank() }
                    bring[d] = r.at(8)
                }
                p.copy(timetable = table, belongings = bring)
            })
        }

        data["カテゴリー"]?.let { rows ->
            val cats = rows.mapNotNull { r ->
                val name = r.at(0).ifEmpty { return@mapNotNull null }
                if (name == "かくす") return@mapNotNull null
                val who = r.at(2)
                val visible = when {
                    who in setOf("ぜんいん", "みんな", "全員") -> c.profiles.map { it.id }.toSet()
                    who.isEmpty() || who == "かくす" -> emptySet()
                    else -> who.split(',', '、', '，').map { it.trim() }.mapNotNull { n -> c.profiles.firstOrNull { it.name == n }?.id }.toSet()
                }
                AppCategory(
                    id = c.categories.firstOrNull { it.name == name }?.id ?: stableId("category", name),
                    name = name, emoji = r.at(1).ifEmpty { "⭐" }, visibleTo = visible, isVideo = r.at(3).lowercase() in yes,
                )
            }
            c = c.copy(categories = cats)
        }

        data["アプリ"]?.let { rows ->
            val map = mutableMapOf<String, String>()
            rows.forEach { r ->
                val pkg = r.at(1).ifEmpty { resolvePackage(r.at(0)) ?: return@forEach }
                when (val cat = r.at(2)) {
                    "" -> {}
                    "かくす" -> map[pkg] = Categories.HIDDEN
                    else -> c.categories.firstOrNull { it.name == cat }?.let { map[pkg] = it.id }
                }
            }
            c = c.copy(appCategory = map)
        }

        data["せってい"]?.let { rows ->
            val cals = rows.filter { it.at(0) == "カレンダー" }.map { it.at(1) }.filter { it.isNotBlank() }
            if (cals.isNotEmpty()) c = c.copy(calendarUrls = cals)
            rows.forEach { r ->
                when (r.at(0)) {
                    "かけ算" -> c = c.copy(hardGate = r.at(1) == "むずかしい")
                    "てんき" -> c = c.copy(weather = weather)
                }
            }
        }
        return c
    }

    // ---------------------------------------------------------------- 書き出す（最初のシートの元）

    /** 今の設定（と入っているアプリ）を、シートと同じ形の表にする */
    fun tables(context: Context, config: AppConfig): Map<String, List<List<String>>> {
        val apps = Launcher.listApps(context)
        fun who(p: Profile?) = p?.name ?: "ぜんいん"
        return linkedMapOf(
            "こども" to listOf(CHILDREN) + config.profiles.map { listOf(it.name, com.theta.kidshome.ui.themeById(it.themeId).name) },
            "ボタン" to listOf(BUTTONS) + config.profiles.flatMap { p ->
                p.tiles.map { t ->
                    listOf(
                        who(p),
                        when (t.type) {
                            TileType.VIDEO -> "どうが"; TileType.APP -> "アプリ"; TileType.DRAWING -> "おえかき"; TileType.CALL -> "でんわ"; TileType.INSTRUMENT -> "がっき"; TileType.EDUPAD -> "くるくる"; TileType.WORDPRO -> "わーぷろ"; TileType.PUZZLE -> "パズル"; TileType.TYPING -> "タイピング"; TileType.MOJI -> "もじ"; TileType.KUKU -> "くく"; TileType.MAP -> "ちず"; TileType.TASU -> "たしざん"
                        },
                        t.label,
                        when (t.type) { TileType.APP -> t.packageName.orEmpty(); else -> t.url.orEmpty() },
                        t.emoji,
                        if (t.type == TileType.APP && t.isVideo) "○" else "",
                    )
                }
            },
            "カテゴリー" to listOf(CATEGORIES) + config.categories.map { cat ->
                val names = config.profiles.filter { it.id in cat.visibleTo }.map { it.name }
                listOf(
                    cat.name, cat.emoji,
                    when {
                        names.isEmpty() -> "かくす"
                        names.size == config.profiles.size -> "ぜんいん"
                        else -> names.joinToString(", ")
                    },
                    if (cat.isVideo) "○" else "",
                )
            },
            "アプリ" to listOf(APPS) + apps.map { a ->
                val id = config.appCategory[a.packageName]
                listOf(a.label, a.packageName, if (id == Categories.HIDDEN) "かくす" else config.categories.firstOrNull { it.id == id }?.name.orEmpty())
            },
            "じかんわり" to listOf(TIMETABLE) + config.profiles.flatMap { p ->
                com.theta.kidshome.ui.SchoolDays.indices.map { d ->
                    listOf(p.name, com.theta.kidshome.ui.SchoolDays[d]) + (0 until 6).map { p.timetable[d].getOrNull(it).orEmpty() } + p.belongings[d]
                }
            },
            "せってい" to listOf(
                SETTINGS,
                listOf("かけ算", if (config.hardGate) "むずかしい" else "ふつう"),
                listOf("てんき", config.weather?.let { if (it.useLocation) "いまいるところ" else it.name } ?: "なし"),
            ) + config.calendarUrls.map { listOf("カレンダー", it) },
        )
    }

    /** 表を .xlsx にする（Google ドライブで開いて「Google スプレッドシートとして保存」できる） */
    fun writeXlsx(tables: Map<String, List<List<String>>>, out: File) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun col(i: Int): String = if (i < 26) ('A' + i).toString() else col(i / 26 - 1) + ('A' + i % 26)
        val names = tables.keys.toList()
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(path: String, body: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(body.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
                    """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""" +
                    """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                    names.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
                    "</Types>",
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                    names.withIndex().joinToString("") { (i, n) -> """<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" } +
                    "</sheets></workbook>",
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                    names.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } +
                    "</Relationships>",
            )
            names.forEachIndexed { si, n ->
                val rows = tables.getValue(n)
                val body = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
                rows.forEachIndexed { ri, r ->
                    body.append("""<row r="${ri + 1}">""")
                    r.forEachIndexed { ci, v ->
                        body.append("""<c r="${col(ci)}${ri + 1}" t="inlineStr"><is><t xml:space="preserve">${esc(v)}</t></is></c>""")
                    }
                    body.append("</row>")
                }
                body.append("</sheetData></worksheet>")
                put("xl/worksheets/sheet${si + 1}.xml", body.toString())
            }
        }
    }
}
