package com.feiqi.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.BmiScale
import com.feiqi.data.model.UserProfile
import com.feiqi.data.model.WeightRecord
import com.feiqi.ui.components.ConfirmDialog
import com.feiqi.ui.components.DatePickerField
import com.feiqi.ui.components.EmptyState
import com.feiqi.ui.theme.ExpenseRed
import com.feiqi.ui.theme.IncomeGreen
import com.feiqi.ui.theme.OnSurfaceVariant
import com.feiqi.ui.theme.Outline
import com.feiqi.ui.theme.Primary
import com.feiqi.ui.theme.PrimaryContainer
import com.feiqi.ui.theme.SurfaceVariant
import com.feiqi.ui.theme.Tertiary
import com.feiqi.ui.theme.WarmAccent
import com.feiqi.utils.DateUtils
import kotlin.math.abs
import java.time.LocalDate

@Composable
fun HealthScreen(
    viewModel: HealthViewModel,
    modifier: Modifier = Modifier
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<WeightRecord?>(null) }
    var chartRange by remember { mutableStateOf(WeightChartRange.DAY) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { toastMessage = it }
    }

    val latestWeight = records.maxByOrNull { it.date }?.weight
    val chartPoints = remember(records, chartRange) {
        HealthViewModel.buildChartPoints(records, chartRange)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            HeaderRow(recordCount = records.size)

            Spacer(modifier = Modifier.height(16.dp))

            PersonalInfoCard(
                profile = profile,
                latestWeight = latestWeight,
                onEdit = { showProfileDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            WeightInputCard(
                onSave = { weight, date, note -> viewModel.addRecord(weight, date, note) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            WeightCurveCard(
                points = chartPoints,
                range = chartRange,
                onRangeChange = { chartRange = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.recent_trace),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (records.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.health_empty_title),
                    description = stringResource(R.string.health_empty_desc),
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    records.sortedByDescending { it.date }.forEach { record ->
                        WeightRecordItem(
                            record = record,
                            onEdit = { editingRecord = record },
                            onDelete = { viewModel.deleteRecord(record.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            profile = profile,
            onConfirm = { gender, age, height ->
                viewModel.saveProfile(gender, age, height)
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    editingRecord?.let { record ->
        WeightEditDialog(
            record = record,
            onConfirm = { weight, date, note ->
                viewModel.updateRecord(record.copy(weight = weight, date = date, note = note))
                editingRecord = null
            },
            onDismiss = { editingRecord = null }
        )
    }

    toastMessage?.let { message ->
        HealthToast(message = message, onDismiss = { toastMessage = null })
    }
}

@Composable
private fun HeaderRow(recordCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.health_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.health_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant
            )
        }
        StatPill(text = stringResource(R.string.health_record_count, recordCount))
    }
}

@Composable
private fun StatPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Primary,
            // 记录条数变多（如「共 128 条记录」）时省略，避免把左侧标题挤窄。
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------- 个人信息 + BMI ----------------

@Composable
private fun PersonalInfoCard(
    profile: UserProfile,
    latestWeight: Double?,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bmi = profile.bmi(latestWeight)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.personal_info),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit_personal_info),
                        tint = Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoChip(
                    label = stringResource(R.string.gender),
                    value = profile.gender.ifBlank { "--" },
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    label = stringResource(R.string.age),
                    value = if (profile.age > 0) "${profile.age}" else "--",
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    label = "身高",
                    value = if (profile.heightCm > 0) trimNumber(profile.heightCm) else "--",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (bmi == null) SurfaceVariant else bmiContainer(bmi))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (bmi == null) {
                    Text(
                        text = stringResource(R.string.bmi_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.bmi),
                                style = MaterialTheme.typography.labelMedium,
                                color = OnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = String.format("%.1f", bmi),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = BmiScale.label(bmi),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = bmiColor(bmi),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = BmiScale.hint(bmi),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.End,
                            // 不写死宽度：按剩余空间自适应，字体放大或窄屏时不再与左侧 BMI 数值争宽。
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (latestWeight != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "按最新体重 ${trimNumber(latestWeight)} kg 计算",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun bmiColor(bmi: Double) = when {
    bmi < 18.5 -> WarmAccent
    bmi < 24.0 -> IncomeGreen
    bmi < 28.0 -> WarmAccent
    else -> ExpenseRed
}

private fun bmiContainer(bmi: Double) = when {
    bmi < 18.5 -> com.feiqi.ui.theme.CardAmber
    bmi < 24.0 -> com.feiqi.ui.theme.CardGreen
    bmi < 28.0 -> com.feiqi.ui.theme.CardAmber
    else -> com.feiqi.ui.theme.CardRed
}

@Composable
private fun ProfileEditDialog(
    profile: UserProfile,
    onConfirm: (gender: String, age: String, height: String) -> Unit,
    onDismiss: () -> Unit
) {
    val maleText = stringResource(R.string.gender_male)
    val femaleText = stringResource(R.string.gender_female)
    var gender by remember { mutableStateOf(profile.gender.ifBlank { maleText }) }
    var age by remember { mutableStateOf(if (profile.age > 0) profile.age.toString() else "") }
    var height by remember {
        mutableStateOf(if (profile.heightCm > 0) trimNumber(profile.heightCm) else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_personal_info)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.gender),
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(maleText, femaleText).forEach { option ->
                        val selected = gender == option
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) PrimaryContainer else SurfaceVariant)
                                .clickable { gender = option }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) Primary else OnSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.age)) },
                    trailingIcon = { Text(stringResource(R.string.age_unit)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it },
                    label = { Text(stringResource(R.string.height_cm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(gender, age, height) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ---------------- 体重曲线 ----------------

@Composable
private fun WeightCurveCard(
    points: List<WeightPoint>,
    range: WeightChartRange,
    onRangeChange: (WeightChartRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.weight_curve),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                RangeSwitch(range = range, onRangeChange = onRangeChange)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.weight_curve_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant
                    )
                }
            } else {
                val values = points.map { it.value }
                val maxV = values.max()
                val minV = values.min()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "最高 ${trimNumber(maxV)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                    Text(
                        text = "最低 ${trimNumber(minV)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                WeightLineChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = points.first().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                    if (points.size > 2) {
                        Text(
                            text = points[points.size / 2].label,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                    if (points.size > 1) {
                        Text(
                            text = points.last().label,
                            style = MaterialTheme.typography.labelSmall,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeSwitch(
    range: WeightChartRange,
    onRangeChange: (WeightChartRange) -> Unit
) {
    val options = listOf(
        WeightChartRange.DAY to stringResource(R.string.view_day),
        WeightChartRange.MONTH to stringResource(R.string.view_month),
        WeightChartRange.YEAR to stringResource(R.string.view_year)
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { (value, label) ->
            val selected = value == range
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onRangeChange(value) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) com.feiqi.ui.theme.OnPrimary else OnSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeightLineChart(
    points: List<WeightPoint>,
    modifier: Modifier = Modifier
) {
    val lineColor = Primary
    val fillColor = PrimaryContainer
    val gridColor = Outline

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(com.feiqi.ui.theme.Background)
            .border(1.dp, gridColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        val values = points.map { it.value.toFloat() }
        val maxV = values.max()
        val minV = values.min()
        val span = if (abs(maxV - minV) < 0.01f) 1f else (maxV - minV)
        val padTop = size.height * 0.12f
        val usableHeight = size.height - padTop * 2

        // 背景网格线
        val gridCount = 3
        for (i in 0..gridCount) {
            val y = padTop + usableHeight * i / gridCount
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        if (points.size == 1) {
            val center = Offset(size.width / 2f, padTop + usableHeight / 2f)
            drawCircle(color = lineColor, radius = 6f, center = center)
            return@Canvas
        }

        val stepX = size.width / (points.size - 1).toFloat()
        val offsets = values.mapIndexed { index, v ->
            val ratio = (v - minV) / span
            Offset(stepX * index, padTop + usableHeight * (1f - ratio))
        }

        // 面积填充
        val areaPath = Path().apply {
            moveTo(offsets.first().x, size.height)
            offsets.forEach { lineTo(it.x, it.y) }
            lineTo(offsets.last().x, size.height)
            close()
        }
        drawPath(path = areaPath, color = fillColor.copy(alpha = 0.55f))

        // 折线
        val linePath = Path().apply {
            moveTo(offsets.first().x, offsets.first().y)
            offsets.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path = linePath, color = lineColor, style = Stroke(width = 4f))

        // 数据点
        offsets.forEach { offset ->
            drawCircle(color = com.feiqi.ui.theme.Surface, radius = 6f, center = offset)
            drawCircle(color = lineColor, radius = 4f, center = offset)
        }
    }
}

// ---------------- 体重录入与列表 ----------------

@Composable
private fun WeightInputCard(
    onSave: (weight: String, date: LocalDate, note: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var weight by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(DateUtils.today()) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.record_weight),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = {
                        weight = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedButton(
                    onClick = {
                        if (weight.toDoubleOrNull() == null || weight.toDoubleOrNull()!! <= 0) {
                            error = "请输入有效体重"
                            return@OutlinedButton
                        }
                        onSave(weight, selectedDate, note)
                        weight = ""
                        note = ""
                        selectedDate = DateUtils.today()
                        error = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.save))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            DatePickerField(
                date = selectedDate,
                onDateSelected = { selectedDate = it },
                label = stringResource(R.string.date),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.note_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun WeightRecordItem(
    record: WeightRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${record.weight.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Primary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${record.weight} kg",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(DateUtils.monthDay(record.date))
                        if (record.note.isNotBlank()) {
                            append(" · ")
                            append(record.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.edit),
                    tint = Primary
                )
            }
            IconButton(onClick = { showConfirm = true }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = ExpenseRed
                )
            }
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_delete_weight),
            text = stringResource(R.string.confirm_delete_weight_text, record.weight),
            onConfirm = {
                onDelete()
                showConfirm = false
            },
            onDismiss = { showConfirm = false }
        )
    }
}

@Composable
private fun WeightEditDialog(
    record: WeightRecord,
    onConfirm: (weight: Double, date: LocalDate, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    // 已编辑对象作为 key：换一条记录时表单必须刷新成新记录的数值（无 key 的 remember 会沿用旧值）。
    var weight by remember(record) { mutableStateOf(trimNumber(record.weight)) }
    var date by remember(record) { mutableStateOf(record.date) }
    var note by remember(record) { mutableStateOf(record.note) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_weight_record)) },
        text = {
            Column {
                OutlinedTextField(
                    value = weight,
                    onValueChange = {
                        weight = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DatePickerField(
                    date = date,
                    onDateSelected = { date = it },
                    label = stringResource(R.string.date),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = weight.toDoubleOrNull()
                    if (value == null || value <= 0) {
                        error = "请输入有效体重"
                        return@TextButton
                    }
                    onConfirm(value, date, note)
                }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun HealthToast(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
    ) {
        LaunchedEffect(message) {
            kotlinx.coroutines.delay(2200)
            onDismiss()
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceVariant)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** 去掉多余的小数尾巴：170.0 -> 170，62.5 -> 62.5 */
private fun trimNumber(value: Double): String {
    return if (abs(value - value.toLong()) < 0.05) {
        value.toLong().toString()
    } else {
        // 固定 Locale.US：这里是**可编辑输入框**的初值，必须与保存时的 toDoubleOrNull() 口径一致，
        // 否则在逗号小数分隔符的系统语言下会回填 "62,5"，用户不再改动就保存会解析失败。
        String.format(java.util.Locale.US, "%.1f", value)
    }
}
