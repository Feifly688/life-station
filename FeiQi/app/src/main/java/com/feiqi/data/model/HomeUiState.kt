package com.feiqi.data.model

/**
 * 首页 UI 状态。
 * 注意：「习惯」功能已移除，首页不再展示习惯数据；
 * 健康/体重记录通过 [latestWeight] 展示入口。
 */
data class HomeUiState(
    val greeting: String = "",
    val dateLabel: String = "",
    val atmosphere: String = "",
    val lifeIndex: Int = 60,
    val monthExpense: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthBalance: Double = 0.0,
    val budgetUsedPercent: Float = 0f,
    /** 今日未完成待办条数（含清单内条目）。 */
    val todayTodoCount: Int = 0,
    /** 今日待办：单条日程 + 待办清单聚合后的结果。 */
    val todayTodoItems: List<ScheduleListItem> = emptyList(),
    val latestWeight: WeightRecord? = null,
    /** 书影音：最近添加的作品（首页展示，点击进入书影音页）。 */
    val recentMedia: List<Media> = emptyList(),
    /** 购买物品：待买。 */
    val shoppingToBuy: List<ShoppingItem> = emptyList(),
    /** 购买物品：已买。 */
    val shoppingBought: List<ShoppingItem> = emptyList(),
    val recentRecords: List<AccountRecord> = emptyList(),
    val quote: String = "今天，慢慢来。",
    /** 语录作者（内置语录没有作者，远程语录集可能提供）。 */
    val quoteAuthor: String? = null
)
