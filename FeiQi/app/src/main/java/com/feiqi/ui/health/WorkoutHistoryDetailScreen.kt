package com.feiqi.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feiqi.R
import com.feiqi.data.entity.WorkoutRecordEntity
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.data.model.estimateDaySeconds
import com.feiqi.data.model.secondsToMinutes
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils

/**
 * 训练记录详情页（v2 阶段 4）。
 *
 * ## 为什么是「查看」而不是「取消打卡」
 * 此前健身主页的历史网格里，点一下已完成 Day 会**直接静默取消打卡**（无确认、无出口），
 * 误触即丢数据。v2 阶段 4 反转为「点开看详情」，取消能力暂时移除：
 * 记录一旦生成即**不可撤销**，但当天仍可继续完成其它训练日的打卡。
 * 将来若要恢复撤销，入口放在本页的「更多」菜单，复用 `WorkoutViewModel.undo()`。
 *
 * ## 数据来源（**无需改库**）
 * 打卡记录只存 `planId + dayNumber + date + completedAt`，
 * 训练内容靠 [WorkoutViewModel.runtimePlan] 按 `planId` 装配。
 * 若计划内容取不到（[plan] 为 null），渲染兜底文案而非白屏。
 */
@Composable
internal fun WorkoutHistoryDetailScreen(
    record: WorkoutRecordEntity,
    plan: WorkoutPlan?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val date = remember(record.date) { runCatching { DateUtils.parseIso(record.date) }.getOrNull() }
    val completedAt = remember(record.completedAt) {
        java.time.Instant.ofEpochMilli(record.completedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
    }
    val minutes = remember(plan) { secondsToMinutes(estimateDaySeconds(plan)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FeiQiSpacing.lg),
        ) {
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
                Text(
                    text = stringResource(R.string.workout_history_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))

            // 顶部概览：日期 + 部位
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeiQiRadius.lg),
                colors = CardDefaults.cardColors(containerColor = CardAmber),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(FeiQiSpacing.lg)) {
                    Text(
                        text = date?.let { DateUtils.monthDay(it) } ?: record.date,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
                    Text(
                        text = plan?.bodyParts ?: stringResource(R.string.workout_history_missing_plan),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(FeiQiSpacing.md))
                    Text(
                        text = stringResource(
                            R.string.workout_history_completed_at,
                            DateUtils.fullDateTime(completedAt),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))

            if (plan == null) {
                // 计划内容不可用：仍展示打卡事实，不给白屏
                WorkoutEmptyState(
                    emoji = "📋",
                    title = stringResource(R.string.workout_history_missing_plan),
                    description = stringResource(R.string.workout_history_missing_desc),
                )
            } else {
                // 计划名
                InfoRow(
                    label = stringResource(R.string.workout_history_plan),
                    value = plan.title,
                )
                Spacer(modifier = Modifier.height(FeiQiSpacing.md))
                InfoRow(
                    label = stringResource(R.string.workout_history_duration),
                    value = com.feiqi.data.model.estimatedDurationLabel(minutes * 60),
                )

                Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
                Text(
                    text = stringResource(R.string.workout_history_moves),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
                plan.exercises.forEachIndexed { index, exercise ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FeiQiSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                            )
                        }
                        Spacer(modifier = Modifier.width(FeiQiSpacing.md))
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(FeiQiSpacing.sm))
                        Text(
                            text = "${exercise.sets} 组 × " + if (exercise.durationSeconds > 0) {
                                "${exercise.durationSeconds} 秒"
                            } else {
                                exercise.reps
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
            // 明确告知不可撤销，避免用户以为误触能恢复
            Text(
                text = stringResource(R.string.workout_history_undo_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
        }
    }
}

/** 「标签 : 值」一行。 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Spacer(modifier = Modifier.width(FeiQiSpacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
