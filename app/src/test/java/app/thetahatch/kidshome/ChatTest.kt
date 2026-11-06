package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.Chat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTest {
    @Test fun roundTrip() {
        val s = Chat.newSecret()
        val plain = "{\"text\":\"だいすき\"}".toByteArray()
        val enc = Chat.encrypt(s, plain)
        assertArrayEquals(plain, Chat.decrypt(s, enc))
        assertTrue(runCatching { Chat.decrypt(Chat.newSecret(), enc) }.isFailure)
    }

    @Test fun topic() {
        assertEquals(Chat.topic("abcdefgh12345678"), Chat.topic("abcdefgh12345678"))
        assertNotEquals(Chat.topic("abcdefgh12345678"), Chat.topic("abcdefgh12345679"))
        assertEquals(32, Chat.topic("x").length)
    }
}
