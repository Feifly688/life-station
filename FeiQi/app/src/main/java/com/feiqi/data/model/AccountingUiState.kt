package com.feiqi.data.model

data class AccountingUiState(
    val monthSummary: MonthSummary = MonthSummary(),
    val categorySums: List<CategorySum> = emptyList(),
    val groupedRecords: Map<String, List<AccountRecord>> = emptyMap(),
    val hasRecordToday: Boolean = true,
    val editingRecord: AccountRecord? = null,
    val showForm: Boolean = false
)
