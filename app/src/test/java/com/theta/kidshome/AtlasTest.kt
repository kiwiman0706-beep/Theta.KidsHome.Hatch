package com.theta.kidshome

import com.theta.kidshome.data.Atlas
import com.theta.kidshome.data.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasTest {
    @Test fun encodeRoundTrip() {
        val pts = listOf(LatLon(35.68123, 139.76712), LatLon(35.0, 136.9), LatLon(34.70254, 135.49595))
        val back = Atlas.decode(Atlas.encode(pts))
        assertEquals(pts.size, back.size)
        pts.zip(back).forEach { (a, b) -> assertEquals(a.lat, b.lat, 1e-5); assertEquals(a.lon, b.lon, 1e-5) }
        assertTrue(Atlas.decode("").isEmpty())
    }

    @Test fun distance() {
        // 東京駅 → 新大阪駅 は まっすぐ 約 400km
        val d = Atlas.km(LatLon(35.6812, 139.7671), LatLon(34.7334, 135.5001))
        assertTrue(d in 395.0..410.0)
        assertEquals("350 メートル", Atlas.distanceText(0.348))
        assertEquals("1 じかん 5 ふん", Atlas.timeText(65))
    }

    @Test fun simplifyAndJoin() {
        val line = (0..100).map { LatLon(35.0, 139.0 + it * 0.001) }
        assertEquals(2, Atlas.simplify(line, 10.0).size)
        val joined = Atlas.join(listOf(line.take(51), line.drop(50).reversed()))
        assertEquals(1, joined.size)
        assertEquals(101, joined[0].size)
    }
}
