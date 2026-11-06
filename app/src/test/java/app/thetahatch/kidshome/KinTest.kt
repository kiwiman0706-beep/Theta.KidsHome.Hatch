package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.Kin
import app.thetahatch.kidshome.data.Person
import org.junit.Assert.assertEquals
import org.junit.Test

class KinTest {
    private val people = listOf(
        Person("gp", "おじいちゃん", sex = "m", spouse = "gm"),
        Person("gm", "おばあちゃん", sex = "f"),
        Person("dad", "パパ", sex = "m", father = "gp", mother = "gm", spouse = "mom", birthYear = 1985),
        Person("aunt", "ゆみ", sex = "f", father = "gp", mother = "gm", birthYear = 1990, spouse = "uncle2"),
        Person("uncle2", "けん", sex = "m"),
        Person("mom", "ママ", sex = "f"),
        Person("me", "はな", sex = "f", father = "dad", mother = "mom", birthYear = 2018, birthMonth = 5, birthDay = 1),
        Person("sis", "めい", sex = "f", father = "dad", mother = "mom", birthYear = 2020),
        Person("cousin", "たろう", sex = "m", father = "uncle2", mother = "aunt"),
    )

    private fun rel(a: String, b: String) = Kin.relation(people, a, b)!!

    @Test fun fromMe() {
        assertEquals("おばさん", rel("me", "aunt").term)
        assertEquals("はな の おとうさん の いもうと", rel("me", "aunt").chain)
        assertEquals("おじさん", rel("me", "uncle2").term)
        assertEquals("いとこ", rel("me", "cousin").term)
        assertEquals("いもうと", rel("me", "sis").term)
        assertEquals("おねえさん", rel("sis", "me").term)
        assertEquals("おじいさん", rel("me", "gp").term)
    }

    @Test fun fromDad() {
        assertEquals("おとうさん", rel("dad", "gp").term)
        assertEquals("いもうと", rel("dad", "aunt").term)
        assertEquals("おい", rel("aunt", "cousin").let { Kin.relation(people, "dad", "cousin")!! }.term)
        assertEquals("むすめ", rel("dad", "me").term)
        assertEquals("おくさん", rel("dad", "mom").term)
        assertEquals("ぎり の おとうさん", rel("mom", "gp").term)
        assertEquals("めい", rel("aunt", "me").term)
    }

    @Test fun generations() {
        val g = Kin.generations(people, "me")
        assertEquals(-2, g["gp"])
        assertEquals(-1, g["aunt"])
        assertEquals(0, g["cousin"])
    }

    @Test fun birthday() {
        val c = java.util.Calendar.getInstance().apply { set(2026, 3, 30) }
        assertEquals(1, Kin.daysUntil(people[6], c))
        assertEquals(7, Kin.age(people[6], 2026, 4, 30))
        assertEquals(8, Kin.age(people[6], 2026, 5, 1))
    }
}
