package com.feiqi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.feiqi.R
import com.feiqi.ui.theme.OnPrimary
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.SurfaceVariant
import com.feiqi.utils.DateUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 提醒设置弹窗。
 * 默认以月历网格展示年月日；点击上方日期芯片（确切年月日区域）可在日历与
 * 三列滚轮下拉之间切换。时间始终为滚轮选择，保持当前亮色主题。
 */
@Composable
fun ReminderSettingDialog(
    initialDateTime: LocalDateTime,
    initialRecurring: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime, Boolean) -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }
    var mode by remember { mutableStateOf(Mode.DATE) } // DATE / TIME
    var dateView by remember { mutableStateOf(DateView.CALENDAR) } // CALENDAR / WHEEL
    var isRecurring by remember { mutableStateOf(initialRecurring) }
    var isLunar by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.CHINA) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.set_reminder_time),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 提醒时间：日期芯片（点击切换日历/滚轮）、时间芯片（滚轮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reminder_time),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateTimeChip(
                            text = selectedDate.format(dateFormatter),
                            selected = mode == Mode.DATE,
                            onClick = {
                                if (mode == Mode.DATE) {
                                    dateView = if (dateView == DateView.CALENDAR) {
                                        DateView.WHEEL
                                    } else {
                                        DateView.CALENDAR
                                    }
                                } else {
                                    mode = Mode.DATE
                                }
                            }
                        )
                        DateTimeChip(
                            text = selectedTime.format(timeFormatter),
                            selected = mode == Mode.TIME,
                            onClick = { mode = Mode.TIME }
                        )
                    }
                }

                // 日期视图切换提示
                if (mode == Mode.DATE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (dateView == DateView.CALENDAR) {
                            stringResource(R.string.date_view_hint_calendar)
                        } else {
                            stringResource(R.string.date_view_hint_wheel)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 重复提醒
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isRecurring = !isRecurring },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.repeat_reminder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isRecurring) {
                                stringResource(R.string.daily)
                            } else {
                                stringResource(R.string.no_repeat)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // 农历
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.lunar),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = isLunar,
                        onCheckedChange = { isLunar = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Primary,
                            checkedTrackColor = Primary.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // 日期（日历/滚轮）或时间（滚轮）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    when {
                        mode == Mode.TIME -> TimeWheelPicker(
                            initialTime = selectedTime,
                            onTimeChanged = { selectedTime = it }
                        )
                        dateView == DateView.WHEEL -> DateWheelPicker(
                            selectedDate = selectedDate,
                            onSelect = { selectedDate = it }
                        )
                        else -> CalendarMonthGrid(
                            selectedDate = selectedDate,
                            onSelect = { selectedDate = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            onConfirm(LocalDateTime.of(selectedDate, selectedTime), isRecurring)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

private enum class Mode { DATE, TIME }
private enum class DateView { CALENDAR, WHEEL }

@Composable
private fun DateTimeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary else SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) OnPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/** 月历网格：默认展示年月日，可翻月、点选日期。 */
@Composable
private fun CalendarMonthGrid(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewYm by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }
    val today = DateUtils.today()
    val daysInMonth = viewYm.lengthOfMonth()
    val leadingBlanks = viewYm.atDay(1).dayOfWeek.value % 7
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewYm = viewYm.minusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.prev_month),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = stringResource(R.string.year_month, viewYm.year, viewYm.monthValue),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = { viewYm = viewYm.plusMonths(1) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_month),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { wd ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = wd,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        val totalCells = leadingBlanks + daysInMonth
        val weeks = (totalCells + 6) / 7
        for (w in 0 until weeks) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val idx = w * 7 + c
                    val day = if (idx < leadingBlanks || idx >= leadingBlanks + daysInMonth) {
                        null
                    } else {
                        idx - leadingBlanks + 1
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val date = viewYm.atDay(day)
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Primary else Color.Transparent)
                                    .border(
                                        width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                                        color = if (isToday && !isSelected) Primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelect(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) OnPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateWheelPicker(
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    val yearRange = (selectedDate.year - 5)..(selectedDate.year + 5)
    val years = remember(yearRange) { yearRange.toList() }
    val months = remember { (1..12).toList() }

    var selectedYear by remember { mutableIntStateOf(selectedDate.year) }
    var selectedMonth by remember { mutableIntStateOf(selectedDate.monthValue) }
    var selectedDay by remember { mutableIntStateOf(selectedDate.dayOfMonth) }

    // 月份变化时，限制日期有效范围
    val daysInMonth by remember(selectedYear, selectedMonth) {
        derivedStateOf {
            YearMonth.of(selectedYear, selectedMonth).lengthOfMonth()
        }
    }

    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        val day = selectedDay.coerceIn(1, daysInMonth)
        val date = runCatching {
            LocalDate.of(selectedYear, selectedMonth, day)
        }.getOrDefault(selectedDate)
        if (date != selectedDate) {
            onSelect(date)
        }
    }

    // 当外部 selectedDate 改变时同步
    LaunchedEffect(selectedDate) {
        if (selectedDate.year != selectedYear) selectedYear = selectedDate.year
        if (selectedDate.monthValue != selectedMonth) selectedMonth = selectedDate.monthValue
        if (selectedDate.dayOfMonth != selectedDay) selectedDay = selectedDate.dayOfMonth
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = String.format(Locale.CHINA, "%04d/%02d", selectedYear, selectedMonth),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelColumn(
                items = years.map { "%04d".format(it) },
                initialIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                onIndexChanged = { selectedYear = years[it] },
                suffix = "年",
                modifier = Modifier.width(90.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            WheelColumn(
                items = months.map { "%02d".format(it) },
                initialIndex = selectedMonth - 1,
                onIndexChanged = { selectedMonth = it + 1 },
                suffix = "月",
                modifier = Modifier.width(70.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            WheelColumn(
                items = (1..daysInMonth).map { "%02d".format(it) },
                initialIndex = (selectedDay - 1).coerceIn(0, daysInMonth - 1),
                onIndexChanged = { selectedDay = it + 1 },
                suffix = "日",
                modifier = Modifier.width(70.dp)
            )
        }
    }
}

@Composable
private fun TimeWheelPicker(
    initialTime: LocalTime,
    onTimeChanged: (LocalTime) -> Unit
) {
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }

    var hourIndex by remember { mutableIntStateOf(initialTime.hour) }
    var minuteIndex by remember { mutableIntStateOf(initialTime.minute) }

    LaunchedEffect(hourIndex, minuteIndex) {
        onTimeChanged(LocalTime.of(hourIndex, minuteIndex))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelColumn(
            items = hours,
            initialIndex = hourIndex,
            onIndexChanged = { hourIndex = it },
            suffix = "时",
            modifier = Modifier.width(90.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        WheelColumn(
            items = minutes,
            initialIndex = minuteIndex,
            onIndexChanged = { minuteIndex = it },
            suffix = "分",
            modifier = Modifier.width(90.dp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onIndexChanged: (Int) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier
) {
    val visibleCount = 5
    val paddingCount = visibleCount / 2
    val itemHeight = 48.dp

    // 列表结构为 [占位 x paddingCount] + [数据项] + [占位 x paddingCount]。
    // 要让第 i 个数据项停在中心格，firstVisibleItemIndex 必须等于 i（而不是 i + paddingCount），
    // 否则默认显示的时分会固定偏移 paddingCount * 2 格，看起来像「随机时间」。
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    )
    val snappingLayout = remember(listState) {
        SnapLayoutInfoProvider(listState)
    }
    val flingBehavior = rememberSnapFlingBehavior(snappingLayout)

    val selectedIndex by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            if (layout.visibleItemsInfo.isEmpty()) {
                // 首帧尚未测量，直接回退到初始值，避免误触发 onIndexChanged。
                return@derivedStateOf initialIndex
            }
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val closest = layout.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - center)
            }
            ((closest?.index ?: (initialIndex + paddingCount)) - paddingCount)
                .coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in items.indices) {
            onIndexChanged(selectedIndex)
        }
    }

    Box(modifier = modifier.height(itemHeight * visibleCount)) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(paddingCount) {
                Box(modifier = Modifier.height(itemHeight)) { }
            }
            items(items.size) { index ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index] + suffix,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            items(paddingCount) {
                Box(modifier = Modifier.height(itemHeight)) { }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .height(itemHeight)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Primary.copy(alpha = 0.08f))
                .border(1.dp, Primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
        )
    }
}
