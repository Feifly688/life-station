package com.feiqi.ui.health

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.feiqi.R
import com.feiqi.data.model.MonthProgress
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.CardGreen
import com.feiqi.ui.theme.FeiQiMotion
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OutlineVariant
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.Secondary
import com.feiqi.ui.theme.WarmAccent

/**
 * 健身模块共享 UI 组件（v2 阶段 4）。
 *
 * 统一在此实现**按压缩放**、**空状态**、**统计面板**、**今日训练卡**，
 * 避免同一个交互在五个页面各写一遍（此前 `EmptyPlaceholder` 就有两份实现）。
 *
 * ## 动效纪律
 * - 时长一律 [FeiQiMotion.normal]（200ms）+ [FastOutSlowInEasing]；
 * - 状态变化**只改样式**（alpha / scale / color），不改组合树结构 ——
 *   用 `if` 增删节点会造成布局跳变（项目既有铁律）。
 */

/** 按下时的缩放比例。 */
private const val PRESSED_SCALE = 0.97f

/**
 * 可点击容器：按下缩放 + 执行点击。
 *
 * `indication = null` 是刻意的——缩放本身就是反馈，再叠加水波纹会显得廉价；
 * 需要水波纹的场景（如表单按钮）直接用 Material3 的 `Button`。
 *
 * ⚠️ 只能用于**非 Material3 组件**（Card / Box / Text）。
 * `Button` / `IconButton` 自带点击处理，叠加本扩展会注册两个点击源导致事件重复。
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = PRESSED_SCALE,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = FeiQiMotion.fast, easing = FastOutSlowInEasing),
        label = "pressable",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
}

/** 统一空状态：emoji（圆形浅底）+ 标题 + 副文案 + 可选行动按钮。 */
@Composable
internal fun WorkoutEmptyState(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .padding(vertical = FeiQiSpacing.xl, horizontal = FeiQiSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CardAmber),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(FeiQiSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
            androidx.compose.material3.Button(
                onClick = onAction,
                shape = RoundedCornerShape(FeiQiRadius.sm),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Color.White,
                ),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

/**
 * 健身中心 Hero 面板（健康页顶部）。
 *
 * 展示**跨全部计划**的三项自律数据：真实日历连续 / 本月完成 / 累计估算时长。
 * 三者口径不同（见 `WorkoutStats.kt`），刻意并列呈现而不是合成一个数字。
 */
@Composable
internal fun WorkoutHeroPanel(
    streakDays: Int,
    month: MonthProgress,
    totalLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick),
        shape = RoundedCornerShape(FeiQiRadius.lg),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(CardAmber, Secondary.copy(alpha = 0.20f))
                    )
                )
                .padding(FeiQiSpacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.workout_hero_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.workout_hero_enter),
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary,
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "🔥 $streakDays",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarmAccent,
                )
                Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
                Text(
                    text = stringResource(R.string.workout_hero_streak_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCell(
                    label = stringResource(R.string.workout_hero_month),
                    value = "${month.doneDays} / ${month.totalDays}",
                    modifier = Modifier.weight(1f),
                )
                StatCell(
                    label = stringResource(R.string.workout_hero_total),
                    value = totalLabel,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            // 本月进度条：月度口径独立于计划进度（分母是自然月天数）
            ThinProgressBar(
                fraction = month.fraction,
                color = WarmAccent,
                trackColor = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

/** Hero 里的单个统计格。 */
@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * 细进度条（高度 6dp，圆角端点）。
 *
 * 动画走 [FeiQiMotion.normal]，值变化时平滑推进而非瞬移。
 */
@Composable
internal fun ThinProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Primary,
    trackColor: Color = OutlineVariant,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = FeiQiMotion.normal, easing = FastOutSlowInEasing),
        label = "thinProgress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round,
    )
}

/**
 * 今日训练大卡（健康页主体）。
 *
 * 视觉层级：标题行（今日训练 + 难度徽章）→ 部位大字 → 指标行（时长/动作数）
 * → 动作预览（最多 [previewLimit] 条，超出折叠）→ 主按钮。
 */
@Composable
internal fun TodayWorkoutCard(
    plan: WorkoutPlan,
    estimatedMinutes: Int,
    difficultyLabel: String?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    previewLimit: Int = 4,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeiQiRadius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(FeiQiSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.workout_today),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (!difficultyLabel.isNullOrBlank()) {
                    Text(
                        text = difficultyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(CardGreen)
                            .padding(horizontal = FeiQiSpacing.sm, vertical = 3.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Text(
                text = "🔥 ${plan.bodyParts}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FeiQiRadius.sm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = FeiQiSpacing.md, vertical = FeiQiSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.lg),
            ) {
                Text(
                    text = "⏱ ${stringResource(R.string.workout_about_minutes, estimatedMinutes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "📋 ${stringResource(R.string.workout_exercise_unit, plan.exercises.size)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            // 动作预览：最多 previewLimit 条，避免长卡片把主按钮推到屏外
            plan.exercises.take(previewLimit).forEachIndexed { index, exercise ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
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
                        maxLines = 1,
                    )
                }
            }
            val rest = plan.exercises.size - previewLimit
            if (rest > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.workout_more_exercises, rest),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(start = FeiQiSpacing.md),
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            androidx.compose.material3.Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(FeiQiRadius.sm),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Color.White,
                ),
            ) {
                Text(text = stringResource(R.string.workout_start))
            }
        }
    }
}

/** 小节标题 + 右侧可选「更多 ›」入口。 */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(FeiQiRadius.sm))
                    .pressable(onClick = onAction)
                    .padding(horizontal = FeiQiSpacing.sm, vertical = FeiQiSpacing.xs),
            )
        }
    }
}
