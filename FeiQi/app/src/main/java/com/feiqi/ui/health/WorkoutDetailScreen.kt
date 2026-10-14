package com.feiqi.ui.health

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.ui.theme.CardGreen
import com.feiqi.ui.theme.FeiQiMotion
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils

/**
 * 训练详情页：日期 + 部位 + 动作列表（逐组完成）+ 底部「完成今日训练」打卡。
 *
 * 「完成本组」只更新**本页的组状态**（带动画反馈），
 * 全部动作完成后才允许「完成今日训练」写入打卡记录 —— 与需求一致。
 */
@Composable
internal fun WorkoutDetailScreen(
    plan: WorkoutPlan,
    alreadyCheckedIn: Boolean,
    onCheckIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 逐组完成状态用 rememberSaveable：旋转屏幕 / 进程重建后训练进度不丢。
    // Set<Int> 不是 Saveable 能直接存的类型，这里用逗号串做编码（空集 = 空串）。
    var doneIndexesRaw by rememberSaveable(plan.dayNumber) { mutableStateOf("") }
    val doneExercises: Set<Int> = remember(doneIndexesRaw) {
        if (doneIndexesRaw.isEmpty()) {
            emptySet()
        } else {
            doneIndexesRaw.split(',').mapNotNull { it.toIntOrNull() }.toSet()
        }
    }
    var celebrated by rememberSaveable(plan.dayNumber) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val allDone = doneExercises.size >= plan.exercises.size

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FeiQiSpacing.lg),
        ) {
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
                Column {
                    Text(
                        text = "Day ${plan.dayNumber} · ${plan.bodyParts}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = todayLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (alreadyCheckedIn) {
                Spacer(modifier = Modifier.height(FeiQiSpacing.md))
                CheckedInBanner()
            }

            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            plan.exercises.forEachIndexed { index, exercise ->
                TrainingExerciseCard(
                    exercise = exercise,
                    done = index in doneExercises,
                    modifier = Modifier.padding(bottom = FeiQiSpacing.sm),
                )
                CompleteSetButton(
                    done = index in doneExercises,
                    onClick = {
                        val next =
                            if (index in doneExercises) doneExercises - index
                            else doneExercises + index
                        doneIndexesRaw = next.sorted().joinToString(",")
                    },
                    modifier = Modifier.padding(bottom = FeiQiSpacing.sm),
                )
            }

            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Button(
                onClick = {
                    celebrated = true
                    onCheckIn()
                },
                enabled = allDone && !alreadyCheckedIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("workout_check_in"),
                shape = RoundedCornerShape(FeiQiRadius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
            ) {
                Text(
                    text = when {
                        alreadyCheckedIn -> stringResource(R.string.workout_checked)
                        allDone -> stringResource(R.string.workout_finish)
                        else -> stringResource(R.string.workout_finish_disabled)
                    }
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
        }

        // 打卡成功后的轻量庆祝反馈：底部浮出一张卡片（淡入 + 上移）
        CelebrateBanner(
            visible = celebrated,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(FeiQiSpacing.lg),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 「完成本组」按钮：完成后变为「已完成」态（缩放动效）。 */
@Composable
private fun CompleteSetButton(
    done: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (done) 1f else 0.98f,
        animationSpec = tween(durationMillis = FeiQiMotion.fast, easing = FastOutSlowInEasing),
        label = "setDone",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .testTag("workout_set_done"),
        shape = RoundedCornerShape(FeiQiRadius.sm),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (done) CardGreen else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text = if (done) "✓ " + stringResource(R.string.workout_set_done) else stringResource(R.string.workout_set))
    }
}

/** 已打卡横幅。 */
@Composable
private fun CheckedInBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardGreen, RoundedCornerShape(FeiQiRadius.sm))
            .padding(FeiQiSpacing.md)
    ) {
        Text(
            text = stringResource(R.string.workout_checked),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 打卡成功动画横幅。 */
@Composable
private fun CelebrateBanner(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = FeiQiMotion.normal, easing = FastOutSlowInEasing),
        label = "celebrate",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 40f,
        animationSpec = tween(durationMillis = FeiQiMotion.normal, easing = FastOutSlowInEasing),
        label = "celebrateOffset",
    )
    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alpha
                    translationY = offsetY
                }
                .background(CardGreen, RoundedCornerShape(FeiQiRadius.md))
                .padding(FeiQiSpacing.md)
                .testTag("workout_celebrate")
        ) {
            Text(
                text = stringResource(R.string.workout_celebrate),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}