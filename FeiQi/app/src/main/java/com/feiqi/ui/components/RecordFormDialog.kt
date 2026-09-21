package com.feiqi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feiqi.R
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.AccountType
import com.feiqi.data.repository.DEFAULT_EXPENSE_CATEGORIES
import com.feiqi.data.repository.DEFAULT_INCOME_CATEGORIES
import com.feiqi.ui.theme.Primary
import com.feiqi.utils.DateUtils
import java.time.LocalDateTime

/**
 * 记账记录编辑弹窗（新增/编辑共用）。
 * 提升为共享组件，首页「最近记账」与记账页列表均可复用，避免重复实现。
 */
@Composable
fun RecordFormDialog(
    record: AccountRecord?,
    onDismiss: () -> Unit,
    onSave: (AccountType, String, String, LocalDateTime, String) -> Unit
) {
    var type by remember(record) { mutableStateOf(record?.type ?: AccountType.EXPENSE) }
    var amount by remember(record) { mutableStateOf(record?.amount?.let { "%.2f".format(it) } ?: "") }
    var category by remember(record) {
        mutableStateOf(record?.category ?: DEFAULT_EXPENSE_CATEGORIES.first())
    }
    var dateTime by remember(record) { mutableStateOf(record?.dateTime ?: DateUtils.now()) }
    var note by remember(record) { mutableStateOf(record?.note ?: "") }
    val categories = if (type == AccountType.EXPENSE) DEFAULT_EXPENSE_CATEGORIES else DEFAULT_INCOME_CATEGORIES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit)) },
        text = {
            Column {
                TabRow(
                    selectedTabIndex = if (type == AccountType.EXPENSE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Primary
                ) {
                    Tab(
                        selected = type == AccountType.EXPENSE,
                        onClick = { type = AccountType.EXPENSE },
                        text = { Text(stringResource(R.string.expense)) }
                    )
                    Tab(
                        selected = type == AccountType.INCOME,
                        onClick = { type = AccountType.INCOME },
                        text = { Text(stringResource(R.string.income)) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                CategoryDropdown(
                    options = categories,
                    selected = category,
                    onSelected = { category = it },
                    label = stringResource(R.string.category),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                DateTimePickerField(
                    dateTime = dateTime,
                    onDateTimeSelected = { dateTime = it },
                    dateLabel = stringResource(R.string.date),
                    timeLabel = stringResource(R.string.time)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(type, amount, category, dateTime, note) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
