package com.theta.kidshome.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 天気を出す場所。useLocation なら端末のおおよその位置で、lat・lon はその都度更新（車で移動するので） */
data class WeatherPlace(val name: String, val lat: Double, val lon: Double, val useLocation: Boolean = false)

data class Day(val date: Long, val code: Int, val max: Double, val min: Double, val rain: Int)

data class Forecast(val temp: Double, val code: Int, val isDay: Boolean, val days: List<Day>, val fetchedAt: Long)

/**
 * 天気予報（Open-Meteo。API キー不要・無料）。Theta Home Hatch と同じ。
 * 送るのは緯度・経度（小数 2 桁に丸める）だけ。
 */
object Weather {
    var forecast by mutableStateOf<Forecast?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var loading = false
    private val scope = MainScope()

    /** 30 分に 1 回まで取り直す */
    fun refresh(context: Context, force: Boolean = false) {
        val place = Store.config.weather ?: return
        val last = forecast?.fetchedAt ?: 0L
        if (loading || (!force && System.currentTimeMillis() - last < 30 * 60_000)) return
        loading = true
        val app = context.applicationContext
        scope.launch {
            val r = runCatching {
                val p = if (place.useLocation) locate(app)?.let { (la, lo) -> place.copy(lat = la, lon = lo) } ?: place else place
                withContext(Dispatchers.IO) { fetch(p) }
            }
            r.getOrNull()?.let { forecast = it }
            error = r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            loading = false
        }
    }

    fun clear() {
        forecast = null
    }

    private fun fetch(place: WeatherPlace): Forecast {
        val lat = "%.2f".format(Locale.US, place.lat)
        val lon = "%.2f".format(Locale.US, place.lon)
        val text = get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=3&timeformat=unixtime"
        )
        return parse(text, System.currentTimeMillis())
    }

    fun parse(text: String, now: Long): Forecast {
        val o = JSONObject(text)
        val c = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).map { i ->
            Day(
                dt.getLong(i) * 1000, d.getJSONArray("weather_code").optInt(i),
                d.getJSONArray("temperature_2m_max").optDouble(i), d.getJSONArray("temperature_2m_min").optDouble(i),
                d.getJSONArray("precipitation_probability_max").optInt(i),
            )
        }
        return Forecast(c.optDouble("temperature_2m"), c.optInt("weather_code"), c.optInt("is_day", 1) == 1, days, now)
    }

    /** 地名から場所を探す（日本語可） */
    suspend fun search(name: String): List<WeatherPlace> = withContext(Dispatchers.IO) {
        val o = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=8&language=ja&name=" + URLEncoder.encode(name.trim(), "UTF-8")))
        val a = o.optJSONArray("results") ?: return@withContext emptyList()
        (0 until a.length()).map { a.getJSONObject(it) }.map { r ->
            val label = listOfNotNull(r.optString("name").ifEmpty { null }, r.optString("admin1").ifEmpty { null }).distinct().joinToString(" / ")
            WeatherPlace(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    /** WMO の天気コード → えもじ */
    fun emoji(code: Int, isDay: Boolean = true): String = when (code) {
        0, 1 -> if (isDay) "☀️" else "🌙"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "☔"
        in 71..77, 85, 86 -> "⛄"
        in 95..99 -> "⚡"
        else -> "☁️"
    }

    /** WMO の天気コード → ひらがな */
    fun label(code: Int): String = when (code) {
        0, 1 -> "はれ"
        2 -> "はれ ときどき くもり"
        3 -> "くもり"
        45, 48 -> "きり"
        in 51..67, in 80..82 -> "あめ"
        in 71..77, 85, 86 -> "ゆき"
        in 95..99 -> "かみなり"
        else -> "くもり"
    }

    /** 端末のおおよその位置（ネットワーク）。許可が無い・取れないときは null */
    @SuppressLint("MissingPermission")
    private suspend fun locate(context: Context): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val loc = if (Build.VERSION.SDK_INT >= 30) {
            withTimeoutOrNull(8000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cancel = CancellationSignal()
                    cont.invokeOnCancellation { cancel.cancel() }
                    lm.getCurrentLocation(provider, cancel, context.mainExecutor) { cont.resumeWith(Result.success(it)) }
                }
            }
        } else null
        val l = loc ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: return null
        return l.latitude to l.longitude
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
        return c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
