package app.thetahatch.kidshome.tepra

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject

/**
 * テプラ PRO SR5900P（Wi-Fi）で ラベル を 印刷 する。
 * KING JIM の TEPRA-Print SDK は 使用許諾 が いる ので 公開 リポジトリ には 入れない。
 * app/libs/TepraPrint.jar が ある とき だけ src/tepra の 実装、ない とき は src/notepra（つかえない）。
 */
interface TepraBridge {
    val available: Boolean
    /** Wi-Fi の SR5900P を さがす。みつけた たび に onFound（プリンター の 情報） */
    fun startDiscover(context: Context, onFound: (Map<String, String>) -> Unit): Boolean
    fun stopDiscover()
    /** render（印刷 できる 高さ ドット, テープ 幅 mm）で 絵 を つくって 印刷。onStatus（文, おわり, 成功） */
    fun print(context: Context, printer: Map<String, String>, copies: Int, render: (Int, Int) -> Bitmap, onStatus: (String, Boolean, Boolean) -> Unit)
}

object Tepra {
    val impl: TepraBridge get() = TepraImpl
    val available get() = impl.available

    private fun prefs(c: Context) = c.getSharedPreferences("tepra", Context.MODE_PRIVATE)

    fun printer(c: Context): Map<String, String>? = prefs(c).getString("printer", null)?.let { s ->
        runCatching { JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrNull()
    }

    fun savePrinter(c: Context, info: Map<String, String>) = prefs(c).edit().putString("printer", JSONObject(info).toString()).apply()

    fun printerName(c: Context) = printer(c)?.let { it["name"]?.ifBlank { null } ?: it.values.firstOrNull { v -> v.contains("SR") } ?: "テプラ" }
}
