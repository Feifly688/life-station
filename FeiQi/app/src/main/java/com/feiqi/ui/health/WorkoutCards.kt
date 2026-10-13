package com.feiqi.ui.health

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.feiqi.data.model.Exercise
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.CardGreen
import com.feiqi.ui.theme.FeiQiMotion
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.Outline
import com.feiqi.ui.theme.Primary

/**
 * 健身模块的可复用卡片与动效。
 *
 * 与现有模块一致：卡片圆角 14dp（[FeiQiRadius.md]）、柔和阴影、Material3 配色，
 * 动效统一 200ms / [FastOutSlowInEasing]。
 */

/** 计划总览卡：Day x / 30 + 完成进度条 + 完成次数。 */
@Composable
internal fun WorkoutProgressCard(
    currentDay: Int,
    completedCount: Int,
    totalDays: Int,
    percent: Float,
    streak: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeiQiRadius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(FeiQiSpacing.lg)) {
            Text(
                text = "30天居家爆炸训练",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Day $currentDay / $totalDays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "已完成 $completedCount 次",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            // 进度条：数值变化时平滑推进（200ms）
            val animated by animateFloatAsState(
                targetValue = (percent / 100f).coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = FeiQiMotion.normal, easing = FastOutSlowInEasing),
                label = "workoutProgress",
            )
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Primary,
                trackColor = Outline.copy(alpha = 0.25f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = "完成进度 ${percent.toInt()}%${if (streak > 0) "　·　连续 $streak 天" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 动作卡：动作名 + 组数/次数/时长 + 备注 + 已完成态。 */
@Composable
internal fun ExerciseCard(
    exercise: Exercise,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeiQiRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (done) CardGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(FeiQiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("${exercise.sets}组")
                        if (exercise.durationSeconds > 0) {
                            append(" × ${exercise.durationSeconds}秒")
                        } else {
                            append(" × ${exercise.reps}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (exercise.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = exercise.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(FeiQiSpacing.sm))
            DoneBadge(done = done)
        }
    }
}

/** 动作完成徽标：未完成为空心圈，已完成为实心对勾（出现时有缩放动效）。 */
@Composable
private fun DoneBadge(done: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (done) 1f else 0.85f,
        animationSpec = tween(durationMillis = FeiQiMotion.normal, easing = FastOutSlowInEasing),
        label = "doneBadge",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(2.dp, Outline, CircleShape)
            )
        }
    }
}

/** 历史记录里的一天：已完成显示对勾。 */
@Composable
internal fun DayHistoryChip(dayNumber: Int, done: Boolean, onClick: (() -> Unit)? = null) {
    val background = when {
        done -> CardGreen.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(FeiQiRadius.sm))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = FeiQiSpacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Day$dayNumber",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** 部位标签（如「🔥 胸 + 三头」）。 */
@Composable
internal fun BodyPartsTag(bodyParts: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(CardAmber.copy(alpha = 0.22f))
            .padding(horizontal = FeiQiSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🔥 $bodyParts",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}