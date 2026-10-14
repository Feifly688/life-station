package com.feiqi.ui.health

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.ExerciseCategoryCard
import com.feiqi.data.model.exerciseDifficultyLabel
import com.feiqi.data.model.exerciseEquipmentLabel
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.OnTertiaryContainer
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.Secondary

/**
 * 动作库主页（健身主页的下级，整页覆盖）。
 *
 * 结构：顶部返回 + 搜索框 → 分类卡区（7 大类，显示动作数）→ 动作网格。
 * 点击分类卡进入该分类；点击动作进入详情。
 */
@Composable
internal fun ExerciseLibraryScreen(
    viewModel: ExerciseViewModel,
    onOpenDetail: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val all by viewModel.allExercises.collectAsStateWithLifecycle()
    val keyword by viewModel.keyword.collectAsStateWithLifecycle()
    val category by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filtered by viewModel.filtered.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = FeiQiSpacing.lg,
                end = FeiQiSpacing.lg,
                top = FeiQiSpacing.md,
                bottom = FeiQiSpacing.xl,
            ),
        ) {
            item {
                LibraryHeader(
                    exerciseCount = all.count { it.category.isNotBlank() },
                    categoryCount = BodyCategory.entries.size,
                    onBack = onBack,
                )
            }

            item {
                LibrarySearchField(
                    keyword = keyword,
                    onKeywordChange = viewModel::setKeyword,
                )
            }

            // 选中分类时隐藏分类卡区，只留「返回全部」
            if (category != null) {
                item {
                    CategoryFilterRow(
                        category = category!!,
                        onClear = { viewModel.selectCategory(null) },
                    )
                }
            } else {
                item {
                    SectionLabel(stringResource(R.string.exercise_categories))
                }
                item {
                    CategoryGrid(
                        cards = filtered.categories,
                        onSelect = { viewModel.selectCategory(it) },
                    )
                }
            }

            item {
                SectionLabel(
                    text = if (category == null) {
                        stringResource(R.string.exercise_all)
                    } else {
                        stringResource(R.string.exercise_category_count, filtered.exercises.size)
                    }
                )
            }

            if (filtered.exercises.isEmpty()) {
                item { LibraryEmptyPlaceholder(hasKeyword = keyword.isNotBlank()) }
            } else {
                item {
                    // 分类卡与动作网格共用同一个 filtered，计数天然一致
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        state = gridState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 2400.dp),
                        horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(FeiQiSpacing.md),
                        userScrollEnabled = false,
                    ) {
                        items(filtered.exercises, key = { it.id }) { exercise ->
                            ExerciseCard(
                                exercise = exercise,
                                onClick = { onOpenDetail(exercise.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(exerciseCount: Int, categoryCount: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                text = stringResource(R.string.exercise_library_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.exercise_library_summary,
                    exerciseCount,
                    categoryCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibrarySearchField(keyword: String, onKeywordChange: (String) -> Unit) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.exercise_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (keyword.isNotEmpty()) {
            {
                IconButton(onClick = { onKeywordChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.exercise_search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        shape = RoundedCornerShape(FeiQiRadius.sm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 分类卡横滑条：emoji + 大类名 + 动作数。 */
@Composable
private fun CategoryGrid(
    cards: List<ExerciseCategoryCard>,
    onSelect: (BodyCategory) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(FeiQiSpacing.sm),
    ) {
        items(cards, key = { it.category.label }) { card ->
            CategoryCard(card = card, onClick = { onSelect(card.category) })
        }
    }
}

@Composable
private fun CategoryCard(card: ExerciseCategoryCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(RoundedCornerShape(FeiQiRadius.md))
            .background(CardAmber)
            .clickable(onClick = onClick)
            .padding(vertical = FeiQiSpacing.md, horizontal = FeiQiSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = card.category.emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(FeiQiSpacing.xs))
        Text(
            text = card.category.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.exercise_unit_count, card.count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryFilterRow(category: BodyCategory, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${category.emoji} ${category.label}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.exercise_show_all),
            style = MaterialTheme.typography.labelLarge,
            color = Primary,
            modifier = Modifier
                .clip(RoundedCornerShape(FeiQiRadius.sm))
                .clickable(onClick = onClear)
                .padding(horizontal = FeiQiSpacing.sm, vertical = FeiQiSpacing.xs),
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

/**
 * 动作卡：图片占位（暖调渐变 + 部位 emoji）+ 名称 + 大类 + 难度徽章。
 *
 * 图片占位用**渐变 + emoji**，`ExerciseEntity.image` 就绪后在此替换为真实图片。
 */
@Composable
internal fun ExerciseCard(
    exercise: ExerciseEntity,
    modifier: Modifier = Modifier,
    trailingBadge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val category = BodyCategory.entries.firstOrNull { it.label == exercise.category }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FeiQiRadius.md))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(FeiQiSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(FeiQiRadius.sm))
                .background(
                    Brush.linearGradient(
                        listOf(CardAmber, Secondary.copy(alpha = 0.22f)),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category?.emoji ?: "🏋",
                style = MaterialTheme.typography.headlineMedium,
            )
            if (exercise.equipment.isNotBlank() && exercise.equipment != "NONE") {
                Text(
                    text = com.feiqi.data.model.ExerciseEquipment.fromCode(exercise.equipment).emoji,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
        Text(
            text = exercise.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = exercise.category.ifBlank { stringResource(R.string.exercise_all_parts) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingBadge != null) {
                trailingBadge()
            } else {
                Text(
                    text = exerciseDifficultyLabel(exercise),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnTertiaryContainer,
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyPlaceholder(hasKeyword: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🔍", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(FeiQiSpacing.sm))
            Text(
                text = if (hasKeyword) {
                    stringResource(R.string.exercise_no_result)
                } else {
                    stringResource(R.string.exercise_library_empty)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}