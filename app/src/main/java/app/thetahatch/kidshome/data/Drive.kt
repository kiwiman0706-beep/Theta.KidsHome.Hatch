package app.thetahatch.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 🚗 いま どこ？（ドライブ マップ）の データ。
 * 路線図：assets/drive/kinki.json（OpenStreetMap、tools/build_drive_map.py・CI の drive-map で 作る）
 * 座標 は だいたい km（近畿 の まんなか で）に して あつかう：x = 東 へ、y = 北 へ。
 */
object DriveMap {
    class Route(val name: String, val ref: String, val city: Boolean, val lines: List<FloatArray>)
    /** kind：ic・jct・sa・pa */
    class Point(val name: String, val kind: String, val lat: Double, val lon: Double, val routes: List<Int>) {
        val x get() = kmX(lon)
        val y get() = kmY(lat)
    }
    class Pref(val code: Int, val kinki: Boolean, val polys: List<FloatArray>)
    class Data(
        val bbox: DoubleArray, val routes: List<Route>, val points: List<Point>, val prefs: List<Pref>,
        val attribution: String, val made: String,
    )

    private const val K = 0.82181  // cos(34.7°)
    fun kmX(lon: Double) = lon * 111.32 * K
    fun kmY(lat: Double) = lat * 110.57

    @Volatile private var cache: Data? = null

    /** 地図 データ（なければ null。主スレッドで呼ばない） */
    fun load(context: Context): Data? {
        cache?.let { return it }
        val o = runCatching { context.assets.open("drive/kinki.json").use { JSONObject(it.bufferedReader().readText()) } }.getOrNull() ?: return null
        fun floats(a: JSONArray) = FloatArray(a.length()) { a.getDouble(it).toFloat() }
        val routes = o.getJSONArray("routes").let { a ->
            List(a.length()) { i ->
                val r = a.getJSONObject(i)
                val ls = r.getJSONArray("lines")
                Route(r.optString("name"), r.optString("ref"), r.optBoolean("city"), List(ls.length()) { floats(ls.getJSONArray(it)) })
            }
        }
        val points = o.getJSONArray("points").let { a ->
            List(a.length()) { i ->
                val p = a.getJSONObject(i)
                val rs = p.optJSONArray("routes") ?: JSONArray()
                Point(p.getString("name"), p.optString("kind", "ic"), p.getDouble("lat"), p.getDouble("lon"), List(rs.length()) { rs.getInt(it) })
            }
        }
        val prefs = o.getJSONArray("prefs").let { a ->
            List(a.length()) { i ->
                val p = a.getJSONObject(i)
                val ps = p.getJSONArray("polys")
                Pref(p.getInt("code"), p.optBoolean("kinki"), List(ps.length()) { floats(ps.getJSONArray(it)) })
            }
        }
        val bb = o.getJSONArray("bbox")
        return Data(DoubleArray(4) { bb.getDouble(it) }, routes, points, prefs, o.optString("attribution"), o.optString("made")).also { cache = it }
    }

    /** いま の ようす */
    class Where(
        /** のって いる（いちばん 近い、600m いない の）路線 */
        val route: Route?,
        /** その 都道府県（コード） */
        val pref: Int?,
        /** すすむ 方向 の つぎ の IC など（と、そこ まで の km） */
        val next: Point?, val nextKm: Double,
        /** いちばん 近い IC（高速 に のって いない とき に 出す） */
        val nearest: Point?, val nearestKm: Double,
    )

    private fun segDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0.0, 1.0)
        return hypot(px - ax - t * dx, py - ay - t * dy)
    }

    /** heading は 北 から 時計まわり の 度（わからない とき は null） */
    fun where(d: Data, lat: Double, lon: Double, heading: Float?): Where {
        val px = kmX(lon); val py = kmY(lat)
        var best = -1; var bestD = Double.MAX_VALUE
        d.routes.forEachIndexed { i, r ->
            for (l in r.lines) {
                var k = 2
                while (k < l.size) {
                    val dd = segDist(px, py, kmX(l[k - 1].toDouble()), kmY(l[k - 2].toDouble()), kmX(l[k + 1].toDouble()), kmY(l[k].toDouble()))
                    if (dd < bestD) { bestD = dd; best = i }
                    k += 2
                }
            }
        }
        val onRoute = best.takeIf { bestD < 0.6 }
        val pref = prefAt(d, lat, lon)
        val candidates = d.points.filter { it.kind != "jct" || onRoute != null }
        val nearest = candidates.filter { it.kind == "ic" }.minByOrNull { hypot(it.x - px, it.y - py) }
        var next: Point? = null; var nextKm = 0.0
        if (heading != null) {
            val hx = sin(Math.toRadians(heading.toDouble())); val hy = cos(Math.toRadians(heading.toDouble()))
            // すすむ 方向 の まえ（左右 55° いない）で、のって いる 路線 の もの を さき に
            val ahead = candidates.mapNotNull { p ->
                val dx = p.x - px; val dy = p.y - py
                val dist = hypot(dx, dy)
                if (dist < 0.3 || dist > 80) return@mapNotNull null
                val cosA = (dx * hx + dy * hy) / dist
                if (cosA < 0.57) null else Triple(p, dist, cosA)
            }
            val pick = (if (onRoute != null) ahead.filter { onRoute in it.first.routes } else ahead).ifEmpty { ahead }
                .minByOrNull { it.second / (0.5 + it.third) }
            if (pick != null) { next = pick.first; nextKm = pick.second }
        }
        return Where(
            onRoute?.let { d.routes[it] }, pref, next, nextKm,
            nearest, nearest?.let { hypot(it.x - px, it.y - py) } ?: 0.0,
        )
    }

    /** 点 が どの 県 に ある か（なければ null） */
    fun prefAt(d: Data, lat: Double, lon: Double): Int? = d.prefs.firstOrNull { p -> p.polys.any { inside(it, lat, lon) } }?.code

    private fun inside(poly: FloatArray, lat: Double, lon: Double): Boolean {
        var c = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val yi = poly[i * 2].toDouble(); val xi = poly[i * 2 + 1].toDouble()
            val yj = poly[j * 2].toDouble(); val xj = poly[j * 2 + 1].toDouble()
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    fun distKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double) = hypot(kmX(aLon) - kmX(bLon), kmY(aLat) - kmY(bLat))
}

/** めじるし：「有馬グランドホテル は ここ の IC が いちばん 近い」。写真 つき で 地図 に 出す */
data class Landmark(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** いちばん 近い IC など の 名前 と 場所 */
    val point: String,
    val lat: Double,
    val lon: Double,
    val emoji: String = "📍",
)

object Landmarks {
    private lateinit var dir: File
    var list by mutableStateOf<List<Landmark>>(emptyList())
        private set
    /** 写真 を かえた とき に ふやす（画面 の 読みなおし 用） */
    var version by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (::dir.isInitialized) return
        dir = File(context.filesDir, "drive").apply { mkdirs() }
        list = runCatching {
            val a = JSONArray(File(dir, "landmarks.json").readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                Landmark(o.getString("id"), o.getString("name"), o.getString("point"), o.getDouble("lat"), o.getDouble("lon"), o.optString("emoji", "📍"))
            }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("point", it.point).put("lat", it.lat).put("lon", it.lon).put("emoji", it.emoji)) }
        File(dir, "landmarks.json").writeText(a.toString())
    }

    fun photoFile(l: Landmark) = File(dir, "${l.id}.jpg")

    fun photo(l: Landmark): Bitmap? = photoFile(l).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun add(context: Context, l: Landmark, photo: Uri?) {
        init(context)
        photo?.let { setPhoto(context, l, it) }
        list = list + l
        save()
    }

    fun remove(l: Landmark) {
        list = list - l
        photoFile(l).delete()
        save()
    }

    /** 写真 を 320px の 正方形 に きりとって しまう */
    fun setPhoto(context: Context, l: Landmark, uri: Uri) {
        val src = runCatching {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { dec, info, _ ->
                val s = maxOf(1f, minOf(info.size.width, info.size.height) / 640f)
                dec.setTargetSize((info.size.width / s).toInt(), (info.size.height / s).toInt())
                dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull() ?: return
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(sq, 320, 320, true)
        photoFile(l).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        version++
    }
}
