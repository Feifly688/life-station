package com.feiqi.ui.health

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.MAX_DAILY_EXERCISES
import com.feiqi.data.model.exercisesOfCategory
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.PrimaryContainer

/**
 * 动作选择页（创建训练计划 → 训练日设置 → 「选择动作」）。
 *
 * 顶部大类筛选 chips + 动作网格，**即选即存**（勾选状态由 [ExerciseViewModel] 持有）。
 * 达到 [MAX_DAILY_EXERCISES] 上限后再点会提示，取消勾选不受限。
 */
@Composable
internal fun ExercisePickerScreen(
    title: String,
    initialCategory: BodyCategory?,
    initialPicked: Set<String>,
    viewModel: ExerciseViewModel,
    onConfirm: (Set<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val limitMessage = stringResource(R.string.exercise_pick_limit)
    val all by viewModel.allExercises.collectAsStateWithLifecycle()
    val picked by viewModel.pickedNames.collectAsStateWithLifecycle()
    var category by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(initialCategory?.label)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 进入页面时用传入的初始勾选初始化（只做一次）
    LaunchedEffect(Unit) {
        viewModel.resetPicked(initialPicked)
    }

    val activeCategory = BodyCategory.entries.firstOrNull { it.label == category }
    val visible = remember(all, activeCategory) {
        if (activeCategory == null) {
            all.filter { it.category.isNotBlank() }
        } else {
            exercisesOfCategory(all, activeCategory)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FeiQiSpacing.md, vertical = FeiQiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(FeiQiSpacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            R.string.exercise_picked_count,
                            picked.size,
                            MAX_DAILY_EXERCISES,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 大类筛选 chips（含「全部」）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = FeiQiSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
            ) {
                FilterChip(
                    text = stringResource(R.string.exercise_all),
                    selected = activeCategory == null,
                    onClick = { category = null },
                )
                BodyCategory.entries.forEach { item ->
                    FilterChip(
                        text = "${item.emoji} ${item.label}",
                        selected = activeCategory == item,
                        onClick = { category = item.label },
                    )
                }
            }

            if (visible.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.exercise_category_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = FeiQiSpacing.lg),
                    contentPadding = PaddingValues(
                        top = FeiQiSpacing.md,
                        bottom = FeiQiSpacing.lg,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
                ) {
                    items(visible, key = { it.id }) { exercise ->
                        PickerExerciseCard(
                            name = exercise.name,
                            emoji = BodyCategory.entries
                                .firstOrNull { it.label == exercise.category }
                                ?.emoji ?: "🏋",
                            categoryLabel = exercise.category,
                            difficultyLabel = exercise.difficulty,
                            picked = exercise.name in picked,
                            onToggle = {
                                if (!viewModel.togglePicked(exercise.name)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(limitMessage)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(FeiQiSpacing.lg),
            ) {
                Button(
                    onClick = { onConfirm(picked) },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(FeiQiRadius.sm),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                    ),
                ) {
                    Text(
                        text = if (picked.isEmpty()) {
                            stringResource(R.string.exercise_pick_at_least_one)
                        } else {
                            stringResource(R.string.exercise_confirm_picked, picked.size)
                        }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
        )
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(FeiQiRadius.sm))
            .background(if (selected) Primary else PrimaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = FeiQiSpacing.md, vertical = FeiQiSpacing.sm),
    )
}

/** 勾选态动作卡：主色描边 + 对勾，选中时轻微放大。 */
@Composable
private fun PickerExerciseCard(
    name: String,
    emoji: String,
    categoryLabel: String,
    difficultyLabel: String,
    picked: Boolean,
    onToggle: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (picked) 1.02f else 1f,
        animationSpec = tween(180),
        label = "pickScale",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(FeiQiRadius.md))
            .background(if (picked) PrimaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onToggle)
            .padding(FeiQiSpacing.md),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(text = emoji, style = MaterialTheme.typography.headlineSmall)
            if (picked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = "$categoryLabel · $difficultyLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}