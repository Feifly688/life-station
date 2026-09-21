package com.feiqi.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.os.Build
import android.widget.Toast
import com.feiqi.R
import com.feiqi.data.model.Schedule
import com.feiqi.data.model.ScheduleListItem
import com.feiqi.ui.components.ConfirmDialog
import com.feiqi.ui.components.DeleteConfirmHost
import com.feiqi.ui.components.DeleteConfirmState
import com.feiqi.ui.components.EmptyState
import com.feiqi.ui.components.InfoDialog
import com.feiqi.ui.components.ReminderSettingDialog
import com.feiqi.ui.components.TextEditDialog
import com.feiqi.ui.components.rememberDeleteConfirm
import com.feiqi.ui.components.recurrenceLabel
import com.feiqi.ui.theme.CardRed
import com.feiqi.ui.theme.ExpenseRed
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Outline
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils
import com.feiqi.utils.NotificationUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import androidx.compose.ui.platform.SoftwareKeyboardController
import java.util.UUID

@Composable
internal fun ScheduleListItemCard(
    item: ScheduleListItem,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    isCompleted: Boolean,
    onToggleGroup: (String) -> Unit,
    onToggleItem: (Schedule) -> Unit,
    onToggleWholeGroup: (listId: String, completed: Boolean) -> Unit,
    onLongPress: (EditTarget) -> Unit,
    onEnterSelection: (ScheduleListItem) -> Unit,
    onToggleSelection: (ScheduleListItem) -> Unit,
    onClickGroup: (ScheduleListItem.Group) -> Unit,
    onDeleteList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (item) {
        is ScheduleListItem.Single -> SingleScheduleCard(
            schedule = item.schedule,
            selectionMode = selectionMode,
            selected = selected,
            onToggle = { onToggleItem(item.schedule) },
            onLongPress = { onEnterSelection(item) },
            onEdit = { onLongPress(EditTarget.Item(item.schedule)) },
            onSelect = { onToggleSelection(item) },
            modifier = modifier
        )
        is ScheduleListItem.Group -> ScheduleGroupCard(
            group = item,
            expanded = expanded,
            selectionMode = selectionMode,
            selected = selected,
            isCompleted = isCompleted,
            onClick = { onClickGroup(item) },
            onToggleExpand = { onToggleGroup(item.listId) },
            onToggleItem = onToggleItem,
            onToggleWholeGroup = { completed -> onToggleWholeGroup(item.listId, completed) },
            onLongPressGroup = { onEnterSelection(item) },
            onLongPressItem = { schedule -> onLongPress(EditTarget.Item(schedule)) },
            onSelect = { onToggleSelection(item) },
            onDeleteList = onDeleteList,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SingleScheduleCard(
    schedule: Schedule,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = DateUtils.today()
    val isOverdue = isScheduleOverdue(schedule.date, schedule.time, schedule.reminder, today) &&
        !schedule.completed
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOverdue && !selectionMode) CardRed else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = if (selectionMode) onSelect else onEdit,
                    onLongClick = if (selectionMode) null else onLongPress
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!selectionMode) {
                Checkbox(
                    checked = schedule.completed,
                    onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Primary,
                        uncheckedColor = Outline
                    )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (schedule.completed && !selectionMode) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (schedule.completed && !selectionMode) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (schedule.note.isNotBlank()) {
                    Text(
                        text = schedule.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isOverdue) {
                    Text(
                        text = stringResource(R.string.overdue),
                        style = MaterialTheme.typography.labelSmall,
                        color = ExpenseRed
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                schedule.time?.let {
                    Text(
                        text = DateUtils.hm(it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = DateUtils.monthDay(schedule.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (schedule.completed) {
                    schedule.completedDate?.let {
                        Text(
                            text = stringResource(R.string.list_completed_date, DateUtils.monthDay(it)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (schedule.reminder && schedule.time != null) {
                        Text(
                            text = DateUtils.hm(schedule.time),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (selectionMode) {
                SelectionIndicator(selected = selected)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ScheduleGroupCard(
    group: ScheduleListItem.Group,
    expanded: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleItem: (Schedule) -> Unit,
    onToggleWholeGroup: (Boolean) -> Unit,
    onLongPressGroup: () -> Unit,
    onLongPressItem: (Schedule) -> Unit,
    onSelect: () -> Unit,
    onDeleteList: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = DateUtils.today()
    val hasOverdue = group.items.any {
        isScheduleOverdue(it.date, it.time, it.reminder, today) && !it.completed
    }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasOverdue && !selectionMode && !isCompleted) {
                CardRed
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (selectionMode) {
                            onSelect()
                        } else {
                            // 无论是否已完成，点击正文（勾选框与右侧按钮以外的区域）统一进入编辑模式。
                            onClick()
                        }
                    },
                    onLongClick = if (selectionMode) null else onLongPressGroup
                )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!selectionMode) {
                    Checkbox(
                        checked = group.allCompleted,
                        onCheckedChange = { checked -> onToggleWholeGroup(checked) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Primary,
                            uncheckedColor = Outline
                        )
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    val titleColor = if (isCompleted && !selectionMode) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val titleDecoration = if (isCompleted && !selectionMode) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    }
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                        textDecoration = titleDecoration,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val recurrenceText = recurrenceLabel(group.recurrence)
                    val overdueText = stringResource(R.string.overdue)
                    val completedDateText = group.items.firstNotNullOfOrNull { it.completedDate }?.let {
                        stringResource(R.string.list_completed_date, DateUtils.monthDay(it))
                    }
                    // 完成后附带当初设置的提醒时间（若有）
                    val reminderTimeText = group.items.firstNotNullOfOrNull { if (it.reminder) it.time else null }?.let {
                        DateUtils.hm(it)
                    }
                    val subInfo = remember(
                        group,
                        hasOverdue,
                        recurrenceText,
                        overdueText,
                        completedDateText,
                        reminderTimeText,
                        isCompleted
                    ) {
                        val parts = mutableListOf<String>()
                        if (isCompleted) {
                            completedDateText?.let { parts += it }
                            reminderTimeText?.let { parts += it }
                        } else {
                            group.items.filter { !it.completed }.minByOrNull { it.date }?.let { near ->
                                parts += buildString {
                                    append(DateUtils.monthDay(near.date))
                                    near.time?.let { t -> append(" ").append(DateUtils.hm(t)) }
                                }
                            }
                        }
                        if (group.isRecurring) parts += recurrenceText
                        if (hasOverdue && !isCompleted) parts += overdueText
                        parts.joinToString(" · ")
                    }
                    if (subInfo.isNotBlank()) {
                        Text(
                            text = subInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOverdue && !isCompleted) {
                                ExpenseRed
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Text(
                    text = "${group.completedCount}/${group.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (selectionMode) {
                    SelectionIndicator(selected = selected)
                } else {
                    // 无论清单是否已完成，右侧均提供展开按钮以查看全部子项（与已完成清单行为一致）。
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.expand),
                            modifier = Modifier.rotate(rotation),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded && !selectionMode,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    group.items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = item.completed,
                                onCheckedChange = { onToggleItem(item) },
                                enabled = !isCompleted,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Primary,
                                    uncheckedColor = Outline
                                )
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (item.completed) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            item.time?.let {
                                Text(
                                    text = DateUtils.hm(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onDeleteList(group.listId) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = ExpenseRed
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.delete),
                                color = ExpenseRed,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompletedSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.completed_with_count, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 判断日程是否「提醒时间已过」（仅对**已设置提醒**的条目生效）：
 * - 未设置提醒（reminder = false，即无时间待办）→ **永不判逾期**，由用户手动完成或删除；
 * - date < today → 过期
 * - date == today 且 time != null 且 now > time → 过期（今天但提醒时间已过）
 * - date == today 且 time == null → 视为「当天提醒」，未过期
 * - date > today → 未过期
 */
private fun isScheduleOverdue(
    date: java.time.LocalDate,
    time: java.time.LocalTime?,
    reminder: Boolean,
    today: java.time.LocalDate
): Boolean {
    if (!reminder) return false
    if (date < today) return true
    if (date == today && time != null) {
        return java.time.LocalTime.now() > time
    }
    return false
}
