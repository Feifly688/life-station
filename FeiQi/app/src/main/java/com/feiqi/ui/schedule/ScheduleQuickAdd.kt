package com.feiqi.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
internal fun QuickAddPanel(
    draftItems: List<DraftItem>,
    currentInput: String,
    listTitle: String,
    reminderEnabled: Boolean,
    reminderDateTime: LocalDateTime,
    recurrence: Recurrence,
    onCurrentInputChange: (String) -> Unit,
    onCommitCurrent: () -> Unit,
    onDraftEdit: (id: String, newText: String) -> Unit,
    onDraftDelete: (id: String) -> Unit,
    onListTitleChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSetReminder: () -> Unit,
    onCancelReminder: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val draftFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    // 删除/退格回跳后需要把光标放到目标行内容的末尾（而非最前面）：该行真正获焦时消费本标记。
    val pendingCursorAtEnd = remember { mutableStateMapOf<String, Boolean>() }
    // 新增待办输入行是否可见：退格回退到上一条待办时收起（避免残留占位提示）。
    var activeInputVisible by remember { mutableStateOf(true) }

    /** 把焦点送到新增待办输入行（并确保软键盘在）。 */
    fun focusActiveInput() {
        activeInputVisible = true
        coroutineScope.launch {
            delay(60)
            focusRequester.safeRequestFocus()
            keyboardController.safeShow()
        }
    }

    /**
     * 把焦点送到某条待办行；[atEnd] 为真时光标落到内容末尾。
     *
     * **同步移交**（不留 60ms 空窗）：目标行此刻**必然已在组合树中**（它就在输入行上方），
     * 且其 FocusRequester 已注册，直接 requestFocus 即可在同一次焦点事务里完成交接。
     * 这样旧输入框失焦与新输入框获焦发生在同一帧 → **软键盘不会先收再弹** → 不会出现
     * 「键盘收起/弹出 + 底部卡片位置抖动」的闪屏（v1.4.0 之前这里是 delay(60) 后再请求，
     * 中间那 60ms 焦点无处安放，正是闪屏的根源）。
     *
     * 仅在极少数情况下（目标行尚未组合、requester 未注册）才退回「延迟 60ms 后再请求」。
     */
    fun focusDraft(id: String, atEnd: Boolean) {
        if (atEnd) pendingCursorAtEnd[id] = true
        val requester = draftFocusRequesters[id]
        if (requester != null) {
            requester.safeRequestFocus()
        } else {
            coroutineScope.launch {
                delay(60)
                draftFocusRequesters[id]?.safeRequestFocus()
            }
        }
    }

    // 清空内容后再次退格即移除该行：删除后光标自动定位到上一条内容的末尾。
    fun deleteDraft(id: String) {
        val idx = draftItems.indexOfFirst { it.id == id }
        onDraftDelete(id)
        // 被删掉的行的待置光标标记要一并清掉（否则万一 id 复用会让光标莫名跳到行尾）。
        pendingCursorAtEnd.remove(id)
        val prevId = if (idx > 0) draftItems.getOrNull(idx - 1)?.id else null
        if (prevId != null) {
            // 删除后光标落到上一条**内容末尾**（与在上一行继续输入的手感一致）。
            focusDraft(prevId, atEnd = true)
        } else {
            // 已删到首条：恢复并显示新增待办输入行，光标落到其上。
            focusActiveInput()
        }
    }

    // 「完成」可用性：当前输入行或任意一条待办有内容才允许提交（空内容时按钮置灰不可点）。
    val canSubmit = currentInput.isNotBlank() || draftItems.any { it.text.isNotBlank() }

    // 已确认的待办达到 2 条及以上 → 本次提交会形成「待办清单」：
    // 面板顶部出现可编辑的清单标题（默认「待办清单」），待办内容默认收起。
    // 标题输入框的显示条件：**本次提交将形成清单**（条目数 ≥ 2）时才出现。
    // 计数把「正在输入的这一条」也算进去——输入行可见时代表用户正准备写第 N+1 条，
    // 因此在「写完第 1 条按回车、开始编辑第 2 条」的那一刻（已确认 1 条 + 输入行可见）
    // 标题框就出现，而不是等到第 2 条也确认之后。
    // 输入行被收起（退格回到上一条继续编辑）时不计入，此时只按已确认条数判断。
    // 注意：面板内的待办内容**始终展开显示**（不存在"添加时收起"）；默认收起的是
    // 添加完成后日程页里的清单卡片（见 ScheduleCards 的 AnimatedVisibility + expandedListIds）。
    val pendingCount = draftItems.size + if (activeInputVisible) 1 else 0
    val showListTitle = pendingCount >= 2

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 将形成清单时才出现：可编辑的清单标题（默认「待办清单」）。
            if (showListTitle) {
                QuickAddListTitleField(
                    title = listTitle,
                    onTitleChange = onListTitleChange
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 待办内容始终展开显示，添加过程中逐条可见可编辑。
                draftItems.forEach { item ->
                    val itemFocusRequester = remember(item.id) { FocusRequester() }
                    DisposableEffect(item.id) {
                        draftFocusRequesters[item.id] = itemFocusRequester
                        onDispose { draftFocusRequesters.remove(item.id) }
                    }
                    DraftItemRow(
                        item = item,
                        onTextChange = { newText -> onDraftEdit(item.id, newText) },
                        onDelete = { deleteDraft(item.id) },
                        // 行内回车 = 该条编辑完成，光标回到下方新增待办输入行继续录入。
                        onFinishEdit = { focusActiveInput() },
                        focusRequester = itemFocusRequester,
                        cursorAtEnd = pendingCursorAtEnd[item.id] == true,
                        onCursorAtEndConsumed = { pendingCursorAtEnd.remove(item.id) }
                    )
                }
                // 输入行**始终保留在组合树中**（不再用 if 条件渲染）：退格回退到上一条时
                // 只是把它的占位文案变透明，卡片高度不变 → 不会出现「卡片先缩一行」的跳变。
                // 配合 focusDraft 的同步焦点移交，IME 全程不收起，回退路径与回车路径一样平滑。
                ActiveInputRow(
                    text = currentInput,
                    placeholderVisible = activeInputVisible,
                    onTextChange = onCurrentInputChange,
                    focusRequester = focusRequester,
                    onCommit = { onCommitCurrent() },
                    onBackspaceOnEmpty = {
                        // 光标回退到上一条已添加待办末尾：收起占位提示（但保留该行占位空间）。
                        draftItems.lastOrNull()?.let { last ->
                            activeInputVisible = false
                            focusDraft(last.id, atEnd = true)
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (reminderEnabled) {
                    ReminderChip(
                        text = buildString {
                            append(DateUtils.friendly(reminderDateTime.toLocalDate()))
                            append(" ")
                            append(DateUtils.hm(reminderDateTime.toLocalTime()))
                            if (recurrence.isRepeating) {
                                append(" · ").append(recurrenceLabel(recurrence))
                            }
                        },
                        onCancel = onCancelReminder
                    )
                } else {
                    TextButton(
                        onClick = onSetReminder,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.set_reminder),
                            color = Primary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            stringResource(R.string.cancel),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    // 内容为空时禁用（置灰且不可点）：只有当前输入行或任一条待办有内容才可提交。
                    TextButton(onClick = onFinish, enabled = canSubmit) {
                        Text(
                            text = stringResource(R.string.finish),
                            color = if (canSubmit) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

}

@Composable
internal fun ActiveInputRow(
    text: String,
    /** 是否显示占位提示。为 false 时**仅把文字设为透明**，占位空间照旧保留 → 布局零变化。 */
    placeholderVisible: Boolean,
    onTextChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onCommit: () -> Unit,
    onBackspaceOnEmpty: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = Outline,
            modifier = Modifier.size(28.dp)
        )
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.key == Key.Backspace && text.isEmpty()) {
                        onBackspaceOnEmpty()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            ),
            decorationBox = { innerTextField ->
                Box {
                    // 始终参与布局（含透明态），避免占位文案的显隐导致行高变化 → 卡片跳动。
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.enter_to_add_todo),
                            color = if (placeholderVisible) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.Transparent
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                        )
                    }
                    innerTextField()
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                // 当前行未输入内容直接回车：触发震动反馈，不新增待办。
                if (text.isBlank()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    onCommit()
                }
            }),
            maxLines = 2
        )
    }
}

/**
 * 清单标题输入框（仅在「本次提交将形成清单」时显示）。
 *
 * 标题默认「待办清单」，添加过程中就能改，不必先建好再进「编辑清单」重命名。
 * 面板内**不含**收起/展开开关——添加时内容始终可见；默认收起的是添加完成后
 * 日程页里的清单卡片。
 */
@Composable
private fun QuickAddListTitleField(
    title: String,
    onTitleChange: (String) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.list_title_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box {
                    // 清空时给回默认名提示（提交时也会回落到默认名，见 submitQuickAdd）。
                    if (title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.default_list_title),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp)
                        )
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 待办行的名称输入。
 *
 * **每一行本身就是输入框**（不再有「静态文本 → 点一下切成输入框」的模式切换）：
 * 添加待办的过程中点哪条就能改哪条的名称，不需要先提交再去修改。
 * - 点击行内任意位置即可把光标落到该行（BasicTextField 原生行为，无需手动请求焦点）；
 * - 内容清空后再退格一次 = 删除该行；
 * - 软键盘回车 = 该行编辑完成（内容已实时回传），焦点回到下方新增待办输入行。
 */
@Composable
internal fun DraftItemRow(
    item: DraftItem,
    onTextChange: (String) -> Unit,
    onDelete: () -> Unit,
    onFinishEdit: () -> Unit,
    focusRequester: FocusRequester,
    cursorAtEnd: Boolean,
    onCursorAtEndConsumed: () -> Unit
) {
    // 行内文本以本地 TextFieldValue 承载（需要光标位置），变更即时回传给上层草稿列表。
    var value by remember(item.id) { mutableStateOf(TextFieldValue(item.text)) }

    // 收到「把光标放到行尾」的请求时执行。
    //
    // 为什么必须放在 LaunchedEffect 里、而不能写在 onFocusChanged 中判断 cursorAtEnd：
    // 焦点移交是**同步**的（为消除闪屏，见 focusDraft），onFocusChanged 会在**重组之前**触发，
    // 此时 modifier 里捕获的 cursorAtEnd 还是上一次组合的旧值（false）→ 光标不会被移到末尾、
    // 标记也不会被消费（v1.4.1 的回归）。LaunchedEffect 在本次组合提交后运行，拿到的必然是新值，
    // 因此顺序无关：无论先获焦还是先置标记，光标都会落到行尾，标记也会被及时消费。
    LaunchedEffect(cursorAtEnd) {
        if (cursorAtEnd) {
            value = TextFieldValue(value.text, TextRange(value.text.length))
            onCursorAtEndConsumed()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = Outline,
            modifier = Modifier.size(28.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = { newVal ->
                value = newVal
                onTextChange(newVal.text)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    // 内容已清空后再退格一次即移除该行（输入法删除，无需确认）。
                    if (event.key == Key.Backspace && value.text.isEmpty()) {
                        onDelete()
                        true
                    } else {
                        false
                    }
                },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                // 内容为空时按回车等同删除该行；否则完成编辑（内容已实时保存）。
                if (value.text.isBlank()) {
                    onDelete()
                } else {
                    onFinishEdit()
                }
            }),
            singleLine = true
        )
    }
}
