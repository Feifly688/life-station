package com.feiqi.ui.accounting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feiqi.R
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.AccountType
import com.feiqi.data.model.CategorySum
import com.feiqi.data.repository.DEFAULT_EXPENSE_CATEGORIES
import com.feiqi.data.repository.DEFAULT_INCOME_CATEGORIES
import com.feiqi.ui.components.AccountListItem
import com.feiqi.ui.components.CategoryLegend
import com.feiqi.ui.components.DateTimePickerField
import com.feiqi.ui.components.RecordFormDialog
import com.feiqi.ui.components.DonutChart
import com.feiqi.ui.components.EmptyState
import com.feiqi.ui.components.SwipeToDeleteContainer
import com.feiqi.ui.theme.CardGreen
import com.feiqi.ui.theme.ExpenseRed
import com.feiqi.ui.theme.IncomeGreen
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils
import java.text.DecimalFormat
import java.time.LocalDateTime

@Composable
fun AccountingScreen(
    viewModel: AccountingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val editingRecord by viewModel.editingRecord.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
    ) {
        item {
            Text(
                text = "记账理财",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        item {
            OutlinedButton(
                onClick = { viewModel.exportExcel() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Assessment,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(stringResource(R.string.export_excel))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!uiState.hasRecordToday) {
            item {
                NoRecordHintCard()
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        item {
            Text(
                text = "记一笔账",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            CompactAddForm(
                onSubmit = { type, amount, category, dateTime, note ->
                    viewModel.saveRecord(type, amount, category, dateTime, note)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            SummaryCards(uiState.monthSummary)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            CompareCard(uiState.monthSummary)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            BudgetCard(uiState.monthSummary)
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            BudgetSettingCard(
                current = uiState.monthSummary.budget,
                onSave = { viewModel.setMonthlyBudget(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            ChartCard(
                categories = uiState.categorySums,
                total = uiState.monthSummary.expense
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Text(
                text = stringResource(R.string.recent_bills),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (uiState.groupedRecords.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.empty_state_title),
                    description = stringResource(R.string.empty_state_desc)
                )
            }
        } else {
            uiState.groupedRecords.forEach { (group, records) ->
                item {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(records, key = { it.id }) { record ->
                    SwipeToDeleteContainer(
                        onDelete = { viewModel.deleteRecord(record) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        AccountListItem(
                            record = record,
                            onClick = { viewModel.openForm(record) },
                            onLongClick = { viewModel.openForm(record) }
                        )
                    }
                }
            }
        }
    }

    if (showForm && editingRecord != null) {
        RecordFormDialog(
            record = editingRecord,
            onDismiss = { viewModel.closeForm() },
            onSave = { type, amount, category, dateTime, note ->
                viewModel.saveRecord(type, amount, category, dateTime, note)
            }
        )
    }
}

@Composable
private fun CompactAddForm(
    onSubmit: (AccountType, String, String, LocalDateTime, String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var type by remember { mutableStateOf(AccountType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DEFAULT_EXPENSE_CATEGORIES.first()) }
    var dateTime by remember { mutableStateOf(DateUtils.now()) }
    var note by remember { mutableStateOf("") }

    val categories = if (type == AccountType.EXPENSE) DEFAULT_EXPENSE_CATEGORIES else DEFAULT_INCOME_CATEGORIES

    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TabRow(
                selectedTabIndex = if (type == AccountType.EXPENSE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Primary
            ) {
                Tab(
                    selected = type == AccountType.EXPENSE,
                    onClick = { type = AccountType.EXPENSE; category = DEFAULT_EXPENSE_CATEGORIES.first() },
                    text = { Text(stringResource(R.string.expense)) }
                )
                Tab(
                    selected = type == AccountType.INCOME,
                    onClick = { type = AccountType.INCOME; category = DEFAULT_INCOME_CATEGORIES.first() },
                    text = { Text(stringResource(R.string.income)) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() || it == '.' }
                    val parts = filtered.split('.')
                    amount = when {
                        filtered.count { it == '.' } > 1 -> amount
                        parts.size == 2 && parts[1].length > 2 -> amount
                        else -> filtered
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.amount)) },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(12.dp))
            com.feiqi.ui.components.CategoryDropdown(
                options = categories,
                selected = category,
                onSelected = { category = it },
                label = stringResource(R.string.category),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            DateTimePickerField(
                dateTime = dateTime,
                onDateTimeSelected = { dateTime = it },
                dateLabel = stringResource(R.string.date),
                timeLabel = stringResource(R.string.time)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.record_time_hint, DateUtils.fullDateTime(dateTime)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.note)) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSubmit(type, amount, category, dateTime, note)
                    amount = ""
                    note = ""
                    category = if (type == AccountType.EXPENSE) {
                        DEFAULT_EXPENSE_CATEGORIES.first()
                    } else {
                        DEFAULT_INCOME_CATEGORIES.first()
                    }
                    dateTime = DateUtils.now()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.add_record))
            }
        }
    }
}

@Composable
private fun SummaryCards(summary: com.feiqi.data.model.MonthSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SummaryCard(
            label = stringResource(R.string.month_income),
            value = "¥${formatMoney(summary.income)}",
            modifier = Modifier.weight(1f),
            containerColor = IncomeGreen.copy(alpha = 0.12f)
        )
        SummaryCard(
            label = stringResource(R.string.month_expense),
            value = "¥${formatMoney(summary.expense)}",
            modifier = Modifier.weight(1f),
            containerColor = ExpenseRed.copy(alpha = 0.12f)
        )
        // 本月剩余 = 预算 - 已支出，随预算调整实时联动；超支显示负数并标红
        val remaining = summary.budgetRemaining
        SummaryCard(
            label = stringResource(R.string.month_balance),
            value = if (remaining < 0) {
                "-¥${formatMoney(kotlin.math.abs(remaining))}"
            } else {
                "¥${formatMoney(remaining)}"
            },
            modifier = Modifier.weight(1f),
            containerColor = if (remaining < 0) {
                ExpenseRed.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            valueColor = if (remaining < 0) ExpenseRed else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier.height(88.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompareCard(summary: com.feiqi.data.model.MonthSummary) {
    val diff = summary.diffFromLastMonth
    val text = if (diff >= 0) {
        stringResource(R.string.more_than_last_month, formatMoney(diff))
    } else {
        stringResource(R.string.less_than_last_month, formatMoney(kotlin.math.abs(diff)))
    }
    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.compare_last_month),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            val percent = if (summary.lastMonthExpense > 0) {
                ((summary.expense - summary.lastMonthExpense) / summary.lastMonthExpense * 100).toInt()
            } else null
            Text(
                // 上月无支出时不做对比（原本显示「↑ 0%」，看起来像"增长了 0%"的假结论）。
                text = when {
                    percent == null -> stringResource(R.string.no_compare_data)
                    percent >= 0 -> "↑ $percent%"
                    else -> "↓ ${kotlin.math.abs(percent)}%"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    percent >= 0 -> ExpenseRed
                    else -> IncomeGreen
                }
            )
        }
    }
}

@Composable
private fun BudgetCard(summary: com.feiqi.data.model.MonthSummary) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.monthly_budget),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "预算 ¥${formatMoney(summary.budget)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { summary.budgetUsedPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.budget_used, (summary.budgetUsedPercent * 100).toInt().toString()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val budgetLeft = summary.budgetRemaining
                Text(
                    text = if (budgetLeft < 0) {
                        stringResource(R.string.budget_over, formatMoney(kotlin.math.abs(budgetLeft)))
                    } else {
                        stringResource(R.string.budget_remaining, formatMoney(budgetLeft))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (budgetLeft < 0) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BudgetSettingCard(
    current: Double,
    onSave: (String) -> Unit
) {
    // 以 current 为 key：预算保存成功后输入框同步显示最新值，避免残留旧输入
    var text by remember(current) {
        mutableStateOf(if (current > 0) String.format(java.util.Locale.US, "%.2f", current) else "")
    }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.monthly_budget_setting),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new.filter { it.isDigit() || it == '.' }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.monthly_budget)) },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.budget_input_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onSave(text) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun ChartCard(categories: List<CategorySum>, total: Double) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.consumption_structure),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (categories.isEmpty()) {
                EmptyState(title = "暂无支出", description = "记录一笔支出后即可看到消费结构。")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(
                        categories = categories,
                        total = total,
                        centerText = "本月支出\n¥${formatMoney(total)}",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    CategoryLegend(
                        categories = categories,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoRecordHintCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CardGreen),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "今天还没记账",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "有空时补一笔，让月度趋势保持完整。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMoney(value: Double): String {
    return DecimalFormat("#,##0.00").format(value)
}
