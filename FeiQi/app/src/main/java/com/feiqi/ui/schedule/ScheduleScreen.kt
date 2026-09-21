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
import androidx.compose.material.icons.filled.Edit
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

/**
 * 安全请求焦点：小窗/多窗口模式下，配置变更或重组时序可能使 FocusRequester 尚未绑定到
 * 可聚焦节点，直接 requestFocus() 会抛 IllegalStateException 造成闪退。捕获以避免崩溃。
 */
internal fun FocusRequester.safeRequestFocus() {
    try {
        requestFocus()
    } catch (_: IllegalStateException) {
        // 节点尚未挂载，忽略
    }
}

/**
 * 安全唤起软键盘：多窗口/小窗模式下窗口 token 可能临时失效，show() 可能抛异常，捕获避免崩溃。
 */
internal fun SoftwareKeyboardController?.safeShow() {
    try {
        this?.show()
    } catch (_: Exception) {
        // 窗口 token 失效，忽略
    }
}

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val deleteConfirm = rememberDeleteConfirm()

    var draftItems by remember { mutableStateOf(listOf<DraftItem>()) }
    var currentInput by remember { mutableStateOf("") }
    var isQuickAddActive by remember { mutableStateOf(false) }
    var reminderDateTime by remember {
        mutableStateOf(LocalDateTime.of(DateUtils.today(), defaultReminderTime()))
    }
    // 提醒复选框：默认不开启，点击设置提醒后才启用。
    var reminderEnabled by remember { mutableStateOf(false) }
    var quickAddRecurring by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderSetTip by remember { mutableStateOf<String?>(null) }

    var expandedListIds by remember { mutableStateOf(setOf<String>()) }
    var completedExpanded by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<ScheduleListItem.Group?>(null) }
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

    // 多选删除状态
    var selectionMode by remember { mutableStateOf(false) }
    var selectedScheduleIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedListIds by remember { mutableStateOf(setOf<String>()) }
    val selectedCount = selectedScheduleIds.size + selectedListIds.size

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Toast.makeText(
            context,
            if (granted) "通知权限已开启，到点会弹提醒" else "未授权通知，将无法收到提醒",
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // 点击 + 后自动聚焦并弹出软键盘
    LaunchedEffect(isQuickAddActive) {
        if (isQuickAddActive) {
            delay(120)
            focusRequester.safeRequestFocus()
            keyboardController.safeShow()
        }
    }

    // 进入快速添加时退出多选
    LaunchedEffect(isQuickAddActive) {
        if (isQuickAddActive && selectionMode) {
            selectionMode = false
            selectedScheduleIds = emptySet()
            selectedListIds = emptySet()
        }
    }

    val (activeItems, completedItems) = uiState.listItems.partition {
        when (it) {
            is ScheduleListItem.Single -> !it.schedule.completed
            is ScheduleListItem.Group -> !it.allCompleted
        }
    }

    val allItems = activeItems + completedItems
    val totalSelectable = allItems.size
    val allSelected = totalSelectable > 0 && selectedCount == totalSelectable

    fun enterSelection(item: ScheduleListItem) {
        selectionMode = true
        when (item) {
            is ScheduleListItem.Single -> selectedScheduleIds = selectedScheduleIds + item.schedule.id
            is ScheduleListItem.Group -> selectedListIds = selectedListIds + item.listId
        }
    }

    fun toggleSelection(item: ScheduleListItem) {
        when (item) {
            is ScheduleListItem.Single -> {
                selectedScheduleIds = if (item.schedule.id in selectedScheduleIds) {
                    selectedScheduleIds - item.schedule.id
                } else {
                    selectedScheduleIds + item.schedule.id
                }
            }
            is ScheduleListItem.Group -> {
                selectedListIds = if (item.listId in selectedListIds) {
                    selectedListIds - item.listId
                } else {
                    selectedListIds + item.listId
                }
            }
        }
    }

    fun selectAll() {
        selectedScheduleIds = allItems
            .filterIsInstance<ScheduleListItem.Single>()
            .map { it.schedule.id }
            .toSet()
        selectedListIds = allItems
            .filterIsInstance<ScheduleListItem.Group>()
            .map { it.listId }
            .toSet()
    }

    fun clearSelection() {
        selectedScheduleIds = emptySet()
        selectedListIds = emptySet()
    }

    fun exitSelectionMode() {
        selectionMode = false
        clearSelection()
    }

    fun isSelected(item: ScheduleListItem): Boolean = when (item) {
        is ScheduleListItem.Single -> item.schedule.id in selectedScheduleIds
        is ScheduleListItem.Group -> item.listId in selectedListIds
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 普通模式显示标题，多选模式显示选择状态栏
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selectedCount,
                    allSelected = allSelected,
                    onClose = ::exitSelectionMode,
                    onSelectAll = {
                        if (allSelected) clearSelection() else selectAll()
                    }
                )
            } else {
                Text(
                    text = stringResource(R.string.schedule_overview),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = if (isQuickAddActive) 120.dp else 92.dp
                )
            ) {
                if (activeItems.isEmpty() && completedItems.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.schedule_empty_title),
                            description = stringResource(R.string.schedule_empty_desc)
                        )
                    }
                } else {
                    items(activeItems, key = { itemKey(it) }) { item ->
                        ScheduleListItemCard(
                            item = item,
                            expanded = item is ScheduleListItem.Group && item.listId in expandedListIds,
                            selectionMode = selectionMode,
                            selected = isSelected(item),
                            isCompleted = false,
                            onToggleGroup = { listId ->
                                if (!selectionMode) {
                                    expandedListIds = expandedListIds.xor(listId)
                                }
                            },
                            onToggleItem = { schedule ->
                                if (!selectionMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleComplete(schedule)
                                }
                            },
                            onToggleWholeGroup = { listId, completed ->
                                if (!selectionMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setGroupCompleted(listId, completed)
                                }
                            },
                            onLongPress = { target ->
                                if (!selectionMode) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    editTarget = target
                                }
                            },
                            onEnterSelection = { enterSelection(it) },
                            onToggleSelection = { toggleSelection(it) },
                            onClickGroup = { editingGroup = it },
                            onDeleteList = { listId ->
                                deleteConfirm.request(context.getString(R.string.delete_list_confirm)) {
                                    viewModel.deleteList(listId)
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                if (completedItems.isNotEmpty()) {
                    item {
                        CompletedSectionHeader(
                            count = completedItems.size,
                            expanded = completedExpanded,
                            onToggle = { if (!selectionMode) completedExpanded = !completedExpanded }
                        )
                    }
                    if (completedExpanded) {
                        items(completedItems, key = { "done-${itemKey(it)}" }) { item ->
                            ScheduleListItemCard(
                                item = item,
                                expanded = item is ScheduleListItem.Group && item.listId in expandedListIds,
                                selectionMode = selectionMode,
                                selected = isSelected(item),
                                isCompleted = true,
                                onToggleGroup = { listId ->
                                    if (!selectionMode) {
                                        expandedListIds = expandedListIds.xor(listId)
                                    }
                                },
                                onToggleItem = { schedule ->
                                    if (!selectionMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggleComplete(schedule)
                                    }
                                },
                                onToggleWholeGroup = { listId, completed ->
                                    if (!selectionMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setGroupCompleted(listId, completed)
                                    }
                                },
                                onLongPress = { target ->
                                    if (!selectionMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        editTarget = target
                                    }
                                },
                                onEnterSelection = { enterSelection(it) },
                                onToggleSelection = { toggleSelection(it) },
                                onClickGroup = { editingGroup = it },
                                onDeleteList = { listId ->
                                    deleteConfirm.request(context.getString(R.string.delete_list_confirm)) {
                                        viewModel.deleteList(listId)
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        // 底部快速添加条 / 多选删除条
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        ) {
            if (selectionMode) {
                SelectionBottomBar(
                    enabled = selectedCount > 0,
                    onDelete = {
                        val msg = context.getString(
                            R.string.delete_selected_confirm,
                            selectedCount
                        )
                        deleteConfirm.request(msg) {
                            viewModel.deleteSelected(selectedScheduleIds, selectedListIds)
                            exitSelectionMode()
                        }
                    }
                )
            } else if (isQuickAddActive) {
                QuickAddPanel(
                    draftItems = draftItems,
                    currentInput = currentInput,
                    reminderEnabled = reminderEnabled,
                    reminderDateTime = reminderDateTime,
                    onCurrentInputChange = { currentInput = it },
                    onCommitCurrent = {
                        draftItems = draftItems + DraftItem(text = currentInput.trim())
                        currentInput = ""
                    },
                    onDraftEdit = { id, newText ->
                        draftItems = draftItems.map {
                            if (it.id == id) it.copy(text = newText) else it
                        }
                    },
                    onDraftDelete = { id ->
                        draftItems = draftItems.filter { it.id != id }
                    },
                    focusRequester = focusRequester,
                    onSetReminder = { showReminderDialog = true },
                    onCancelReminder = { reminderEnabled = false },
                    onFinish = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val finalText = currentInput.trim()
                        val finalDrafts = if (finalText.isNotEmpty()) {
                            draftItems + DraftItem(text = finalText)
                        } else draftItems
                        submitQuickAdd(
                            viewModel = viewModel,
                            titles = finalDrafts.map { it.text },
                            reminderDateTime = reminderDateTime,
                            reminderEnabled = reminderEnabled,
                            isRecurring = quickAddRecurring,
                            context = context,
                            notificationPermissionLauncher = notificationPermissionLauncher
                        )
                        draftItems = emptyList()
                        currentInput = ""
                        isQuickAddActive = false
                    },
                    onDismiss = {
                        isQuickAddActive = false
                        draftItems = emptyList()
                        currentInput = ""
                    }
                )
            } else {
                FloatingActionButton(
                    onClick = {
                        // 每次重新打开都复位：默认不启用提醒、默认一次性（非每日重复）、提醒时间=当前+1分钟
                        reminderDateTime = LocalDateTime.of(DateUtils.today(), defaultReminderTime())
                        reminderEnabled = false
                        quickAddRecurring = false
                        isQuickAddActive = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    shape = CircleShape,
                    containerColor = Primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_todo),
                        tint = OnPrimary
                    )
                }
            }
        }

        // 编辑清单弹窗（已改为屏幕内普通浮层，与 QuickAddPanel 同机制）：
        // 置于根 Box 内作为覆盖层，全屏遮罩 + 底部对齐卡片，imePadding 随输入法上移。
        editingGroup?.let { group ->
            ListEditDialog(
                group = group,
                isCompleted = group.allCompleted,
                onDismiss = { editingGroup = null },
                onSave = { title, items, deletedIds, reminderDateTime, reminderEnabled, isRecurring ->
                    viewModel.saveListEdit(
                        listId = group.listId,
                        newTitle = title,
                        items = items,
                        deletedIds = deletedIds,
                        reminderDateTime = reminderDateTime,
                        reminderEnabled = reminderEnabled,
                        isRecurring = isRecurring
                    )
                    editingGroup = null
                },
                onDeleteList = {
                    viewModel.deleteList(group.listId)
                    editingGroup = null
                }
            )
        }

    }

    DeleteConfirmHost(deleteConfirm)

    if (showReminderDialog) {
        ReminderSettingDialog(
            initialDateTime = reminderDateTime,
            initialRecurring = quickAddRecurring,
            onDismiss = { showReminderDialog = false },
            onConfirm = { picked, recurring ->
                reminderDateTime = picked
                reminderEnabled = true
                quickAddRecurring = recurring
                showReminderDialog = false
                reminderSetTip = "${DateUtils.monthDay(picked.toLocalDate())} " +
                    DateUtils.hm(picked.toLocalTime())
            }
        )
    }

    reminderSetTip?.let { timeText ->
        InfoDialog(
            title = stringResource(R.string.reminder_set_title),
            text = stringResource(R.string.reminder_set_message, timeText),
            onDismiss = { reminderSetTip = null }
        )
    }

    editTarget?.let { target ->
        when (target) {
            is EditTarget.Item -> TextEditDialog(
                title = stringResource(R.string.edit_todo),
                initialText = target.schedule.title,
                label = stringResource(R.string.what_to_do),
                onConfirm = {
                    viewModel.updateTitle(target.schedule, it)
                    editTarget = null
                },
                onDismiss = { editTarget = null }
            )
            is EditTarget.ListTitle -> TextEditDialog(
                title = stringResource(R.string.rename_list),
                initialText = target.currentTitle,
                label = stringResource(R.string.group),
                onConfirm = {
                    viewModel.renameList(target.listId, it)
                    editTarget = null
                },
                onDismiss = { editTarget = null }
            )
            is EditTarget.AppendToList -> TextEditDialog(
                title = stringResource(R.string.append_to_list),
                initialText = "",
                label = stringResource(R.string.what_to_do),
                confirmText = stringResource(R.string.add_todo),
                onConfirm = {
                    viewModel.appendToList(target.listId, it)
                    editTarget = null
                },
                onDismiss = { editTarget = null }
            )
        }
    }
}

internal fun submitQuickAdd(
    viewModel: ScheduleViewModel,
    titles: List<String>,
    reminderDateTime: LocalDateTime,
    reminderEnabled: Boolean,
    isRecurring: Boolean,
    context: android.content.Context,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (titles.isEmpty()) {
        Toast.makeText(context, "请填写待办事项", Toast.LENGTH_SHORT).show()
        return
    }
    viewModel.addSchedules(
        titles = titles,
        date = reminderDateTime.toLocalDate(),
        time = reminderDateTime.toLocalTime(),
        reminder = reminderEnabled,
        isRecurring = isRecurring
    )
    if (reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && !NotificationUtils.hasPermission(context)
    ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        Toast.makeText(context, context.getString(R.string.need_permission_toast), Toast.LENGTH_SHORT).show()
    }
}

internal fun itemKey(item: ScheduleListItem): String = when (item) {
    is ScheduleListItem.Single -> "s-${item.schedule.id}"
    is ScheduleListItem.Group -> "g-${item.listId}"
}

internal fun Set<String>.xor(id: String): Set<String> =
    if (contains(id)) minus(id) else plus(id)

internal data class DraftItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String
)

/** 长按后要编辑的对象。 */
internal sealed class EditTarget {
    data class Item(val schedule: Schedule) : EditTarget()
    data class ListTitle(val listId: String, val currentTitle: String) : EditTarget()
    data class AppendToList(val listId: String) : EditTarget()
}

/** 默认提醒时间：当前时间加 1 分钟（秒/纳秒清零）。 */
internal fun defaultReminderTime(): LocalTime = LocalTime.now().plusMinutes(1).withSecond(0).withNano(0)
