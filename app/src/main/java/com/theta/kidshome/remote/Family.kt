package com.theta.kidshome.remote

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.theta.kidshome.data.GuidePack
import com.theta.kidshome.data.Store
import com.theta.kidshome.media.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * おや の スマホ から 子ども の タブレット を うごかす（おやモード）。
 * 通り道 は 呼びかけ と おなじ ntfy。つないだ とき に きめた 合いことば（鍵）で しるし（HMAC）を つけて、
 * トピック を しっている だけ の 人 が かってに めいれい できない ように する。
 *   おや → こども：<topic>       に 「KH1 <json の base64>.<しるし>」
 *   こども → おや：<topic>-up    に ようす（おなじ 形）
 */
object Family {
    private const val PREFIX = "KH1 "

    // ---------------------------------------------------------------- しるし

    fun newKey(): String = ByteArray(24).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }

    private fun mac(key: String, data: String): String {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return m.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun seal(key: String, o: JSONObject): String {
        val body = Base64.encodeToString(o.put("ts", System.currentTimeMillis()).toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return PREFIX + body + "." + mac(key, body)
    }

    /** しるし が あっていれば 中身。ちがう・ふるい（10 分 より 前）なら null */
    fun open(key: String, text: String, maxAgeMs: Long = 10 * 60_000L): JSONObject? {
        if (!text.startsWith(PREFIX)) return null
        val (body, sig) = text.removePrefix(PREFIX).trim().split('.').takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: return null
        if (!java.security.MessageDigest.isEqual(mac(key, body).toByteArray(), sig.toByteArray())) return null
        val o = runCatching { JSONObject(String(Base64.decode(body, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - o.optLong("ts") > maxAgeMs) return null
        return o
    }

    fun isSealed(text: String) = text.startsWith(PREFIX)

    // ---------------------------------------------------------------- ntfy

    suspend fun publish(topic: String, text: String) = withContext(Dispatchers.IO) {
        val c = URL("${Remote.SERVER}/$topic").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "KidsHome")
        c.outputStream.use { it.write(text.toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        if (code / 100 != 2) throw java.io.IOException("HTTP $code")
    }

    /** トピック の さいきん の めっせーじ（あたらしい 順） */
    suspend fun poll(topic: String, since: String = "1h"): List<String> = withContext(Dispatchers.IO) {
        val c = URL("${Remote.SERVER}/$topic/json?poll=1&since=${URLEncoder.encode(since, "UTF-8")}").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "KidsHome")
        val out = mutableListOf<String>()
        try {
            if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { l -> runCatching { JSONObject(l) }.getOrNull()?.takeIf { it.optString("event") == "message" }?.let { out += it.optString("message") } }
            }
        } finally { c.disconnect() }
        out.reversed()
    }

    // ---------------------------------------------------------------- こども の タブレット

    private fun secrets(c: Context) = c.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    /** つなぐ 合いことば（はじめて なら 作る） */
    fun pairKey(c: Context): String = secrets(c).getString("familyKey", null) ?: newKey().also { secrets(c).edit().putString("familyKey", it).apply() }

    fun resetKey(c: Context) = secrets(c).edit().putString("familyKey", newKey()).apply()

    /** つなぐ QR の 中身 */
    fun pairText(c: Context, topic: String): String {
        val name = Store.config.profiles.joinToString("・") { it.name }
        return "kidshome:pair?t=" + URLEncoder.encode(topic, "UTF-8") + "&k=" + pairKey(c) + "&n=" + URLEncoder.encode(name + " の タブレット", "UTF-8")
    }

    /** おや から の めいれい を うけとる（主スレッド）。うけとれたら true */
    fun receive(context: Context, text: String): Boolean {
        val o = open(pairKey(context), text) ?: return false
        val seen = context.getSharedPreferences("remote", Context.MODE_PRIVATE)
        if (o.optLong("ts") <= seen.getLong("familyTs", 0)) return true // おなじ もの を 2 かい しない
        seen.edit().putLong("familyTs", o.optLong("ts")).apply()
        val now = System.currentTimeMillis()
        when (o.optString("c")) {
            "msg" -> Remote.apply(context, o.optString("text"))
            "lock" -> {
                val min = o.optInt("min", 30).coerceIn(1, 600)
                Remote.apply(context, "/lock $min " + o.optString("text"))
            }
            "unlock" -> Remote.apply(context, "/unlock " + o.optString("text"))
            "song" -> {
                val title = o.optString("title").trim(); val notes = o.optString("notes").trim()
                if (title.isNotEmpty() && notes.isNotEmpty()) Store.update { c -> c.copy(songs = c.songs.filter { it.title != title } + Song(title, notes)) }
            }
            "delsong" -> Store.update { c -> c.copy(songs = c.songs.filter { it.title != o.optString("title") }) }
            "set" -> {
                if (o.has("nightDim")) Store.update { it.copy(nightDim = o.getBoolean("nightDim")) }
                if (o.has("dictLevel")) Store.update { it.copy(dictLevel = o.getInt("dictLevel").coerceIn(1, 3)) }
                if (o.has("autoUpdate")) Store.update { it.copy(autoUpdate = o.getBoolean("autoUpdate")) }
                if (o.has("guide")) GuidePack.setEnabled(context, o.getBoolean("guide"))
                if (o.has("homeStyle")) Store.update { it.copy(homeStyle = if (o.getString("homeStyle") == "tabs") "tabs" else "room") }
            }
        }
        // いまの ようす を かえす（status の とき も）
        val topic = Store.config.remoteTopic ?: return true
        val key = pairKey(context)
        val status = status(context, now)
        scope.launch { runCatching { publish("$topic-up", seal(key, status)) } }
        return true
    }

    private val scope = kotlinx.coroutines.MainScope()

    private fun status(context: Context, now: Long): JSONObject {
        val c = Store.config
        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: -1
        val charging = (bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return JSONObject()
            .put("c", "status")
            .put("version", runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull())
            .put("battery", level).put("charging", charging)
            .put("lockedUntil", c.videoLockUntil.takeIf { it > now } ?: 0)
            .put("profile", c.currentProfile.name)
            .put("songs", JSONArray(c.songs.map { it.title }))
            .put("nightDim", c.nightDim).put("dictLevel", c.dictLevel).put("autoUpdate", c.autoUpdate)
            .put("guide", GuidePack.enabled).put("homeStyle", c.homeStyle)
    }

    // ---------------------------------------------------------------- おや の スマホ

    data class Device(val name: String, val topic: String, val key: String)

    private fun prefs(c: Context) = c.getSharedPreferences("family", Context.MODE_PRIVATE)

    /** この スマホ を おやモード で つかう */
    fun isParent(c: Context) = prefs(c).getBoolean("parent", false)
    fun setParent(c: Context, on: Boolean) = prefs(c).edit().putBoolean("parent", on).apply()

    var devices by mutableStateOf<List<Device>>(emptyList())
        private set

    fun loadDevices(c: Context) {
        devices = runCatching {
            JSONArray(prefs(c).getString("devices", "[]")).let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { Device(it.getString("name"), it.getString("topic"), it.getString("key")) }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveDevices(c: Context, list: List<Device>) {
        prefs(c).edit().putString("devices", JSONArray(list.map { JSONObject().put("name", it.name).put("topic", it.topic).put("key", it.key) }).toString()).apply()
        devices = list
    }

    /** kidshome:pair?… を よんで つなぐ */
    fun pair(c: Context, text: String): Device? {
        if (!text.startsWith("kidshome:pair?")) return null
        val q = text.substringAfter('?').split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") }
        val d = Device(q["n"]?.ifBlank { null } ?: "タブレット", q["t"] ?: return null, q["k"] ?: return null)
        saveDevices(c, devices.filter { it.topic != d.topic } + d)
        return d
    }

    fun forget(c: Context, d: Device) = saveDevices(c, devices.filter { it.topic != d.topic })

    suspend fun send(d: Device, o: JSONObject) = publish(d.topic, seal(d.key, o))

    /** タブレット の さいきん の ようす（しるし が あう もの の うち あたらしい もの） */
    suspend fun lastStatus(d: Device): JSONObject? =
        poll("${d.topic}-up", "12h").firstNotNullOfOrNull { open(d.key, it, maxAgeMs = 24 * 3600_000L) }
}
