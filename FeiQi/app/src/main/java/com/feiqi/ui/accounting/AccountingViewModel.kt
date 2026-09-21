package com.feiqi.ui.accounting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.AccountType
import com.feiqi.data.model.AccountingUiState
import com.feiqi.data.model.MonthSummary
import com.feiqi.data.repository.AccountRepository
import com.feiqi.data.repository.PreferencesRepository
import com.feiqi.utils.DateUtils
import com.feiqi.utils.ExcelExporter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class AccountingViewModel(
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository,
    private val excelExporter: ExcelExporter
) : ViewModel() {

    private val currentMonth = DateUtils.today()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val records = accountRepository.getRecordsForMonth(currentMonth)
    private val categorySums = accountRepository.getCategorySumsForMonth(currentMonth)
    private val monthExpense = accountRepository.getExpenseForMonth(currentMonth)
    private val monthIncome = accountRepository.getIncomeForMonth(currentMonth)
    private val lastMonthExpense = accountRepository.getExpenseForMonth(currentMonth.minusMonths(1))
    private val budget = preferencesRepository.monthlyBudget
    private val hasRecordToday = accountRepository.hasRecordOnDate(DateUtils.today())

    val uiState: StateFlow<AccountingUiState> = combine(
        records,
        categorySums,
        monthExpense,
        monthIncome,
        lastMonthExpense,
        budget,
        hasRecordToday
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val recs = values[0] as List<AccountRecord>
        val cats = values[1] as List<com.feiqi.data.model.CategorySum>
        val expense = values[2] as Double
        val income = values[3] as Double
        val lastExpense = values[4] as Double
        val budgetValue = values[5] as Double
        val recorded = values[6] as Boolean
        AccountingUiState(
            monthSummary = MonthSummary(
                income = income,
                expense = expense,
                budget = budgetValue,
                lastMonthExpense = lastExpense
            ),
            categorySums = cats,
            groupedRecords = recs.groupBy { DateUtils.friendly(it.date) },
            hasRecordToday = recorded
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountingUiState()
    )

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm

    private val _editingRecord = MutableStateFlow<AccountRecord?>(null)
    val editingRecord: StateFlow<AccountRecord?> = _editingRecord

    /** 仅用于长按编辑已有记录，新增走页面内的常驻表单。 */
    fun openForm(record: AccountRecord) {
        _editingRecord.value = record
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
        _editingRecord.value = null
    }

    fun saveRecord(
        type: AccountType,
        amountText: String,
        category: String,
        dateTime: LocalDateTime,
        note: String
    ) {
        val amount = amountText.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            viewModelScope.launch { _events.emit("金额需大于 0") }
            return
        }
        val editing = _editingRecord.value
        val record = editing?.copy(
            type = type,
            amount = amount,
            category = category,
            dateTime = dateTime,
            note = note
        ) ?: AccountRecord(
            type = type,
            amount = amount,
            category = category,
            dateTime = dateTime,
            note = note
        )
        viewModelScope.launch {
            runCatching {
                if (record.id == 0L) accountRepository.insert(record) else accountRepository.update(record)
            }.onSuccess {
                _events.emit("已保存")
                closeForm()
            }.onFailure {
                _events.emit("保存失败：${it.message}")
            }
        }
    }

    fun deleteRecord(record: AccountRecord) {
        viewModelScope.launch {
            runCatching { accountRepository.delete(record) }
                .onSuccess { _events.emit("已删除") }
                .onFailure { _events.emit("删除失败：${it.message}") }
        }
    }

    fun setMonthlyBudget(text: String) {
        val normalized = text.replace(",", "").replace(" ", "").trim()
        val value = normalized.toDoubleOrNull()
        if (value == null || value < 0) {
            viewModelScope.launch { _events.emit("请输入有效的预算金额") }
            return
        }
        viewModelScope.launch {
            runCatching { preferencesRepository.setMonthlyBudget(value) }
                .onSuccess { _events.emit("预算已更新") }
                .onFailure { _events.emit("保存失败：${it.message}") }
        }
    }

    fun exportExcel() {
        viewModelScope.launch {
            runCatching {
                val records = accountRepository.getAllOnce()
                if (records.isEmpty()) throw IllegalStateException("暂无数据可导出")
                excelExporter.exportAccounts(records).getOrThrow()
            }.onSuccess { path ->
                _events.emit("已导出 Excel：$path")
            }.onFailure { e ->
                _events.emit("导出失败：${e.message ?: "请检查存储权限后重试"}")
            }
        }
    }
}
