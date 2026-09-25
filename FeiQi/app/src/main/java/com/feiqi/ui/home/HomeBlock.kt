package com.feiqi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.feiqi.data.model.HomeCard
import kotlin.math.roundToInt

/**
 * 首页区块容器：**普通模式下只是一个透明容器（零视觉影响）**，进入布局编辑模式后：
 * - 顶部出现「☰ 按住拖动」手柄行（只有手柄响应拖动，卡片自身的点击/滚动不受影响）；
 * - 区块被描边+浅底色框出来，让"可移动单位"一目了然；
 * - 正在拖动的区块浮到最上层、跟随手指位移并带阴影。
 *
 * 位移只作用在**视觉层**（[Modifier.offset]），不改变测量尺寸，因此
 * [onHeightMeasured] 汇报的高度始终稳定，可用于相邻交换的阈值判断。
 *
 * @param onHeightMeasured 上报本区块实测高度（像素），父层用它决定"拖过一半就交换"。
 */
@Composable
internal fun HomeBlock(
    card: HomeCard,
    editing: Boolean,
    dragging: Boolean,
    dragOffsetY: Float,
    onHeightMeasured: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onHeightMeasured(it.size.height) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { alpha = if (dragging) 0.96f else 1f }
            .offsetY(if (dragging) dragOffsetY else 0f)
            .then(
                if (editing) {
                    Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .shadow(if (dragging) 10.dp else 0.dp, shape, clip = false)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                }
            )
    ) {
        if (editing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp)
                        // 手柄独占拖动：不影响区块内部卡片的点击与列表滚动
                        .pointerInput(card) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragDelta(dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                )
                Text(
                    text = card.displayName(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        content()

        if (editing) Spacer(modifier = Modifier.height(6.dp))
    }
}

/** 竖直位移包装（保持可读性，避免在链式调用里散落 IntOffset 换算）。 */
private fun Modifier.offsetY(value: Float): Modifier =
    this.then(
        Modifier.offset { IntOffset(x = 0, y = value.roundToInt()) }
    )

/** 编辑模式下展示的区块名（与文案资源解耦，便于快速识别）。 */
internal fun HomeCard.displayName(): String = when (this) {
    HomeCard.LIFE_INDEX -> "生活指数"
    HomeCard.STATS -> "数据概览"
    HomeCard.TODAY_TODO -> "今日待办"
    HomeCard.SHOPPING -> "购买物品"
    HomeCard.MEDIA -> "书影音"
    HomeCard.RECENT_TRACE -> "最近记账"
    HomeCard.QUOTE -> "每日语录"
}
