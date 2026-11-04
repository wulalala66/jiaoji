package com.wulalala66.jiaoji

import org.junit.Assert.assertEquals
import org.junit.Test

class CalcTest {

    private fun v(expr: String) = Calc.eval(expr)

    @Test
    fun basicArithmetic() {
        assertEquals(4.0, v("2+2"), 1e-9)
        assertEquals(2.5, v("10/4"), 1e-9)
        assertEquals(14.0, v("2+3*4"), 1e-9)
        assertEquals(20.0, v("(2+3)*4"), 1e-9)
        assertEquals(-5.0, v("-5"), 1e-9)
        assertEquals(1.0, v("3-2"), 1e-9)
    }

    @Test
    fun percentAndPower() {
        assertEquals(157.98, v("(600*26.33)/100"), 1e-9)
        assertEquals(8.0, v("2^3"), 1e-9)
        assertEquals(1.0, v("10%3"), 1e-9)
    }

    @Test
    fun chineseSymbolsAreNormalised() {
        assertEquals(6.0, v("（2+4）"), 1e-9)
        assertEquals(6.0, v("2×3"), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun divideByZeroFails() {
        v("1/0")
    }
}
