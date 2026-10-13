package com.feiqi.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 30 天健身计划：数据完整性与「进度 / 连续天数」派生逻辑。
 *
 * 这些是纯函数，不依赖 Android，因此在 JVM 上直接跑。
 */
class WorkoutPlanTest {

    @Test
    fun plans_coverExactly30Days() {
        assertEquals(30, WORKOUT_PLANS.size)
        assertEquals((1..30).toList(), WORKOUT_PLANS.map { it.dayNumber })
        assertEquals(PLAN_ID, WORKOUT_PLANS.first().planId)
        assertTrue("每天都要有动作", WORKOUT_PLANS.all { it.exercises.isNotEmpty() })
    }

    @Test
    fun plans_firstWeekMatchesSpec() {
        assertEquals("胸 + 三头", WORKOUT_PLANS[0].bodyParts)
        assertEquals(
            listOf("俯卧撑", "宽距俯卧撑", "慢速俯卧撑", "钻石俯卧撑", "臂屈伸"),
            WORKOUT_PLANS[0].exercises.map { it.name }
        )
        assertEquals("肩 + 腹", WORKOUT_PLANS[1].bodyParts)
        assertEquals("背 + 二头", WORKOUT_PLANS[2].bodyParts)
        assertEquals("腿 + 臀", WORKOUT_PLANS[3].bodyParts)
        assertEquals("恢复训练", WORKOUT_PLANS[6].bodyParts)
    }

    @Test
    fun plans_cycleAfterFirstWeek() {
        // 第 8 天回到第 1 天的循环（同一套部位）
        assertEquals(WORKOUT_PLANS[0].bodyParts, WORKOUT_PLANS[7].bodyParts)
        assertEquals(WORKOUT_PLANS[0].exercises.map { it.name }, WORKOUT_PLANS[7].exercises.map { it.name })
    }

    // ---------------- 进度派生 ----------------

    @Test
    fun progress_empty_startsAtDay1() {
        val progress = computeWorkoutProgress(emptySet())
        assertEquals(1, progress.currentDay)
        assertEquals(0, progress.completedCount)
        assertEquals(0f, progress.percent, 0.01f)
    }

    @Test
    fun progress_movesToFirstUnfinishedDay() {
        val progress = computeWorkoutProgress(setOf(1, 2, 3))
        assertEquals(4, progress.currentDay)
        assertEquals(3, progress.completedCount)
        assertEquals(10f, progress.percent, 0.01f) // 3/30
    }

    @Test
    fun progress_allDone_staysAtLastDay() {
        val progress = computeWorkoutProgress((1..30).toSet())
        assertEquals(30, progress.currentDay)
        assertEquals(30, progress.completedCount)
        assertEquals(100f, progress.percent, 0.01f)
    }

    @Test
    fun progress_ignoresOutOfRangeDays() {
        assertEquals(0, computeWorkoutProgress(setOf(0, 31, 99)).completedCount)
        assertEquals(1, computeWorkoutProgress(setOf(0, 31)).currentDay)
    }

    @Test
    fun progress_deduplicatesSameDay() {
        assertEquals(1, computeWorkoutProgress(setOf(3, 3, 3)).completedCount)
    }

    // ---------------- 连续天数派生 ----------------

    @Test
    fun streak_countsConsecutiveDays() {
        assertEquals(3, computeStreak(setOf(1, 2, 3), today = 3))
    }

    @Test
    fun streak_keepsWhenTodayNotYetDone() {
        // 今天还没练，但昨天及之前连续 → 连续记录保留
        assertEquals(2, computeStreak(setOf(1, 2), today = 3))
    }

    @Test
    fun streak_resetsWhenBroken() {
        assertEquals(0, computeStreak(setOf(1, 2), today = 5))
        assertEquals(0, computeStreak(emptySet(), today = 3))
        assertEquals(1, computeStreak(setOf(5), today = 5))
    }
}