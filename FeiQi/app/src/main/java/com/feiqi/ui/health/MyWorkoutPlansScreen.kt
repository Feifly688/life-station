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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.entity.WorkoutPlanEntity
import com.feiqi.data.model.WorkoutGoal
import com.feiqi.data.model.WorkoutPlanSeed
import com.feiqi.data.model.WorkoutPlanType
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.PrimaryContainer
import com.feiqi.utils.DateUtils

/**
 * 「我的训练计划」列表页（健身主页的**下一级**，整页覆盖）。
 *
 * 展示官方计划 + 用户自建计划；用户计划可「开始训练」切换当前训练计划。
 *
 * ## 本阶段不含删除
 * 级联删除会牵动 `workout_records` 的一致性（是否保留历史打卡），
 * 留到下一阶段与软删除字段（`deleted`）一并处理，见 `FITNESS_MODULE_V2_STAGE2.md`。
 */
@Composable
internal fun MyWorkoutPlansScreen(
    planViewModel: WorkoutPlanViewModel,
    onCreatePlan: () -> Unit,
    onStartPlan: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val userPlans by planViewModel.userPlans.collectAsStateWithLifecycle()
    val officialPlans by planViewModel.officialPlans.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        planViewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeiQiSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = FeiQiSpacing.md,
                bottom = FeiQiSpacing.xl,
            ),
        ) {
            item {
                PlansHeader(onBack = onBack)
            }

            if (officialPlans.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.workout_official_plans)) }
                items(officialPlans, key = { it.planId }) { plan ->
                    PlanCard(
                        plan = plan,
                        highlight = false,
                        onStart = { onStartPlan(plan.planId) },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.workout_my_plans),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCreatePlan) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.workout_create_plan),
                            tint = Primary,
                        )
                    }
                }
            }

            if (userPlans.isEmpty()) {
                item { EmptyPlanPlaceholder(onCreate = onCreatePlan) }
            } else {
                items(userPlans, key = { it.planId }) { plan ->
                    PlanCard(
                        plan = plan,
                        highlight = true,
                        onStart = { onStartPlan(plan.planId) },
                    )
                }
                item {
                    Button(
                        onClick = onCreatePlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(FeiQiRadius.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = OnPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
                        Text(text = stringResource(R.string.workout_create_plan))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlansHeader(onBack: () -> Unit) {
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
            text = stringResource(R.string.workout_my_plans_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PlanCard(
    plan: WorkoutPlanEntity,
    highlight: Boolean,
    onStart: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FeiQiRadius.md))
            .clickable(onClick = onStart),
        shape = RoundedCornerShape(FeiQiRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) PrimaryContainer else CardAmber,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(FeiQiSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(FeiQiSpacing.sm))
                GoalBadge(goal = WorkoutGoal.fromCode(plan.goal))
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = stringResource(
                    R.string.workout_plan_meta,
                    plan.cycleDays,
                    WorkoutPlanType.fromCode(plan.type).label,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = stringResource(
                    R.string.workout_plan_created,
                    DateUtils.relativeDate(
                        java.time.LocalDate.ofEpochDay(
                            plan.createdTime / 86_400_000L
                        )
                    ),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(FeiQiRadius.sm))
                    .background(Primary.copy(alpha = 0.10f))
                    .clickable(onClick = onStart)
                    .padding(vertical = FeiQiSpacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.workout_plan_start),
                    style = MaterialTheme.typography.labelLarge,
                    color = Primary,
                )
            }
        }
    }
}

@Composable
private fun GoalBadge(goal: WorkoutGoal) {
    Text(
        text = goal.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(FeiQiRadius.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(horizontal = FeiQiSpacing.sm, vertical = 3.dp),
    )
}

@Composable
private fun EmptyPlanPlaceholder(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FeiQiRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .padding(FeiQiSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🏋️",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Text(
                text = stringResource(R.string.workout_my_plans_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Button(
                onClick = onCreate,
                shape = RoundedCornerShape(FeiQiRadius.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = OnPrimary,
                ),
            ) {
                Text(text = stringResource(R.string.workout_create_first_plan))
            }
        }
    }
}