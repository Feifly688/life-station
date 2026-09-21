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

@Composable
internal fun QuickAddPanel(
    draftItems: List<DraftItem>,
    currentInput: String,
    reminderEnabled: Boolean,
    reminderDateTime: LocalDateTime,
    onCurrentInputChange: (String) -> Unit,
    onCommitCurrent: () -> Unit,
    onDraftEdit: (id: String, newText: String) -> Unit,
    onDraftDelete: (id: String) -> Unit,
    focusRequester: FocusRequester,
    onSetReminder: () -> Unit,
    onCancelReminder: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val draftFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    // 编辑态状态提升到此处，便于在删除/回跳时把光标精确放到上一条内容的末尾。
    var editingDraftId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf(TextFieldValue("")) }
    // 新增待办输入行是否可见：退格回退到上一条待办编辑时收起（避免残留占位提示），
    // 编辑完成（回车确认）或删到首条后再显示。
    var activeInputVisible by remember { mutableStateOf(true) }

    fun startEdit(id: String, atEnd: Boolean) {
        val draft = draftItems.find { it.id == id } ?: return
        editingText = if (atEnd) {
            TextFieldValue(draft.text, selection = TextRange(draft.text.length))
        } else {
            TextFieldValue(draft.text)
        }
        editingDraftId = id
        // 进入编辑态后请求焦点：atEnd=true 时光标落在内容末尾，否则在前端。
        coroutineScope.launch {
            delay(60)
            draftFocusRequesters[id]?.safeRequestFocus()
        }
    }

    // 清空内容后再次退格即移除该行：删除后光标自动定位到上一条内容的末尾。
    fun deleteDraft(id: String) {
        val idx = draftItems.indexOfFirst { it.id == id }
        onDraftDelete(id)
        val prevId = if (idx > 0) draftItems.getOrNull(idx - 1)?.id else null
        editingDraftId = null
        editingText = TextFieldValue("")
        if (prevId != null) {
            startEdit(prevId, atEnd = true)
        } else {
            // 已删到首条：恢复并显示新增待办输入行，光标落到其上。
            activeInputVisible = true
            coroutineScope.launch {
                delay(60)
                focusRequester.safeRequestFocus()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                draftItems.forEach { item ->
                    val itemFocusRequester = remember(item.id) { FocusRequester() }
                    DisposableEffect(item.id) {
                        draftFocusRequesters[item.id] = itemFocusRequester
                        onDispose { draftFocusRequesters.remove(item.id) }
                    }
                    val isEditing = editingDraftId == item.id
                    DraftItemRow(
                        item = item,
                        isEditing = isEditing,
                        editingText = if (isEditing) editingText else TextFieldValue(item.text),
                        onEditingTextChange = { newVal ->
                            editingText = newVal
                            onDraftEdit(item.id, newVal.text)
                        },
                        onStartEdit = { startEdit(item.id, atEnd = false) },
                        onFinishEdit = {
                            // 编辑确认后：退出编辑态，并让光标落到下方的新增待办输入行。
                            editingDraftId = null
                            editingText = TextFieldValue("")
                            activeInputVisible = true
                            coroutineScope.launch {
                                delay(60)
                                focusRequester.safeRequestFocus()
                            }
                        },
                        onDelete = { deleteDraft(item.id) },
                        focusRequester = itemFocusRequester
                    )
                }
                if (activeInputVisible) {
                    ActiveInputRow(
                        text = currentInput,
                        onTextChange = onCurrentInputChange,
                        focusRequester = focusRequester,
                        onCommit = { onCommitCurrent() },
                        onBackspaceOnEmpty = {
                            // 光标回退到上一条已添加待办末尾时，收起本行（不再残留占位提示）。
                            draftItems.lastOrNull()?.let { last ->
                                activeInputVisible = false
                                startEdit(last.id, atEnd = true)
                            }
                        }
                    )
                }
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
                    TextButton(onClick = onFinish) {
                        Text(
                            text = stringResource(R.string.finish),
                            color = Primary,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(draftItems.size) {
        if (draftItems.isNotEmpty()) {
            val last = draftItems.last()
            // 仅当最新一条处于编辑态时才聚焦：否则其 FocusRequester 未绑定到可聚焦节点，
            // 直接 requestFocus() 会抛 IllegalStateException（小窗/多窗口下更易触发）导致闪退。
            if (editingDraftId == last.id) {
                delay(60)
                draftFocusRequesters[last.id]?.safeRequestFocus()
            }
        }
    }
}

@Composable
internal fun ActiveInputRow(
    text: String,
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
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.enter_to_add_todo),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
internal fun DraftItemRow(
    item: DraftItem,
    isEditing: Boolean,
    editingText: TextFieldValue,
    onEditingTextChange: (TextFieldValue) -> Unit,
    onStartEdit: () -> Unit,
    onFinishEdit: () -> Unit,
    onDelete: () -> Unit,
    focusRequester: FocusRequester
) {
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
        if (isEditing) {
            BasicTextField(
                value = editingText,
                onValueChange = { newVal -> onEditingTextChange(newVal) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        // 内容已清空后再退格一次即移除该行（输入法删除，无需确认）。
                        if (event.key == Key.Backspace && editingText.text.isEmpty()) {
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
                    if (editingText.text.isBlank()) {
                        onDelete()
                    } else {
                        onFinishEdit()
                    }
                }),
                singleLine = true
            )
        } else {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onStartEdit() },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
