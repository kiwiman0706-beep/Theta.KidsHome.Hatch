package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.ShopItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopItemsTest {
    @Test fun roundTrip() {
        val i = ShopItems.Item("x", "にんじん", 50, "yaoya")
        val code = ShopItems.encode(i)
        assertTrue(code, code.all { it.isLetterOrDigit() || it == '.' }) // リーダー でも うてる 文字 だけ
        val d = ShopItems.decode(code)!!
        assertEquals("にんじん", d.name); assertEquals(50, d.price); assertEquals("yaoya", d.shop)
        assertEquals("ケーキ", ShopItems.decode(ShopItems.encode(ShopItems.Item("", "ケーキ", 380, "panya")).lowercase())!!.name)
        assertNull(ShopItems.decode("https://example.com"))
    }

    @Test fun rounding() {
        assertEquals(50, ShopItems.round(48))
        assertEquals(100, ShopItems.round(98))
        assertEquals(130, ShopItems.round(121))
        assertEquals(10, ShopItems.round(1))
    }
}
