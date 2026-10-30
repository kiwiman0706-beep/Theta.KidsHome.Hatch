package com.theta.kidshome.data

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * QR コードで読み取った文字から、何の設定かを見分ける。
 * - Google スプレッドシートのリンク
 * - Gemini の API キー（AIza…）
 * - カレンダーの iCal アドレス
 * - まとめて渡す「kidshome:setup?…」（別のタブレットの おやメニューで表示したもの）
 */
object QrSetup {
    data class Setup(
        val sheetUrl: String? = null,
        val geminiKey: String? = null,
        val geminiModel: String? = null,
        val calendarUrls: List<String> = emptyList(),
    ) {
        val isEmpty get() = sheetUrl == null && geminiKey == null && geminiModel == null && calendarUrls.isEmpty()

        /** 読み取ったものの説明（「スプレッドシート・Gemini キー」など） */
        fun summary(): String = listOfNotNull(
            sheetUrl?.let { "スプレッドシート" },
            geminiKey?.let { "Gemini キー" },
            geminiModel?.let { "Gemini モデル" },
            calendarUrls.takeIf { it.isNotEmpty() }?.let { "カレンダー ${it.size}こ" },
        ).joinToString("・")
    }

    private const val PREFIX = "kidshome:setup?"
    private val geminiKey = Regex("^AIza[0-9A-Za-z_-]{30,}$")

    fun isSheet(s: String) = s.startsWith("http") && SheetSync.sheetId(s) != null
    fun isGeminiKey(s: String) = geminiKey.matches(s)
    fun isCalendar(s: String) =
        s.startsWith("webcal://") ||
            (s.startsWith("http") && (s.substringBefore('?').endsWith(".ics") || "calendar.google.com/calendar/ical/" in s))

    fun parse(raw: String): Setup {
        val s = raw.trim()
        return when {
            s.startsWith(PREFIX, ignoreCase = true) -> {
                val q = s.substring(PREFIX.length).split('&').mapNotNull {
                    val k = it.substringBefore('=', "")
                    if (k.isEmpty()) null else k to URLDecoder.decode(it.substringAfter('='), "UTF-8").trim()
                }
                Setup(
                    sheetUrl = q.lastOrNull { it.first == "sheet" }?.second?.takeIf(::isSheet),
                    geminiKey = q.lastOrNull { it.first == "gemini" }?.second?.takeIf(::isGeminiKey),
                    geminiModel = q.lastOrNull { it.first == "model" }?.second?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{3,80}")) },
                    calendarUrls = q.filter { it.first == "ical" }.map { it.second }.filter(::isCalendar),
                )
            }
            isSheet(s) -> Setup(sheetUrl = s)
            isGeminiKey(s) -> Setup(geminiKey = s)
            isCalendar(s) -> Setup(calendarUrls = listOf(s))
            else -> Setup()
        }
    }

    /** 別のタブレットへ まとめて渡す QR の中身 */
    fun encode(setup: Setup): String {
        fun e(v: String) = URLEncoder.encode(v, "UTF-8")
        return PREFIX + buildList {
            setup.sheetUrl?.let { add("sheet=${e(it)}") }
            setup.geminiKey?.let { add("gemini=${e(it)}") }
            setup.geminiModel?.let { add("model=${e(it)}") }
            setup.calendarUrls.forEach { add("ical=${e(it)}") }
        }.joinToString("&")
    }
}
