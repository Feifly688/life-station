package com.feiqi.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页区块顺序的两条纯逻辑：**规整**（normalize）与**移动**（move）。
 *
 * 首页区块顺序持久化在 DataStore（逗号分隔的 id），所以这两条规则必须能扛住
 * 「存储串损坏 / 新增了区块种类 / 拖到越界」等边界，否则会出现丢卡片或崩溃。
 */
class HomeCardTest {

    @Test
    fun defaultOrderContainsEveryCard() {
        // 默认顺序必须覆盖枚举里全部区块且不重复（新增区块时这条会提醒你补默认位）
        assertEquals(HomeCard.entries.size, HomeCard.DEFAULT_ORDER.size)
        assertEquals(HomeCard.DEFAULT_ORDER.size, HomeCard.DEFAULT_ORDER.toSet().size)
    }

    @Test
    fun normalize_emptyInput_fallsBackToDefault() {
        assertEquals(HomeCard.DEFAULT_ORDER, HomeCard.normalize(emptyList()))
        assertEquals(HomeCard.DEFAULT_ORDER, HomeCard.normalize(listOf("", "  ")))
    }

    @Test
    fun normalize_dropsUnknownIds() {
        val result = HomeCard.normalize(listOf("not_a_card", HomeCard.QUOTE.id, "another_junk"))
        // 未知 id 丢弃，已知 id 保留并排在最前
        assertEquals(HomeCard.QUOTE, result.first())
        assertEquals(HomeCard.entries.size, result.size)
        assertTrue(result.containsAll(HomeCard.DEFAULT_ORDER))
    }

    @Test
    fun normalize_appendsMissingCardsToTail() {
        // 只存了 3 个 id（例如旧版本存的，之后新增了区块）→ 缺失项按默认顺序补到末尾
        val stored = listOf(HomeCard.QUOTE.id, HomeCard.STATS.id, HomeCard.MEDIA.id)
        val result = HomeCard.normalize(stored)
        assertEquals(
            listOf(HomeCard.QUOTE, HomeCard.STATS, HomeCard.MEDIA),
            result.take(3)
        )
        assertEquals(HomeCard.entries.size, result.size)
        assertEquals(HomeCard.entries.size, result.toSet().size)
    }

    @Test
    fun normalize_deduplicatesRepeatedIds() {
        val stored = listOf(HomeCard.QUOTE.id, HomeCard.QUOTE.id, HomeCard.STATS.id)
        val result = HomeCard.normalize(stored)
        assertEquals(HomeCard.QUOTE, result.first())
        assertEquals(HomeCard.STATS, result[1])
        assertEquals(HomeCard.entries.size, result.toSet().size)
    }

    @Test
    fun move_backward() {
        val order = HomeCard.DEFAULT_ORDER
        val moved = HomeCard.move(order, from = 0, to = 2)
        assertEquals(order[1], moved[0])
        assertEquals(order[2], moved[1])
        assertEquals(order[0], moved[2])
        assertEquals(order.size, moved.size)
    }

    @Test
    fun move_forward() {
        val order = HomeCard.DEFAULT_ORDER
        val moved = HomeCard.move(order, from = 3, to = 1)
        assertEquals(order[3], moved[1])
        assertEquals(order[1], moved[2])
        assertEquals(order.size, moved.size)
    }

    @Test
    fun move_samePositionOrOutOfBounds_returnsUnchanged() {
        val order = HomeCard.DEFAULT_ORDER
        assertEquals(order, HomeCard.move(order, 1, 1))
        assertEquals(order, HomeCard.move(order, -1, 2))
        assertEquals(order, HomeCard.move(order, 2, 99))
        assertEquals(order, HomeCard.move(order, 99, 0))
    }

    @Test
    fun move_neverLosesOrDuplicatesCards() {
        // 任意起止位置的移动都必须保持「集合不变」——这是拖拽安全性的核心不变量
        val order = HomeCard.DEFAULT_ORDER
        for (from in order.indices) {
            for (to in order.indices) {
                val moved = HomeCard.move(order, from, to)
                assertEquals(order.size, moved.size)
                assertEquals(order.toSet(), moved.toSet())
                assertEquals(order[from], moved[to])
            }
        }
    }

    @Test
    fun idsAreUniqueAndStable() {
        // id 是持久化契约：改 id 等于让老用户的顺序失效，这里固定下来防止误改
        assertEquals(
            listOf("life_index", "stats", "today_todo", "shopping", "media", "recent_trace", "quote"),
            HomeCard.entries.map { it.id }
        )
    }
}
