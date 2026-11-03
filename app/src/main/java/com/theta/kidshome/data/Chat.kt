package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 💌 かぞく の チャット（スタンプ と きまった ことば）を ntfy で おくりあう。
 * かぞく の あいことば から ntfy の トピック名 と 暗号 の かぎ を つくる（ntfy の サーバー から は 中身 が 見えない）。
 * サーバー は ntfy.sh（あとで NAS の ntfy に かえられる）。1 つ の メッセージ は 暗号化 した 添付ファイル 1 つ。
 */
object Chat {
    data class Msg(val id: String, val time: Long, val from: String, val mine: Boolean, val text: String, val image: String?)

    val PHRASES = listOf("おはよう", "おやすみ", "ありがとう", "だいすき", "あそぼう！", "すごいね！", "ごめんね", "いま なに してる？", "おかえり", "がんばってる！")

    private lateinit var app: Context
    var version by mutableIntStateOf(0)
        private set
    var unread by mutableIntStateOf(0)
        private set
    /** さいご に きた もの（画面 に「💌 ○○ から」） */
    var incoming by mutableStateOf<Msg?>(null)
        private set
    var status by mutableStateOf<String?>(null)

    fun init(c: Context) { if (!::app.isInitialized) app = c.applicationContext; unread = prefs().getInt("unread", 0) }
    private fun prefs(c: Context = app) = c.getSharedPreferences("chat", Context.MODE_PRIVATE)

    fun secret(c: Context = app) = prefs(c).getString("secret", "").orEmpty()
    fun setSecret(c: Context, s: String) { prefs(c).edit().putString("secret", s.trim()).putString("since", "12h").apply(); version++ }
    fun server(c: Context = app) = prefs(c).getString("server", "https://ntfy.sh").orEmpty().trimEnd('/')
    fun setServer(c: Context, s: String) { prefs(c).edit().putString("server", s.trim().ifBlank { "https://ntfy.sh" }).apply(); version++ }
    fun on(c: Context = app) = secret(c).length >= 8

    fun newSecret(): String {
        val r = SecureRandom(); val abc = "abcdefghjkmnpqrstuvwxyz23456789"
        return (1..16).map { abc[r.nextInt(abc.length)] }.joinToString("")
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
    fun topic(secret: String) = "kh" + sha("topic:$secret").joinToString("") { "%02x".format(it) }.take(30)
    private fun key(secret: String) = SecretKeySpec(sha("key:$secret"), "AES")

    fun encrypt(secret: String, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(secret), GCMParameterSpec(128, iv)) }
        return iv + c.doFinal(plain)
    }
    fun decrypt(secret: String, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(secret), GCMParameterSpec(128, data, 0, 12)) }
        return c.doFinal(data, 12, data.size - 12)
    }

    private fun device(): String = prefs().getString("device", null) ?: UUID.randomUUID().toString().also { prefs().edit().putString("device", it).apply() }
    private fun dir() = File(app.filesDir, "chat").apply { mkdirs() }

    fun messages(): List<Msg> = runCatching {
        val a = JSONArray(File(dir(), "log.json").readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Msg(it.getString("id"), it.getLong("t"), it.optString("from"), it.optBoolean("mine"), it.optString("text"), it.optString("img").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    private fun save(m: Msg) {
        val all = (messages() + m).takeLast(300)
        File(dir(), "log.json").writeText(JSONArray(all.map {
            JSONObject().put("id", it.id).put("t", it.time).put("from", it.from).put("mine", it.mine).put("text", it.text).put("img", it.image ?: "")
        }).toString())
        version++
    }

    fun image(m: Msg): Bitmap? = m.image?.let { BitmapFactory.decodeFile(File(dir(), it).path) }

    fun markRead() { unread = 0; prefs().edit().putInt("unread", 0).apply() }

    private fun upload(secret: String, body: JSONObject): Boolean = runCatching {
        val enc = encrypt(secret, body.toString().toByteArray())
        val con = (URL("${server()}/${topic(secret)}").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Filename", "m.bin"); setRequestProperty("Title", "kh"); setRequestProperty("Content-Type", "application/octet-stream")
        }
        con.outputStream.use { it.write(enc) }
        val code = con.responseCode; con.disconnect(); code in 200..299
    }.getOrDefault(false)

    /** チャット では ない データ（きょく など）。うけとる と handlers[kind] が よばれる */
    val handlers = HashMap<String, (JSONObject) -> Unit>()

    suspend fun sendData(kind: String, data: JSONObject): Boolean = withContext(Dispatchers.IO) {
        if (!on()) return@withContext false
        upload(secret(), JSONObject().put("v", 1).put("dev", device()).put("kind", kind).put("data", data))
    }

    /** スタンプ（ライブラリ の id）か ことば を おくる */
    suspend fun send(text: String, stampId: String?): Boolean = withContext(Dispatchers.IO) {
        val secret = secret(); if (!on()) return@withContext false
        val from = Store.config.currentProfile.name
        val png = stampId?.let { StampLibrary.bitmap(it) }?.let { b ->
            val s = 160f / maxOf(b.width, b.height)
            val small = Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
            ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        val body = JSONObject().put("v", 1).put("from", from).put("dev", device()).put("text", text)
            .put("img", png?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: "")
        val ok = upload(secret, body)
        if (ok) {
            val id = UUID.randomUUID().toString()
            val img = png?.let { "$id.png".also { n -> File(dir(), n).writeBytes(it) } }
            withContext(Dispatchers.Main) { save(Msg(id, System.currentTimeMillis(), from, true, text, img)) }
        }
        status = if (ok) null else "おくれなかった（インターネット を たしかめて）"
        ok
    }

    /** あたらしい もの を とってくる（ntfy の poll） */
    suspend fun poll() = withContext(Dispatchers.IO) {
        val secret = secret(); if (!on()) return@withContext
        val since = prefs().getString("since", "12h")
        val got = ArrayList<Msg>()
        runCatching {
            val con = (URL("${server()}/${topic(secret)}/json?poll=1&since=$since").openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000 }
            val lines = con.inputStream.bufferedReader().readLines(); con.disconnect()
            var last = since
            lines.forEach { line ->
                val e = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                if (e.optString("event") != "message") return@forEach
                last = e.getString("id")
                val url = e.optJSONObject("attachment")?.optString("url") ?: return@forEach
                runCatching {
                    val bytes = (URL(url).openConnection() as HttpURLConnection).run { connectTimeout = 15_000; readTimeout = 30_000; inputStream.use { it.readBytes() }.also { disconnect() } }
                    val o = JSONObject(String(decrypt(secret, bytes)))
                    if (o.optString("dev") == device()) return@runCatching
                    o.optString("kind").takeIf { it.isNotBlank() }?.let { k ->
                        val d = o.optJSONObject("data")
                        if (d != null) withContext(Dispatchers.Main) { handlers[k]?.invoke(d) }
                        return@runCatching
                    }
                    val id = e.getString("id")
                    val img = o.optString("img").takeIf { it.isNotBlank() }?.let { b64 ->
                        "$id.png".also { n -> File(dir(), n).writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT)) }
                    }
                    got += Msg(id, e.optLong("time") * 1000, o.optString("from").take(20), false, o.optString("text").take(40), img)
                }
            }
            prefs().edit().putString("since", last).apply()
        }.onFailure { status = "とどかない（${it.message}）" }
        if (got.isNotEmpty()) withContext(Dispatchers.Main) {
            val have = messages().map { it.id }.toSet()
            got.filter { it.id !in have }.forEach { save(it); unread++; incoming = it }
            prefs().edit().putInt("unread", unread).apply()
        }
    }
}
