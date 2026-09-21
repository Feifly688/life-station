package com.feiqi.data.model

data class MonthSummary(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val budget: Double = 5000.0,
    val lastMonthExpense: Double = 0.0
) {
    /** 收支结余：收入 - 支出。 */
    val balance: Double
        get() = income - expense

    /**
     * 本月剩余（预算维度）：预算 - 已支出。
     * 可为负值，表示已超支；随预算调整实时联动。
     */
    val budgetRemaining: Double
        get() = budget - expense

    /** 是否已超出本月预算。 */
    val isOverBudget: Boolean
        get() = budget > 0 && expense > budget

    val budgetUsedPercent: Float
        get() = if (budget > 0) ((expense / budget).toFloat().coerceIn(0f, 1f)) else 0f

    val diffFromLastMonth: Double
        get() = expense - lastMonthExpense
}
