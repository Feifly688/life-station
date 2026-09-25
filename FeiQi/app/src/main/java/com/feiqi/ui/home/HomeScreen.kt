package com.feiqi.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.HomeCard
import com.feiqi.data.model.Media
import com.feiqi.data.model.MediaType
import com.feiqi.data.model.Schedule
import com.feiqi.data.model.ScheduleListItem
import com.feiqi.data.model.ShoppingItem
import com.feiqi.ui.components.AccountListItem
import com.feiqi.ui.components.DeleteConfirmHost
import com.feiqi.ui.components.EmptyState
import com.feiqi.ui.components.GlassSurface
import com.feiqi.ui.components.RecordFormDialog
import com.feiqi.ui.components.rememberDeleteConfirm
import com.feiqi.ui.media.CoverPreviewDialog
import com.feiqi.ui.theme.CardAmber
import com.feiqi.ui.theme.FeiQiSpacing
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.GlassStrength
import com.feiqi.ui.theme.CardGreen
import com.feiqi.ui.theme.CardRed
import com.feiqi.ui.theme.ExpenseRed
import com.feiqi.ui.theme.IncomeGreen
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.OnSurfaceVariant
import com.feiqi.ui.theme.OutlineVariant
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.PrimaryContainer
import com.feiqi.ui.theme.SurfaceVariant
import com.feiqi.ui.theme.Tertiary
import java.text.DecimalFormat
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 拖拽到边缘时的自动滚动参数：感应区高度、每帧最小/最大滚动量
private val HOME_EDGE_SCROLL_ZONE = 96.dp
private val HOME_EDGE_SCROLL_MIN = 2.dp
private val HOME_EDGE_SCROLL_MAX = 16.dp

/** 松手归位动画时长（ms）：太快像瞬移，太慢显得拖沓。 */
private const val HOME_SNAP_DURATION_MS = 220

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenAccounting: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenMedia: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showShoppingDialog by remember { mutableStateOf(false) }
    var shoppingTabBought by remember { mutableStateOf(false) }
    var previewMedia by remember { mutableStateOf<Media?>(null) }
    var editingRecord by remember { mutableStateOf<AccountRecord?>(null) }

    // ---------------- 首页布局编辑（区块顺序持久化在 DataStore） ----------------
    val cardOrder by viewModel.cardOrder.collectAsStateWithLifecycle()
    var layoutEditing by remember { mutableStateOf(false) }
    var draggingCard by remember { mutableStateOf<HomeCard?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val blockHeights = remember { mutableStateMapOf<HomeCard, Int>() }
    // 各卡片**内容固有高度**：取最大值作为统一外框高度（各卡片外框同高，内容尺寸不变）
    val contentHeights = remember { mutableStateMapOf<HomeCard, Int>() }
    val densityForFrame = LocalDensity.current
    val uniformFrameHeight: Dp? = contentHeights.values.maxOrNull()
        ?.let { with(densityForFrame) { it.toDp() } }
    // 松手后进入"归位动画"阶段的卡片：视觉上仍按被抬起渲染，直到位移回零
    var settlingCard by remember { mutableStateOf<HomeCard?>(null) }
    val activeCard: HomeCard? = draggingCard ?: settlingCard

    // ---------------- 拖拽辅助：列表状态 / 可视区边界 / 边缘自动滚动 ----------------
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var listTopInRoot by remember { mutableStateOf(0f) }
    var listBottomInRoot by remember { mutableStateOf(0f) }
    // 每帧滚动像素（带符号）：>0 向下滚、<0 向上滚；数值由指针贴近边缘的程度决定
    var autoScrollStep by remember { mutableStateOf(0f) }
    // 最近的指针绝对 Y：手指停在边缘不动时没有新的拖动事件，滚动循环要靠它复算
    var dragPointerRootY by remember { mutableStateOf(0f) }

    /**
     * 拖拽结算：**换位判定 + 边缘自动滚动**。指针移动时调用，**自动滚动的每一帧之后也会调用**。
     *
     * 为什么必须抽出来：手指停在屏幕边缘不动时不会再有拖动事件，若只在 onDrag 里判定，
     * 位移会单方面累加（滚动补偿）而卡片永远不落位 → 卡片被推出可视区、界面只剩空白。
     */
    fun settleDrag(card: HomeCard, pointerRootY: Float) {
        // ① 换位判定：用 while 连续跨越，高速拖动时一帧可能跨过多个区块
        while (true) {
            val index = cardOrder.indexOf(card)
            if (index < 0) return
            val nextHeight =
                if (dragOffsetY > 0 && index < cardOrder.lastIndex) blockHeights[cardOrder[index + 1]] ?: 0 else 0
            val prevHeight =
                if (dragOffsetY < 0 && index > 0) blockHeights[cardOrder[index - 1]] ?: 0 else 0
            when {
                nextHeight > 0 && dragOffsetY > nextHeight / 2f -> {
                    viewModel.moveCard(index, index + 1)
                    dragOffsetY -= nextHeight
                }

                prevHeight > 0 && -dragOffsetY > prevHeight / 2f -> {
                    viewModel.moveCard(index, index - 1)
                    dragOffsetY += prevHeight
                }

                else -> break
            }
        }

        // ② 位移钳制（保险）：正常每跨过一块就会扣掉一块高度，位移天然有界；
        //    这里再兜一层，任何异常都不会把卡片推出屏幕（即"卡片丢失/空白"）。
        val maxHeight = blockHeights.values.maxOrNull() ?: 0
        if (maxHeight > 0) {
            dragOffsetY = HomeDragRules.clampOffset(dragOffsetY, maxHeight.toFloat())
        }

        // ③ 边缘自动滚动：指针进入上/下 96dp 感应区后逐帧滚动，越靠边越快（2dp → 16dp/帧）。
        //    已在队首/队尾且仍要往外拖时**停止滚动**：此时没有可交换的位置，
        //    继续滚只会让卡片脱离自己的槽位。
        val index = cardOrder.indexOf(card)
        val canScrollUp = index > 0
        val canScrollDown = index < cardOrder.lastIndex
        autoScrollStep = HomeDragRules.autoScrollStep(
            pointerRootY = pointerRootY,
            listTop = listTopInRoot,
            listBottom = listBottomInRoot,
            zone = with(density) { HOME_EDGE_SCROLL_ZONE.toPx() },
            minStep = with(density) { HOME_EDGE_SCROLL_MIN.toPx() },
            maxStep = with(density) { HOME_EDGE_SCROLL_MAX.toPx() },
            canScrollUp = canScrollUp,
            canScrollDown = canScrollDown
        )
    }

    // 松手吸附：不在合法槽位时**平滑**回到最近合法槽位（即位移回零），结束后无任何残留偏移
    LaunchedEffect(settlingCard) {
        val card = settlingCard ?: return@LaunchedEffect
        val from = dragOffsetY
        if (from != 0f) {
            Animatable(from).animateTo(
                targetValue = 0f,
                animationSpec = tween(HOME_SNAP_DURATION_MS, easing = FastOutSlowInEasing)
            ) { dragOffsetY = value }
        }
        dragOffsetY = 0f
        settlingCard = null
    }

    // 拖动期间逐帧滚动；滚动量补偿进拖拽位移，被拖卡片才能继续跟手（不脱手、不跳变）
    LaunchedEffect(draggingCard) {
        while (draggingCard != null) {
            val step = autoScrollStep
            val card = draggingCard
            if (step != 0f && card != null) {
                val consumed = listState.scrollBy(step)
                if (consumed != 0f) {
                    // 列表滚了，位移要跟着补，卡片才不脱手
                    dragOffsetY += consumed
                    // 关键修复：滚动后立刻复算「是否该换位」。
                    // 手指停在边缘不动时没有拖动事件，若不复算，位移会一直累加而卡片永不落位，
                    // 表现为卡片被推出可视区、只剩空白。
                    settleDrag(card, dragPointerRootY)
                }
            }
            withFrameNanos { }
        }
        autoScrollStep = 0f
    }

    /** 拖动位移入口：累加位移并记住指针位置，随后交给 [settleDrag] 做换位与自动滚动判定。 */
    fun onBlockDrag(card: HomeCard, deltaY: Float, pointerRootY: Float) {
        dragOffsetY += deltaY
        dragPointerRootY = pointerRootY
        settleDrag(card, pointerRootY)
    }

    val deleteConfirm = rememberDeleteConfirm()
    val deleteShoppingText = stringResource(R.string.delete_shopping_confirm)
    val clearBoughtText = stringResource(R.string.clear_bought_confirm)

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            // 首页数据来自 Room/DataStore 的实时 Flow，始终自动同步，无需手动重算。
            // 下拉手势仅作轻量反馈：短暂展示指示器后收起。
            scope.launch {
                isRefreshing = true
                delay(400)
                isRefreshing = false
            }
        }
    )
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    // 记录可视区上下边界（根坐标），供边缘自动滚动判断
                    val top = coords.localToRoot(Offset.Zero).y
                    val bottom = top + coords.size.height
                    if (top != listTopInRoot) listTopInRoot = top
                    if (bottom != listBottomInRoot) listBottomInRoot = bottom
                }
                // 编辑布局时关闭下拉刷新：拖拽是纵向手势，否则极易误触刷新
                .pullRefresh(pullState, enabled = !layoutEditing && draggingCard == null),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = uiState.greeting,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = uiState.dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            layoutEditing = !layoutEditing
                            // 退出编辑模式时清掉拖动中间态，避免残留位移影响渲染
                            draggingCard = null
                            dragOffsetY = 0f
                        }
                    ) {
                        Text(
                            text = stringResource(
                                if (layoutEditing) R.string.home_layout_done else R.string.home_layout_edit
                            )
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.home_headline),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ---------------- 可排序区块：顺序由 HomeCard 顺序决定（默认顺序见 HomeCard.DEFAULT_ORDER） ----------------
        if (layoutEditing) {
            item {
                Text(
                    text = stringResource(R.string.home_layout_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }

        items(cardOrder, key = { it.id }) { card ->
            HomeBlock(
                card = card,
                editing = layoutEditing,
                dragging = activeCard == card,
                dragOffsetY = if (activeCard == card) dragOffsetY else 0f,
                frameHeight = uniformFrameHeight,
                onHeightMeasured = { h -> if (blockHeights[card] != h) blockHeights[card] = h },
                onContentHeight = { h -> if (contentHeights[card] != h) contentHeights[card] = h },
                onDragStart = {
                    draggingCard = card
                    dragOffsetY = 0f
                },
                onDragDelta = { delta, pointerY -> onBlockDrag(card, delta, pointerY) },
                onDragEnd = {
                    // 松手后若仍有位移（未落在合法槽位），进入平滑归位动画；
                    // 动画由 LaunchedEffect(settlingCard) 负责，结束后位移必为 0
                    val card = draggingCard
                    draggingCard = null
                    autoScrollStep = 0f
                    dragPointerRootY = 0f
                    if (card != null) settlingCard = card else dragOffsetY = 0f
                }
            ) {
                when (card) {
                    HomeCard.LIFE_INDEX -> {
                LifeIndexCard(
                    score = uiState.lifeIndex,
                    desc = stringResource(R.string.life_index_desc),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                    }

                    HomeCard.STATS -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        label = stringResource(R.string.month_expense),
                        value = "¥${formatMoney(uiState.monthExpense)}",
                        hint = stringResource(R.string.month_expense_hint),
                        background = CardRed,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAccounting
                    )
                    StatCard(
                        label = stringResource(R.string.latest_weight),
                        value = uiState.latestWeight?.let { "${it.weight}kg" } ?: "--",
                        hint = stringResource(R.string.latest_weight_hint),
                        background = CardGreen,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenHealth
                    )
                    StatCard(
                        label = stringResource(R.string.today_todo),
                        value = "${uiState.todayTodoCount}件",
                        hint = stringResource(R.string.today_todo_hint),
                        background = CardAmber,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenSchedule
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                    }

                    HomeCard.TODAY_TODO -> {
            // ---------------- 今日待办（单条日程 + 待办清单） ----------------
                SectionHeader(
                    title = stringResource(R.string.today_todo),
                    action = stringResource(R.string.all),
                    onAction = onOpenSchedule
                )
                if (uiState.todayTodoItems.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.today_todo_empty_title),
                        description = stringResource(R.string.today_todo_empty_desc),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.todayTodoItems.forEach { item ->
                            when (item) {
                                is ScheduleListItem.Single -> HomeTodoRow(
                                    item = item.schedule,
                                    onClick = onOpenSchedule
                                )

                                is ScheduleListItem.Group -> HomeTodoGroupCard(
                                    group = item,
                                    onClick = onOpenSchedule
                                )
                            }
                        }
                    }
                }
                    }

                    HomeCard.SHOPPING -> {
            // ---------------- 购买物品（待买 / 已买） ----------------
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.shopping_list),
                    action = stringResource(R.string.add_shopping_item),
                    onAction = { showShoppingDialog = true }
                )
                ShoppingCard(
                    toBuy = uiState.shoppingToBuy,
                    bought = uiState.shoppingBought,
                    showBought = shoppingTabBought,
                    onTabChange = { shoppingTabBought = it },
                    onToggle = viewModel::toggleShoppingItem,
                    onDelete = { id ->
                        deleteConfirm.request(deleteShoppingText) {
                            viewModel.deleteShoppingItem(id)
                        }
                    },
                    onClearBought = {
                        deleteConfirm.request(clearBoughtText) {
                            viewModel.clearBoughtItems()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                    }

                    HomeCard.MEDIA -> {
            // ---------------- 书影音（最近作品） ----------------
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.media_collection),
                    action = stringResource(R.string.all),
                    onAction = onOpenMedia
                )
                if (uiState.recentMedia.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.media_empty_title),
                        description = stringResource(R.string.media_empty_desc),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height((if (uiState.recentMedia.size > 2) 300 else 144).dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        userScrollEnabled = false
                    ) {
                        items(uiState.recentMedia, key = { it.id }) { media ->
                            HomeMediaCard(
                                media = media,
                                onPreview = { if (!media.coverUri.isNullOrBlank()) previewMedia = media },
                                onOpenMedia = onOpenMedia
                            )
                        }
                    }
                }
                    }

                    HomeCard.RECENT_TRACE -> {
                SectionHeader(
                    title = stringResource(R.string.recent_trace),
                    action = stringResource(R.string.all),
                    onAction = onOpenAccounting
                )
                if (uiState.recentRecords.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.empty_state_title),
                        description = stringResource(R.string.empty_state_desc),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    uiState.recentRecords.forEach { record: AccountRecord ->
                        AccountListItem(
                            record = record,
                            onClick = { editingRecord = record },
                            onLongClick = { editingRecord = record },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
                    }

                    HomeCard.QUOTE -> {
                QuoteCard(quote = uiState.quote, author = uiState.quoteAuthor)
                    }
                }
            }
        }
        }

        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = Primary
        )

        if (showShoppingDialog) {
            AddShoppingDialog(
                onConfirm = { name, price ->
                    viewModel.addShoppingItem(name, price)
                    showShoppingDialog = false
                },
                onDismiss = { showShoppingDialog = false }
            )
        }

        previewMedia?.let { media ->
            CoverPreviewDialog(
                coverUri = media.coverUri!!,
                type = media.type,
                onDismiss = { previewMedia = null }
            )
        }

        editingRecord?.let { record ->
            RecordFormDialog(
                record = record,
                onDismiss = { editingRecord = null },
                onSave = { type, amountText, category, dateTime, note ->
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    viewModel.saveRecord(
                        record.copy(
                            type = type,
                            amount = amount,
                            category = category,
                            dateTime = dateTime,
                            note = note
                        )
                    )
                    editingRecord = null
                }
            )
        }

        DeleteConfirmHost(state = deleteConfirm)
    }
}

@Composable
private fun LifeIndexCard(score: Int, desc: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(containerColor = Primary),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.life_index),
                style = MaterialTheme.typography.labelLarge,
                color = OnPrimary.copy(alpha = 0.8f)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = MaterialTheme.typography.displayLarge,
                    color = OnPrimary
                )
                Text(
                    text = stringResource(R.string.life_index_full),
                    style = MaterialTheme.typography.titleLarge,
                    color = OnPrimary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                )
            }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = OnPrimary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    hint: String,
    background: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) {
            // 用最小高度而非固定高度：系统字体放大时卡片可以长高，hint 不会被裁掉。
            modifier.heightIn(min = 100.dp).clickable { onClick() }
        } else {
            modifier.heightIn(min = 100.dp)
        },
        colors = CardDefaults.cardColors(containerColor = background),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuoteCard(quote: String, author: String? = null, modifier: Modifier = Modifier) {
    // 液态玻璃海报卡（DESIGN.md §4）：轻档半透明 + 高光边 + 暖色阴影，透出页面底色更通透
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FeiQiSpacing.lg),
        shape = RoundedCornerShape(FeiQiRadius.lg),
        strength = GlassStrength.Thin,
        elevated = true
    ) {
        Column(modifier = Modifier.padding(FeiQiSpacing.lg + FeiQiSpacing.xs)) {
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 作者可选：内置语录没有作者，远程语录集提供时右对齐显示一行。
            if (!author.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "—— $author",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun HomeTodoRow(item: Schedule, onClick: () -> Unit) {
    val done = item.completed
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (done) PrimaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) OnSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val timeText = item.time?.let { "${it.hour}:${String.format("%02d", it.minute)}" }
                    ?: stringResource(R.string.all_day)
                Text(
                    text = "$timeText · ${item.groupName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant
                )
            }
            val color = if (done) Tertiary else MaterialTheme.colorScheme.outline
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** 首页展示一整份待办清单：标题 + 完成进度 + 前几条条目预览。 */
@Composable
private fun HomeTodoGroupCard(group: ScheduleListItem.Group, onClick: () -> Unit) {
    val allDone = group.allCompleted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) PrimaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.group),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (allDone) OnSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${group.completedCount}/${group.total}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (allDone) Tertiary else Primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            group.items.take(3).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.completed) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (item.completed) Tertiary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.completed) OnSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (group.total > 3) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "还有 ${group.total - 3} 项…",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

// ---------------- 购买物品 ----------------

@Composable
private fun ShoppingCard(
    toBuy: List<ShoppingItem>,
    bought: List<ShoppingItem>,
    showBought: Boolean,
    onTabChange: (Boolean) -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearBought: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        // 与项目其余卡片统一为 16dp 圆角（此前 18dp 是全项目唯一的取值）。
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ShoppingTab(
                        text = stringResource(R.string.to_buy_count, toBuy.size),
                        selected = !showBought,
                        onClick = { onTabChange(false) }
                    )
                    ShoppingTab(
                        text = stringResource(R.string.bought_count, bought.size),
                        selected = showBought,
                        onClick = { onTabChange(true) }
                    )
                }
                if (showBought && bought.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.clear_bought),
                        style = MaterialTheme.typography.labelMedium,
                        color = ExpenseRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClearBought() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val list = if (showBought) bought else toBuy
            if (list.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (showBought) {
                            stringResource(R.string.shopping_empty_bought)
                        } else {
                            stringResource(R.string.shopping_empty_to_buy)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    list.forEach { item ->
                        ShoppingRow(
                            item = item,
                            onToggle = { onToggle(item.id) },
                            onDelete = { onDelete(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShoppingTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) OnPrimary else OnSurfaceVariant
        )
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.bought,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Tertiary)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.bought) OnSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (item.bought) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.price > 0) {
                Text(
                    text = "¥${formatMoney(item.price)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddShoppingDialog(
    onConfirm: (name: String, price: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_shopping_item)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.shopping_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text(stringResource(R.string.shopping_price_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, price) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onAction() }
        )
    }
}

private fun formatMoney(value: Double): String {
    return DecimalFormat("#,##0.00").format(value)
}

/** 首页书影音卡片：封面填满卡片；点击打开预览（有封面）或跳转书影音页（无封面）。 */
@Composable
private fun HomeMediaCard(
    media: Media,
    onPreview: () -> Unit,
    onOpenMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cover = rememberHomeCover(media.coverUri)
    val tint = homeMediaTint(media.type)
    val hasCover = !media.coverUri.isNullOrBlank()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(144.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { if (hasCover) onPreview() else onOpenMedia() }
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(media.type.label, style = MaterialTheme.typography.titleMedium, color = tint)
                }
            }
            // 底部渐变文字区：标题 + 类型
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(Color(0x66000000))
                    .padding(10.dp)
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${media.type.label} · ${media.status.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun rememberHomeCover(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = null
        if (!path.isNullOrBlank()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = 2 // 首页缩略图，简单降采样即可
                    }
                    BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
                }.getOrNull()
            }
            bitmap = bmp
        }
    }
    return bitmap
}

private fun homeMediaTint(type: MediaType): Color = when (type) {
    MediaType.MOVIE -> Color(0xFF6D8CC0)
    MediaType.TV -> Color(0xFFB07CC6)
    MediaType.BOOK -> Color(0xFF7FB069)
    MediaType.MUSIC -> Color(0xFFE08BB0)
    else -> Color(0xFFB0A08C)
}
