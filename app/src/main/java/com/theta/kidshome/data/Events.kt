package com.theta.kidshome.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class CalEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

/**
 * 端末のカレンダー（子どものアカウントの Google カレンダーなど）の予定。許可が無ければ出さない。
 * 今月の初めから、来月の初めまで（月表示と週表示の両方に使う）。
 */
object Events {
    var list by mutableStateOf<List<CalEvent>>(emptyList())
        private set
    private var loadedAt = 0L
    private val scope = MainScope()

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    var error by mutableStateOf<String?>(null)
        private set

    /** 端末のカレンダー（許可があれば）と、iCal の URL の予定をあわせる */
    fun refresh(context: Context, force: Boolean = false) {
        val urls = Store.config.calendarUrls
        if (!hasPermission(context) && urls.isEmpty()) return
        if (!force && System.currentTimeMillis() - loadedAt < 10 * 60_000) return
        loadedAt = System.currentTimeMillis()
        val app = context.applicationContext
        scope.launch {
            val local = if (hasPermission(app)) runCatching { withContext(Dispatchers.IO) { query(app) } }.getOrDefault(emptyList()) else emptyList()
            val (from, to) = range()
            val errors = mutableListOf<String>()
            val remote = urls.flatMapIndexed { i, url ->
                runCatching { withContext(Dispatchers.IO) { Ical.parse(get(url), from, to, icalColors[i % icalColors.size]) } }
                    .onFailure { errors += it.message ?: "読めませんでした" }.getOrDefault(emptyList())
            }
            error = errors.firstOrNull()
            list = (local + remote).sortedBy { it.begin }
        }
    }

    private val icalColors = listOf(0xFF4DA3FF.toInt(), 0xFFFF6B8B.toInt(), 0xFF6BCB77.toInt(), 0xFFFF9F43.toInt())

    private fun range(): Pair<Long, Long> {
        val from = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            add(Calendar.DAY_OF_MONTH, -7)
        }.timeInMillis
        return from to from + 50L * 24 * 3600_000
    }

    private fun get(url: String): String {
        // webcal:// は https:// として読む
        val u = url.trim().replaceFirst(Regex("^webcal://"), "https://")
        val c = java.net.URL(u).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
        return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun query(context: Context): List<CalEvent> {
        val (from, to) = range()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val cols = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
        )
        val out = mutableListOf<CalEvent>()
        context.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                out += CalEvent(c.getString(0).orEmpty(), c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4))
            }
        }
        return out
    }

    /** その日（0 時〜24 時）にかかる予定 */
    fun on(dayStart: Long): List<CalEvent> {
        val dayEnd = dayStart + 24 * 3600_000L
        return list.filter { e ->
            if (e.allDay) {
                // 終日の予定は UTC の日付で入っている
                val tz = java.util.TimeZone.getDefault().getOffset(dayStart)
                e.begin - tz < dayEnd && e.end - tz > dayStart
            } else e.begin < dayEnd && e.end > dayStart
        }
    }
}
