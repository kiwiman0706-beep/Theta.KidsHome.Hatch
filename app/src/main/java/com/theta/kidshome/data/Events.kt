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

    fun refresh(context: Context, force: Boolean = false) {
        if (!hasPermission(context) || (!force && System.currentTimeMillis() - loadedAt < 10 * 60_000)) return
        loadedAt = System.currentTimeMillis()
        val app = context.applicationContext
        scope.launch {
            list = runCatching { withContext(Dispatchers.IO) { query(app) } }.getOrDefault(emptyList())
        }
    }

    private fun query(context: Context): List<CalEvent> {
        val from = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            add(Calendar.DAY_OF_MONTH, -7)
        }.timeInMillis
        val to = from + 50L * 24 * 3600_000
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
