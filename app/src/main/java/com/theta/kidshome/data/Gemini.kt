package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 写真を Gemini で「お絵かき風」にする（Theta Home Hatch と同じ呼び方）。
 * API キーは おうちのひと が入れる。設定の書き出し・スプレッドシートには入れない。
 */
object Gemini {
    const val DEFAULT_MODEL = "gemini-3.1-flash-image"
    private val FALLBACK_MODELS = listOf("gemini-3.1-flash-image", "gemini-2.5-flash-image")

    enum class Style(val emoji: String, val label: String, val prompt: String) {
        COLORING("✏️", "ぬりえ", "Turn this photo into a simple children's coloring page: clean bold black outlines only, no shading, no color, pure white background. Keep the main subjects recognizable and simple."),
        CRAYON("🖍", "クレヨン", "Redraw this photo as a cheerful child-like crayon drawing on white paper, bright colors, simple shapes, visible crayon texture."),
        WATERCOLOR("💧", "すいさい", "Redraw this photo as a soft, gentle watercolor illustration for a picture book, light pastel colors, white paper texture."),
        ANIME("🌟", "まんが", "Redraw this photo as a cute, simple cartoon illustration with clean lines and flat bright colors, suitable for young children."),
    }

    private fun prefs(c: Context) = c.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    fun apiKey(c: Context): String = prefs(c).getString("geminiKey", "").orEmpty()
    fun setApiKey(c: Context, key: String) = prefs(c).edit().putString("geminiKey", key.trim()).apply()
    fun model(c: Context): String = prefs(c).getString("geminiModel", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
    fun setModel(c: Context, m: String) = prefs(c).edit().putString("geminiModel", m.trim()).apply()

    private class ModelNotFound(m: String) : IOException("モデル $m が見つかりません")

    suspend fun transform(context: Context, photo: Bitmap, style: Style): Bitmap = edit(context, photo, style.prompt)

    /** シールの型どり：主役だけを、まっ みどり の背景にして返してもらう（アプリで みどり を すけさせる） */
    const val CUTOUT_PROMPT = "Cut out the single main character or sticker from this photo. Reproduce it faithfully with the same design, " +
        "colors and outline, centered and filling most of the frame, on a perfectly flat pure green (#00FF00) background. " +
        "No shadow, no other objects, no text, no frame."

    suspend fun cutout(context: Context, photo: Bitmap): Bitmap = edit(context, photo, CUTOUT_PROMPT)

    suspend fun edit(context: Context, photo: Bitmap, prompt: String): Bitmap = withContext(Dispatchers.IO) {
        val key = apiKey(context).ifBlank { throw IOException("Gemini の API キーが ありません（おうちのひと メニューで入れてね）") }
        val scaled = photo.let {
            val s = 1024f / maxOf(it.width, it.height)
            if (s < 1f) Bitmap.createScaledBitmap(it, (it.width * s).toInt(), (it.height * s).toInt(), true) else it
        }
        val jpeg = ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        val body = JSONObject().put(
            "contents", JSONArray().put(
                JSONObject().put(
                    "parts", JSONArray()
                        .put(JSONObject().put("text", prompt))
                        .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))))
                )
            )
        ).put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("IMAGE").put("TEXT"))).toString()
        var last: Exception? = null
        for (m in (listOf(model(context)) + FALLBACK_MODELS).distinct()) {
            try {
                return@withContext call(key, m, body)
            } catch (e: ModelNotFound) {
                last = e
            }
        }
        throw last ?: IOException("Gemini を呼べませんでした")
    }

    private fun call(apiKey: String, model: String, body: String): Bitmap {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 120_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", apiKey)
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        if (code == 404) throw ModelNotFound(model)
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
            val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err.take(200))
            throw IOException("Gemini HTTP $code：$msg")
        }
        val json = JSONObject(c.inputStream.bufferedReader().readText())
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini から画像が返りませんでした")
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val data = (p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data"))?.optString("data")
            if (!data.isNullOrEmpty()) {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("画像を読めませんでした")
            }
        }
        throw IOException("Gemini から画像が返りませんでした")
    }
}
