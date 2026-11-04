package com.gluelab.jiaoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 比重那套公式必须和车间 Excel 一致（m3-m1 等），算错一个数整批数据都白填。
 * 样例来自 Excel 截图：M1~M6 = 2.9 / 1.82 / 4.42 / 2.26 / 4.38 / 2.26
 */
class DensityCalcTest {

    @Test
    fun matchesTheExcelSample() {
        val r = DensityCalc.calc(2.9, 1.82, 4.42, 2.26, 4.38, 2.26)
        assertEquals(1.52, r.d1, 1e-9)
        assertEquals(0.44, r.d2, 1e-9)
        assertEquals(1.48, r.d3, 1e-9)
        assertEquals(0.44, r.d4, 1e-9)
        assertNotNull(r.specific)
        assertEquals(1.4074, r.specific!!, 5e-5)
        assertEquals(97.3684, r.solid!!, 5e-4)
        assertEquals(-3.7037, r.foam!!, 5e-4)
    }

    @Test
    fun specificOnlyNeedsFourValues() {
        // 后两个不填（按 0）也能算比重
        val r = DensityCalc.calc(2.9, 1.82, 4.42, 2.26, 0.0, 0.0)
        assertEquals(1.4074, r.specific!!, 5e-5)
        assertNull(r.solid)
        assertNull(r.foam)
    }

    @Test
    fun emptyInputGivesNoSpecific() {
        val r = DensityCalc.calc(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertNull(r.specific)
    }

    @Test
    fun zeroDenominatorDoesNotCrash() {
        // d1 == d2 → 除零，不能崩、也不能给个假数字
        val r = DensityCalc.calc(1.0, 1.0, 3.0, 3.0, 0.0, 0.0)
        assertNull(r.specific)
    }

    @Test
    fun textFormatsNullOrValue() {
        assertEquals("—", DensityCalc.text(null))
        assertEquals("1.4074", DensityCalc.text(1.407407, 4))
        assertEquals("97.37", DensityCalc.text(97.3684, 2))
    }
}
