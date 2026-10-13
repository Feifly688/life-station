package com.feiqi.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 健身 v2 种子数据：把现有 30 天静态计划映射成「计划 / 训练日 / 动作库 / 关联」的完整性。
 *
 * 这些都是纯数据校验（不碰数据库），能挡住"迁移后丢动作 / 丢天 / 关联错位"这类问题。
 */
class WorkoutSeedTest {

    @Test
    fun plan_isOfficialAnd30Days() {
        val plan = WorkoutPlanSeed.plan
        assertEquals("home_beginner_30", plan.planId)
        assertEquals("30天居家爆炸训练", plan.name)
        assertEquals(WorkoutPlanType.OFFICIAL.code, plan.type)
        assertEquals(WorkoutGoal.SHAPE.code, plan.goal)
        assertEquals(30, plan.cycleDays)
        assertEquals("旧打卡记录的 planId 与 v2 计划 id 有意不同，需在后续阶段映射", true,
            WorkoutPlanSeed.LEGACY_PLAN_ID != WorkoutPlanSeed.OFFICIAL_PLAN_ID)
    }

    @Test
    fun days_coverDay1To30() {
        val days = WorkoutPlanSeed.days
        assertEquals(30, days.size)
        assertEquals((1..30).toList(), days.map { it.dayNumber })
        assertTrue(days.all { it.planId == WorkoutPlanSeed.OFFICIAL_PLAN_ID })
        // Day1 必须仍是「胸 + 三头」——现有体验不能变
        assertEquals("胸 + 三头", days.first { it.dayNumber == 1 }.title)
        assertEquals("恢复训练", days.first { it.dayNumber == 7 }.bodyParts)
    }

    @Test
    fun days_matchLegacyStaticPlan() {
        // 天数、部位、描述必须与旧静态计划逐条一致（内容零丢失）
        WORKOUT_PLANS.forEach { source ->
            val seeded = WorkoutPlanSeed.days.first { it.dayNumber == source.dayNumber }
            assertEquals(source.bodyParts, seeded.bodyParts)
            assertEquals(source.description, seeded.description)
        }
    }

    @Test
    fun exercises_areDeduplicatedByName() {
        val exercises = WorkoutPlanSeed.exercises
        val legacyNames = WORKOUT_PLANS.flatMap { it.exercises }.map { it.name }.distinct()
        assertEquals("动作库应按名去重", legacyNames.size, exercises.size)
        assertEquals(legacyNames, exercises.map { it.name })
        assertTrue("动作名唯一", exercises.map { it.name }.distinct().size == exercises.size)
        assertTrue("每个动作都要有部位", exercises.all { it.bodyPart.isNotBlank() })
        assertTrue("图片字段为预留（空串）", exercises.all { it.image.isEmpty() })
    }

    @Test
    fun links_matchLegacyExerciseCount() {
        val links = WorkoutPlanSeed.links
        val legacyTotal = WORKOUT_PLANS.sumOf { it.exercises.size }
        assertEquals("关联行数 = 全部天数动作总数", legacyTotal, links.size)
        // 无重复（同一动作同一天只出现一次）
        assertEquals(links.size, links.map { it.dayId to it.exerciseId }.distinct().size)
    }

    @Test
    fun links_referenceExistingDayAndExercise() {
        val dayIds = WorkoutPlanSeed.days.map { it.id }.toSet()
        val exerciseIds = WorkoutPlanSeed.exercises.map { it.id }.toSet()
        WorkoutPlanSeed.links.forEach { link ->
            assertTrue("dayId 不存在: ${link.dayId}", link.dayId in dayIds)
            assertTrue("exerciseId 不存在: ${link.exerciseId}", link.exerciseId in exerciseIds)
            assertTrue("组数必须为正", link.sets > 0)
            assertTrue("休息时间必须为正", link.restTime > 0)
        }
    }

    @Test
    fun links_keepSetsRepsAndNoteFromLegacyPlan() {
        val nameToId = WorkoutPlanSeed.exercises.associate { it.name to it.id }
        // Day1 俯卧撑：4 组 / 力竭 / 备注"做不了标准动作可以改跪姿"
        val day1 = WorkoutPlanSeed.days.first { it.dayNumber == 1 }
        val pushUp = WorkoutPlanSeed.links.first {
            it.dayId == day1.id && it.exerciseId == nameToId["俯卧撑"]
        }
        assertEquals(4, pushUp.sets)
        assertEquals("力竭", pushUp.reps)
        assertEquals("做不了标准动作可以改跪姿", pushUp.note)
    }

    @Test
    fun sameExerciseCanAppearInMultipleDaysWithDifferentParams() {
        // 窄距俯卧撑在 30 天里出现在多个训练日（7 天循环 ×4 轮多天），
        // 且各天配置不同：Day5 是 3组×12次、Day6 是 3组×力竭
        // —— 这正是 v2 把「动作本体」与「当天怎么练」拆成两张表的意义。
        val nameToId = WorkoutPlanSeed.exercises.associate { it.name to it.id }
        val id = nameToId["窄距俯卧撑"] ?: return
        val rows = WorkoutPlanSeed.links.filter { it.exerciseId == id }

        assertTrue("同一动作应出现在多个训练日", rows.size >= 2)
        assertEquals("同一天不应重复出现", rows.size, rows.map { it.dayId }.distinct().size)
        assertTrue(
            "不同训练日的配置应各自独立（应含 12次 与 力竭）",
            rows.any { it.reps == "12次" } && rows.any { it.reps == "力竭" },
        )
    }
}
