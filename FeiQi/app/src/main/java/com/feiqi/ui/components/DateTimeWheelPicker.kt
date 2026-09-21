package com.feiqi.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.feiqi.utils.DateUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 仿小米风格的滚轮日期时间选择器（底部弹层）。
 * 三列滚轮：日期(M月d日 周x) / 小时(HH时) / 分钟(mm分)，选中项白色加粗高亮。
 * 农历为占位开关（暂未实现，禁用）。
 */
@Composable
fun DateTimeWheelPickerDialog(
    initialDateTime: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    val dates = remember {
        val today = DateUtils.today()
        (0..395).map { today.minusDays(30).plusDays(it.toLong()) }
    }
    val dateLabels = remember {
        dates.map { it.format(DateTimeFormatter.ofPattern("M月d日 E", Locale.CHINA)) }
    }
    val hourItems = remember { (0..23).map { "${it.toString().padStart(2, '0')}时" } }
    val minuteItems = remember { (0..59).map { "${it.toString().padStart(2, '0')}分" } }

    var dateIndex by remember {
        mutableStateOf(dates.indexOf(initialDateTime.toLocalDate()).coerceAtLeast(0))
    }
    var hourIndex by remember { mutableStateOf(initialDateTime.hour.coerceIn(0, 23)) }
    var minuteIndex by remember { mutableStateOf(initialDateTime.minute.coerceIn(0, 59)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentHeight(Alignment.Bottom)
        ) {
            Surface(
                color = Color(0xFF1C1C1E),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WheelPicker(
                            items = dateLabels,
                            initialIndex = dateIndex,
                            onIndexChanged = { dateIndex = it },
                            modifier = Modifier.weight(2.4f)
                        )
                        WheelPicker(
                            items = hourItems,
                            initialIndex = hourIndex,
                            onIndexChanged = { hourIndex = it },
                            modifier = Modifier.weight(1f)
                        )
                        WheelPicker(
                            items = minuteItems,
                            initialIndex = minuteIndex,
                            onIndexChanged = { minuteIndex = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 农历占位开关（暂未实现，禁用）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "显示农历",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Switch(checked = false, enabled = false, onCheckedChange = {})
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A4A4C),
                                contentColor = Color.White
                            )
                        ) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                onConfirm(
                                    LocalDateTime.of(
                                        dates[dateIndex],
                                        LocalTime.of(hourIndex, minuteIndex)
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3B82F6),
                                contentColor = Color.White
                            )
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

/**
 * 单列表滚轮选择器。selectedIndex 通过 onIndexChanged 实时回调，
 * 居中项白色加粗高亮，非选中项半透明白色，居中带淡白高亮条。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCount = 5
    val paddingCount = 2
    val itemHeight = 44.dp
    val centerOffset = (visibleCount - 1) / 2

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialIndex + paddingCount - centerOffset).coerceAtLeast(0)
    )

    val selectedIndex by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportSize.height / 2
            val closest = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
            }
            val rawIndex = closest?.index ?: 0
            (rawIndex - paddingCount).coerceIn(0, items.lastIndex)
        }
    }

    LaunchedEffect(selectedIndex) {
        onIndexChanged(selectedIndex)
    }

    Box(
        modifier = modifier
            .widthIn(min = 48.dp)
            .height(itemHeight * visibleCount)
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            repeat(paddingCount) {
                item { Spacer(modifier = Modifier.height(itemHeight)) }
            }
            items(items.size) { index ->
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        color = if (index == selectedIndex) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.4f)
                        },
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            repeat(paddingCount) {
                item { Spacer(modifier = Modifier.height(itemHeight)) }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .height(itemHeight)
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.2f))
        )
    }
}
