package com.feiqi.data.model

import com.feiqi.data.entity.ExerciseEntity

/**
 * 动作库的**展示规则**（纯函数，无数据库 / 无 Android 依赖，可单测）。
 *
 * UI 只负责渲染本文件算出的结果，不内联过滤与计数逻辑。
 */

/** 分类卡：一个大类 + 动作数。 */
data class ExerciseCategoryCard(
    val category: BodyCategory,
    val count: Int,
)

/** 动作库搜索结果。 */
data class ExerciseSearchResult(
    /** 非空分类卡（仅显示有动作的分类）。 */
    val categories: List<ExerciseCategoryCard>,
    /** 命中的动作（按大类筛选后再按名称筛选）。 */
    val exercises: List<ExerciseEntity>,
) {
    val isEmpty: Boolean get() = exercises.isEmpty()
}

/** 名称模糊匹配（大小写不敏感；空白关键词返回全部）。 */
fun matchesExerciseKeyword(exercise: ExerciseEntity, keyword: String): Boolean {
    val trimmed = keyword.trim()
    if (trimmed.isEmpty()) return true
    // 动作名精确片段 > 目标肌肉 / 部位任一命中，检索手感更宽松
    return exercise.name.contains(trimmed, ignoreCase = true) ||
        exercise.targetMuscle.contains(trimmed, ignoreCase = true) ||
        exercise.category.contains(trimmed, ignoreCase = true)
}

/**
 * 按大类 + 关键词筛选动作库。
 *
 * @param all 全库动作
 * @param keyword 名称关键词（空 = 不过滤）
 * @param category 选中的大类（null = 全部大类）
 *
 * ## 规则
 * - `category == null` 时展示全部动作，分类卡只显示**有动作**的分类；
 * - `category != null` 时分类卡只显示该分类（即使关键词把动作全过滤掉，卡片计数
 *   也按**关键词过滤后**的条数，保证卡片与列表永远一致）。
 */
fun filterExercises(
    all: List<ExerciseEntity>,
    keyword: String = "",
    category: BodyCategory? = null,
): ExerciseSearchResult {
    val searched = all.filter { matchesExerciseKeyword(it, keyword) }

    val visible = if (category == null) {
        searched
    } else {
        searched.filter { it.category == category.label }
    }

    val cards = if (category == null) {
        // 全部视图：按BodyCategory 声明序展示非空分类
        BodyCategory.entries
            .map { cat -> ExerciseCategoryCard(cat, searched.count { it.category == cat.label }) }
            .filter { it.count > 0 }
    } else {
        val count = searched.count { it.category == category.label }
        if (count > 0) listOf(ExerciseCategoryCard(category, count)) else emptyList()
    }

    return ExerciseSearchResult(categories = cards, exercises = visible)
}

/**
 * 某一大类下的全部动作（动作选择页用）。
 *
 * 先按大类拿（`exercises.category`），再补充按具体部位匹配的
 * （兼容尚未补齐 `category` 的老数据，只填了 `bodyPart`）。
 */
fun exercisesOfCategory(
    all: List<ExerciseEntity>,
    category: BodyCategory,
): List<ExerciseEntity> {
    val byCategory = all.filter { it.category == category.label }
    val covered = byCategory.map { it.name }.toSet()
    val byPart = all.filter { it.name !in covered && it.bodyPart in category.detailParts }
    return byCategory + byPart
}

/**
 * 动作详情页的「目标」展示串：大类 + 具体目标肌肉。
 *
 * ## 回退链（兼容老数据）
 * 老数据可能只有 `bodyPart`（阶段 1 的种子），`category` / `targetMuscle` 为空，
 * 因此依次回落到 `category` → `targetMuscle` → `bodyPart` → 「全身」。
 */
fun exerciseTargetLabel(exercise: ExerciseEntity): String {
    val muscle = exercise.targetMuscle.ifBlank { exercise.bodyPart }
    val category = exercise.category
    return when {
        category.isBlank() && muscle.isBlank() -> "全身"
        muscle.isBlank() -> category
        category.isBlank() || category == muscle -> muscle
        else -> "$category · $muscle"
    }
}

/** 步骤按行拆成有序列表（供详情页渲染编号列表）。 */
fun exerciseStepList(exercise: ExerciseEntity): List<String> =
    exercise.steps.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

/** 难度文案（容错）。 */
fun exerciseDifficultyLabel(exercise: ExerciseEntity): String =
    ExerciseDifficulty.fromCode(exercise.difficulty).label

/** 器械文案（容错，未知值显示「无器械」）。 */
fun exerciseEquipmentLabel(exercise: ExerciseEntity): String =
    ExerciseEquipment.fromCode(exercise.equipment).label