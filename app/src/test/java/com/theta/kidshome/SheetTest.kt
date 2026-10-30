package com.theta.kidshome

import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Categories
import com.theta.kidshome.data.SheetSync
import com.theta.kidshome.data.TileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class SheetTest {
    @Test
    fun parsesQuotedCsv() {
        val rows = SheetSync.parseCsv("\"なまえ\",\"テーマ\"\n\"おねえちゃん\",\"うさぎ\"\n\"a, b\",\"x \"\"y\"\"\"\n")
        assertEquals(listOf("なまえ", "テーマ"), rows[0])
        assertEquals(listOf("a, b", "x \"y\""), rows[2])
    }

    @Test
    fun appliesSheet() {
        val base = AppConfig.default()
        val data = mapOf(
            "こども" to listOf(listOf("おねえちゃん", "ユニコーン"), listOf("いもうと", "ねこ")),
            "ボタン" to listOf(
                listOf("おねえちゃん", "どうが", "ティンピン", "https://www.netflix.com/title/1", "🐻", ""),
                listOf("ぜんいん", "おえかき", "", "", "", ""),
                listOf("いもうと", "アプリ", "くるくる", "jp.kuru", "🔢", ""),
            ),
            "カテゴリー" to listOf(listOf("べんきょう", "📚", "ぜんいん", ""), listOf("どうが", "🎬", "いもうと", "○")),
            "アプリ" to listOf(listOf("A", "jp.a", "べんきょう"), listOf("B", "jp.b", "かくす"), listOf("C", "jp.c", "")),
            "せってい" to listOf(listOf("かけ算", "むずかしい")),
        )
        val c = SheetSync.apply(base, data)
        // こどもは名前で同じ id のまま
        assertEquals(base.profiles.map { it.id }, c.profiles.map { it.id })
        assertEquals("unicorn", c.profiles[0].themeId)
        assertEquals(listOf(TileType.VIDEO, TileType.DRAWING), c.profiles[0].tiles.map { it.type })
        assertEquals("com.netflix.mediaclient", c.profiles[0].tiles[0].packageName)
        assertEquals(listOf(TileType.DRAWING, TileType.APP), c.profiles[1].tiles.map { it.type })
        val video = c.categories.first { it.name == "どうが" }
        assertEquals(setOf(c.profiles[1].id), video.visibleTo)
        assertTrue(video.isVideo)
        assertEquals(c.categories.first { it.name == "べんきょう" }.id, c.appCategory["jp.a"])
        assertEquals(Categories.HIDDEN, c.appCategory["jp.b"])
        assertTrue("jp.c" !in c.appCategory)
        assertTrue(c.hardGate)
        // 2 回読んでも id は変わらない（画面がちらつかない）
        assertEquals(c, SheetSync.apply(c, data))
    }

    @Test
    fun keepsLocalWhenSheetMissing() {
        val base = AppConfig.default()
        assertEquals(base, SheetSync.apply(base, mapOf("ボタン" to null)))
    }

    @Test
    fun writesXlsx() {
        val f = File.createTempFile("kids", ".xlsx")
        SheetSync.writeXlsx(linkedMapOf("こども" to listOf(listOf("なまえ", "テーマ"), listOf("<a&b>", "うさぎ"))), f)
        ZipFile(f).use { z ->
            assertTrue(z.getEntry("xl/workbook.xml") != null)
            val sheet = z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).bufferedReader().readText()
            assertTrue(sheet.contains("&lt;a&amp;b&gt;"))
        }
    }

    @Test
    fun appliesTimetable() {
        val base = AppConfig.default()
        val data = mapOf(
            "じかんわり" to listOf(
                listOf("おねえちゃん", "げつ", "こくご", "さんすう", "", "", "", "", "たいそうふく"),
                listOf("おねえちゃん", "水曜", "おんがく", "", "", "", "", "", ""),
            ),
        )
        val p = SheetSync.apply(base, data).profiles[0]
        assertEquals(listOf("こくご", "さんすう"), p.timetable[0])
        assertEquals(listOf("おんがく"), p.timetable[2])
        assertEquals("たいそうふく", p.belongings[0])
        assertTrue(p.hasTimetable)
        assertTrue(!SheetSync.apply(base, data).profiles[1].hasTimetable)
    }
}
