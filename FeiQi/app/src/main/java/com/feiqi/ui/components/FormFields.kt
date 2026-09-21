package com.feiqi.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feiqi.R
import com.feiqi.utils.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val filtered = new.filter { it.isDigit() || it == '.' }
            val parts = filtered.split('.')
            val sanitized = when {
                filtered.count { it == '.' } > 1 -> value
                parts.size == 2 && parts[1].length > 2 -> value
                else -> filtered
            }
            onValueChange(sanitized)
        },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text("¥") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    date: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = DateUtils.monthDay(date),
        onValueChange = {},
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { open = true }) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = label
                )
            }
        }
    )

    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateSelected(selected)
                        }
                        open = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * 日期 + 时间（精确到秒）选择。日期走系统 DatePicker，时间走系统 TimePicker，
 * 秒数默认取当前秒，可在时间弹窗里单独微调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    dateTime: LocalDateTime,
    onDateTimeSelected: (LocalDateTime) -> Unit,
    dateLabel: String,
    timeLabel: String,
    modifier: Modifier = Modifier
) {
    var openDate by remember { mutableStateOf(false) }
    var openTime by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = DateUtils.monthDay(dateTime.toLocalDate()),
            onValueChange = {},
            modifier = Modifier.weight(1f),
            label = { Text(dateLabel) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { openDate = true }) {
                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = dateLabel)
                }
            }
        )
        OutlinedTextField(
            value = DateUtils.hms(dateTime.toLocalTime()),
            onValueChange = {},
            modifier = Modifier.weight(1f),
            label = { Text(timeLabel) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { openTime = true }) {
                    Icon(imageVector = Icons.Default.Schedule, contentDescription = timeLabel)
                }
            }
        )
    }

    if (openDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateTime.toLocalDate()
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { openDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateTimeSelected(LocalDateTime.of(selected, dateTime.toLocalTime()))
                        }
                        openDate = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { openDate = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (openTime) {
        val timeState = rememberTimePickerState(
            initialHour = dateTime.hour,
            initialMinute = dateTime.minute,
            is24Hour = true
        )
        var second by remember { mutableStateOf(dateTime.second.toString()) }
        AlertDialog(
            onDismissRequest = { openTime = false },
            title = { Text(timeLabel) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = second,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(2)
                            second = digits
                        },
                        label = { Text("秒") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sec = second.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    onDateTimeSelected(
                        LocalDateTime.of(
                            dateTime.toLocalDate(),
                            LocalTime.of(timeState.hour, timeState.minute, sec)
                        )
                    )
                    openTime = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { openTime = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 仅时间选择（HH:mm），返回 null 表示未设置时间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    time: LocalTime?,
    onTimeSelected: (LocalTime?) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = time?.let { DateUtils.hm(it) } ?: "--:--",
        onValueChange = {},
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = { open = true }) {
                Icon(imageVector = Icons.Default.Schedule, contentDescription = label)
            }
        }
    )

    if (open) {
        val timeState = rememberTimePickerState(
            initialHour = time?.hour ?: LocalTime.now().hour,
            initialMinute = time?.minute ?: LocalTime.now().minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(LocalTime.of(timeState.hour, timeState.minute))
                    open = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
