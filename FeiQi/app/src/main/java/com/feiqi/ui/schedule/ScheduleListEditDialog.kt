package com.feiqi.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.border
    import androidx.compose.foundation.combinedClickable
    import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.app.Activity
import android.os.Build
import android.view.WindowManager
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

private const val NEW_ITEM_FOCUS_KEY = "__NEW_ITEM_FOCUS__"

/** 清单编辑弹窗：支持重命名、增删改子项、提醒/重复设置与删除清单。 */
@Composable
internal fun ListEditDialog(
    group: ScheduleListItem.Group,
    isCompleted: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        items: List<EditListItem>,
        deletedIds: Set<Long>,
        reminderDateTime: LocalDateTime?,
        reminderEnabled: Boolean,
        recurrence: Recurrence
    ) -> Unit,
    onDeleteList: () -> Unit
) {
    var title by remember { mutableStateOf(group.title) }
    var itemStates by remember(group.items) {
        mutableStateOf(
            group.items.mapIndexed { index, schedule ->
                EditItemState(
                    key = schedule.id.toString(),
                    id = schedule.id,
                    title = schedule.title,
                    completed = schedule.completed,
                    order = index
                )
            }
        )
    }
    var deletedIds by remember { mutableStateOf(setOf<Long>()) }
    var newItemText by remember { mutableStateOf("") }
    var reminderDateTime by remember {
        mutableStateOf(
            // 已设置过提醒则保留原提醒时间；否则默认「当前时间 +1 分钟」，
            // 避免把清单的创建/排期时间当作默认提醒时间（见 Req5）。
            if (group.items.any { it.reminder }) {
                LocalDateTime.of(
                    group.items.firstNotNullOfOrNull { it.date } ?: DateUtils.today(),
                    group.items.firstNotNullOfOrNull { it.time } ?: defaultReminderTime()
                )
            } else {
                LocalDateTime.of(DateUtils.today(), defaultReminderTime())
            }
        )
    }
    var reminderEnabled by remember { mutableStateOf(group.items.any { it.reminder }) }
    var recurrence by remember { mutableStateOf(group.recurrence) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val newItemFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val itemFocusRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    // 列表状态：新增/目标行可能暂未进入视口而尚未组合，先滚入可见区域再请求焦点。
    val listState = rememberLazyListState()
    // 删除/回跳后需要把光标放到目标行内容的末尾（而非最前面）。仅记录「待置末尾光标」的
    // 那一行 key；该行获得焦点时在 onFocusChanged 中把光标移到末尾并清空本变量。
    // 用单一变量 + 同步删除，避免此前「待删标记 + focusEndKey 双状态机」在快速删除时
    // 出现的焦点竞态与光标乱跳。
    // 每个 row 独立记录「获焦时是否需要把光标放到内容末尾」：删除/回跳时把目标 row 标记 true，
    // 该 row 真正获焦后置末尾并消费。比共享 String? 更稳健——快速连续删除多个目标 row 时
    // 各自标记互不覆盖（共享 String? 会被下一次删除覆盖，导致首个目标获焦时不等自身 key，
    // 光标落行首）。
    val pendingCursorAtEnd = remember { mutableStateMapOf<String, Boolean>() }
    var initialFocusDone by remember { mutableStateOf(false) }
    // 新增待办输入行是否可见：退格回退到上一条条目编辑时收起，编辑确认或删到首条后再显示。
    var newItemVisible by remember { mutableStateOf(true) }

    // 确保底部「新增待办输入行」（LazyColumn 末尾项）在视口内并已组合，再请求焦点。
    // 注册见下方 NewItemInputRow 调用处的 DisposableEffect（key = NEW_ITEM_FOCUS_KEY）。
    suspend fun focusNewItemRobust() {
        runCatching {
            listState.scrollToItem(maxOf(0, listState.layoutInfo.totalItemsCount - 1))
        }
        var tries = 0
        while (itemFocusRequesters[NEW_ITEM_FOCUS_KEY] == null && tries < 30) {
            delay(30)
            tries++
        }
        itemFocusRequesters[NEW_ITEM_FOCUS_KEY]?.safeRequestFocus()
        keyboardController.safeShow()
    }

    // 统一的「把焦点送回底部『新增待办输入行』」逻辑（带滚入视口与等待组合，避免长列表
    // 时新增行处于视口外未组合、FocusRequester 未绑定导致焦点丢失/输入法收起）。
    // 关键：用 coroutineScope 延迟 60ms 再请求焦点，避开 IME「完成/回车」释放焦点与
    // Compose 重新分配焦点的时序冲突（直接同步 requestFocus 在部分输入法下会被当作
    // 「已在焦点」而忽略，导致焦点丢失）。这与 QuickAdd 的 onFinishEdit 完全一致。
    val focusNewItemRow: () -> Unit = {
        if (!isCompleted) {
            newItemVisible = true
            coroutineScope.launch {
                delay(60)
                focusNewItemRobust()
            }
        }
    }

    // 统一的「保存并退出」逻辑：校验并落库当前编辑结果，然后关闭弹窗。
    // 点击空白区域、系统返回键、完成按钮均走此路径，实现「跳出编辑自动保存」。
    // 若清单内容（待办）已全部清空，则点击空白区域即自动删除该清单，而非保存空清单。
    val saveAndDismiss: () -> Unit = {
        val validItems = itemStates
            .filter { it.id !in deletedIds }
            .filter { it.title.trim().isNotEmpty() }
            .mapIndexed { index, state ->
                EditListItem(
                    id = state.id,
                    title = state.title.trim(),
                    completed = state.completed
                )
            }
        if (validItems.isNotEmpty()) {
            onSave(
                title,
                validItems,
                deletedIds,
                if (reminderEnabled) reminderDateTime else null,
                reminderEnabled,
                recurrence
            )
        } else {
            // 内容已清空：自动删除该清单（不再保存一个空清单）。
            onDeleteList()
        }
    }

    // 首次进入弹窗：把焦点送到底部「新增待办输入行」并弹出输入法（仅执行一次）。
    // 删除/回跳的焦点转移已改为在各操作处理函数内「同步」完成（见 onDelete /
    // onBackspaceOnEmpty），不再依赖本副作用，避免快速操作时焦点竞态。
    LaunchedEffect(Unit) {
        if (!initialFocusDone && !isCompleted) {
            delay(60)
            focusNewItemRobust()
            initialFocusDone = true
        }
    }

    // 编辑清单弹窗为「屏幕内普通浮层」（不再使用 Compose Dialog 独立窗口），与新增待办窗口
    // （QuickAddPanel）同机制。**关键**：遮罩必须覆盖整个屏幕（含键盘顶之下的区域），
    // 否则卡片下方到键盘顶之间会露出页面背景形成浅色空隙。imePadding 放到「卡片的包裹 Box」
    // 上（而非外层容器），让卡片单独被抬到键盘顶，卡片底到屏幕底之间的 imePadding 区域
    // 透明 → 露出下方深色遮罩，从而整个屏幕（含键盘顶之下）都是深色，浅色空隙消失。
    // 本工程 Compose BOM 下 adjustResize 不会可靠触发窗口重算，故用 imePadding 抬升；
    // 弹窗显示期间切到 adjustNothing（见下）避免 adjustResize 偶发 resize 造成双抬升空隙。
    // 返回键由 BackHandler 接管 = 保存并退出。
    Box(modifier = Modifier.fillMaxSize()) {
        // 返回键 = 保存并退出。
        BackHandler(enabled = true) { saveAndDismiss() }
        // 弹窗显示期间把窗口 softInputMode 切到 adjustNothing（避免 adjustResize 偶发 resize），
        // 退出时还原。
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.context as? Activity)?.window
            val previousMode = window?.attributes?.softInputMode
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            onDispose {
                if (previousMode != null) window.setSoftInputMode(previousMode)
            }
        }
        // 屏幕高度（dp）：直接读 LocalConfiguration，不依赖 BoxWithConstraints（后者受 imePadding
        // 缩窄影响）。maxPanelHeight/listMaxHeight 仍按「屏高-留白」算，与 QuickAddPanel 一致。
        val configuration = LocalConfiguration.current
        val screenHeightDp = configuration.screenHeightDp.dp
        val maxPanelHeight = (screenHeightDp - 24.dp).coerceAtLeast(160.dp)
        val listMaxHeight = (screenHeightDp * 0.42f)
            .coerceAtMost((maxPanelHeight - 140.dp).coerceAtLeast(80.dp))
        val scrimInteractionSource = remember { MutableInteractionSource() }
        // 自定义遮罩：覆盖整个屏幕（含键盘顶之下的区域，确保卡片下方的 imePadding 区域也是深色）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = scrimInteractionSource,
                    indication = null
                ) { saveAndDismiss() }
        )
        // 卡片包装 Box：align(BottomCenter) + imePadding() → 卡片底 = 屏幕-键盘高度；
        // 卡片底到屏幕底之间的 imePadding 区域透明，露出外层遮罩（深色），消除浅色空隙。
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        ) {
            // 浮动卡片面板：满宽（两侧留 8dp 间隙，与 QuickAddPanel 保持一致）、内容自适应高度。
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    // 吸收面板内部空白区域的点击，避免穿透到下方遮罩触发误保存；
                    // 子组件（输入框、按钮等）的点击仍由各自消费，互不干扰。
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxPanelHeight.coerceAtLeast(160.dp))
                        .padding(16.dp)
                ) {
                // 清单名称（标题）
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.list_name_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        // weight(fill=false)：列表占据标题与底部操作栏之间的「剩余空间」，
                        // 但不超过自身内容高度（fill=false 保证待办少时面板依旧紧凑、不撑满屏）；
                        // 当键盘升起导致可用高度变小时，列表被压缩到剩余空间内并内部滚动，
                        // 从而底部「取消/完成」操作栏始终可见、不被裁切。
                        .weight(1f, fill = false)
                        .heightIn(max = listMaxHeight),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                items(itemStates, key = { it.key }) { state ->
                    val itemFocusRequester = remember(state.key) { FocusRequester() }
                    DisposableEffect(state.key) {
                        itemFocusRequesters[state.key] = itemFocusRequester
                        onDispose { itemFocusRequesters.remove(state.key) }
                    }
                    EditableItemRow(
                        state = state,
                        focusRequester = itemFocusRequester,
                        onTitleChange = { newText ->
                            itemStates = itemStates.map {
                                if (it.key == state.key) it.copy(title = newText) else it
                            }
                        },
                        onToggleComplete = {
                            itemStates = itemStates.map {
                                if (it.key == state.key) {
                                    it.copy(completed = !it.completed)
                                } else it
                            }
                        },
                        onDelete = {
                            val idx = itemStates.indexOf(state)
                            val remaining = itemStates.filter { it.key != state.key }
                            // 目标焦点：聚焦「删除后仍存在（将保留）的那一行」，优先上一条，
                            // 否则（删首条）为删除后剩下的首条；若删除后清单为空且可新增，
                            // 则回到底部「新增待办输入行」（其 key 固定为 NEW_ITEM_FOCUS_KEY）。
                            // 必须用「删除后的剩余列表」来算目标，避免误把待删行自身当目标。
                            val targetKey = if (remaining.isNotEmpty()) {
                                remaining.getOrNull(maxOf(0, idx - 1))?.key
                                    ?: remaining.first().key
                            } else if (!isCompleted) {
                                NEW_ITEM_FOCUS_KEY
                            } else {
                                null
                            }
                            // 立即记入 deletedIds，保证若中途保存（点空白/返回）也不会把待删项误存。
                            state.id?.let { deletedIds = deletedIds + it }
                            // 同步移除当前行 + 把焦点转移到目标行：当前行（正聚焦）被移除的瞬间，
                            // 目标行（相邻可见、恒已组合）已提前被 requestFocus → 焦点无空窗，
                            // 输入法不收起；光标随后在 onFocusChanged 中落到目标行末尾。
                            // 删除「相邻可见行」目标恒已组合，requestFocus 同步生效，无多帧空窗
                            // （此前 flicker 源于目标在视口外未组合导致 requestFocus 失效）。
                            itemStates = remaining
                            when (targetKey) {
                                NEW_ITEM_FOCUS_KEY -> {
                                    // 清单删空：聚焦底部新增输入行（可能暂未组合，走稳健路径）。
                                    newItemVisible = true
                                    coroutineScope.launch { focusNewItemRobust() }
                                }
                                null -> {
                                    // 已完成清单删空：无新增行，焦点随移除自然释放。
                                }
                                else -> {
                                    // 把目标 row 标记为「获焦时把光标置末尾」+ 同步抢焦点 + safeShow。
                                    // 该 row 真正获焦后 onFocusChanged 会消费此标记，置 cursor 到末尾。
                                    pendingCursorAtEnd[targetKey] = true
                                    itemFocusRequesters[targetKey]?.safeRequestFocus()
                                    keyboardController.safeShow()
                                }
                            }
                        },
                        cursorAtEnd = pendingCursorAtEnd[state.key] == true,
                        onCursorAtEndConsumed = { pendingCursorAtEnd.remove(state.key) },
                        onConfirmDone = {
                            // 编辑确认后：光标跳回底部「新增待办输入行」开头，可继续输入下一条。
                            focusNewItemRow()
                        }
                    )
                }
                item {
                    if (isCompleted) {
                        // 已完成清单：仅可编辑已有待办，不再提供新增待办输入框。
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.completed_list_no_add_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (newItemVisible) {
                        // 把「新增待办输入行」的 FocusRequester 注册到 itemFocusRequesters，
                        // 供 focusNewItemRobust() 在滚入视口后等待其组合并请求焦点（长列表场景）。
                        DisposableEffect(newItemFocusRequester) {
                            itemFocusRequesters[NEW_ITEM_FOCUS_KEY] = newItemFocusRequester
                            onDispose { itemFocusRequesters.remove(NEW_ITEM_FOCUS_KEY) }
                        }
                        NewItemInputRow(
                            text = newItemText,
                            onTextChange = { newItemText = it },
                            onCommit = {
                                val newKey = UUID.randomUUID().toString()
                                itemStates = itemStates + EditItemState(
                                    key = newKey,
                                    title = newItemText.trim()
                                )
                                newItemText = ""
                                // 新增待办后：焦点保持在底部「新增输入行」开头（红圈位置），
                                // 让用户可以继续输入下一条待办。改用延迟请求焦点，避开 IME
                                // 「完成」释放焦点的时序冲突（与 QuickAdd 一致）。
                                focusNewItemRow()
                            },
                            onBackspaceOnEmpty = {
                                // 光标回退到上一条条目末尾（上一条恒已组合、相邻可见）。
                                // **关键顺序**：先把焦点同步抢到上一条（pendingCursorAtEnd 标记 +
                                // requestFocus + safeShow），**再**收起本行 newItemVisible=false。
                                // 若先收起再聚焦，NewItemInputRow 移除瞬间焦点落空、键盘短暂收起
                                // 再弹出（闪烁）；先抢焦点则 last 已获焦、键盘常驻，再收起本行
                                // 焦点无空窗、键盘不闪烁。
                                itemStates.lastOrNull()?.let { last ->
                                    pendingCursorAtEnd[last.key] = true
                                    itemFocusRequesters[last.key]?.safeRequestFocus()
                                    keyboardController.safeShow()
                                    newItemVisible = false
                                }
                            },
                            focusRequester = newItemFocusRequester
                        )
                    }
                }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 底部操作栏：布局与「新增待办清单窗口」(QuickAddPanel) 一致——
                // 提醒/重复在左，取消/完成在右。
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
                                // 删除提醒后，默认提醒时间重置为「当前系统时间 +1 分钟」，
                                // 使下次再次设置提醒时默认显示 now+1min，而非沿用已删除的旧时间。
                                reminderDateTime = LocalDateTime.now()
                                    .withSecond(0)
                                    .withNano(0)
                                    .plusMinutes(1)
                            }
                        )
                    } else {
                        TextButton(
                            onClick = { showReminderDialog = true },
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
            onConfirm = { dateTime, picked ->
                reminderDateTime = dateTime
                reminderEnabled = true
                recurrence = picked
                showReminderDialog = false
            }
        )
    }
}

@Composable
internal fun EditableItemRow(
    state: EditItemState,
    focusRequester: FocusRequester,
    onTitleChange: (String) -> Unit,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    cursorAtEnd: Boolean,
    onCursorAtEndConsumed: () -> Unit,
    onConfirmDone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // 仅以 key 作为 remember 键，避免每次按键因 state.title 变化而重置光标位置。
    var text by remember(state.key) { mutableStateOf(TextFieldValue(state.title)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 固定宽度的勾选框容器，确保编辑/非编辑状态对齐一致。
        // 编辑态点击勾选框：触发震动反馈，并真正切换该条目的完成态（L2 修复）。
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = state.completed,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleComplete()
                },
                enabled = true,
                colors = CheckboxDefaults.colors(
                    checkedColor = Primary,
                    uncheckedColor = Outline
                )
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { newVal ->
                text = newVal
                onTitleChange(newVal.text)
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { fs ->
                    // 删除/回跳后进入此行时，把光标放到内容末尾而非最前面。
                    // 由父级 pendingCursorAtEnd map 标记（row 独立），该 row 真正获焦时消费。
                    if (fs.isFocused && cursorAtEnd) {
                        text = text.copy(selection = TextRange(text.text.length))
                        onCursorAtEndConsumed()
                    }
                }
                .onKeyEvent { event ->
                    when {
                        // 内容清空后再退格一次即移除该行（输入法删除，无需确认）。
                        event.key == Key.Backspace && text.text.isEmpty() -> {
                            onDelete()
                            true
                        }
                        // 回车 = 确认本行并跳到「新增待办输入行」继续输入。
                        // 仅拦截硬件键盘回车（软键盘回车走 keyboardActions.onDone，见下）。
                        // 仅在按键按下时响应，避免按下/抬起重复触发。
                        event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                                && event.key == Key.Enter -> {
                            onConfirmDone()
                            true
                        }
                        else -> false
                    }
                },
            // imeAction=Done：软键盘回车键（显示「完成」）会触发 keyboardActions.onDone，
            // 从而正确新增待办项/确认；不会自动收起键盘（我们不调用 hide），且 onDone 内会
            // 把焦点转移到另一条输入框（见 onConfirmDone/focusNewItemRow），输入法保持常驻。
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirmDone() }),
            textStyle = TextStyle(
                color = if (state.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                textDecoration = if (state.completed) TextDecoration.LineThrough else null
            ),
            singleLine = true
        )
    }
}

@Composable
internal fun NewItemInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    onBackspaceOnEmpty: () -> Unit,
    focusRequester: FocusRequester
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 与已添加待办的勾选框保持完全一致（大小 40dp 容器 + 同款 Checkbox），
        // 点击仅触发震动反馈，新条目本身无完成态可切换。
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Checkbox(
                checked = false,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                enabled = true,
                colors = CheckboxDefaults.colors(
                    checkedColor = Primary,
                    uncheckedColor = Outline
                )
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    when {
                        event.key == Key.Backspace && text.isEmpty() -> {
                            onBackspaceOnEmpty()
                            true
                        }
                        // 回车 = 确认新增待办（非空时提交，空时仅震动反馈）。
                        // 仅拦截硬件键盘回车（软键盘回车走 keyboardActions.onDone，见下）。
                        // 仅在按键按下时响应，避免按下/抬起重复触发。
                        event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN
                                && event.key == Key.Enter -> {
                            if (text.isBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                onCommit()
                            }
                            true
                        }
                        else -> false
                    }
                },
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyLarge.fontSize
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            text = stringResource(R.string.enter_to_add_todo),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    innerTextField()
                }
            },
            // imeAction=Done：软键盘回车键（显示「完成」）触发 keyboardActions.onDone，
            // 非空时提交为一条新待办并聚焦回新增输入行（输入法保持常驻，不收起）。
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                // 当前行未输入内容直接回车：触发震动反馈，不新增待办。
                if (text.isBlank()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                } else {
                    onCommit()
                }
            }),
            singleLine = true
        )
    }
}

internal data class EditItemState(
    val key: String,
    val id: Long? = null,
    val title: String = "",
    val completed: Boolean = false,
    val order: Int = 0
)
