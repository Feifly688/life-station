package com.feiqi.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.data.model.estimateDaySeconds
import com.feiqi.data.model.secondsToMinutes
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
    planViewModel: WorkoutPlanViewModel,
    onStartTraining: (WorkoutPlan) -> Unit,
    onOpenMyPlans: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenHistory: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val completedDays by viewModel.completedDayList.collectAsStateWithLifecycle()
    val runtimePlan by viewModel.runtimePlan.collectAsStateWithLifecycle()
    val totalDays by viewModel.totalDays.collectAsStateWithLifecycle()
    val activePlanId by viewModel.activePlanId.collectAsStateWithLifecycle()
    val allPlans by planViewModel.plans.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 进度只认当前计划的打卡记录；换计划时 completedDays 会整体切换，避免串号
    val todayPlan = remember(runtimePlan, progress.currentDay) {
        runtimePlan.firstOrNull { it.dayNumber == progress.currentDay } ?: runtimePlan.lastOrNull()
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

            PlanSwitcherRow(
                plans = allPlans,
                activePlanId = activePlanId,
                onSelect = viewModel::selectPlan,
                onOpenMyPlans = onOpenMyPlans,
                modifier = Modifier.padding(top = FeiQiSpacing.md),
            )

            ExerciseLibraryEntryCard(
                onClick = onOpenLibrary,
                modifier = Modifier.padding(top = FeiQiSpacing.md),
            )

            WorkoutProgressCard(
                currentDay = progress.currentDay,
                completedCount = progress.completedCount,
                totalDays = totalDays,
                percent = progress.percent,
                planName = runtimePlan.firstOrNull()?.title?.substringAfter("· ")?.trim()
                    ?: allPlans.firstOrNull { it.planId == activePlanId }?.name
                    ?: stringResource(R.string.workout_title),
                modifier = Modifier.padding(top = FeiQiSpacing.md),
            )

            if (todayPlan != null) {
                TodayWorkoutCard(
                    plan = todayPlan,
                    estimatedMinutes = remember(todayPlan) {
                        secondsToMinutes(estimateDaySeconds(todayPlan))
                    },
                    difficultyLabel = null,
                    onStart = { onStartTraining(todayPlan) },
                    modifier = Modifier.padding(top = FeiQiSpacing.lg),
                )
            }

            SectionTitle(
                title = stringResource(R.string.workout_history),
                modifier = Modifier.padding(top = FeiQiSpacing.lg),
            )
            if (completedDays.isEmpty()) {
                WorkoutEmptyState(
                    emoji = "🏆",
                    title = stringResource(R.string.workout_history_empty),
                    description = stringResource(R.string.workout_history_empty_desc),
                )
            } else {
                DayHistoryGrid(
                    totalDays = totalDays,
                    completedDays = completedDays,
                    onDayClick = { day -> if (day in completedDays) onOpenHistory(day) },
                )
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 动作库入口卡（🏋 + 标题 + 副标题 + 进入）。 */
@Composable
private fun ExerciseLibraryEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeiQiRadius.md))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FeiQiRadius.md),
        colors = CardDefaults.cardColors(containerColor = CardAmber),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(FeiQiSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "\uD83C\uDFCB", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.width(FeiQiSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.exercise_library_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.exercise_library_entry_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.exercise_library_enter),
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
            )
        }
    }
}

/**
 * 当前计划切换条：横向滚动的计划名 + 「我的计划 ›」入口。
 *
 * 官方计划与用户自建计划都可直接切换，切换后进度 / 今日训练 / 历史全部跟随。
 */
@Composable
private fun PlanSwitcherRow(
    plans: List<com.feiqi.data.entity.WorkoutPlanEntity>,
    activePlanId: String,
    onSelect: (String) -> Unit,
    onOpenMyPlans: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
        ) {
            plans.forEach { plan ->
                val active = plan.planId == activePlanId
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(FeiQiRadius.sm))
                        .background(if (active) Primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onSelect(plan.planId) }
                        .padding(horizontal = FeiQiSpacing.md, vertical = FeiQiSpacing.sm),
                )
            }
        }
        Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
        Text(
            text = stringResource(R.string.workout_my_plans) + " ›",
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(FeiQiRadius.sm))
                .clickable(onClick = onOpenMyPlans)
                .padding(horizontal = FeiQiSpacing.sm, vertical = FeiQiSpacing.sm),
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

/** 历史记录：按当前计划总天数分行的打勾网格。 */
@Composable
private fun DayHistoryGrid(
    totalDays: Int,
    completedDays: List<Int>,
    onDayClick: (Int) -> Unit,
) {
    val done = completedDays.toSet()
    val days = remember(totalDays) { (1..totalDays.coerceAtLeast(0)).toList() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FeiQiSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
    ) {
        days.chunked(5).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
            ) {
                week.forEach { day ->
                    DayHistoryChip(
                        dayNumber = day,
                        done = day in done,
                        onClick = { onDayClick(day) },
                    )
                }
            }
        }
    }
}

/** 今天日期文案（详情页用）。 */
internal fun todayLabel(): String = DateUtils.relativeDate(DateUtils.today())