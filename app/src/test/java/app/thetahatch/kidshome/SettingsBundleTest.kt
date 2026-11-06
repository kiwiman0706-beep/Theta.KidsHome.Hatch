package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.SettingsBundle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBundleTest {
    @Test fun qrPartsRoundTrip() {
        val o = JSONObject().put("app", "kidshome").put("big", (1..3000).joinToString(",") { "x$it" })
        val parts = SettingsBundle.qrParts(o)
        assertTrue(parts.size > 1)
        val c = SettingsBundle.Collector()
        parts.reversed().forEach { assertTrue(c.add(it)) }
        assertTrue(c.done)
        assertEquals(o.toString(), c.result().toString())
        assertFalse(SettingsBundle.Collector().add("KH1.100.x.e3"))
    }
}
