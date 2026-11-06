package com.theta.kidshome

import com.theta.kidshome.data.Migration
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MigrationTest {
    private fun tmp(): File = Files.createTempDirectory("mig").toFile()

    @Test fun roundTrip() {
        val src = tmp()
        File(src, "drawings/p1/abc").mkdirs(); File(src, "drawings/p1/abc/marks.json").writeText("[1,2]")
        File(src, "shop_items.json").writeText("{\"a\":1}")
        File(src, "packs/stamps").mkdirs(); File(src, "packs/stamps/x.webp").writeText("pack")   // 入れない
        File(src, "dict").mkdirs(); File(src, "dict/kokugo.sqlite").writeText("big")             // 入れない
        val prefs = mapOf(
            "kidshome" to JSONObject().put("config", JSONObject().put("s", "{...}")),
            "points" to JSONObject().put("bal:p1", JSONObject().put("i", 123)),
        )
        val out = ByteArrayOutputStream()
        val w = Migration.write(out, "com.theta.kidshome", "0.61.0", prefs, src)
        assertEquals(2, w.prefs); assertEquals(2, w.files)

        val dst = tmp()
        val got = HashMap<String, JSONObject>()
        val r = Migration.read(ByteArrayInputStream(out.toByteArray()), dst) { n, o -> got[n] = o }
        assertEquals(2, r.prefs); assertEquals(2, r.files)
        assertEquals(123, got.getValue("points").getJSONObject("bal:p1").getInt("i"))
        assertEquals("[1,2]", File(dst, "drawings/p1/abc/marks.json").readText())
        assertTrue(File(dst, "shop_items.json").exists())
        assertFalse(File(dst, "packs").exists()); assertFalse(File(dst, "dict").exists())
    }

    @Test fun rejectsOtherZipsAndSecretsAndEscapes() {
        val bad = ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("hello.txt")); z.write(1) } }
        assertTrue(runCatching { Migration.read(ByteArrayInputStream(bad.toByteArray()), tmp()) { _, _ -> } }.isFailure)

        val evil = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z ->
                z.putNextEntry(ZipEntry("manifest.json")); z.write(JSONObject().put("kind", Migration.KIND).toString().toByteArray()); z.closeEntry()
                z.putNextEntry(ZipEntry("prefs/secrets.json")); z.write("{}".toByteArray()); z.closeEntry()       // 秘密 は 読まない
                z.putNextEntry(ZipEntry("files/drawings/../../evil")); z.write(1); z.closeEntry()               // そと へ は 書かない
            }
        }
        val got = HashMap<String, JSONObject>()
        val r = runCatching { Migration.read(ByteArrayInputStream(evil.toByteArray()), tmp()) { n, o -> got[n] = o } }
        assertTrue(r.isFailure)
        assertFalse(got.containsKey("secrets"))
    }
}
