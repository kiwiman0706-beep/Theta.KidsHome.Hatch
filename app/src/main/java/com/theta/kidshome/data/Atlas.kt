package com.theta.kidshome.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** ちず の ばしょ。route は おうちからの 車の みち（調べてあれば） */
data class Place(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "📍",
    val lat: Double,
    val lon: Double,
    val memo: String = "",
    val home: Boolean = false,
    val route: List<LatLon> = emptyList(),
    val routeKm: Double = 0.0,
    val routeMin: Int = 0,
) {
    val at get() = LatLon(lat, lon)
}

enum class LineKind(val label: String, val emoji: String) { ROAD("こうそくどうろ", "🛣️"), RAIL("てつどう", "🚄"), RIVER("かわ", "🌊") }

/** 地図に かさねる 線（高速道路・線路・川）。segments は とぎれた 線のあつまり */
data class GeoLine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: LineKind,
    val color: Int,
    val segments: List<List<LatLon>>,
    val visible: Boolean = true,
)

/**
 * ちず の データ（おうちのひと が ふやす）。アプリの中の map.json に保存。
 * 地名さがし：Nominatim、みち：OSRM（routing.openstreetmap.de）、高速道路・線路：Overpass（どれも OpenStreetMap）。
 */
object Atlas {
    private lateinit var file: File
    var places by mutableStateOf<List<Place>>(emptyList())
        private set
    var lines by mutableStateOf<List<GeoLine>>(emptyList())
        private set
    /** "osm" / "gsi" / "pale" */
    var style by mutableStateOf("gsi")
        private set

    val home get() = places.firstOrNull { it.home }

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "map.json")
        runCatching { load(JSONObject(file.readText())) }
    }

    private fun load(o: JSONObject) {
        style = o.optString("style", "gsi")
        places = o.optJSONArray("places")?.objects()?.map { p ->
            Place(
                id = p.getString("id"), name = p.optString("name"), emoji = p.optString("emoji", "📍"),
                lat = p.getDouble("lat"), lon = p.getDouble("lon"), memo = p.optString("memo"), home = p.optBoolean("home"),
                route = decode(p.optString("route")), routeKm = p.optDouble("routeKm", 0.0), routeMin = p.optInt("routeMin"),
            )
        } ?: emptyList()
        lines = o.optJSONArray("lines")?.objects()?.map { l ->
            GeoLine(
                id = l.getString("id"), name = l.optString("name"),
                kind = runCatching { LineKind.valueOf(l.optString("kind")) }.getOrDefault(LineKind.ROAD),
                color = l.optInt("color"), visible = l.optBoolean("visible", true),
                segments = l.optJSONArray("segments")?.let { a -> (0 until a.length()).map { decode(a.getString(it)) } } ?: emptyList(),
            )
        } ?: emptyList()
    }

    private fun save() {
        val o = JSONObject().put("style", style)
            .put("places", JSONArray(places.map { p ->
                JSONObject().put("id", p.id).put("name", p.name).put("emoji", p.emoji).put("lat", p.lat).put("lon", p.lon)
                    .put("memo", p.memo).put("home", p.home).put("route", encode(p.route)).put("routeKm", p.routeKm).put("routeMin", p.routeMin)
            }))
            .put("lines", JSONArray(lines.map { l ->
                JSONObject().put("id", l.id).put("name", l.name).put("kind", l.kind.name).put("color", l.color).put("visible", l.visible)
                    .put("segments", JSONArray(l.segments.map(::encode)))
            }))
        if (::file.isInitialized) runCatching { file.writeText(o.toString()) }
    }

    fun chooseStyle(s: String) { style = s; save() }

    fun upsert(p: Place) {
        places = (if (places.any { it.id == p.id }) places.map { if (it.id == p.id) p else it } else places + p)
            .map { if (p.home && it.id != p.id && it.home) it.copy(home = false) else it }
        save()
    }

    fun remove(p: Place) { places = places.filter { it.id != p.id }; save() }

    fun addLine(l: GeoLine) { lines = lines + l; save() }
    fun updateLine(l: GeoLine) { lines = lines.map { if (it.id == l.id) l else it }; save() }
    fun removeLine(l: GeoLine) { lines = lines.filter { it.id != l.id }; save() }

    // ---------------------------------------------------------------- けいさん

    /** 2 点の きょり（km） */
    fun km(a: LatLon, b: LatLon): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(h))
    }

    /** 子ども向けの きょり（「1.2 キロ」「350 メートル」） */
    fun distanceText(km: Double): String = when {
        km < 1 -> "${(km * 1000 / 10).roundToInt() * 10} メートル"
        km < 10 -> "${"%.1f".format(km)} キロ"
        else -> "${km.roundToInt()} キロ"
    }

    fun timeText(min: Int): String = if (min < 60) "${min} ふん" else "${min / 60} じかん" + if (min % 60 > 0) " ${min % 60} ふん" else ""

    /** Douglas-Peucker で 点をへらす（tolerance は だいたいの メートル） */
    fun simplify(pts: List<LatLon>, toleranceM: Double): List<LatLon> {
        if (pts.size < 3) return pts
        val keep = BooleanArray(pts.size).also { it[0] = true; it[pts.lastIndex] = true }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to pts.lastIndex) }
        val kx = 111_320.0 * cos(Math.toRadians(pts[0].lat)); val ky = 110_540.0
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxD = 0.0; var idx = -1
            val ax = pts[s].lon * kx; val ay = pts[s].lat * ky; val bx = pts[e].lon * kx; val by = pts[e].lat * ky
            val dx = bx - ax; val dy = by - ay; val len2 = dx * dx + dy * dy
            for (i in s + 1 until e) {
                val px = pts[i].lon * kx; val py = pts[i].lat * ky
                val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
                val qx = ax + t * dx - px; val qy = ay + t * dy - py
                val d = sqrt(qx * qx + qy * qy)
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > toleranceM) { keep[idx] = true; stack.add(s to idx); stack.add(idx to e) }
        }
        return pts.filterIndexed { i, _ -> keep[i] }
    }

    /** 点の ならびを 小さな文字列に（緯度経度 5 けた、さぶん） */
    fun encode(pts: List<LatLon>): String {
        val sb = StringBuilder()
        var la = 0L; var lo = 0L
        pts.forEach {
            val a = Math.round(it.lat * 1e5); val b = Math.round(it.lon * 1e5)
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(a - la).append(',').append(b - lo)
            la = a; lo = b
        }
        return sb.toString()
    }

    fun decode(s: String): List<LatLon> {
        if (s.isBlank()) return emptyList()
        var la = 0L; var lo = 0L
        return s.split(';').mapNotNull { p ->
            val (a, b) = p.split(',').map { it.toLongOrNull() ?: return@mapNotNull null }
            la += a; lo += b
            LatLon(la / 1e5, lo / 1e5)
        }
    }

    /** とぎれとぎれの 線を、はしが ちかいもの どうし つなぐ */
    fun join(segs: List<List<LatLon>>, gapM: Double = 30.0): List<List<LatLon>> {
        val rest = segs.filter { it.size >= 2 }.toMutableList()
        val out = mutableListOf<List<LatLon>>()
        while (rest.isNotEmpty()) {
            var cur = rest.removeAt(0)
            var grew = true
            while (grew) {
                grew = false
                val it = rest.iterator()
                while (it.hasNext()) {
                    val s = it.next()
                    val g = gapM / 1000
                    cur = when {
                        km(cur.last(), s.first()) < g -> cur + s.drop(1)
                        km(cur.last(), s.last()) < g -> cur + s.reversed().drop(1)
                        km(cur.first(), s.last()) < g -> s + cur.drop(1)
                        km(cur.first(), s.first()) < g -> s.reversed() + cur.drop(1)
                        else -> continue
                    }
                    it.remove(); grew = true
                }
            }
            out += cur
        }
        return out
    }

    // ---------------------------------------------------------------- ネット

    private fun get(url: String, post: String? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 90_000
        c.setRequestProperty("User-Agent", "KidsHome/1.0 (com.theta.kidshome; family use)")
        c.setRequestProperty("Accept-Language", "ja")
        if (post != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { it.write(post.toByteArray()) }
        }
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    data class Found(val name: String, val detail: String, val lat: Double, val lon: Double)

    /** 地名・住所・お店の名前 で さがす */
    suspend fun search(q: String): List<Found> = withContext(Dispatchers.IO) {
        val a = JSONArray(get("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=10&accept-language=ja&countrycodes=jp&q=" + URLEncoder.encode(q.trim(), "UTF-8")))
        a.objects().map { r ->
            val full = r.optString("display_name")
            Found(r.optString("name").ifBlank { full.substringBefore(',') }, full, r.getString("lat").toDouble(), r.getString("lon").toDouble())
        }
    }

    /** 車の みち（点・km・ふん） */
    suspend fun route(from: LatLon, to: LatLon): Triple<List<LatLon>, Double, Int> = withContext(Dispatchers.IO) {
        val url = "https://routing.openstreetmap.de/routed-car/route/v1/driving/${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson"
        val r = JSONObject(get(url)).getJSONArray("routes").getJSONObject(0)
        val c = r.getJSONObject("geometry").getJSONArray("coordinates")
        val pts = (0 until c.length()).map { c.getJSONArray(it).let { p -> LatLon(p.getDouble(1), p.getDouble(0)) } }
        Triple(simplify(pts, 15.0), r.getDouble("distance") / 1000, (r.getDouble("duration") / 60).roundToInt())
    }

    /** 名前で 高速道路・線路・川 を さがして 線にする（日本の中） */
    suspend fun findLine(name: String, kind: LineKind): List<List<LatLon>> = withContext(Dispatchers.IO) {
        val n = name.trim().replace("\\", "").replace("\"", "")
        val filter = when (kind) {
            LineKind.ROAD -> "way(area.jp)[\"highway\"~\"^(motorway|trunk)$\"][\"name\"~\"$n\"];"
            LineKind.RAIL -> "way(area.jp)[\"railway\"~\"^(rail|subway|light_rail|monorail)$\"][\"name\"~\"$n\"];"
            LineKind.RIVER -> "way(area.jp)[\"waterway\"=\"river\"][\"name\"=\"$n\"];"
        }
        val q = "[out:json][timeout:120];area[\"ISO3166-1\"=\"JP\"][admin_level=2]->.jp;($filter);out geom;"
        val o = JSONObject(get("https://overpass-api.de/api/interpreter", "data=" + URLEncoder.encode(q, "UTF-8")))
        val segs = o.getJSONArray("elements").objects().mapNotNull { e ->
            e.optJSONArray("geometry")?.objects()?.map { LatLon(it.getDouble("lat"), it.getDouble("lon")) }
        }
        // 上り・下りの 2 本を 1 本に見えるくらいまで まびく
        join(segs).map { simplify(it, 40.0) }.filter { it.size >= 2 }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
