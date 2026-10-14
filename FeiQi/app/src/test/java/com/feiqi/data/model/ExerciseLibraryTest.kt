package com.feiqi.data.model

import com.feiqi.data.entity.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 动作库（v2阶段 3）种子完整性 + 展示规则单测。
 *
 * 覆盖：种子自洽性、部位→大类映射、搜索、分类计数、动作详情解析、逐日动作挑选。
 */
class ExerciseLibraryTest {

    private val library = ExerciseLibrarySeed.exercises

    // ---------------- 种子完整性 ----------------

    @Test
    fun `动作名全局唯一`() {
        val names = library.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `全部动作已填大类且大类合法`() {
        val valid = BodyCategory.labels
        library.forEach { exercise ->
            assertTrue(
                "${exercise.name} 大类非法: ${exercise.category}",
                exercise.category in valid,
            )
        }
    }

    @Test
    fun `具体部位必须落在所属大类的detailParts 内`() {
        library.forEach { exercise ->
            val category = BodyCategory.entries.first { it.label == exercise.category }
            assertTrue(
                "${exercise.name} 部位 ${exercise.bodyPart} 不属于 ${exercise.category}",
                exercise.bodyPart in category.detailParts,
            )
        }
    }

    @Test
    fun `目标肌肉 步骤 注意事项 均非空`() {
        library.forEach { exercise ->
            assertTrue("${exercise.name} 缺 targetMuscle", exercise.targetMuscle.isNotBlank())
            assertTrue("${exercise.name} 缺 steps", exercise.steps.isNotBlank())
            assertTrue("${exercise.name} 缺 tips", exercise.tips.isNotBlank())
            assertTrue("${exercise.name} 缺 description", exercise.description.isNotBlank())
        }
    }

    @Test
    fun `难度与器械取值合法`() {
        val difficulties = ExerciseDifficulty.entries.map { it.code }.toSet()
        val equipments = ExerciseEquipment.entries.map { it.code }.toSet()
        library.forEach { exercise ->
            assertTrue(exercise.difficulty in difficulties)
            assertTrue(exercise.equipment in equipments)
        }
    }

    @Test
    fun `七个训练大类均有动作`() {
        BodyCategory.entries.forEach { category ->
            val count = library.count { it.category == category.label }
            assertTrue("${category.label} 没有动作", count > 0)
        }
    }

    @Test
    fun `需求清单里的动作全部收录`() {
        val required = listOf(
            // 胸
            "俯卧撑", "宽距俯卧撑", "钻石俯卧撑", "跪姿俯卧撑",
            // 背
            "超人式", "Y字伸展", "毛巾划船",
            // 肩
            "Pike 俯卧撑", "侧平举",
            // 腿
            "深蹲", "箭步蹲", "保加利亚分腿蹲",
            // 臀
            "臀桥",
            // 腹
            "卷腹", "俄罗斯转体", "平板支撑", "登山跑",
            // 手臂
            "二头弯举", "臂屈伸",
        )
        required.forEach { name ->
            assertNotNull("缺少动作: $name", ExerciseLibrarySeed.byName(name))
        }
    }

    @Test
    fun `臀桥归臀部 深蹲归腿部`() {
        assertEquals(BodyCategory.HIP.label, ExerciseLibrarySeed.byName("臀桥")?.category)
        assertEquals(BodyCategory.LEG.label, ExerciseLibrarySeed.byName("深蹲")?.category)
    }

    @Test
    fun `钻石俯卧撑归胸部但目标肌肉是三头`() {
        val exercise = ExerciseLibrarySeed.byName("钻石俯卧撑")
        assertEquals(BodyCategory.CHEST.label, exercise?.category)
        assertEquals("三头", exercise?.bodyPart)
    }

    @Test
    fun `预留字段默认为空串`() {
        library.forEach { exercise ->
            assertEquals("", exercise.image)
            assertEquals("", exercise.videoUrl)
        }
    }

    // ---------------- 部位 → 大类反查 ----------------

    @Test
    fun `部位反查大类可确定`() {
        assertEquals(BodyCategory.CHEST, ExerciseLibrarySeed.categoryOfBodyPart("胸"))
        assertEquals(BodyCategory.HIP, ExerciseLibrarySeed.categoryOfBodyPart("臀"))
        assertEquals(BodyCategory.LEG, ExerciseLibrarySeed.categoryOfBodyPart("腿"))
        assertEquals(BodyCategory.CORE, ExerciseLibrarySeed.categoryOfBodyPart("腹"))
    }

    @Test
    fun `未知部位反查返回 null`() {
        assertEquals(null, ExerciseLibrarySeed.categoryOfBodyPart("全身"))
    }

    @Test
    fun `三头同时出现在胸部与手臂的部位集合`() {
        assertTrue(BodyCategory.CHEST.detailParts.contains("三头"))
        assertTrue(BodyCategory.ARM.detailParts.contains("三头"))
    }

    // ---------------- 器械 ----------------

    @Test
    fun `器械枚举为确认的四项`() {
        assertEquals(
            listOf("NONE", "BAND", "DUMBBELL", "MAT"),
            ExerciseEquipment.entries.map { it.code },
        )
        assertEquals("无器械", ExerciseEquipment.NONE.label)
        assertEquals("瑜伽垫", ExerciseEquipment.MAT.label)
    }

    @Test
    fun `未知器械码回落为无器械`() {
        assertEquals(ExerciseEquipment.NONE, ExerciseEquipment.fromCode("KETTLEBELL"))
        assertEquals(ExerciseEquipment.NONE, ExerciseEquipment.fromCode(""))
    }

    // ---------------- 搜索 ----------------

    private val all = library

    @Test
    fun `空关键词返回全部`() {
        val result = filterExercises(all, keyword = "", category = null)
        assertEquals(all.size, result.exercises.size)
    }

    @Test
    fun `按名称模糊匹配`() {
        val result = filterExercises(all, keyword = "俯卧撑", category = null)
        assertTrue(result.exercises.isNotEmpty())
        assertTrue(result.exercises.all { it.name.contains("俯卧撑") })
    }

    @Test
    fun `关键词命中目标肌肉`() {
        val result = filterExercises(all, keyword = "腹直肌", category = null)
        assertEquals(listOf("卷腹"), result.exercises.map { it.name })
    }

    @Test
    fun `无结果时分类卡为空`() {
        val result = filterExercises(all, keyword = "不存在的动作xyz", category = null)
        assertTrue(result.isEmpty)
        assertTrue(result.categories.isEmpty())
    }

    @Test
    fun `选中分类后只显示该分类`() {
        val result = filterExercises(all, keyword = "", category = BodyCategory.HIP)
        assertEquals(listOf("臀桥"), result.exercises.map { it.name })
    }

    @Test
    fun `分类卡计数与列表一致`() {
        val result = filterExercises(all, keyword = "俯卧撑", category = null)
        val sum = result.categories.sumOf { it.count }
        assertEquals(result.exercises.size, sum)
    }

    @Test
    fun `分类卡按大类声明序且不含空类`() {
        val result = filterExercises(all, keyword = "", category = null)
        val order = BodyCategory.entries.map { it.label }
        val actual = result.categories.map { it.category.label }
        assertEquals(order.filter { it in actual }, actual)
        assertTrue(result.categories.all { it.count > 0 })
    }

    @Test
    fun `大写关键词也能匹配`() {
        val result = filterExercises(all, keyword = "PIKE", category = null)
        assertEquals(listOf("Pike 俯卧撑"), result.exercises.map { it.name })
    }

    // ---------------- 详情解析 ----------------

    @Test
    fun `步骤按行拆分且去空`() {
        val exercise = ExerciseLibrarySeed.byName("俯卧撑")!!
        val steps = exerciseStepList(exercise)
        assertEquals(3, steps.size)
        assertTrue(steps.all { it.isNotBlank() })
    }

    @Test
    fun `难度与器械文案可解析`() {
        val exercise = ExerciseLibrarySeed.byName("深蹲")!!
        assertEquals("初级", exerciseDifficultyLabel(exercise))
        assertEquals("无器械", exerciseEquipmentLabel(exercise))
    }

    @Test
    fun `目标文案为大类加目标肌肉`() {
        val exercise = ExerciseLibrarySeed.byName("俯卧撑")!!
        assertEquals("胸部 · 胸大肌", exerciseTargetLabel(exercise))
    }

    @Test
    fun `分类缺失时目标文案回落`() {
        val legacy = ExerciseEntity(
            id = 1L, name = "某动作", bodyPart = "胸",
            difficulty = "BEGINNER", description = "d",
        )
        assertEquals("胸", exerciseTargetLabel(legacy))
    }

    // ---------------- 按分类取动作 ----------------

    @Test
    fun `按分类取动作只返回该类`() {
        val hip = exercisesOfCategory(all, BodyCategory.HIP)
        assertEquals(listOf("臀桥"), hip.map { it.name })
    }

    @Test
    fun `按分类取动作兼容仅有部位的老数据`() {
        // category 为空但bodyPart 属于胸部 → 应被补进来
        val legacy = ExerciseEntity(
            id = 99L, name = "老数据俯卧撑", bodyPart = "胸",
            difficulty = "BEGINNER", description = "", category = "",
        )
        val chest = exercisesOfCategory(all + legacy, BodyCategory.CHEST)
        assertTrue(chest.any { it.name == "老数据俯卧撑" })
    }

    // ---------------- 逐日动作挑选（创建计划联动） ----------------

    private val chestDay = PlannedDay(1, BodyCategory.CHEST)
    private val restDay = PlannedDay(2, null)

    @Test
    fun `手动指定的动作优先于自动挑选`() {
        val picked = resolveDayExercises(chestDay, all, manual = listOf("宽距俯卧撑", "钻石俯卧撑"))
        assertEquals(listOf("宽距俯卧撑", "钻石俯卧撑"), picked.map { it.name })
    }

    @Test
    fun `手动指定的顺序被保留`() {
        val picked = resolveDayExercises(chestDay, all, manual = listOf("钻石俯卧撑", "跪姿俯卧撑"))
        assertEquals(listOf("钻石俯卧撑", "跪姿俯卧撑"), picked.map { it.name })
    }

    @Test
    fun `手动指定不存在的动作被丢弃不产生悬空引用`() {
        val picked = resolveDayExercises(chestDay, all, manual = listOf("俯卧撑", "不存在动作"))
        assertEquals(listOf("俯卧撑"), picked.map { it.name })
    }

    @Test
    fun `手动指定受上限约束`() {
        val manual = listOf("俯卧撑", "宽距俯卧撑", "钻石俯卧撑", "跪姿俯卧撑")
        val picked = resolveDayExercises(chestDay, all, manual = manual, maxCount = 2)
        assertEquals(2, picked.size)
    }

    @Test
    fun `未指定时按大类自动挑选且初级优先`() {
        val picked = resolveDayExercises(chestDay, all)
        assertTrue(picked.isNotEmpty())
        // 胸部 4 个动作里初级（俯卧撑、跪姿俯卧撑）应排在前面
        assertEquals("初级", ExerciseDifficulty.fromCode(picked.first().difficulty).label)
    }

    @Test
    fun `自动挑选不超过上限`() {
        val picked = resolveDayExercises(chestDay, all)
        assertTrue(picked.size <= MAX_DAILY_EXERCISES)
    }

    @Test
    fun `休息日没有动作`() {
        assertTrue(resolveDayExercises(restDay, all).isEmpty())
    }

    @Test
    fun `自动挑选不会跨大类`() {
        val picked = resolveDayExercises(PlannedDay(1, BodyCategory.HIP), all)
        assertTrue(picked.all { it.category == BodyCategory.HIP.label })
    }

    // ---------------- 草稿手动动作（阶段 3 扩展） ----------------

    @Test
    fun `草稿默认无手动动作 保持阶段2 行为`() {
        val draft = PlanDraft(
            name = "p", goal = WorkoutGoal.SHAPE, cycleDays = 7, frequencyPerWeek = 3,
            categories = listOf(BodyCategory.CHEST),
            days = assignCategories(layoutTrainingDays(7, 3), listOf(BodyCategory.CHEST)),
        )
        assertTrue(draft.dayExercises.isEmpty())
        assertEquals(0, draft.manualDayCount)
        assertFalse(draft.hasManualExercises(1))
    }

    @Test
    fun `草稿可记录手动动作`() {
        val draft = PlanDraft(
            name = "p", goal = WorkoutGoal.SHAPE, cycleDays = 7, frequencyPerWeek = 3,
            categories = listOf(BodyCategory.CHEST),
            days = assignCategories(layoutTrainingDays(7, 3), listOf(BodyCategory.CHEST)),
            dayExercises = mapOf(1 to listOf("俯卧撑")),
        )
        assertTrue(draft.hasManualExercises(1))
        assertEquals(1, draft.manualDayCount)
    }

    // ---------------- 官方种子复用动作库 ----------------

    @Test
    fun `官方计划的胸部动作已带上大类字段`() {
        val pushUp = WorkoutPlanSeed.exercises.first { it.name == "俯卧撑" }
        assertEquals(BodyCategory.CHEST.label, pushUp.category)
        assertTrue(pushUp.steps.isNotBlank())
    }

    @Test
    fun `官方计划里未收录的动作大类留空`() {
        val stretch = WorkoutPlanSeed.exercises.first { it.name == "全身拉伸" }
        assertEquals("", stretch.category)
        assertEquals("全身", stretch.bodyPart)
    }
}