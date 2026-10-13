package com.feiqi.data.model

/**
 * 健身模块 v2 的枚举（数据库以**英文码**存储，展示层再映射为中文）。
 *
 * 为什么存英文码：后续要做筛选/统计（如"列出所有初级动作"）时，
 * 字符串枚举在数据库里无法安全比较与聚合。
 */

/** 计划类型：官方计划 / 用户自建。 */
enum class WorkoutPlanType(val code: String, val label: String) {
    OFFICIAL("OFFICIAL", "官方计划"),
    USER_CREATED("USER_CREATED", "我的计划");

    companion object {
        /** 容错解析（未知值回落到 [OFFICIAL]，脏数据不会崩）。 */
        fun fromCode(code: String): WorkoutPlanType =
            entries.firstOrNull { it.code == code } ?: OFFICIAL
    }
}

/** 训练目标。 */
enum class WorkoutGoal(val code: String, val label: String) {
    BULK("BULK", "增肌"),
    CUT("CUT", "减脂"),
    SHAPE("SHAPE", "塑形"),
    MAINTAIN("MAINTAIN", "保持");

    companion object {
        fun fromCode(code: String): WorkoutGoal = entries.firstOrNull { it.code == code } ?: SHAPE
    }
}

/** 动作难度。 */
enum class ExerciseDifficulty(val code: String, val label: String) {
    BEGINNER("BEGINNER", "初级"),
    INTERMEDIATE("INTERMEDIATE", "中级"),
    ADVANCED("ADVANCED", "高级");

    companion object {
        fun fromCode(code: String): ExerciseDifficulty =
            entries.firstOrNull { it.code == code } ?: BEGINNER
    }
}
