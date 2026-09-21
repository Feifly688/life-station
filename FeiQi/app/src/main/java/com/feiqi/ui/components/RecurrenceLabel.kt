package com.feiqi.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.feiqi.R
import com.feiqi.data.model.Recurrence

/**
 * 重复规则的展示文案，与「重复提醒」下拉框的选项一一对应。
 * 卡片上的循环标记、清单编辑弹窗的摘要都复用同一份文案，避免出现两套说法。
 */
@Composable
fun recurrenceLabel(recurrence: Recurrence): String = stringResource(
    when (recurrence) {
        Recurrence.NONE -> R.string.no_repeat
        Recurrence.DAILY -> R.string.repeat_daily
        Recurrence.WEEKDAYS -> R.string.repeat_weekdays
        Recurrence.WEEKLY -> R.string.repeat_weekly
        Recurrence.MONTHLY -> R.string.repeat_monthly
        Recurrence.YEARLY -> R.string.repeat_yearly
    }
)

/** 下拉框选项顺序（不重复 → 每天 → 周一至周五 → 每周 → 每月 → 每年）。 */
val recurrenceOptions: List<Recurrence> = listOf(
    Recurrence.NONE,
    Recurrence.DAILY,
    Recurrence.WEEKDAYS,
    Recurrence.WEEKLY,
    Recurrence.MONTHLY,
    Recurrence.YEARLY
)
