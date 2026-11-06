package app.thetahatch.kidshome.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * せってい を まとめて 書きだす・読みこむ（ファイル か、なんまい か の QR）。
 * 入れる：ホーム の せってい（こども・タイル・カテゴリー・せいげん など）と、各 機能 の せってい。
 * 入れない：GitHub トークン・Gemini キー（えらんだ とき だけ）、ポイント の 残高・りれき、端末 ごと の もの。
 */
object SettingsBundle {
    /** 書きだす SharedPreferences と、その 中 で はぶく key */
    private val PREFS = mapOf(
        "shop" to emptySet<String>(),
        "tepra" to emptySet(),
        "drawing" to emptySet(),
        "coding" to emptySet(),
        "chat" to setOf("device", "since", "unread"),
        "doll" to setOf("tag", "checked"),
        "points" to setOf("*bal:", "*day:", "*keys:", "*log:", "usedBills"),
        "stamps" to setOf("*own:"),
        "guide" to emptySet(),
    )

    private fun skip(key: String, rules: Set<String>) = rules.any { r -> if (r.startsWith("*")) key.startsWith(r.drop(1)) else key == r }

    fun export(c: Context, withGemini: Boolean): JSONObject {
        val o = JSONObject().put("v", 1).put("app", "kidshome").put("time", System.currentTimeMillis())
        o.put("config", Store.config.toJson())
        val prefs = JSONObject()
        PREFS.forEach { (name, rules) ->
            val p = JSONObject()
            c.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
                if (skip(k, rules) || v == null) return@forEach
                p.put(k, when (v) {
                    is Boolean -> JSONObject().put("b", v)
                    is Int -> JSONObject().put("i", v)
                    is Long -> JSONObject().put("l", v)
                    is Float -> JSONObject().put("f", v.toDouble())
                    is String -> JSONObject().put("s", v)
                    is Set<*> -> JSONObject().put("ss", JSONArray(v.map { it.toString() }))
                    else -> return@forEach
                })
            }
            if (p.length() > 0) prefs.put(name, p)
        }
        o.put("prefs", prefs)
        if (withGemini) {
            val key = Gemini.apiKey(c)
            if (key.isNotBlank()) o.put("gemini", JSONObject().put("key", key).put("model", Gemini.model(c)))
        }
        return o
    }

    /** 読みこむ。この タブレット の 子ども（currentProfileId）と はった ふせん は そのまま */
    fun import(c: Context, o: JSONObject): String {
        require(o.optString("app") == "kidshome") { "キッズホーム の せってい では ありません" }
        val local = Store.config
        val incoming = appConfigFromJson(o.getJSONObject("config"))
        val merged = incoming.copy(
            currentProfileId = incoming.profiles.firstOrNull { it.id == local.currentProfileId }?.id ?: incoming.currentProfileId,
            profiles = incoming.profiles.map { p -> p.copy(stickers = local.profiles.firstOrNull { it.id == p.id }?.stickers.orEmpty()) },
        )
        Store.update { merged }
        val prefs = o.optJSONObject("prefs") ?: JSONObject()
        var n = 0
        prefs.keys().forEach { name ->
            val rules = PREFS[name] ?: return@forEach // しらない もの は 入れない
            val p = prefs.getJSONObject(name)
            val e = c.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            p.keys().forEach { k ->
                if (skip(k, rules)) return@forEach
                val v = p.getJSONObject(k)
                when {
                    v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                    v.has("i") -> e.putInt(k, v.getInt("i"))
                    v.has("l") -> e.putLong(k, v.getLong("l"))
                    v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
                    v.has("s") -> e.putString(k, v.getString("s"))
                    v.has("ss") -> e.putStringSet(k, v.getJSONArray("ss").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
                }
                n++
            }
            e.apply()
        }
        o.optJSONObject("gemini")?.let { g -> Gemini.setApiKey(c, g.getString("key")); g.optString("model").takeIf { it.isNotBlank() }?.let { Gemini.setModel(c, it) } }
        return "こども ${merged.profiles.size} 人・せってい $n こ を 入れました" + if (o.has("gemini")) "（Gemini キー も）" else ""
    }

    // ---------------------------------------------------------------- QR（なんまい か に わける）

    private const val CHUNK = 900

    fun pack(o: JSONObject): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(o.toString().toByteArray()) }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray())
    }

    fun unpack(s: String): JSONObject =
        JSONObject(String(GZIPInputStream(ByteArrayInputStream(java.util.Base64.getUrlDecoder().decode(s))).readBytes()))

    /** QR 1 まい ずつ の 文字：KHSET1.<ばん>.<ぜんぶ>.<しるし>.<なかみ> */
    fun qrParts(o: JSONObject): List<String> {
        val all = pack(o)
        val tag = MessageDigest.getInstance("SHA-256").digest(all.toByteArray()).take(4).joinToString("") { "%02x".format(it) }
        val parts = all.chunked(CHUNK)
        return parts.mapIndexed { i, p -> "KHSET1.${i + 1}.${parts.size}.$tag.$p" }
    }

    /** 読んだ QR を あつめる */
    class Collector {
        var tag: String? = null; private set
        var total = 0; private set
        private val got = HashMap<Int, String>()
        val count get() = got.size
        val done get() = total > 0 && got.size == total

        /** QR 1 まい。まちがい なら false */
        fun add(text: String): Boolean {
            val p = text.split('.', limit = 5)
            if (p.size != 5 || p[0] != "KHSET1") return false
            val i = p[1].toIntOrNull() ?: return false; val n = p[2].toIntOrNull() ?: return false
            if (tag != p[3]) { tag = p[3]; total = n; got.clear() }
            if (i in 1..n) got[i] = p[4]
            return true
        }

        fun result(): JSONObject = unpack((1..total).joinToString("") { got.getValue(it) })
    }
}
