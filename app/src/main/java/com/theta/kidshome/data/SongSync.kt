package com.theta.kidshome.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.theta.kidshome.media.Song
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 🎹 ピアノ の 足した 曲 を タブレット どうし で そろえる（💌 チャット の ntfy を つかう）。
 * 曲 ごと に「いつ 足した／けした か」を おぼえ、あたらしい ほう を のこす（両方 で 足した 曲 は どちら も のこる）。
 */
object SongSync {
    data class Entry(val notes: String, val time: Long, val deleted: Boolean)

    private lateinit var app: Context
    private val scope = MainScope()
    private var pending: Job? = null
    var status by mutableStateOf<String?>(null)

    private fun prefs() = app.getSharedPreferences("songsync", Context.MODE_PRIVATE)

    fun init(c: Context) {
        if (::app.isInitialized) return
        app = c.applicationContext
        Chat.handlers["songs"] = ::receive
        // 半日 に 1 かい は いま の 曲 を ながす（とどかなかった タブレット の ため）
        if (System.currentTimeMillis() - prefs().getLong("sent", 0) > 12 * 3600_000L) send(delayMs = 20_000)
    }

    fun state(): Map<String, Entry> = runCatching {
        val o = JSONObject(prefs().getString("state", "{}"))
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { Entry(it.optString("n"), it.optLong("t"), it.optBoolean("d")) } }
    }.getOrDefault(emptyMap())

    private fun save(st: Map<String, Entry>) {
        val o = JSONObject(); st.forEach { (k, e) -> o.put(k, JSONObject().put("n", e.notes).put("t", e.time).put("d", e.deleted)) }
        prefs().edit().putString("state", o.toString()).apply()
    }

    /** この タブレット で 曲 が かわった か しらべて、かわって いれば おくる */
    fun localChanged(songs: List<Song>) {
        if (!::app.isInitialized) return
        val st = state().toMutableMap(); val now = System.currentTimeMillis()
        var changed = false
        songs.forEach { s ->
            val e = st[s.title]
            if (e == null || e.deleted || e.notes != s.notes) { st[s.title] = Entry(s.notes, now, false); changed = true }
        }
        st.filter { (k, e) -> !e.deleted && songs.none { it.title == k } }.forEach { (k, e) -> st[k] = e.copy(time = now, deleted = true); changed = true }
        if (changed) { save(st); send() }
    }

    fun send(delayMs: Long = 3000) {
        if (!::app.isInitialized || !Chat.on(app)) return
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            val o = JSONObject(); state().forEach { (k, e) -> o.put(k, JSONObject().put("n", e.notes).put("t", e.time).put("d", e.deleted)) }
            val ok = Chat.sendData("songs", JSONObject().put("songs", o))
            if (ok) prefs().edit().putLong("sent", System.currentTimeMillis()).apply()
            status = if (ok) "ほか の タブレット に おくりました" else "おくれません でした（💌 チャット の あいことば を きめて ください）"
        }
    }

    /** ほか の タブレット から：あたらしい ほう を のこす */
    fun merge(local: Map<String, Entry>, incoming: Map<String, Entry>): Map<String, Entry> {
        val out = local.toMutableMap()
        incoming.forEach { (k, e) -> if ((out[k]?.time ?: -1) < e.time) out[k] = e }
        return out
    }

    private fun receive(d: JSONObject) {
        val o = d.optJSONObject("songs") ?: return
        val incoming = o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { Entry(it.optString("n"), it.optLong("t"), it.optBoolean("d")) } }
        val merged = merge(state(), incoming)
        save(merged)
        val keep = merged.filter { !it.value.deleted }
        val cur = Store.config.songs
        val next = cur.filter { it.title in keep }.map { Song(it.title, keep.getValue(it.title).notes) } +
            keep.filter { (k, _) -> cur.none { it.title == k } }.map { (k, e) -> Song(k, e.notes) }
        if (next != cur) {
            Store.update { it.copy(songs = next) }
            status = "🎹 ほか の タブレット から 曲 が とどきました"
        }
    }
}
