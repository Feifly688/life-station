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
    fun clamp_keepsOffsetWithinCardHeight() {
        val cardHeight = 300f
        assertEquals(0f, HomeDragRules.clampOffset(0f, cardHeight))
        assertEquals(120f, HomeDragRules.clampOffset(120f, cardHeight))
        // 关键：位移累加到几千像素时，仍被钳在"一张卡片高度"内 —— 槽位与视觉位置不脱节
        assertEquals(300f, HomeDragRules.clampOffset(9999f, cardHeight))
        assertEquals(-300f, HomeDragRules.clampOffset(-9999f, cardHeight))
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
        assertEquals(300f, value)
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

    // ---------------- 视口钳制（"拖到边缘卡片消失"的直接修复） ----------------

    private val viewTop = 100f
    private val viewBottom = 1100f

    @Test
    fun viewport_insideBounds_isUnchanged() {
        assertEquals(50f, HomeDragRules.clampToViewport(50f, 300f, 200f, viewTop, viewBottom))
    }

    @Test
    fun viewport_aboveTop_isPushedBackDown() {
        // 卡片可见顶部跑到视口上方 60px → 位移往下补 60，卡片立刻回到视口内
        val fixed = HomeDragRules.clampToViewport(-200f, viewTop - 60f, 200f, viewTop, viewBottom)
        assertEquals(-140f, fixed)
        // 钳制后卡片顶部恰好在视口上边界
        assertEquals(viewTop, viewTop - 60f + (fixed - (-200f)))
    }

    @Test
    fun viewport_belowBottom_isPushedBackUp() {
        // 卡片底部超出视口下边界 80px → 位移往上收 80
        val visualTop = viewBottom - 120f
        assertEquals(220f, HomeDragRules.clampToViewport(300f, visualTop, 200f, viewTop, viewBottom))
    }

    @Test
    fun viewport_tallerThanViewport_alignsTopOnly() {
        // 卡片比视口还高：只保证顶部可见，避免上下同时钳制来回抖
        assertEquals(10f, HomeDragRules.clampToViewport(0f, viewTop - 10f, 2000f, viewTop, viewBottom))
    }

    @Test
    fun viewport_degenerateInputs_areUnchanged() {
        assertEquals(12f, HomeDragRules.clampToViewport(12f, 0f, 100f, 500f, 500f))
        assertEquals(12f, HomeDragRules.clampToViewport(12f, 0f, 0f, viewTop, viewBottom))
    }

    // ---------------- 让位判定（"其余卡片不提前让位"） ----------------

    @Test
    fun swap_doesNotYieldBeforeReachingTargetSlot() {
        val n = 300f
        // 旧阈值（半块）与新阈值（0.9 块）都不再让位 —— 其余卡片保持不动
        assertEquals(0, HomeDragRules.swapDirection(n / 2f, n, n))
        assertEquals(0, HomeDragRules.swapDirection(n * 0.9f, n, n))
        // 真正悬停到目标位置（越过整块）才让位
        assertEquals(1, HomeDragRules.swapDirection(n * 1.01f, n, n))
    }

    @Test
    fun swap_upward_alsoRequiresFullBlock() {
        val prev = 260f
        assertEquals(0, HomeDragRules.swapDirection(-prev * 0.9f, 0f, prev))
        assertEquals(-1, HomeDragRules.swapDirection(-prev * 1.01f, 0f, prev))
    }

    @Test
    fun swap_atListBoundaries_neverYields() {
        // 队首继续向上拖 / 队尾继续向下拖：没有邻块（高度 0）→ 不让位
        assertEquals(0, HomeDragRules.swapDirection(-9999f, 300f, 0f))
        assertEquals(0, HomeDragRules.swapDirection(9999f, 0f, 300f))
    }

    @Test
    fun swap_residualAfterSwapIsNearZero_soNoJumpAndNoFlapping() {
        val n = 320f
        val offsetAtSwap = n + 1f
        assertEquals(1, HomeDragRules.swapDirection(offsetAtSwap, n, n))
        // 换位时 dragOffsetY -= n → 残差≈0（视觉连续、不跳动）
        val residual = offsetAtSwap - n
        assertTrue("残差应接近 0，实际 $residual", kotlin.math.abs(residual) < n * 0.05f)
        // 残差不足以触发反向让位（天然滞回，不会来回跳）
        assertEquals(0, HomeDragRules.swapDirection(residual, n, n))
    }

    @Test
    fun step_quarterViewportZone_triggersBeforeReachingTheEdge() {
        // 感应区 = 可视区高度 × 1/4：进入上下各 1/4 区域即开始滚动（无需拖到最边缘）
        val top = 0f
        val bottom = 800f
        val zone = (bottom - top) / 4f // 200
        val justInsideBottom = HomeDragRules.autoScrollStep(
            pointerRootY = bottom - zone + 1f,
            listTop = top,
            listBottom = bottom,
            zone = zone,
            minStep = 3f,
            maxStep = 20f,
            canScrollUp = true,
            canScrollDown = true
        )
        assertTrue("进入下 1/4 区域就应开始滚动，实际 $justInsideBottom", justInsideBottom > 0f)

        val outsideZone = HomeDragRules.autoScrollStep(
            pointerRootY = bottom - zone - 1f,
            listTop = top,
            listBottom = bottom,
            zone = zone,
            minStep = 3f,
            maxStep = 20f,
            canScrollUp = true,
            canScrollDown = true
        )
        assertEquals("1/4 区域之外不应滚动", 0f, outsideZone)
    }
}
