package com.feiqi.ui.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.MAX_PLAN_NAME_LENGTH
import com.feiqi.data.model.WORKOUT_CYCLE_OPTIONS
import com.feiqi.data.model.WORKOUT_FREQUENCY_OPTIONS
import com.feiqi.data.model.WorkoutGoal
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.PrimaryContainer
import kotlin.math.ceil

/**
 * 创建训练计划（**分步向导**，整页覆盖）。
 *
 * ## 4 步流程
 * 1. **基础信息**：计划名称 + 训练目标（💪增肌 / 🔥减脂 / ✨塑形 / 🌱保持）
 * 2. **训练周期**：7 / 14 / 30 天或自定义
 * 3. **训练频率**：每周 3~6 天，实时预览「练 / 休」排布
 * 4. **训练日设置**：勾选身体部位（6 大类），系统按周期 × 频率自动排布
 *
 * 第 4 步**不允许用户逐日手调**（阶段 2 范围），排布由
 * `data/model/WorkoutPlanBuilder` 的纯函数完成，保存时按部位从动作库取动作。
 */
@Composable
internal fun CreateWorkoutPlanScreen(
    viewModel: WorkoutPlanViewModel,
    onSaved: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableIntStateOf(0) }
    val name by viewModel.name.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val cycleDays by viewModel.cycleDays.collectAsStateWithLifecycle()
    val frequency by viewModel.frequency.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val saveable by viewModel.saveable.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    val stepTitles = listOf(
        stringResource(R.string.workout_step_basic),
        stringResource(R.string.workout_step_cycle),
        stringResource(R.string.workout_step_frequency),
        stringResource(R.string.workout_step_days),
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // 顶部：返回 + 步骤指示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeiQiSpacing.md, vertical = FeiQiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (step == 0) {
                        viewModel.resetDraft()
                        onBack()
                    } else {
                        step -= 1
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
                Text(
                    text = stepTitles[step],
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${step + 1}/${stepTitles.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StepProgressBar(step = step, total = stepTitles.size)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FeiQiSpacing.lg),
            ) {
                Spacer(modifier = Modifier.height(FeiQiSpacing.lg))

                // 步骤内容：常驻组合树，按 step 切样式，避免结构跳变
                StepBasicSection(
                    visible = step == 0,
                    name = name,
                    goal = goal,
                    onNameChange = viewModel::setName,
                    onGoalChange = viewModel::setGoal,
                )
                StepCycleSection(
                    visible = step == 1,
                    cycleDays = cycleDays,
                    onCycleChange = viewModel::setCycleDays,
                )
                StepFrequencySection(
                    visible = step == 2,
                    cycleDays = cycleDays,
                    frequency = frequency,
                    onFrequencyChange = viewModel::setFrequency,
                )
                StepDaysSection(
                    visible = step == 3,
                    cycleDays = cycleDays,
                    categories = categories,
                    onToggleCategory = viewModel::toggleCategory,
                    trainingDayCount = draft.trainingDayCount,
                )

                Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
            }

            // 底部操作条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(FeiQiSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
            ) {
                if (step > 0) {
                    OutlinedStepButton(
                        text = stringResource(R.string.workout_step_prev),
                        onClick = { step -= 1 },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        if (step < 3) {
                            step += 1
                        } else {
                            viewModel.save { planId -> onSaved(planId) }
                        }
                    },
                    enabled = if (step == 3) saveable else true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(FeiQiRadius.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                    ),
                ) {
                    Text(
                        text = if (step < 3) {
                            stringResource(R.string.workout_step_next)
                        } else {
                            stringResource(R.string.workout_step_save)
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 顶部进度条（分段）。 */
@Composable
private fun StepProgressBar(step: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FeiQiSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.xs),
    ) {
        repeat(total) { index ->
            val active = index <= step
            val color by animateColorAsState(
                targetValue = if (active) Primary else MaterialTheme.colorScheme.outlineVariant,
                animationSpec = tween(220),
                label = "stepBar",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

@Composable
private fun StepSection(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String, subtitle: String? = null) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- Step 1：基础信息 ----------------

@Composable
private fun StepBasicSection(
    visible: Boolean,
    name: String,
    goal: WorkoutGoal,
    onNameChange: (String) -> Unit,
    onGoalChange: (WorkoutGoal) -> Unit,
) {
    StepSection(visible) {
        Column {
            SectionTitle(
                text = stringResource(R.string.workout_create_name_label),
                subtitle = stringResource(R.string.workout_create_name_hint),
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                placeholder = {
                    Text(stringResource(R.string.workout_create_name_placeholder))
                },
                shape = RoundedCornerShape(FeiQiRadius.sm),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
            Text(
                text = "${name.trim().length}/$MAX_PLAN_NAME_LENGTH",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )

            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
            SectionTitle(text = stringResource(R.string.workout_create_goal_label))
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            GoalGrid(selected = goal, onSelect = onGoalChange)
        }
    }
}

@Composable
private fun GoalGrid(selected: WorkoutGoal, onSelect: (WorkoutGoal) -> Unit) {
    val items = listOf(
        WorkoutGoal.BULK to "💪",
        WorkoutGoal.CUT to "🔥",
        WorkoutGoal.SHAPE to "✨",
        WorkoutGoal.MAINTAIN to "🌱",
    )
    Column(verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.md)) {
                row.forEach { (goal, emoji) ->
                    SelectableCard(
                        modifier = Modifier.weight(1f),
                        selected = goal == selected,
                        onClick = { onSelect(goal) },
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
                        Text(
                            text = goal.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (goal == selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- Step 2：周期 ----------------

@Composable
private fun StepCycleSection(
    visible: Boolean,
    cycleDays: Int,
    onCycleChange: (Int) -> Unit,
) {
    StepSection(visible) {
        Column {
            SectionTitle(
                text = stringResource(R.string.workout_create_cycle_label),
                subtitle = stringResource(R.string.workout_create_cycle_hint),
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.md)) {
                WORKOUT_CYCLE_OPTIONS.forEach { option ->
                    SelectableCard(
                        modifier = Modifier.weight(1f),
                        selected = option == cycleDays,
                        onClick = { onCycleChange(option) },
                    ) {
                        Text(
                            text = "$option",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.workout_unit_day),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Text(
                text = stringResource(R.string.workout_create_custom_cycle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            OutlinedTextField(
                value = cycleDays.toString(),
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    if (digits.isNotEmpty()) {
                        val value = digits.toIntOrNull() ?: return@OutlinedTextField
                        if (value in 1..365) onCycleChange(value)
                    }
                },
                singleLine = true,
                suffix = { Text(stringResource(R.string.workout_unit_day)) },
                shape = RoundedCornerShape(FeiQiRadius.sm),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------- Step 3：频率 ----------------

@Composable
private fun StepFrequencySection(
    visible: Boolean,
    cycleDays: Int,
    frequency: Int,
    onFrequencyChange: (Int) -> Unit,
) {
    val preview = remember(cycleDays, frequency) {
        com.feiqi.data.model.layoutTrainingDays(cycleDays, frequency)
    }
    StepSection(visible) {
        Column {
            SectionTitle(
                text = stringResource(R.string.workout_create_frequency_label),
                subtitle = stringResource(R.string.workout_create_frequency_hint),
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm)) {
                WORKOUT_FREQUENCY_OPTIONS.forEach { option ->
                    SelectableCard(
                        modifier = Modifier.weight(1f),
                        selected = option == frequency,
                        onClick = { onFrequencyChange(option) },
                    ) {
                        Text(
                            text = "$option",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.workout_unit_per_week),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
            SectionTitle(text = stringResource(R.string.workout_create_preview))
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            WeekPreview(preview)
        }
    }
}

/** 一周排布预览：7 个小格，训练日高亮 + 「练」，休息日「休」。 */
@Composable
private fun WeekPreview(days: List<com.feiqi.data.model.PlannedDay>) {
    val firstWeek = days.take(7)
    val trainingCount = firstWeek.count { !it.isRest }
    val perWeek = ceil(days.size / 7.0).toInt().coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.xs),
    ) {
        firstWeek.forEach { planned ->
            val isTraining = !planned.isRest
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(FeiQiRadius.sm))
                    .background(
                        if (isTraining) PrimaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(vertical = FeiQiSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "周${"一二三四五六日"[planned.dayNumber - 1]}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isTraining) stringResource(R.string.workout_mark_train)
                    else stringResource(R.string.workout_mark_rest),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isTraining) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
    Text(
        text = stringResource(
            R.string.workout_create_preview_summary,
            days.count { !it.isRest },
            perWeek,
            trainingCount,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------- Step 4：训练部位 ----------------

@Composable
private fun StepDaysSection(
    visible: Boolean,
    cycleDays: Int,
    categories: List<BodyCategory>,
    onToggleCategory: (BodyCategory) -> Unit,
    trainingDayCount: Int,
) {
    StepSection(visible) {
        Column {
            SectionTitle(
                text = stringResource(R.string.workout_create_days_label),
                subtitle = stringResource(R.string.workout_create_days_hint),
            )
            Spacer(modifier = Modifier.height(FeiQiSpacing.md))
            BodyCategoryGrid(selected = categories, onToggle = onToggleCategory)

            Spacer(modifier = Modifier.height(FeiQiSpacing.xl))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(FeiQiRadius.md),
                colors = CardDefaults.cardColors(containerColor = CardAmber),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(FeiQiSpacing.lg)) {
                    Text(
                        text = stringResource(R.string.workout_create_summary_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
                    Text(
                        text = stringResource(R.string.workout_create_summary_days, cycleDays, trainingDayCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
                    Text(
                        text = if (categories.isEmpty()) {
                            stringResource(R.string.workout_create_summary_empty)
                        } else {
                            categories.joinToString(" · ") { "${it.emoji} ${it.label}" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 6 大类部位，2 列网格，多选。 */
@Composable
private fun BodyCategoryGrid(
    selected: List<BodyCategory>,
    onToggle: (BodyCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md)) {
        BodyCategory.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.md)) {
                row.forEach { category ->
                    SelectableCard(
                        modifier = Modifier.weight(1f),
                        selected = category in selected,
                        onClick = { onToggle(category) },
                    ) {
                        Text(
                            text = category.emoji,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (category in selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = category.displayParts,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 通用选择卡 ----------------

/**
 * 可选卡片：选中态暖调填充 + 主色描边 + 右上角对勾；
 * 点击时轻微缩放（0.97）作为触觉之外的视觉反馈。
 */
@Composable
private fun SelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.985f,
        animationSpec = tween(180),
        label = "selectableScale",
    )
    val border by animateColorAsState(
        targetValue = if (selected) Primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(200),
        label = "selectableBorder",
    )
    val container by animateColorAsState(
        targetValue = if (selected) PrimaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(200),
        label = "selectableContainer",
    )
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(FeiQiRadius.md))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(FeiQiRadius.md),
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(modifier = Modifier.padding(FeiQiSpacing.md)) {
                // 选中对勾：常驻组合树 + alpha渐变，避免结构增删引起布局跳变
                val checkAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    animationSpec = tween(180),
                    label = "checkAlpha",
                )
                Column {
                    content()
                }
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = border,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .graphicsLayer { this.alpha = checkAlpha },
                )
            }
        }
    }
}

@Composable
private fun OutlinedStepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(FeiQiRadius.sm))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(FeiQiRadius.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}