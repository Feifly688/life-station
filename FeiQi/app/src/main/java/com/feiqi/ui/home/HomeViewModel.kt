package com.feiqi.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.HomeUiState
import com.feiqi.data.model.Media
import com.feiqi.data.model.Schedule
import com.feiqi.data.model.ScheduleListItem
import com.feiqi.data.model.ShoppingItem
import com.feiqi.data.repository.AccountRepository
import com.feiqi.data.repository.MediaRepository
import com.feiqi.data.repository.PreferencesRepository
import com.feiqi.data.repository.ScheduleRepository
import com.feiqi.utils.DateUtils
import com.feiqi.utils.Quotes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class HomeViewModel(
    private val accountRepository: AccountRepository,
    private val scheduleRepository: ScheduleRepository,
    private val preferencesRepository: PreferencesRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val today = DateUtils.today()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val recentRecords = accountRepository.getRecent(5)
    private val monthExpense = accountRepository.getExpenseForMonth(today)
    private val monthIncome = accountRepository.getIncomeForMonth(today)
    private val lastMonthExpense = accountRepository.getExpenseForMonth(today.minusMonths(1))
    private val budget = preferencesRepository.monthlyBudget
    private val latestWeight = preferencesRepository.latestWeight
    // 首页今日待办需要同时联动「单条日程」与「待办清单」，所以取全量后在内存里聚合。
    private val allSchedules = scheduleRepository.getAll()
    private val shoppingItems = preferencesRepository.shoppingItems
    private val recentMedia = mediaRepository.getRecent(4)

    val uiState: StateFlow<HomeUiState> = combine(
        recentRecords,
        monthExpense,
        monthIncome,
        lastMonthExpense,
        budget,
        latestWeight,
        allSchedules,
        shoppingItems,
        recentMedia
    ) { arrays ->
        val records = arrays[0] as List<*>
        val expense = arrays[1] as Double
        val income = arrays[2] as Double
        @Suppress("UNUSED_VARIABLE")
        val lastExpense = arrays[3] as Double
        val budgetValue = arrays[4] as Double
        val weight = arrays[5] as? com.feiqi.data.model.WeightRecord
        @Suppress("UNCHECKED_CAST")
        val schedules = arrays[6] as List<Schedule>
        @Suppress("UNCHECKED_CAST")
        val shopping = arrays[7] as List<ShoppingItem>
        @Suppress("UNCHECKED_CAST")
        val media = arrays[8] as List<Media>

        val used = if (budgetValue > 0) (expense / budgetValue).toFloat().coerceIn(0f, 1f) else 0f
        val todayItems = buildTodayItems(schedules, today)
        val pendingCount = todayItems.sumOf { item ->
            when (item) {
                is ScheduleListItem.Single -> if (item.schedule.completed) 0 else 1
                is ScheduleListItem.Group -> item.items.count { !it.completed }
            }
        }

        HomeUiState(
            greeting = DateUtils.greeting(),
            dateLabel = buildDateLabel(today),
            atmosphere = DateUtils.atmosphere(today),
            lifeIndex = computeLifeIndex(expense, budgetValue, used),
            monthExpense = expense,
            monthIncome = income,
            monthBalance = income - expense,
            budgetUsedPercent = used,
            todayTodoCount = pendingCount,
            todayTodoItems = todayItems,
            latestWeight = weight,
            recentMedia = media,
            shoppingToBuy = shopping.filter { !it.bought }.sortedByDescending { it.createdAt },
            shoppingBought = shopping.filter { it.bought }
                .sortedByDescending { it.boughtAt ?: it.createdAt },
            recentRecords = records.filterIsInstance<com.feiqi.data.model.AccountRecord>(),
            quote = Quotes.daily(today).text
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    // ---------------- 购买物品 ----------------

    fun addShoppingItem(name: String, priceText: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch { _events.emit("请填写物品名称") }
            return
        }
        val price = priceText.trim().toDoubleOrNull() ?: 0.0
        viewModelScope.launch {
            runCatching { preferencesRepository.addShoppingItem(trimmed, price) }
                .onSuccess { _events.emit("已加入待买") }
                .onFailure { _events.emit("添加失败：${it.message}") }
        }
    }

    fun toggleShoppingItem(id: String) {
        viewModelScope.launch {
            runCatching {
                val target = preferencesRepository.shoppingItems.first().firstOrNull { it.id == id }
                preferencesRepository.toggleShoppingItem(id)
                target
            }.onSuccess { target ->
                if (target != null) {
                    _events.emit(if (target.bought) "已移回待买" else "已买到，移入已买")
                }
            }.onFailure { _events.emit("操作失败：${it.message}") }
        }
    }

    fun deleteShoppingItem(id: String) {
        viewModelScope.launch {
            runCatching { preferencesRepository.deleteShoppingItem(id) }
                .onSuccess { _events.emit("已删除") }
                .onFailure { _events.emit("删除失败：${it.message}") }
        }
    }

    fun clearBoughtItems() {
        viewModelScope.launch {
            runCatching { preferencesRepository.clearBoughtItems() }
                .onSuccess { _events.emit("已清空已买") }
                .onFailure { _events.emit("清空失败：${it.message}") }
        }
    }

    // ---------------- 记账记录编辑（首页「最近记账」点按编辑） ----------------

    fun saveRecord(record: AccountRecord) {
        if (record.amount <= 0) {
            viewModelScope.launch { _events.emit("金额需大于 0") }
            return
        }
        viewModelScope.launch {
            runCatching {
                if (record.id == 0L) accountRepository.insert(record) else accountRepository.update(record)
            }.onSuccess { _events.emit("已保存") }
                .onFailure { _events.emit("保存失败：${it.message}") }
        }
    }

    // ---------------- 今日待办（单条 + 清单） ----------------

    /**
     * 把「今天」相关的日程聚合成首页展示项：
     * - 单条日程：日期为今天，或已逾期未完成
     * - 待办清单：清单内存在今天 / 逾期未完成的条目，就整份展示
     */
    private fun buildTodayItems(all: List<Schedule>, today: LocalDate): List<ScheduleListItem> {
        val (withList, singles) = all.partition { it.listId != null }

        val singleItems = singles
            .filter { it.date == today || (it.date < today && !it.completed) }
            .sortedWith(compareBy({ it.completed }, { it.time ?: LocalTime.MAX }))
            .map { ScheduleListItem.Single(it) }

        val groups = withList.groupBy { it.listId!! }
            .filterValues { items ->
                items.any { it.date == today || (it.date < today && !it.completed) }
            }
            .map { (listId, items) ->
                ScheduleListItem.Group(
                    listId = listId,
                    title = items.firstOrNull { it.listTitle.isNotBlank() }?.listTitle
                        ?: items.first().title,
                    items = items.sortedBy { it.itemOrder },
                    isRecurring = items.any { it.isRecurring }
                )
            }
            .sortedWith(
                compareByDescending<ScheduleListItem.Group> { !it.allCompleted }
                    .thenBy { it.title }
            )

        return singleItems + groups
    }

    private fun buildDateLabel(date: LocalDate): String {
        val week = when (date.dayOfWeek.value) {
            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
        }
        return "${date.monthValue}月${date.dayOfMonth}日 · 星期$week"
    }

    private fun computeLifeIndex(expense: Double, budget: Double, used: Float): Int {
        if (budget <= 0) return 60
        val base = 100 - (used * 60).toInt()
        return base.coerceIn(55, 100)
    }
}
