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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
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
import com.feiqi.data.model.Recurrence
import com.feiqi.data.model.Schedule
import com.feiqi.data.model.ScheduleListItem
import com.feiqi.data.model.ScheduleListRules
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
import com.feiqi.ui.theme.OnSurfaceVariant
import com.feiqi.ui.theme.Outline
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.SurfaceVariant
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
    var quickAddRecurrence by remember { mutableStateOf(Recurrence.NONE) }
    // 清单标题：仅当本次添加 ≥2 条（形成清单）时显示并生效，默认「待办清单」。
    var quickAddListTitle by remember { mutableStateOf("待办清单") }
    var showReminderDialog by remember { mutableStateOf(false) }
    var reminderSetTip by remember { mutableStateOf<String?>(null) }

    // 记录**被用户主动收起**的清单 id —— 用「收起集合」而非「展开集合」，
    // 这样初始空集合天然等于「全部展开」：进入待办页第一帧就是展开态，
    // 不会出现「先渲染成收起、再播放展开动画」的肉眼可见过程。
    var collapsedListIds by remember { mutableStateOf(setOf<String>()) }
    // 已完成区里的清单卡片采用**相反**的默认值：默认收起，只记录「被用户展开」的清单。
    // 这样「已完成」区块本身展开（一眼看到完成了哪些清单），但不会把每张清单的条目全部铺开。
    var expandedDoneListIds by remember { mutableStateOf(setOf<String>()) }
    // 「已完成」区块默认展开（点标题可收起）。
    var completedExpanded by remember { mutableStateOf(true) }
    // 待办搜索（按待办名 / 清单名模糊匹配）
    var searchQuery by remember { mutableStateOf("") }
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

    // 待办区 / 已完成区的划分规则收敛在 ScheduleListRules（纯函数 + 单测覆盖），
    // 这里只负责取值与渲染，规则调整不再需要改动本文件。
    val today = DateUtils.today()
    val searchKeyword = searchQuery.trim()
    val searching = searchKeyword.isNotEmpty()
    // 搜索：按待办名称或清单名称模糊匹配（忽略大小写）；命中后仍按"待办区/已完成区"分区展示
    val visibleItems = remember(uiState.listItems, searchKeyword) {
        if (searchKeyword.isEmpty()) {
            uiState.listItems
        } else {
            uiState.listItems.filter { it.matchesKeyword(searchKeyword) }
        }
    }
    val (activeItems, completedItems) = ScheduleListRules.partition(visibleItems, today)

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
                Column {
                    Text(
                        text = stringResource(R.string.schedule_overview),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    ScheduleSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClear = { searchQuery = "" }
                    )
                }
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
                        if (searching) {
                            EmptyState(
                                title = stringResource(R.string.schedule_search_empty_title),
                                description = stringResource(R.string.schedule_search_empty_desc)
                            )
                        } else {
                            EmptyState(
                                title = stringResource(R.string.schedule_empty_title),
                                description = stringResource(R.string.schedule_empty_desc)
                            )
                        }
                    }
                } else {
                    items(activeItems, key = { itemKey(it) }) { item ->
                        ScheduleListItemCard(
                            item = item,
                            expanded = item is ScheduleListItem.Group && (searching || item.listId !in collapsedListIds),
                            selectionMode = selectionMode,
                            selected = isSelected(item),
                            isCompleted = false,
                            onToggleGroup = { listId ->
                                if (!selectionMode) {
                                    collapsedListIds = collapsedListIds.xor(listId)
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
                                // 已完成区：清单卡片默认收起，点标题才展开。
                                expanded = item is ScheduleListItem.Group && (searching || item.listId in expandedDoneListIds),
                                selectionMode = selectionMode,
                                selected = isSelected(item),
                                // 逾期未完成的清单也在已完成区，此时不应被当成"已完成"（否则标题划删除线、子项勾选框被禁用）
                                isCompleted = item is ScheduleListItem.Group && item.allCompleted,
                                onToggleGroup = { listId ->
                                    if (!selectionMode) {
                                        expandedDoneListIds = expandedDoneListIds.xor(listId)
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
                                        // 取消勾选「逾期清单」时自动展开，便于继续查看/编辑（仅逾期清单，不影响其他）
                                        if (!completed && item is ScheduleListItem.Group &&
                                            item.items.any { it.isDeadlineOverdue(today) }
                                        ) {
                                            if (listId !in expandedDoneListIds) {
                                                expandedDoneListIds = expandedDoneListIds + listId
                                            }
                                        }
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
                    listTitle = quickAddListTitle,
                    reminderEnabled = reminderEnabled,
                    reminderDateTime = reminderDateTime,
                    recurrence = quickAddRecurrence,
                    onCurrentInputChange = { currentInput = it },
                    onListTitleChange = { quickAddListTitle = it },
                    onCommitCurrent = {
                        // 空内容不入草稿行（避免留下一个空输入框）；与「完成」按钮的可用性判断一致。
                        val text = currentInput.trim()
                        if (text.isNotEmpty()) {
                            draftItems = draftItems + DraftItem(text = text)
                            currentInput = ""
                        }
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
                        val submitted = submitQuickAdd(
                            viewModel = viewModel,
                            titles = finalDrafts.map { it.text },
                            listTitle = quickAddListTitle,
                            reminderDateTime = reminderDateTime,
                            reminderEnabled = reminderEnabled,
                            recurrence = quickAddRecurrence,
                            context = context,
                            notificationPermissionLauncher = notificationPermissionLauncher
                        )
                        // 内容为空时**保持面板打开**（已提示「请填写待办事项」），让用户继续输入。
                        if (submitted) {
                            draftItems = emptyList()
                            currentInput = ""
                            isQuickAddActive = false
                        }
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
                        // 每次重新打开都复位：默认不启用提醒（即无时间待办）、默认不重复、
                        // 清单标题回到默认「待办清单」、提醒时间=当前+1分钟
                        reminderDateTime = LocalDateTime.of(DateUtils.today(), defaultReminderTime())
                        reminderEnabled = false
                        quickAddRecurrence = Recurrence.NONE
                        quickAddListTitle = "待办清单"
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
                onSave = { title, items, deletedIds, reminderDateTime, reminderEnabled, recurrence ->
                    viewModel.saveListEdit(
                        listId = group.listId,
                        newTitle = title,
                        items = items,
                        deletedIds = deletedIds,
                        reminderDateTime = reminderDateTime,
                        reminderEnabled = reminderEnabled,
                        recurrence = recurrence
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
            initialRecurrence = quickAddRecurrence,
            onDismiss = { showReminderDialog = false },
            onConfirm = { picked, recurrence ->
                reminderDateTime = picked
                reminderEnabled = true
                quickAddRecurrence = recurrence
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
            is EditTarget.Item -> if (target.schedule.listId == null) {
                // 单条待办：标题 + 提醒（未设置时显示「设置提醒」按钮，已设置时显示提醒芯片）。
                ItemEditDialog(
                    schedule = target.schedule,
                    onDismiss = { editTarget = null },
                    onSave = { newTitle, reminderDateTime, reminderEnabled, recurrence ->
                        viewModel.saveItemEdit(
                            schedule = target.schedule,
                            newTitle = newTitle,
                            reminderDateTime = reminderDateTime,
                            reminderEnabled = reminderEnabled,
                            recurrence = recurrence
                        )
                        editTarget = null
                    }
                )
            } else {
                // 清单条目：提醒属于整份清单，这里只改标题（清单的提醒/重复走点击清单卡片）。
                TextEditDialog(
                    title = stringResource(R.string.edit_todo),
                    initialText = target.schedule.title,
                    label = stringResource(R.string.what_to_do),
                    onConfirm = {
                        viewModel.updateTitle(target.schedule, it)
                        editTarget = null
                    },
                    onDismiss = { editTarget = null }
                )
            }
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

/**
 * 提交快速添加。
 *
 * [listTitle] 只在提交 ≥2 条（形成清单）时生效；单条仍是「内容即标题」，与原有行为一致。
 *
 * @return 是否已成功加入日程；false 表示内容为空（已弹提示），调用方**不应关闭**添加面板。
 */
internal fun submitQuickAdd(
    viewModel: ScheduleViewModel,
    titles: List<String>,
    listTitle: String,
    reminderDateTime: LocalDateTime,
    reminderEnabled: Boolean,
    recurrence: Recurrence,
    context: android.content.Context,
    notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
): Boolean {
    if (titles.isEmpty()) {
        Toast.makeText(context, "请填写待办事项", Toast.LENGTH_SHORT).show()
        return false
    }
    viewModel.addSchedules(
        titles = titles,
        date = reminderDateTime.toLocalDate(),
        // 未主动设置提醒 → 无时间状态：不写入时间，系统不判逾期，由用户手动完成或删除。
        time = if (reminderEnabled) reminderDateTime.toLocalTime() else null,
        reminder = reminderEnabled,
        recurrence = if (reminderEnabled) recurrence else Recurrence.NONE,
        // 用户把标题清空时回落到默认「待办清单」，不产生无名清单。
        listTitle = listTitle.trim().ifBlank { "待办清单" }
    )
    if (reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && !NotificationUtils.hasPermission(context)
    ) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        Toast.makeText(context, context.getString(R.string.need_permission_toast), Toast.LENGTH_SHORT).show()
    }
    return true
}

/**
 * 监听"用户主动按下这一行"（**Initial 阶段**，早于输入框自身消费事件）。
 *
 * 用途：区分「用户点击定位光标」与「程序化移交焦点」——
 * 前者要保留用户点到的位置，后者一律把光标送到行尾。
 */
internal fun Modifier.observeUserTap(tapped: androidx.compose.runtime.MutableState<Boolean>): Modifier =
    this.pointerInput(tapped) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                    tapped.value = true
                }
            }
        }
    }

/** 搜索匹配：待办标题或清单标题包含关键字（忽略大小写）。 */
private fun ScheduleListItem.matchesKeyword(keyword: String): Boolean = when (this) {
    is ScheduleListItem.Single -> schedule.title.contains(keyword, ignoreCase = true)
    is ScheduleListItem.Group -> title.contains(keyword, ignoreCase = true) ||
        items.any { it.title.contains(keyword, ignoreCase = true) }
}

/** 顶部待办搜索框：圆角浅底 + 放大镜 + 清空按钮（与应用其它输入框同一套视觉）。 */
@Composable
private fun ScheduleSearchBar(query: String, onQueryChange: (String) -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(Primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.schedule_search_clear),
                tint = OnSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClear)
            )
        }
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
