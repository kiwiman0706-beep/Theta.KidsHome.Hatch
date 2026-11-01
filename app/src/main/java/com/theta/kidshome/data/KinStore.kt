package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** かけいず の ひと と かお写真 を アプリ の 中 に ほぞん（filesDir/kin/） */
object KinStore {
    var people by mutableStateOf<List<Person>>(emptyList())
        private set
    /** かお写真 が かわった とき */
    var photoVersion by mutableIntStateOf(0)
        private set
    private var loaded = false

    private fun dir(c: Context) = File(c.filesDir, "kin").apply { mkdirs() }

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        people = runCatching {
            val a = JSONArray(File(dir(c), "people.json").readText())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                fun str(k: String) = o.optString(k).takeIf { it.isNotBlank() && !o.isNull(k) }
                Person(
                    id = o.getString("id"), name = o.optString("name"), kana = o.optString("kana"), sex = o.optString("sex"),
                    birthYear = o.optInt("y"), birthMonth = o.optInt("m"), birthDay = o.optInt("d"),
                    father = str("father"), mother = str("mother"), spouse = str("spouse"),
                    friend = o.optBoolean("friend"), profileId = str("profile"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun save(c: Context) {
        val a = JSONArray(people.map { p ->
            JSONObject().put("id", p.id).put("name", p.name).put("kana", p.kana).put("sex", p.sex)
                .put("y", p.birthYear).put("m", p.birthMonth).put("d", p.birthDay)
                .put("father", p.father ?: JSONObject.NULL).put("mother", p.mother ?: JSONObject.NULL).put("spouse", p.spouse ?: JSONObject.NULL)
                .put("friend", p.friend).put("profile", p.profileId ?: JSONObject.NULL)
        })
        File(dir(c), "people.json").writeText(a.toString())
    }

    fun newId() = UUID.randomUUID().toString().take(8)

    fun put(c: Context, p: Person) {
        var list = people.filter { it.id != p.id } + p
        // つれあい は おたがい に。ほか の 人 の つれあい だった なら はずす
        list = list.map { o ->
            when {
                o.id == p.spouse -> o.copy(spouse = p.id)
                o.id != p.id && o.spouse == p.id && p.spouse != o.id -> o.copy(spouse = null)
                else -> o
            }
        }
        // プロフィール 本人 は 1 人 だけ
        if (p.profileId != null) list = list.map { if (it.id != p.id && it.profileId == p.profileId) it.copy(profileId = null) else it }
        people = list.sortedBy { people.indexOfFirst { o -> o.id == it.id }.let { i -> if (i < 0) Int.MAX_VALUE else i } }
        save(c)
    }

    fun remove(c: Context, id: String) {
        people = people.filter { it.id != id }.map { p ->
            p.copy(father = p.father.takeIf { it != id }, mother = p.mother.takeIf { it != id }, spouse = p.spouse.takeIf { it != id })
        }
        photo(c, id).delete()
        save(c); photoVersion++
    }

    fun photo(c: Context, id: String) = File(dir(c), "$id.jpg")

    /** えらんだ 写真 の まんなか を 正方形 に して 512 に */
    fun setPhoto(c: Context, id: String, uri: Uri) {
        val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = 1024f / minOf(info.size.width, info.size.height)
            if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
        }
        val side = minOf(bmp.width, bmp.height)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 3, side, side)
        val out = Bitmap.createScaledBitmap(sq, 512, 512, true)
        photo(c, id).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        photoVersion++
    }

    fun photoBitmap(c: Context, id: String): Bitmap? = photo(c, id).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    /** この 子 本人（なければ null） */
    fun me(profileId: String) = people.firstOrNull { it.profileId == profileId }
}
