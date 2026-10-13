package com.feiqi.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.WORKOUT_PLANS
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils

/**
 * 健身主页：计划总览 + 今日训练 + 连续训练 + 历史记录。
 *
 * 作为**健康页的二级页**整页覆盖（与专注页一致的实现方式），不新增底部导航 Tab。
 */
@Composable
internal fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onStartTraining: (WorkoutPlan) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()
    val completedDays by viewModel.completedDayList.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val todayPlan = remember(progress.currentDay) {
        WORKOUT_PLANS.firstOrNull { it.dayNumber == progress.currentDay } ?: WORKOUT_PLANS.last()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FeiQiSpacing.lg),
        ) {
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            HeaderRow(onBack = onBack)

            WorkoutProgressCard(
                currentDay = progress.currentDay,
                completedCount = progress.completedCount,
                totalDays = WORKOUT_PLANS.size,
                percent = progress.percent,
                streak = streak,
                modifier = Modifier.padding(top = FeiQiSpacing.md),
            )

            TodayPlanCard(
                plan = todayPlan,
                onStart = { onStartTraining(todayPlan) },
                modifier = Modifier.padding(top = FeiQiSpacing.lg),
            )

            SectionTitle(
                title = stringResource(R.string.workout_history),
                modifier = Modifier.padding(top = FeiQiSpacing.lg),
            )
            DayHistoryGrid(
                completedDays = completedDays,
                onDayClick = { day ->
                    if (day in completedDays) viewModel.undo(day)
                },
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 二级页顶部：返回 + 标题。 */
@Composable
private fun HeaderRow(onBack: () -> Unit) {
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
            text = stringResource(R.string.workout_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 今日训练卡：部位标签 + 动作列表 + 开始按钮。 */
@Composable
private fun TodayPlanCard(
    plan: WorkoutPlan,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeiQiRadius.md),
        colors = CardDefaults.cardColors(containerColor = CardAmber),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(FeiQiSpacing.lg)) {
            Text(
                text = stringResource(R.string.workout_today),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = "🔥 ${plan.bodyParts}",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = plan.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            plan.exercises.forEachIndexed { index, exercise ->
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${exercise.sets}组 × " + if (exercise.durationSeconds > 0) {
                                "${exercise.durationSeconds}秒"
                            } else {
                                exercise.reps
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("workout_start"),
                shape = RoundedCornerShape(FeiQiRadius.sm),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
            ) {
                Text(text = stringResource(R.string.workout_start))
            }
        }
    }
}

/** 区块小标题。 */
@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** 历史记录：30 天打勾网格。 */
@Composable
private fun DayHistoryGrid(
    completedDays: List<Int>,
    onDayClick: (Int) -> Unit,
) {
    val done = completedDays.toSet()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FeiQiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
    ) {
        WORKOUT_PLANS.chunked(5).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
            ) {
                week.forEach { plan ->
                    DayHistoryChip(
                        dayNumber = plan.dayNumber,
                        done = plan.dayNumber in done,
                        onClick = { onDayClick(plan.dayNumber) },
                    )
                }
            }
        }
    }
}

/** 今天日期文案（详情页用）。 */
internal fun todayLabel(): String = DateUtils.relativeDate(DateUtils.today())