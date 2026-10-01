package com.feiqi.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页网格布局规则：
 * - 列数自适应（超窄屏单列）；
 * - 半宽瓷砖不成对时补满整行（不出现半行空隙）。
 */
class HomeGridRulesTest {

    // ---------------- 列数 ----------------

    @Test
    fun columns_ultraNarrow_isSingleColumn() {
        // 320 / 339dp 等窄屏：双列会让卡片过窄、标题大量截断 → 单列
        assertEquals(1, HomeGridRules.columnsFor(320f))
        assertEquals(1, HomeGridRules.columnsFor(339f))
        assertEquals(1, HomeGridRules.columnsFor(0f))
    }

    @Test
    fun columns_normal_isTwoColumns() {
        assertEquals(2, HomeGridRules.columnsFor(340f))
        assertEquals(2, HomeGridRules.columnsFor(411f))
        assertEquals(2, HomeGridRules.columnsFor(800f)) // 大屏由限宽 720dp 居中处理，列数仍为 2
    }

    // ---------------- 占列数（span） ----------------

    @Test
    fun spans_singleColumn_everyCardFillsTheRow() {
        val spans = HomeGridRules.spansFor(listOf(1, 2, 1, 2), columns = 1)
        assertEquals(listOf(1, 1, 1, 1), spans)
    }

    @Test
    fun spans_paired_keepsBothHalfWidth() {
        assertEquals(listOf(1, 1), HomeGridRules.spansFor(listOf(1, 1), columns = 2))
        assertEquals(listOf(1, 1, 2), HomeGridRules.spansFor(listOf(1, 1, 2), columns = 2))
    }

    @Test
    fun spans_loneHalfWidth_promotesToFullRow() {
        // 孤立半宽（前 / 后 / 中）都提升为整行，否则右半边留白
        assertEquals(listOf(2), HomeGridRules.spansFor(listOf(1), columns = 2))
        assertEquals(listOf(2, 2), HomeGridRules.spansFor(listOf(1, 2), columns = 2))
        assertEquals(listOf(2, 2), HomeGridRules.spansFor(listOf(2, 1), columns = 2))
        assertEquals(listOf(1, 1, 2), HomeGridRules.spansFor(listOf(1, 1, 1), columns = 2))
        assertEquals(listOf(2, 2, 2), HomeGridRules.spansFor(listOf(1, 2, 1), columns = 2))
    }

    @Test
    fun spans_fullWidthCards_areUntouched() {
        assertEquals(listOf(2, 2, 2), HomeGridRules.spansFor(listOf(2, 2, 2), columns = 2))
        assertEquals(emptyList<Int>(), HomeGridRules.spansFor(emptyList(), columns = 2))
    }

    @Test
    fun spans_neverLeaveAHalfEmptyRow() {
        // 不变式：按顺序累加占列数，每"行"必然被填满（累计到 2 才归零），不存在只占 1 的半行
        val orders = listOf(
            listOf(1), listOf(1, 1), listOf(1, 2), listOf(2, 1), listOf(1, 1, 1),
            listOf(1, 2, 1), listOf(2, 2), listOf(2, 1, 1, 2), listOf(1, 1, 1, 1), listOf(2, 2, 2)
        )
        orders.forEach { order ->
            val spans = HomeGridRules.spansFor(order, columns = 2)
            var acc = 0
            spans.forEach { span ->
                require(span in 1..2) { "span 越界：$span（顺序 $order）" }
                acc += span
                if (acc >= 2) acc = 0
            }
            assertEquals("顺序 $order 出现半行空隙（实际 spans=$spans）", 0, acc)
        }
    }

    @Test
    fun spans_preserveLengthAndDeclaredFullWidth() {
        val order = listOf(2, 1, 2, 1, 1)
        val spans = HomeGridRules.spansFor(order, columns = 2)
        assertEquals("输出长度必须与输入一致", order.size, spans.size)
        assertTrue("整行卡片不应被缩小", spans[0] == 2 && spans[2] == 2)
        // 索引 1（孤立半宽，下一张是整行）→ 提升；索引 3、4 成对 → 保持 1
        assertEquals(listOf(2, 2, 2, 1, 1), spans)
    }
}
