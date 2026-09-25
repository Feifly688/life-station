package com.feiqi.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页拖拽的两条关键规则（「卡片被拖到边缘后丢失、只剩空白」的成因所在）：
 * 1. [HomeDragRules.clampOffset] —— 位移永远有界，卡片不会被推出可视区；
 * 2. [HomeDragRules.autoScrollStep] —— 已到队首/队尾时停止滚动，避免"滚得动但换不了位"。
 */
class HomeDragRulesTest {

    // ---------------- 位移钳制 ----------------

    @Test
    fun clamp_keepsOffsetWithinOneAndHalfBlock() {
        val maxHeight = 300f
        assertEquals(0f, HomeDragRules.clampOffset(0f, maxHeight))
        assertEquals(120f, HomeDragRules.clampOffset(120f, maxHeight))
        // 关键：自动滚动把位移累加到几千像素时，仍然被钳在 1.5 块高度内
        assertEquals(450f, HomeDragRules.clampOffset(9999f, maxHeight))
        assertEquals(-450f, HomeDragRules.clampOffset(-9999f, maxHeight))
    }

    @Test
    fun clamp_withNoMeasuredHeight_returnsZero() {
        assertEquals(0f, HomeDragRules.clampOffset(500f, 0f))
    }

    @Test
    fun clamp_neverGrowsWithRepeatedApplication() {
        // 反复钳制不会放大位移（幂等性），保证逐帧调用安全
        var value = 800f
        repeat(10) { value = HomeDragRules.clampOffset(value, 300f) }
        assertEquals(450f, value)
    }

    // ---------------- 边缘自动滚动 ----------------

    private val top = 100f
    private val bottom = 1100f
    private val zone = 96f
    private val minStep = 6f
    private val maxStep = 42f

    private fun step(y: Float, canUp: Boolean = true, canDown: Boolean = true) =
        HomeDragRules.autoScrollStep(y, top, bottom, zone, minStep, maxStep, canUp, canDown)

    @Test
    fun step_inMiddle_isZero() {
        assertEquals(0f, step(600f))
        // 感应区外沿：刚好等于边界不进区
        assertEquals(0f, step(top + zone))
        assertEquals(0f, step(bottom - zone))
    }

    @Test
    fun step_nearTop_scrollsUp() {
        val s = step(top + zone / 2f)
        assertTrue("靠近上边缘应向上滚（负值），实际 $s", s < 0f)
    }

    @Test
    fun step_nearBottom_scrollsDown() {
        val s = step(bottom - zone / 2f)
        assertTrue("靠近下边缘应向下滚（正值），实际 $s", s > 0f)
    }

    @Test
    fun step_closerToEdgeIsFaster_andCappedAtMax() {
        val half = step(bottom - zone / 2f)
        val edge = step(bottom)
        assertTrue("越靠边越快：$half → $edge", edge > half)
        assertEquals("贴边时达到最大速度", maxStep, edge, 0.01f)
        assertEquals("刚进感应区时为最小速度", minStep, step(bottom - zone + 0.01f), 0.5f)
    }

    @Test
    fun step_atFirstOrLastBlock_isZero_evenAtEdge() {
        // 已在队首还想往上拖 / 已在队尾还想往下拖 → 不滚动（这是防"卡片飞出"的关键规则）
        assertEquals(0f, step(top, canUp = false, canDown = true))
        assertEquals(0f, step(bottom, canUp = true, canDown = false))
    }

    @Test
    fun step_withZeroZone_isZero() {
        assertEquals(
            0f,
            HomeDragRules.autoScrollStep(0f, top, bottom, 0f, minStep, maxStep, true, true)
        )
    }

    @Test
    fun step_signMatchesDirection() {
        // 上方只可能是负值（向上滚）、下方只可能是正值（向下滚）
        for (y in listOf(top, top + zone / 4f, top + zone - 1f)) {
            assertTrue(step(y) <= 0f)
        }
        for (y in listOf(bottom - zone + 1f, bottom - zone / 4f, bottom)) {
            assertTrue(step(y) >= 0f)
        }
    }
}
