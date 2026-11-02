package com.theta.kidshome

import com.theta.kidshome.data.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun breakdown() {
        assertEquals(mapOf(500 to 1, 100 to 2, 50 to 1, 10 to 3, 5 to 1, 1 to 2), Money.breakdown(787))
        assertEquals(mapOf(1000 to 2), Money.breakdown(2000))
        assertEquals(787, Money.sum(Money.breakdown(787)))
    }

    @Test fun prices() {
        assertEquals(50, Money.price(48, 0))
        assertEquals(100, Money.price(980, 0))
        assertEquals(10, Money.price(1, 0))
        assertEquals(48, Money.price(48, 1))
        assertEquals(107, Money.total(listOf(48, 50), 2)) // 98 × 1.1 = 107.8 → 107
        assertEquals(98, Money.total(listOf(48, 50), 1))
    }

    @Test fun customerPays() {
        assertEquals(287, Money.customerPays(287, 0))
        assertEquals(300, Money.customerPays(287, 1))
        assertEquals(500, Money.customerPays(287, 2))
        assertEquals(1000, Money.customerPays(287, 3))
        assertEquals(1000, Money.customerPays(500, 1)) // おなじ 金額 は 1 つ に まとめる（500 と 1000 だけ）
    }
}
