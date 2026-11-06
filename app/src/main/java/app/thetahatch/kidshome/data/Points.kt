package app.thetahatch.kidshome.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🪙 ポイント：がんばった ぶん だけ たまる（1 字 かく・1 もん とく → 1 ポイント など）。
 * きせかえ・ごほうび スタンプ と こうかん。おや の ごほうび しへい（QR）でも もらえる。
 * 子ども ごと（プロフィール id）。おなじ もの を なんども やって かせげない よう、key は 1 日 1 かい。
 */
object Points {
    private lateinit var app: Context
    /** ふえた・へった 合図 */
    var version by mutableIntStateOf(0)
        private set
    /** さいご に もらった ぶん（画面 に「+1 🪙」を 出す） */
    var lastGain by mutableStateOf<Gain?>(null)
        private set

    data class Gain(val n: Int, val why: String, val at: Long = System.currentTimeMillis())
    data class Entry(val time: Long, val n: Int, val why: String)

    /** きせかえ 1 こ の ねだん */
    const val DOLL_COST = 10
    /** はじめ に もらえる ポイント */
    private const val STARTER = 30

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
    }

    private fun prefs(c: Context = app) = c.getSharedPreferences("points", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(Date())
    private fun me() = Store.config.currentProfile.id

    /** 1 日 に もらえる 上限（0 = なし）。おや が きめる */
    fun dailyCap(c: Context = app) = prefs(c).getInt("cap", 50)
    fun setDailyCap(c: Context, n: Int) { prefs(c).edit().putInt("cap", n).apply(); version++ }

    /** はじめて の とき：まえ の 🎟 チケット を ポイント に（1 まい = DOLL_COST） */
    private fun ensure(c: Context, profile: String) {
        val p = prefs(c)
        if (p.contains("bal:$profile")) return
        val old = runCatching { DollPack.legacyTickets(c, profile) }.getOrDefault(0)
        p.edit().putInt("bal:$profile", STARTER + old * DOLL_COST).apply()
    }

    fun balance(c: Context = app, profile: String = me()): Int { ensure(c, profile); return prefs(c).getInt("bal:$profile", 0) }

    fun todayEarned(c: Context = app, profile: String = me()) = prefs(c).getInt("day:$profile:${today()}", 0)

    private fun log(c: Context, profile: String, n: Int, why: String) {
        val key = "log:$profile"
        val a = runCatching { JSONArray(prefs(c).getString(key, "[]")) }.getOrDefault(JSONArray())
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("n", n).put("w", why))
        val keep = JSONArray(); val from = (a.length() - 200).coerceAtLeast(0)
        for (i in from until a.length()) keep.put(a.get(i))
        prefs(c).edit().putString(key, keep.toString()).apply()
    }

    fun history(c: Context = app, profile: String = me()): List<Entry> {
        val a = runCatching { JSONArray(prefs(c).getString("log:$profile", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Entry(o.getLong("t"), o.getInt("n"), o.optString("w")) } }.reversed()
    }

    /**
     * がんばった ので もらう。key が あれば 1 日 1 かい だけ（おなじ 字・おなじ もんだい）。
     * 1 日 の 上限 を こえた ぶん は もらえない。もらえた 数 を かえす。
     */
    fun earn(n: Int, why: String, key: String? = null): Int {
        if (!::app.isInitialized || n <= 0) return 0
        val c = app; val profile = me(); val day = today()
        ensure(c, profile)
        val p = prefs(c)
        if (key != null) {
            val keys = p.getStringSet("keys:$profile:$day", emptySet()).orEmpty()
            if (key in keys) return 0
            // きのう まで の key は すてる
            p.all.keys.filter { it.startsWith("keys:$profile:") && it != "keys:$profile:$day" || it.startsWith("day:$profile:") && it != "day:$profile:$day" }
                .forEach { p.edit().remove(it).apply() }
            p.edit().putStringSet("keys:$profile:$day", keys + key).apply()
        }
        val cap = dailyCap(c)
        val got = if (cap > 0) n.coerceAtMost((cap - todayEarned(c, profile)).coerceAtLeast(0)) else n
        if (got <= 0) { lastGain = Gain(0, "きょう の ぶん は おわり（また あした）"); return 0 }
        p.edit().putInt("bal:$profile", balance(c, profile) + got).putInt("day:$profile:$day", todayEarned(c, profile) + got).apply()
        log(c, profile, got, why)
        lastGain = Gain(got, why); version++
        return got
    }

    /** おや から（上限 なし） */
    fun give(c: Context, profile: String, n: Int, why: String) {
        ensure(c, profile)
        prefs(c).edit().putInt("bal:$profile", balance(c, profile) + n).apply()
        log(c, profile, n, why)
        if (profile == me()) lastGain = Gain(n, why)
        version++
    }

    fun spend(c: Context, profile: String, n: Int, why: String): Boolean {
        if (balance(c, profile) < n) return false
        prefs(c).edit().putInt("bal:$profile", balance(c, profile) - n).apply()
        log(c, profile, -n, why); version++
        return true
    }

    // ---------------------------------------------------------------- ごほうび しへい（QR）

    /** しへい の QR：KHP1.<ポイント>.<ばんごう> */
    data class Bill(val value: Int, val serial: String)

    val BILL_VALUES = listOf(1, 5, 10, 50, 100)

    fun encodeBill(b: Bill) = "KHP1.${b.value}.${b.serial}"
    fun decodeBill(text: String): Bill? = runCatching {
        val p = text.trim().split('.')
        if (p.size != 3 || p[0] != "KHP1") return null
        val v = p[1].toInt(); val s = p[2]
        if (v !in 1..1000 || s.length !in 6..24 || !s.all { it.isLetterOrDigit() }) return null
        Bill(v, s)
    }.getOrNull()

    fun newSerial(): String {
        val r = java.security.SecureRandom()
        val abc = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..10).map { abc[r.nextInt(abc.length)] }.joinToString("")
    }

    fun billUsed(c: Context, b: Bill) = b.serial in prefs(c).getStringSet("usedBills", emptySet()).orEmpty()
    /** つかった しへい（この タブレット では もう つかえない） */
    fun redeem(c: Context, profile: String, b: Bill): Boolean {
        if (billUsed(c, b)) return false
        prefs(c).edit().putStringSet("usedBills", prefs(c).getStringSet("usedBills", emptySet()).orEmpty() + b.serial).apply()
        give(c, profile, b.value, "ごほうび しへい（${b.value}）")
        return true
    }

    // ---------------------------------------------------------------- おうちのひと の 暗証番号

    private fun hash(pin: String, salt: String) =
        MessageDigest.getInstance("SHA-256").digest("$salt:$pin".toByteArray()).joinToString("") { "%02x".format(it) }

    fun hasPin(c: Context) = prefs(c).contains("pin")
    fun setPin(c: Context, pin: String) {
        val salt = newSerial()
        prefs(c).edit().putString("pinSalt", salt).putString("pin", hash(pin, salt)).apply(); version++
    }
    fun clearPin(c: Context) { prefs(c).edit().remove("pin").remove("pinSalt").apply(); version++ }
    fun checkPin(c: Context, pin: String) = hasPin(c) && hash(pin, prefs(c).getString("pinSalt", "").orEmpty()) == prefs(c).getString("pin", null)
    /** おやモード の 入り口 も 暗証番号 に する か */
    fun pinForGate(c: Context) = hasPin(c) && prefs(c).getBoolean("pinGate", false)
    fun setPinForGate(c: Context, v: Boolean) { prefs(c).edit().putBoolean("pinGate", v).apply(); version++ }
}
