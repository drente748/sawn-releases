package com.sawn.app.ui.status

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverageStateTest {
    @Test
    fun allThree_on_isFull() {
        assertEquals(CoverageLevel.Full, coverageLevel(CoverageState(true, true, true)))
    }

    @Test
    fun anyTwo_on_isPartial() {
        assertEquals(CoverageLevel.Partial, coverageLevel(CoverageState(true, false, true)))
    }

    @Test
    fun onlyOne_on_isPartial() {
        assertEquals(CoverageLevel.Partial, coverageLevel(CoverageState(true, false, false)))
    }

    @Test
    fun allOff_isOff() {
        assertEquals(CoverageLevel.Off, coverageLevel(CoverageState(false, false, false)))
    }

    @Test
    fun anotherRealCombination_accessibilityOnly_isPartial() {
        assertEquals(CoverageLevel.Partial, coverageLevel(CoverageState(false, true, false)))
    }
}
