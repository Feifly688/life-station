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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.ExerciseEquipment
import com.feiqi.data.model.exerciseDifficultyLabel
import com.feiqi.data.model.exerciseStepList
import com.feiqi.data.model.exerciseTargetLabel
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.CardRed
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.Secondary

/**
 * 动作详情页（整页覆盖）。
 *
 * 结构：返回 + 大图占位 → 动作名 + 大类 → 信息卡（目标/难度/器械）→ 介绍 → 步骤 → 注意事项。
 */
@Composable
internal fun ExerciseDetailScreen(
    exerciseId: Long,
    viewModel: ExerciseViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercise by viewModel.exerciseById(exerciseId).collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val current = exercise
        if (current == null) {
            // 动作已被删除 / 尚未读到：只给返回，避免白屏无出口
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.exercise_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Box
        }

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
            }

            HeroImage(exercise = current)
            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))

            Text(
                text = current.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = exerciseTargetLabel(current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
            InfoCard(exercise = current)

            if (current.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
                DetailSection(title = stringResource(R.string.exercise_intro))
                Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
                Text(
                    text = current.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val steps = exerciseStepList(current)
            if (steps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
                DetailSection(title = stringResource(R.string.exercise_steps))
                Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
                Column(verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm)) {
                    steps.forEachIndexed { index, step ->
                        StepRow(index = index + 1, text = step)
                    }
                }
            }

            if (current.tips.isNotBlank()) {
                Spacer(modifier = Modifier.height(FeiQiSpacing.lg))
                DetailSection(title = stringResource(R.string.exercise_tips))
                Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
                TipsCard(text = current.tips)
            }

            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
        }
    }
}

/** 大图占位：暖调渐变 + 部位 emoji + 器械角标（`image` 就绪后替换）。 */
@Composable
private fun HeroImage(exercise: ExerciseEntity) {
    val category = BodyCategory.entries.firstOrNull { it.label == exercise.category }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(FeiQiRadius.lg))
            .background(
                Brush.linearGradient(listOf(CardAmber, Secondary.copy(alpha = 0.28f)))
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = category?.emoji ?: "🏋", style = MaterialTheme.typography.displaySmall)
    }
}

/** 动作信息卡：目标 / 难度 / 器械 三行。 */
@Composable
private fun InfoCard(exercise: ExerciseEntity) {
    val equipment = ExerciseEquipment.fromCode(exercise.equipment)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeiQiRadius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(FeiQiSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
    ) {
        InfoRow(
            label = stringResource(R.string.exercise_target),
            value = exerciseTargetLabel(exercise),
        )
        InfoRow(
            label = stringResource(R.string.exercise_difficulty),
            value = exerciseDifficultyLabel(exercise),
        )
        InfoRow(
            label = stringResource(R.string.exercise_equipment),
            value = "${equipment.emoji} ${equipment.label}",
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Spacer(modifier = Modifier.width(FeiQiSpacing.sm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 步骤行：主色圆形序号 + 文本。 */
@Composable
private fun StepRow(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
            )
        }
        Spacer(modifier = Modifier.width(FeiQiSpacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 注意事项：警示色左边框卡片（不复用红色系表示错误，仅作提示底色）。 */
@Composable
private fun TipsCard(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeiQiRadius.sm))
            .background(CardRed.copy(alpha = 0.55f))
            .padding(FeiQiSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Primary.copy(alpha = 0.45f)),
        )
        Spacer(modifier = Modifier.width(FeiQiSpacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}