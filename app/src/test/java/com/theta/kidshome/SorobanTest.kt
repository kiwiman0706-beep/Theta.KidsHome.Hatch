package com.theta.kidshome

import com.theta.kidshome.ui.Rod
import com.theta.kidshome.ui.Soroban
import org.junit.Assert.assertEquals
import org.junit.Test

class SorobanTest {
    @Test fun value() {
        assertEquals(1207L, Soroban.value(listOf(Rod(), Rod(false, 1), Rod(false, 2), Rod(), Rod(true, 2))))
    }

    @Test fun touchOnes() {
        assertEquals(3, Soroban.touchOne(Rod(ones = 0), 2).ones) // 3 こ め まで 上げる
        assertEquals(1, Soroban.touchOne(Rod(ones = 3), 1).ones) // 2 こ め から 下げる
        assertEquals(4, Soroban.touchOne(Rod(ones = 2), 3).ones)
    }
}
