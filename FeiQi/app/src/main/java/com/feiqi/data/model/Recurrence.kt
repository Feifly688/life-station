package com.feiqi.data.model

/**
 * 提醒重复规则（对应「重复提醒」下拉框的六个选项）。
 *
 * 语义约定（与界面文案一一对应）：
 * - [NONE] 不重复：只在指定日期提醒一次，完成后不再顺延。
 * - [DAILY] 每天：每次完成后顺延 1 天。
 * - [WEEKDAYS] 周一至周五：顺延到下一个工作日，跳过周六、周日。
 * - [WEEKLY] 每周：顺延 7 天（保持同一星期几）。
 * - [MONTHLY] 每月：顺延 1 个月（保持同一日；目标月没有该日时取该月最后一天）。
 * - [YEARLY] 每年：顺延 1 年（保持同一月日；2 月 29 日在平年退到 2 月 28 日）。
 *
 * 注意：只有清单/日程**开启提醒**时重复才有意义，未开启提醒的条目一律 [NONE]。
 */
enum class Recurrence(val code: String) {
    NONE("NONE"),
    DAILY("DAILY"),
    WEEKDAYS("WEEKDAYS"),
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY");

    /** 是否属于「会循环」的规则。 */
    val isRepeating: Boolean get() = this != NONE

    companion object {
        private val byCode: Map<String, Recurrence> = entries.associateBy { it.code }

        /** 容错解析（未知值一律回落到 [NONE]，保证旧数据与脏数据不崩溃）。 */
        fun from(code: String?): Recurrence = byCode[code?.trim()?.uppercase()] ?: NONE
    }
}
