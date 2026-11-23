package app.thetahatch.kidshome.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SD カード の みはり。
 * - 見えなく なったら 一覧 は 消さず に「さがし ちゅう」に して、もどったら 自動 で 読みなおす
 *   （Android の 知らせ（MEDIA_MOUNTED など）と、見えない あいだ は 30 びょう ごと の たしかめ）
 * - なにが おきた か を 時刻 つき で のこす（おやメニュー で 見る）
 */
object SdWatch {
    enum class State { UNKNOWN, OK, MISSING, NO_PERMISSION }

    var state by mutableStateOf(State.UNKNOWN)
        private set
    /** いま の ようす を ことば で（見えない とき の 理由） */
    var reason by mutableStateOf("")
        private set
    var log by mutableStateOf<List<String>>(emptyList())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    private fun prefs(c: Context) = c.getSharedPreferences("sdwatch", Context.MODE_PRIVATE)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        log = runCatching { JSONArray(prefs(app).getString("log", "[]")).let { a -> List(a.length()) { a.getString(it) } } }.getOrDefault(emptyList())
        // Android からの 知らせ（SD を さした・ぬいた・接触 が きれた など）
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val path = i.data?.path.orEmpty()
                val what = when (i.action) {
                    Intent.ACTION_MEDIA_MOUNTED -> "✅ SD が つかえる よう に なった"
                    Intent.ACTION_MEDIA_CHECKING -> "🔎 SD を しらべ はじめた（さした ばかり）"
                    Intent.ACTION_MEDIA_UNMOUNTED -> "⚠️ Android が SD を はずした"
                    Intent.ACTION_MEDIA_EJECT -> "⚠️ SD を とりだす 合図"
                    Intent.ACTION_MEDIA_REMOVED -> "⚠️ SD が ぬかれた"
                    Intent.ACTION_MEDIA_BAD_REMOVAL -> "❌ SD が いきなり ぬけた（はずす 前 に 接触 が きれた。カード の さしこみ・接触 を うたがう）"
                    Intent.ACTION_MEDIA_UNMOUNTABLE -> "❌ SD が 読めない（こわれて いる／形式 が ちがう かも）"
                    Intent.ACTION_MEDIA_NOFS -> "❌ SD の 中身 の 形式 が わからない"
                    Intent.ACTION_MEDIA_SHARED -> "⚠️ SD が パソコン に つながって いる（USB）"
                    else -> i.action.orEmpty()
                }
                note(app, "$what $path".trim())
                if (i.action == Intent.ACTION_MEDIA_MOUNTED) scope.launch { delay(3000); check(app, rescan = true) }
                else scope.launch { check(app, rescan = false) }
            }
        }
        val f = IntentFilter().apply {
            listOf(
                Intent.ACTION_MEDIA_MOUNTED, Intent.ACTION_MEDIA_CHECKING, Intent.ACTION_MEDIA_UNMOUNTED, Intent.ACTION_MEDIA_EJECT,
                Intent.ACTION_MEDIA_REMOVED, Intent.ACTION_MEDIA_BAD_REMOVAL, Intent.ACTION_MEDIA_UNMOUNTABLE, Intent.ACTION_MEDIA_NOFS,
                Intent.ACTION_MEDIA_SHARED,
            ).forEach { addAction(it) }
            addDataScheme("file")
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(app, receiver, f, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        }
        // たしかめ：見えない あいだ は 30 びょう ごと、読めて いる とき は 5 ふん ごと（SD に なるべく さわらない）
        scope.launch {
            while (true) {
                delay(if (state == State.OK) 5 * 60_000L else 30_000L)
                runCatching { check(app, rescan = false) }
            }
        }
    }

    /** いま の ようす を しらべて、もどって いたら 読みなおす */
    suspend fun check(c: Context, rescan: Boolean) {
        val tree = MediaLibrary.treeUri(c) ?: return
        val (st, why) = withContext(Dispatchers.IO) { probe(c, tree) }
        val was = state
        set(c, st, why)
        if (st == State.OK && (rescan || was == State.MISSING || was == State.NO_PERMISSION || MediaLibrary.isEmpty())) MediaLibrary.refresh(c, force = true)
    }

    /** ようす を かえる（かわった とき だけ 記録） */
    fun set(c: Context, st: State, why: String) {
        if (st == state && why == reason) return
        val before = state
        state = st; reason = why
        when {
            st == State.OK && before != State.UNKNOWN && before != State.OK -> note(c, "✅ SD の フォルダ が また 読める")
            st != State.OK -> note(c, "${if (st == State.NO_PERMISSION) "🔒" else "💾"} $why")
        }
    }

    fun note(c: Context, msg: String) {
        val t = SimpleDateFormat("M/d HH:mm:ss", Locale.JAPAN).format(Date())
        log = (listOf("$t  $msg") + log).take(40)
        prefs(c).edit().putString("log", JSONArray(log).toString()).apply()
    }

    fun clearLog(c: Context) { log = emptyList(); prefs(c).edit().remove("log").apply() }

    /** えらんだ フォルダ が 読める か。読めない とき は 理由 も */
    fun probe(c: Context, tree: Uri): Pair<State, String> {
        // 1. 許可（フォルダ を えらんだ とき の）が のこって いる か
        val perm = c.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
        if (!perm) return State.NO_PERMISSION to "フォルダ の 許可 が きえた（おやメニュー で「📁 フォルダ を えらびなおす」）"
        // 2. その SD（ボリューム）が いま どう なって いる か
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull().orEmpty()
        val volId = docId.substringBefore(':')
        val sm = c.getSystemService(StorageManager::class.java)
        val vol = sm?.storageVolumes?.firstOrNull { v ->
            if (volId == "primary") v.isPrimary else v.uuid?.equals(volId, ignoreCase = true) == true
        }
        if (vol == null) return State.MISSING to "SD カード（$volId）が 見えない（ぬけて いる／接触 が わるい／Android が まだ 見つけて いない）"
        val vs = vol.state
        if (vs != Environment.MEDIA_MOUNTED && vs != Environment.MEDIA_MOUNTED_READ_ONLY) {
            return State.MISSING to when (vs) {
                Environment.MEDIA_CHECKING -> "SD を しらべ ちゅう（さした ばかり。すこし まつ）"
                Environment.MEDIA_UNMOUNTED -> "SD は ささって いる が Android が はずして いる（設定 の ストレージ で「マウント」）"
                Environment.MEDIA_EJECTING -> "SD を とりだし ちゅう"
                Environment.MEDIA_BAD_REMOVAL -> "SD が いきなり ぬけた（接触 を うたがう）"
                Environment.MEDIA_REMOVED -> "SD が ぬけて いる"
                Environment.MEDIA_UNMOUNTABLE -> "SD が 読めない（こわれて いる かも。パソコン で チェック）"
                Environment.MEDIA_NOFS -> "SD の 形式 が わからない（exFAT／FAT32 で フォーマット）"
                Environment.MEDIA_SHARED -> "SD が パソコン に つながって いる"
                else -> "SD の ようす：$vs"
            }
        }
        // 3. えらんだ フォルダ そのもの を 開けるか
        return runCatching {
            val ok = c.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, docId), arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { it.moveToFirst() } ?: false
            if (ok) State.OK to "" else State.MISSING to "SD は ある が「${docId.substringAfter(':')}」フォルダ が 見つからない（名前 を かえた？ べつ の SD？）"
        }.getOrElse { State.MISSING to "フォルダ が 開けない：${it.javaClass.simpleName} ${it.message.orEmpty().take(80)}" }
    }
}
