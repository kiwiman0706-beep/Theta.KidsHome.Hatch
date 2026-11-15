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
    /** 路線 の 上 の IC など の ならび（line：どの 線 の 上 か、pts：点 の 番号、arcs：線 の はじめ から の km） */
    class Seq(val line: Int, val pts: IntArray, val arcs: FloatArray)
    class Route(val name: String, val ref: String, val city: Boolean, val lines: List<FloatArray>, val seqs: List<Seq> = emptyList())
    /** つながり：route が -1 の とき は のりかえ・つなぎ（まっすぐ 引く） */
    class Edge(val a: Int, val b: Int, val km: Float, val route: Int, val line: Int, val ka: Float, val kb: Float)
    /** kind：ic・jct・sa・pa */
    class Point(val name: String, val kind: String, val lat: Double, val lon: Double, val routes: List<Int>) {
        val x get() = kmX(lon)
        val y get() = kmY(lat)
    }
    class Pref(val code: Int, val kinki: Boolean, val polys: List<FloatArray>)
    class Data(
        val bbox: DoubleArray, val routes: List<Route>, val points: List<Point>, val prefs: List<Pref>,
        val attribution: String, val made: String, val edges: List<Edge> = emptyList(),
    ) {
        val adj: Map<Int, List<Edge>> by lazy { edges.flatMap { listOf(it.a to it, it.b to it) }.groupBy({ it.first }, { it.second }) }
    }

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
                val sq = r.optJSONArray("seqs") ?: JSONArray()
                val seqs = List(sq.length()) { k ->
                    val o2 = sq.getJSONObject(k)
                    val f = o2.getJSONArray("pts")
                    Seq(o2.getInt("line"), IntArray(f.length() / 2) { f.getInt(it * 2) }, FloatArray(f.length() / 2) { f.getDouble(it * 2 + 1).toFloat() })
                }
                Route(r.optString("name"), r.optString("ref"), r.optBoolean("city"), List(ls.length()) { floats(ls.getJSONArray(it)) }, seqs)
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
        val edges = (o.optJSONArray("edges") ?: JSONArray()).let { a ->
            List(a.length()) { i ->
                val e = a.getJSONArray(i)
                Edge(e.getInt(0), e.getInt(1), e.getDouble(2).toFloat(), e.getInt(3), e.getInt(4), e.getDouble(5).toFloat(), e.getDouble(6).toFloat())
            }
        }
        val bb = o.getJSONArray("bbox")
        return Data(DoubleArray(4) { bb.getDouble(it) }, routes, points, prefs, o.optString("attribution"), o.optString("made"), edges).also { cache = it }
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

    // ---------------------------------------------------------------- みち さがし

    /** さがした みち：とおる つながり と だいたい の km */
    class Trip(val from: Int, val to: Int, val edges: List<Edge>, val km: Double) {
        /** 「中国自動車道 → 吹田JCT → 近畿自動車道」の よう な じゅんばん（3km より みじかい 区間 は はぶく） */
        fun steps(d: Data): List<String> {
            val legs = ArrayList<Pair<Int, Double>>()  // 路線 と km
            val at = ArrayList<Int>()  // その 路線 に のった 点
            var cur = from
            for (e in edges) {
                val next = if (e.a == cur) e.b else e.a
                if (e.route >= 0) {
                    if (legs.isNotEmpty() && legs.last().first == e.route) legs[legs.size - 1] = e.route to legs.last().second + e.km
                    else { legs += e.route to e.km.toDouble(); at += cur }
                }
                cur = next
            }
            val keep = legs.indices.filter { legs[it].second >= 3 || legs.size == 1 }
            val out = ArrayList<String>()
            keep.forEachIndexed { k, i ->
                val name = d.routes[legs[i].first].name
                if (out.lastOrNull() == name) return@forEachIndexed
                if (k > 0) d.points[at[i]].let { p -> if (p.kind == "jct") out += p.name }
                out += name
            }
            return out
        }
    }

    /** a から b まで の みち（高速道路 だけ、阪神高速 も おなじ）。のりかえ は 2km ぶん たす */
    fun trip(d: Data, a: Int, b: Int): Trip? {
        if (a == b) return Trip(a, b, emptyList(), 0.0)
        val dist = HashMap<Int, Double>().apply { put(a, 0.0) }
        val prev = HashMap<Int, Edge>()
        val q = java.util.PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        q += 0.0 to a
        while (q.isNotEmpty()) {
            val (dd, u) = q.poll()!!
            if (u == b) break
            if (dd > (dist[u] ?: Double.MAX_VALUE)) continue
            for (e in d.adj[u].orEmpty()) {
                val v = if (e.a == u) e.b else e.a
                val w = e.km + (if (e.route < 0) 2.0 else 0.0)
                val nd = dd + w
                if (nd < (dist[v] ?: Double.MAX_VALUE)) { dist[v] = nd; prev[v] = e; q += nd to v }
            }
        }
        if (b !in prev) return null
        val list = ArrayList<Edge>()
        var u = b
        while (u != a) { val e = prev.getValue(u); list += e; u = if (e.a == u) e.b else e.a }
        list.reverse()
        return Trip(a, b, list, list.sumOf { it.km.toDouble() })
    }

    /** みち の 出発点：高速 を 走って いる とき は すすむ 先 の 点、そう で なければ いちばん 近い IC */
    fun startPoint(d: Data, w: Where?, lat: Double, lon: Double): Int? {
        w?.next?.takeIf { w.route != null }?.let { n -> return d.points.indexOf(n) }
        val px = kmX(lon); val py = kmY(lat)
        return d.points.indices.filter { d.points[it].kind == "ic" && d.adj.containsKey(it) }.minByOrNull { hypot(d.points[it].x - px, d.points[it].y - py) }
    }

    /** 線 の a km から b km まで を 切りだす（lat,lon,…）。本物 の 地図 で みち を ぬる 用 */
    fun cut(line: FloatArray, a: Float, b: Float): FloatArray {
        val lo = minOf(a, b); val hi = maxOf(a, b)
        val out = ArrayList<Float>()
        var run = 0.0
        var k = 2
        fun lerp(t: Double, i: Int) { out += (line[i - 2] + (line[i] - line[i - 2]) * t).toFloat(); out += (line[i - 1] + (line[i + 1] - line[i - 1]) * t).toFloat() }
        while (k < line.size) {
            val l = hypot(kmX(line[k + 1].toDouble()) - kmX(line[k - 1].toDouble()), kmY(line[k].toDouble()) - kmY(line[k - 2].toDouble()))
            val s0 = run; val s1 = run + l
            if (s1 >= lo && s0 <= hi && l > 0) {
                if (out.isEmpty()) lerp(((lo - s0) / l).coerceIn(0.0, 1.0), k)
                lerp(((hi - s0) / l).coerceIn(0.0, 1.0), k)
            }
            run = s1
            k += 2
        }
        return out.toFloatArray()
    }

    // ---------------------------------------------------------------- 模式図（たて・よこ・ななめ 45° だけ）

    /** a から b へ：たて か よこ → ななめ → たて か よこ（地下鉄 の 路線図 の よう に）。x0,y0,x1,y1,x2,y2,x3,y3 */
    fun bend(ax: Float, ay: Float, bx: Float, by: Float): FloatArray {
        val dx = bx - ax; val dy = by - ay
        val adx = kotlin.math.abs(dx); val ady = kotlin.math.abs(dy)
        val sx = kotlin.math.sign(dx); val sy = kotlin.math.sign(dy)
        return if (adx >= ady) {
            val h = (adx - ady) / 2 * sx
            floatArrayOf(ax, ay, ax + h, ay, ax + h + ady * sx, by, bx, by)
        } else {
            val v = (ady - adx) / 2 * sy
            floatArrayOf(ax, ay, ax, ay + v, bx, ay + v + adx * sy, bx, by)
        }
    }

    /** 折れ線 の 長さ の t（0〜1）の ところ */
    fun along(f: FloatArray, t: Float): Pair<Float, Float> {
        var total = 0f
        for (k in 2 until f.size step 2) total += hypot(f[k] - f[k - 2], f[k + 1] - f[k - 1])
        var want = total * t.coerceIn(0f, 1f)
        for (k in 2 until f.size step 2) {
            val l = hypot(f[k] - f[k - 2], f[k + 1] - f[k - 1])
            if (want <= l && l > 0) return (f[k - 2] + (f[k] - f[k - 2]) * want / l) to (f[k - 1] + (f[k + 1] - f[k - 1]) * want / l)
            want -= l
        }
        return f[f.size - 2] to f[f.size - 1]
    }

    /** 高速 の 上 の どこ か（模式図 で 車 を おく ため）：路線・ならび・何番目 の あいだ・その あいだ の どれくらい */
    class Spot(val route: Int, val seq: Int, val k: Int, val t: Float)

    fun spot(d: Data, lat: Double, lon: Double): Spot? {
        val px = kmX(lon); val py = kmY(lat)
        var best: Spot? = null; var bestD = 0.6
        d.routes.forEachIndexed { ri, r ->
            r.seqs.forEachIndexed { si, sq ->
                val l = r.lines.getOrNull(sq.line) ?: return@forEachIndexed
                var run = 0.0
                var k = 2
                while (k < l.size) {
                    val ax = kmX(l[k - 1].toDouble()); val ay = kmY(l[k - 2].toDouble())
                    val bx = kmX(l[k + 1].toDouble()); val by = kmY(l[k].toDouble())
                    val dx = bx - ax; val dy = by - ay
                    val len = hypot(dx, dy)
                    val t = if (len == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / (len * len)).coerceIn(0.0, 1.0)
                    val dd = hypot(px - ax - t * dx, py - ay - t * dy)
                    if (dd < bestD) {
                        val arc = (run + t * len).toFloat()
                        val i = (0 until sq.arcs.size - 1).firstOrNull { arc >= sq.arcs[it] && arc <= sq.arcs[it + 1] }
                        if (i != null) {
                            bestD = dd
                            best = Spot(ri, si, i, ((arc - sq.arcs[i]) / (sq.arcs[i + 1] - sq.arcs[i]).coerceAtLeast(0.001f)))
                        }
                    }
                    run += len
                    k += 2
                }
            }
        }
        return best
    }
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
