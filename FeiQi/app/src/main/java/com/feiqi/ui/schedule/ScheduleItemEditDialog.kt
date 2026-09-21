package com.feiqi.ui.schedule

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.feiqi.R
import com.feiqi.data.model.Recurrence
import com.feiqi.data.model.Schedule
import com.feiqi.ui.components.ReminderSettingDialog
import com.feiqi.ui.components.recurrenceLabel
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 单条日程 / 待办的编辑浮层（点击卡片正文进入）。
 *
 * 与「新增待办」(QuickAddPanel)、「编辑清单」(ListEditDialog) 完全同机制：屏幕内普通浮层 +
 * 全屏遮罩 + 底部对齐卡片 + imePadding 抬升（**不用 Compose Dialog**，见项目 IME 铁律），
 * 保证键盘顶之下无浅色空隙、卡片不被裁切。
 *
 * 提醒行的显示规则（修复点）：
 * - **未设置提醒** → 显示「+ 设置提醒」按钮（点击打开提醒设置弹窗，可设时间 + 重复）；
 * - **已设置提醒** → 显示提醒芯片（日期 + 时间 + 重复规则），点芯片上的 × 可取消提醒；
 * 取消后回到「设置提醒」按钮状态，两种状态可反复切换。
 */
@Composable
internal fun ItemEditDialog(
    schedule: Schedule,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        reminderDateTime: LocalDateTime?,
        reminderEnabled: Boolean,
        recurrence: Recurrence
    ) -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }

    var title by remember { mutableStateOf(schedule.title) }
    var reminderEnabled by remember { mutableStateOf(schedule.reminder) }
    var recurrence by remember { mutableStateOf(schedule.recurrence) }
    var reminderDateTime by remember {
        mutableStateOf(
            LocalDateTime.of(schedule.date, schedule.time ?: defaultReminderTime())
        )
    }
    var showReminderDialog by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }

    // 保存并退出：标题为空时只提示、**不关闭**（与新增待办面板的空内容行为一致）。
    val saveAndDismiss: () -> Unit = {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.schedule_title_required), Toast.LENGTH_SHORT).show()
        } else {
            onSave(
                trimmed,
                if (reminderEnabled) reminderDateTime else null,
                reminderEnabled,
                recurrence
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 返回键 = 保存并退出。
        BackHandler(enabled = true) { saveAndDismiss() }

        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.context as? Activity)?.window
            val previousMode = window?.attributes?.softInputMode
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose {
                if (previousMode != null) window.setSoftInputMode(previousMode)
            }
        }

        // 全屏遮罩（含键盘顶之下区域），点击 = 保存并退出。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { saveAndDismiss() }
        )

        // 卡片包装：align(BottomCenter) + imePadding() → 卡片底 = 键盘顶，其下为深色遮罩。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    // 吸收卡片内空白点击，避免穿透到遮罩误触发保存。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.edit_todo),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.what_to_do)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        // 软键盘「完成」= 保存并退出，不额外隐藏键盘（由退出流程统一处理）。
                        keyboardActions = KeyboardActions(onDone = { saveAndDismiss() })
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 底部操作栏：布局与 QuickAddPanel / ListEditDialog 一致——提醒在左，取消/完成在右。
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
                                onCancel = {
                                    reminderEnabled = false
                                    // 取消提醒后回到「当前时间 +1 分钟」，避免沿用已废弃的旧时间。
                                    reminderDateTime = LocalDateTime.now()
                                        .withSecond(0)
                                        .withNano(0)
                                        .plusMinutes(1)
                                }
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    // 打开提醒弹窗前收起键盘：提醒弹窗是独立 Dialog 窗口，
                                    // 键盘不收起会挤压其可视区域。
                                    keyboardController?.hide()
                                    showReminderDialog = true
                                },
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.set_reminder),
                                    color = Primary
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(onClick = saveAndDismiss) {
                                Text(
                                    text = stringResource(R.string.finish),
                                    color = Primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReminderDialog) {
        ReminderSettingDialog(
            initialDateTime = reminderDateTime,
            initialRecurrence = recurrence,
            onDismiss = { showReminderDialog = false },
            onConfirm = { picked, pickedRecurrence ->
                reminderDateTime = picked
                reminderEnabled = true
                recurrence = pickedRecurrence
                showReminderDialog = false
            }
        )
    }

    // 首次进入把焦点送到标题输入框并弹出键盘（与其他编辑浮层一致）。
    LaunchedEffect(Unit) {
        if (!initialFocusDone) {
            delay(60)
            titleFocusRequester.safeRequestFocus()
            keyboardController.safeShow()
            initialFocusDone = true
        }
    }
}
