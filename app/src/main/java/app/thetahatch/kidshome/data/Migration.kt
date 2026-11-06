package app.thetahatch.kidshome.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 📦 引っ越し：新しい キッズホーム（app.thetahatch.kidshome）へ うつす ため の zip。
 * 入れる：設定（SharedPreferences）・ポイント・絵・スタンプ・字・家系図・写真・チャット・レジ の ラベル・自分 で 足した ぬりえ／ゆるキャラ の 絵。
 * 入れない：秘密（Gemini キー・トークン）、おや の スマホ との つながり、更新 の 記録、取り直せる パック（packs/）と 辞書（dict/）。
 * 中身：manifest.json、prefs/<名前>.json、files/<filesDir から の 場所>。
 */
object Migration {
    const val KIND = "kidshome-migration"

    /** まるごと 入れない 設定 */
    private val SKIP_PREFS = setOf("secrets", "family", "remote", "update", "rescue", "positions", "nuriepack", "yurupack")
    /** 設定 の 中 で 入れない key（取り直せる パック の 版・この 端末 だけ の もの） */
    private val SKIP_KEYS = mapOf(
        "stamppack" to setOf("tag"),
        "doll" to setOf("tag", "checked"),
        "chat" to setOf("device", "since", "unread"),
    )
    /** filesDir の 中 で 入れる もの */
    val FILES = listOf("drawings", "stickers", "stamps", "moji", "kin", "japan", "chat", "shop_items.json", "shopitems", "nurie", "yuru")

    private fun prefNames(c: Context): List<String> =
        File(c.applicationInfo.dataDir, "shared_prefs").listFiles()?.filter { it.extension == "xml" }?.map { it.nameWithoutExtension }
            ?.filter { it !in SKIP_PREFS && !it.startsWith("androidx.") && !it.startsWith("WebView") }?.sorted().orEmpty()

    private fun prefToJson(c: Context, name: String): JSONObject {
        val skip = SKIP_KEYS[name].orEmpty()
        val o = JSONObject()
        c.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (k in skip || v == null) return@forEach
            o.put(k, when (v) {
                is Boolean -> JSONObject().put("b", v)
                is Int -> JSONObject().put("i", v)
                is Long -> JSONObject().put("l", v)
                is Float -> JSONObject().put("f", v.toDouble())
                is String -> JSONObject().put("s", v)
                is Set<*> -> JSONObject().put("ss", JSONArray(v.map { it.toString() }))
                else -> return@forEach
            })
        }
        return o
    }

    data class Summary(val prefs: Int, val files: Int, val bytes: Long)

    /** 書きだす */
    fun export(c: Context, out: OutputStream): Summary {
        val names = prefNames(c)
        val version = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull().orEmpty()
        return write(out, c.packageName, version, names.associateWith { prefToJson(c, it) }, c.filesDir)
    }

    /** zip を 作る（Context なし：テスト でも つかう） */
    fun write(out: OutputStream, fromPackage: String, version: String, prefs: Map<String, JSONObject>, root: File): Summary {
        var files = 0; var bytes = 0L
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(JSONObject().put("kind", KIND).put("v", 1).put("fromPackage", fromPackage).put("version", version)
                .put("time", System.currentTimeMillis()).put("prefs", JSONArray(prefs.keys.toList())).toString(1).toByteArray())
            z.closeEntry()
            prefs.forEach { (n, o) -> z.putNextEntry(ZipEntry("prefs/$n.json")); z.write(o.toString().toByteArray()); z.closeEntry() }
            FILES.map { File(root, it) }.filter { it.exists() }.forEach { top ->
                top.walkTopDown().filter { it.isFile }.forEach { f ->
                    z.putNextEntry(ZipEntry("files/" + f.relativeTo(root).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                    files++; bytes += f.length()
                }
            }
        }
        return Summary(prefs.size, files, bytes)
    }

    /** 読みこむ（いま の 設定・ファイル は うわがき）。おわったら アプリ を 開きなおす */
    fun import(c: Context, input: InputStream): Summary = read(input, c.filesDir) { pref, o ->
        val ed = c.getSharedPreferences(pref, Context.MODE_PRIVATE).edit().clear()
        o.keys().forEach { k ->
            val v = o.getJSONObject(k)
            when {
                v.has("b") -> ed.putBoolean(k, v.getBoolean("b"))
                v.has("i") -> ed.putInt(k, v.getInt("i"))
                v.has("l") -> ed.putLong(k, v.getLong("l"))
                v.has("f") -> ed.putFloat(k, v.getDouble("f").toFloat())
                v.has("s") -> ed.putString(k, v.getString("s"))
                v.has("ss") -> ed.putStringSet(k, v.getJSONArray("ss").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
            }
        }
        ed.commit()
    }

    /** zip を 読む（Context なし）。設定 は writePref に、ファイル は root に */
    fun read(input: InputStream, root: File, writePref: (String, JSONObject) -> Unit): Summary {
        var manifest: JSONObject? = null
        var prefs = 0; var files = 0; var bytes = 0L
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name.replace('\\', '/')
                if (e.isDirectory) continue
                if (name == "manifest.json") {
                    manifest = JSONObject(String(z.readBytes()))
                    require(manifest?.optString("kind") == KIND) { "引っ越し の zip では ありません" }
                    continue
                }
                requireNotNull(manifest) { "引っ越し の zip では ありません" }
                if (name.startsWith("prefs/") && name.endsWith(".json")) {
                    val pref = name.removePrefix("prefs/").removeSuffix(".json")
                    if (pref.isBlank() || pref.contains('/') || pref in SKIP_PREFS) continue
                    writePref(pref, JSONObject(String(z.readBytes()))); prefs++
                } else if (name.startsWith("files/")) {
                    val rel = name.removePrefix("files/")
                    if (FILES.none { rel == it || rel.startsWith("$it/") }) continue
                    val f = File(root, rel)
                    require(f.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "あやしい ファイル名" }
                    f.parentFile?.mkdirs()
                    f.outputStream().use { o -> z.copyTo(o) }
                    files++; bytes += f.length()
                }
            }
        }
        requireNotNull(manifest) { "引っ越し の zip では ありません" }
        return Summary(prefs, files, bytes)
    }
}
