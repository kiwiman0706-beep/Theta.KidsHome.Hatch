package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * おもちゃ の しょうひん（おままごと の 食材 など）に はる QR ラベル。
 * QR に 名前 と ねだん を ぜんぶ 入れる ので、どの タブレット でも 読める。
 * 外付け の バーコードリーダー（キーボード として うつ）でも まちがえない よう、英数字 と「.」だけ：
 *   KH1.<ねだん>.<おみせ>.<名前 の UTF-8 を 16 しんすう>
 */
object ShopItems {
    data class Item(val id: String, val name: String, val price: Int, val shop: String)

    fun encode(i: Item): String = "KH1.${i.price}.${i.shop.filter { it.isLetterOrDigit() }.ifEmpty { "other" }}." +
        i.name.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it) }

    fun decode(text: String): Item? = runCatching {
        val t = text.trim()
        if (!t.startsWith("KH1.", ignoreCase = true)) return null
        val p = t.split('.')
        if (p.size < 4) return null
        val hex = p[3]
        val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        Item(t, String(bytes, Charsets.UTF_8), p[1].toInt(), p[2].lowercase())
    }.getOrNull()

    /** 1 円 の くらい は きりあげ て 10 円 きざみ に */
    fun round(price: Int) = ((price + 9) / 10 * 10).coerceAtLeast(10)

    // ---------------------------------------------------------------- ほぞん（おや の タブレット）

    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    private var loaded = false

    private fun file(c: Context) = File(c.filesDir, "shop_items.json")
    fun photo(c: Context, id: String) = File(c.filesDir, "shopitems/$id.jpg").apply { parentFile?.mkdirs() }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        items = runCatching {
            val a = JSONArray(file(c).readText())
            (0 until a.length()).map { a.getJSONObject(it).let { o -> Item(o.getString("id"), o.getString("name"), o.getInt("price"), o.optString("shop", "other")) } }
        }.getOrDefault(emptyList())
    }

    private fun save(c: Context) = file(c).writeText(JSONArray(items.map { JSONObject().put("id", it.id).put("name", it.name).put("price", it.price).put("shop", it.shop) }).toString())

    fun add(c: Context, name: String, price: Int, shop: String, photo: Bitmap?): Item {
        val i = Item(UUID.randomUUID().toString().take(8), name.trim(), price, shop)
        photo?.let { b -> photo(c, i.id).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        items = items + i; save(c)
        return i
    }

    fun update(c: Context, i: Item) { items = items.map { if (it.id == i.id) i else it }; save(c) }
    fun remove(c: Context, i: Item) { items = items - i; photo(c, i.id).delete(); save(c) }

    // ---------------------------------------------------------------- ラベル の 絵

    /** QR（左）と 名前・ねだん（右）。height は テプラ の 印刷 できる 高さ（ドット） */
    fun label(i: Item, height: Int): Bitmap {
        val pad = (height * 0.06f).toInt().coerceAtLeast(2)
        val q = height - pad * 2
        val m = QRCodeWriter().encode(encode(i), BarcodeFormat.QR_CODE, q, q, mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textSize = height * 0.36f }
        val price = Paint(name).apply { textSize = height * 0.4f }
        val priceText = "${i.price}えん"
        val textW = maxOf(name.measureText(i.name), price.measureText(priceText))
        val w = (pad * 3 + q + textW + pad * 2).toInt()
        val b = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(b)
        cv.drawColor(Color.WHITE)
        val black = Paint().apply { color = Color.BLACK }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) cv.drawPoint((pad + x).toFloat(), (pad + y).toFloat(), black)
        val tx = (pad * 3 + q).toFloat()
        cv.drawText(i.name, tx, height * 0.42f, name)
        cv.drawText(priceText, tx, height * 0.88f, price)
        return b
    }

    // ---------------------------------------------------------------- 写真 から（Gemini）

    val SHOPS = listOf("yaoya" to "やおや", "panya" to "パンや", "okashi" to "おかしや", "bunbougu" to "ぶんぼうぐや", "other" to "そのほか")

    private const val PROMPT = "これは 子ども の おままごと 用 の おもちゃ の 写真 です。何 の 品物（食べもの・のみもの・日用品 など）を 表して いるか 考えて、" +
        "日本 の スーパー や お店 で 1 こ（1 パック）買う ときの だいたい の 値段（税込・円）を 答えて ください。" +
        "JSON だけ で：{\"name\": 子ども が 読める ひらがな・カタカナ の みじかい 名前（8 文字 まで）, \"price\": 整数, " +
        "\"shop\": \"yaoya\"（やさい・くだもの）か \"panya\"（パン・ケーキ）か \"okashi\"（おかし・のみもの）か \"bunbougu\"（ぶんぼうぐ・おもちゃ）か \"other\"}"

    suspend fun recognize(c: Context, photo: Bitmap): Item {
        val text = Gemini.ask(c, photo, PROMPT)
        val o = JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1))
        val shop = o.optString("shop").takeIf { s -> SHOPS.any { it.first == s } } ?: "other"
        return Item("", o.optString("name").trim().take(12), round(o.optInt("price", 100)), shop)
    }
}
