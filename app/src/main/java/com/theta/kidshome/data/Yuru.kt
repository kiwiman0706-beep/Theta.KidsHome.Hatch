package com.theta.kidshome.data

import android.content.Context
import org.json.JSONObject

/** 都道府県 の ゆるキャラ（assets/content/yuruchara.json）。絵 は 著作権 が ある ので 入れない */
object Yuru {
    data class Chara(val pref: Int, val name: String, val kana: String, val who: String, val emoji: String, val story: String)

    @Volatile private var cache: List<Chara>? = null

    fun parse(json: String): List<Chara> {
        val a = JSONObject(json).getJSONArray("chara")
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Chara(it.getInt("pref"), it.getString("name"), it.optString("kana", it.getString("name")), it.optString("who"), it.optString("emoji", "🧸"), it.getString("story"))
        }
    }

    fun all(c: Context): List<Chara> = cache ?: runCatching {
        parse(c.assets.open("content/yuruchara.json").bufferedReader().readText())
    }.getOrDefault(emptyList()).also { cache = it }

    fun of(c: Context, pref: Int) = all(c).filter { it.pref == pref }
}
