package com.theta.kidshome

import com.theta.kidshome.data.Points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointsTest {
    @Test fun billRoundTrip() {
        val b = Points.Bill(50, Points.newSerial())
        assertEquals(b, Points.decodeBill(Points.encodeBill(b)))
        assertEquals(10, Points.newSerial().length)
        assertTrue(Points.newSerial().all { it.isLetterOrDigit() })
    }

    @Test fun badBills() {
        assertNull(Points.decodeBill("KH1.100.yasai.e381"))
        assertNull(Points.decodeBill("KHP1.0.ABCDEFGH"))
        assertNull(Points.decodeBill("KHP1.5000.ABCDEFGH"))
        assertNull(Points.decodeBill("KHP1.10.AB"))
        assertNull(Points.decodeBill("KHP1.10.ABC-DEFGH"))
        assertNull(Points.decodeBill("hello"))
    }
}
