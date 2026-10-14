package com.feiqi.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户自建计划排布算法单测（[WorkoutPlanBuilder]）。
 *
 * 覆盖：频率 × 周期组合、训练日数量、部位轮转、保存校验、部位映射。
 */
class WorkoutPlanBuilderTest {

    // ---------------- layoutTrainingDays ----------------

    @Test
    fun `每周3天 30天周期 训练日数量正确`() {
        val layout = layoutTrainingDays(cycleDays = 30, frequencyPerWeek = 3)
        // 30 天 = 4 整周(28 天 × 3) + 余 2 天(周一/周三命中其一) = 13
        assertEquals(30, layout.size)
        assertEquals(13, layout.count { !it.isRest })
    }

    @Test
    fun `每周3天 训练日落在周一三五`() {
        val layout = layoutTrainingDays(cycleDays = 7, frequencyPerWeek = 3)
        val trainingDays = layout.filter { !it.isRest }.map { ((it.dayNumber - 1) % 7) + 1 }
        assertEquals(listOf(1, 3, 5), trainingDays)
    }

    @Test
    fun `每周4天 7天周期 训练日为4天`() {
        val layout = layoutTrainingDays(cycleDays = 7, frequencyPerWeek = 4)
        assertEquals(4, layout.count { !it.isRest })
    }

    @Test
    fun `每周5天 7天周期 训练日为5天`() {
        val layout = layoutTrainingDays(cycleDays = 7, frequencyPerWeek = 5)
        assertEquals(5, layout.count { !it.isRest })
    }

    @Test
    fun `每周6天 14天周期 训练日为12天`() {
        val layout = layoutTrainingDays(cycleDays = 14, frequencyPerWeek = 6)
        assertEquals(12, layout.count { !it.isRest })
    }

    @Test
    fun `周期短于每周频率时按周期封顶`() {
        // 3 天周期 + 每周 6 天 → 最多 3 个训练日，不越界
        val layout = layoutTrainingDays(cycleDays = 3, frequencyPerWeek = 6)
        assertEquals(3, layout.size)
        assertEquals(3, layout.count { !it.isRest })
    }

    @Test
    fun `非法入参被收敛 至少1个训练日`() {
        val layout = layoutTrainingDays(cycleDays = 0, frequencyPerWeek = 0)
        assertEquals(1, layout.size)
        assertEquals(1, layout.count { !it.isRest })
    }

    @Test
    fun `每天至多一个训练日标记不重复`() {
        val layout = layoutTrainingDays(cycleDays = 30, frequencyPerWeek = 6)
        assertEquals(layout.size, layout.map { it.dayNumber }.distinct().size)
        assertTrue(layout.all { it.dayNumber in 1..30 })
    }

    // ---------------- assignCategories ----------------

    @Test
    fun `部位轮转分配到训练日 休息日保持空`() {
        val layout = layoutTrainingDays(cycleDays = 7, frequencyPerWeek = 3)
        val assigned = assignCategories(layout, listOf(BodyCategory.CHEST, BodyCategory.LEG))
        val training = assigned.filter { !it.isRest }
        // 3 个训练日，2 个部位轮转 → 胸、腿、胸
        assertEquals(listOf(BodyCategory.CHEST, BodyCategory.LEG, BodyCategory.CHEST), training.map { it.category })
        // 休息日 category 必须为 null
        assertTrue(assigned.filter { it.isRest }.all { it.category == null })
    }

    @Test
    fun `部位为空时全部为休息日`() {
        val layout = layoutTrainingDays(cycleDays = 7, frequencyPerWeek = 3)
        val assigned = assignCategories(layout, emptyList())
        assertTrue(assigned.all { it.isRest })
    }

    @Test
    fun `单一部位时所有训练日同部位`() {
        val layout = layoutTrainingDays(cycleDays = 14, frequencyPerWeek = 4)
        val assigned = assignCategories(layout, listOf(BodyCategory.CORE))
        assertTrue(assigned.filter { !it.isRest }.all { it.category == BodyCategory.CORE })
    }

    // ---------------- 部位映射 ----------------

    @Test
    fun `六大类部位映射到动作库细粒度部位`() {
        assertEquals(listOf("胸", "三头"), BodyCategory.CHEST.detailParts)
        assertEquals(listOf("背", "二头"), BodyCategory.BACK.detailParts)
        assertEquals(listOf("肩"), BodyCategory.SHOULDER.detailParts)
        assertEquals(listOf("腿", "臀"), BodyCategory.LEG.detailParts)
        assertEquals(listOf("二头", "三头"), BodyCategory.ARM.detailParts)
        assertEquals(listOf("腹"), BodyCategory.CORE.detailParts)
    }

    @Test
    fun `展示串为中文加号连接`() {
        assertEquals("胸 + 三头", BodyCategory.CHEST.displayParts)
        assertEquals("腿 + 臀", BodyCategory.LEG.displayParts)
    }

    @Test
    fun `动作库部位全部被六大类覆盖`() {
        val libraryParts = setOf("胸", "三头", "肩", "腹", "背", "二头", "腿", "臀")
        val covered = BodyCategory.entries.flatMap { it.detailParts }.toSet()
        assertTrue(libraryParts.all { it in covered })
    }

    // ---------------- 校验 ----------------

    @Test
    fun `名称校验 空白与超长不合法`() {
        assertFalse(isValidPlanName(""))
        assertFalse(isValidPlanName("   "))
        assertFalse(isValidPlanName("一".repeat(MAX_PLAN_NAME_LENGTH + 1)))
        assertTrue(isValidPlanName("我的增肌计划"))
        assertTrue(isValidPlanName("一".repeat(MAX_PLAN_NAME_LENGTH)))
    }

    @Test
    fun `草稿可保存需 名称合法 且 有部位 且 有训练日`() {
        val layout = layoutTrainingDays(30, 3).let {
            assignCategories(it, listOf(BodyCategory.CHEST))
        }
        val good = PlanDraft("我的计划", WorkoutGoal.BULK, 30, 3, listOf(BodyCategory.CHEST), layout)
        assertTrue(isPlanDraftSaveable(good))

        val noName = good.copy(name = " ")
        assertFalse(isPlanDraftSaveable(noName))

        val noCategory = good.copy(categories = emptyList())
        assertFalse(isPlanDraftSaveable(noCategory))

        val noTrainingDay = good.copy(
            days = assignCategories(layoutTrainingDays(30, 3), emptyList())
        )
        assertFalse(isPlanDraftSaveable(noTrainingDay))
    }

    @Test
    fun `草稿摘要与训练日数正确`() {
        val layout = assignCategories(layoutTrainingDays(7, 3), listOf(BodyCategory.LEG))
        val draft = PlanDraft("腿部计划", WorkoutGoal.CUT, 7, 3, listOf(BodyCategory.LEG), layout)
        assertEquals(3, draft.trainingDayCount)
        assertTrue(draft.categorySummary.contains("腿部"))
    }

    @Test
    fun `周期与频率选项为约定值`() {
        assertEquals(listOf(7, 14, 30), WORKOUT_CYCLE_OPTIONS)
        assertEquals(listOf(3, 4, 5, 6), WORKOUT_FREQUENCY_OPTIONS)
    }

    // ---------------- planId 别名（老打卡兼容） ----------------

    @Test
    fun `官方计划读取别名包含旧 id`() {
        val aliases = WorkoutPlanSeed.readAliases(WorkoutPlanSeed.OFFICIAL_PLAN_ID)
        assertTrue(aliases.contains(WorkoutPlanSeed.OFFICIAL_PLAN_ID))
        assertTrue(aliases.contains(WorkoutPlanSeed.LEGACY_PLAN_ID))
    }

    @Test
    fun `用户计划读取别名只有自身`() {
        val aliases = WorkoutPlanSeed.readAliases("user_abc12345")
        assertEquals(listOf("user_abc12345"), aliases)
    }

    // ---------------- 进度派生支持任意周期 ----------------

    @Test
    fun `自定义周期进度按自定义天数计算`() {
        val progress = computeWorkoutProgress(setOf(1, 2), totalDays = 14)
        assertEquals(3, progress.currentDay)
        assertEquals(2, progress.completedCount)
        assertEquals(2 * 100f / 14, progress.percent, 0.001f)
    }

    @Test
    fun `超出周期的完成记录被忽略`() {
        val progress = computeWorkoutProgress(setOf(1, 20), totalDays = 7)
        assertEquals(1, progress.completedCount)
    }

    @Test
    fun `自定义周期连续天数正确`() {
        assertEquals(3, computeStreak(setOf(3, 4, 5), today = 5, totalDays = 14))
        // 断档后重新连上：3 与 5 之间断开，从 5 起算 1 天
        assertEquals(1, computeStreak(setOf(3, 5), today = 6, totalDays = 14))
        // 今天与昨天都没练 → 归零
        assertEquals(0, computeStreak(setOf(3, 5), today = 9, totalDays = 14))
    }
}