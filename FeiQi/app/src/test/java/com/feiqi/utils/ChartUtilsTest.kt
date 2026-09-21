package com.feiqi.utils

import androidx.compose.ui.graphics.Color
import com.feiqi.data.model.CategorySum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartUtilsTest {

    private fun cat(name: String, amount: Double) = CategorySum(name, amount, Color(0xFF000000), 0f)

    @Test
    fun calculateDonutSegments_emptyCategories_returnsEmpty() {
        assertTrue(ChartUtils.calculateDonutSegments(emptyList(), 100.0).isEmpty())
    }

    @Test
    fun calculateDonutSegments_totalZero_returnsEmpty() {
        assertTrue(ChartUtils.calculateDonutSegments(listOf(cat("a", 10.0)), 0.0).isEmpty())
    }

    @Test
    fun calculateDonutSegments_safeTotalZero_returnsEmpty() {
        assertTrue(ChartUtils.calculateDonutSegments(listOf(cat("a", 0.0)), 100.0).isEmpty())
    }

    @Test
    fun calculateDonutSegments_segmentsSumTo360_andStartAtMinus90() {
        val cats = listOf(cat("a", 25.0), cat("b", 75.0))
        val segs = ChartUtils.calculateDonutSegments(cats, 100.0)
        assertEquals(2, segs.size)
        assertEquals(-90f, segs.first().startAngle, 0.001f)
        val total = segs.sumOf { it.sweepAngle.toDouble() }
        assertEquals(360.0, total, 0.001)
        // 第二个扇区起点 = -90 + 90 = 0
        assertEquals(0f, segs[1].startAngle, 0.001f)
    }

    @Test
    fun calculateDonutSegments_proportionalSweep() {
        val cats = listOf(cat("a", 50.0), cat("b", 50.0))
        val segs = ChartUtils.calculateDonutSegments(cats, 100.0)
        assertEquals(180f, segs[0].sweepAngle, 0.001f)
        assertEquals(180f, segs[1].sweepAngle, 0.001f)
    }
}
