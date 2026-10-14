package com.feiqi.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feiqi.data.entity.WorkoutRecordEntity
import com.feiqi.data.model.MonthProgress
import com.feiqi.data.model.WORKOUT_PLANS
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.data.model.WorkoutPlanSeed
import com.feiqi.data.model.WorkoutProgress
import com.feiqi.data.model.computeCalendarStreak
import com.feiqi.data.model.computeStreak
import com.feiqi.data.model.computeWorkoutProgress
import com.feiqi.data.model.estimateDaySeconds
import com.feiqi.data.model.monthProgress
import com.feiqi.data.model.planOfDay
import com.feiqi.data.repository.WorkoutCatalogRepository
import com.feiqi.data.repository.WorkoutRepository
import com.feiqi.utils.DateUtils
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 健身主页 ViewModel（v2 阶段 2：**多计划**）。
 *
 * ## 与阶段 1 的差别
 * - 计划不再写死：新增 [activePlanId]，主页 / 进度 / 历史网格全部跟随当前计划；
 * - 训练日内容从静态 `WORKOUT_PLANS` 改为 [runtimePlan]（官方走静态，用户计划走 Room）；
 * - 打卡按当前 planId 落库；官方计划的读取会自动合并旧 id（见 [WorkoutRepository]）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModel(
    private val repository: WorkoutRepository,
    private val catalogRepository: WorkoutCatalogRepository,
) : ViewModel() {

    /** 当前训练的计划 id（默认官方 30 天计划）。 */
    private val _activePlanId = MutableStateFlow(WorkoutPlanSeed.OFFICIAL_PLAN_ID)

    val activePlanId: StateFlow<String> = _activePlanId

    /** 切换当前训练的计划。 */
    fun selectPlan(planId: String) {
        _activePlanId.value = planId
    }

    /** 当前计划的训练日（官方 = 静态常量；用户计划 = Room 装配）。 */
    val runtimePlan: StateFlow<List<WorkoutPlan>> = _activePlanId
        .flatMapLatest { planId -> flow { emit(catalogRepository.loadRuntimePlan(planId).orEmpty()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORKOUT_PLANS)

    /** 当前计划总天数。 */
    val totalDays: StateFlow<Int> = runtimePlan
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WORKOUT_PLANS.size)

    /** 已完成的天数（跟随当前计划，官方含旧 id 合并）。 */
    val completedDays: StateFlow<Set<Int>> = _activePlanId
        .flatMapLatest { planId -> repository.completedDays(planId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 进度（当前训练天数 / 完成次数 / 百分比）。 */
    val progress: StateFlow<WorkoutProgress> =
        combine(completedDays, totalDays) { days, total -> computeWorkoutProgress(days, total) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                computeWorkoutProgress(emptySet(), WORKOUT_PLANS.size),
            )

    /** 连续训练天数。 */
    val streak: StateFlow<Int> =
        combine(completedDays, totalDays, progress) { days, total, p ->
            computeStreak(days, p.currentDay, total)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 已完成的天（历史网格打勾用）。 */
    val completedDayList: StateFlow<List<Int>> = completedDays
        .map { it.toList().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- v2 阶段 4：跨计划统计 ----------------

    /**
     * **全部计划**的打卡记录。
     *
     * 顶部「连续训练 / 本月完成 / 累计时长」都是跨计划口径，换计划不清零。
     */
    val allRecords: StateFlow<List<WorkoutRecordEntity>> = repository.allRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 全部打卡日期（解析失败 records 直接跳过，不让一条脏数据毁掉整个统计）。 */
    private val allDates: StateFlow<List<LocalDate>> = allRecords
        .map { records -> records.mapNotNull { record -> runCatching { DateUtils.parseIso(record.date) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** **真实日历**连续训练天数（跨全部计划）。 */
    val calendarStreak: StateFlow<Int> = allDates
        .map { dates -> computeCalendarStreak(dates, DateUtils.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 本月训练进度（分母 = 当月自然天数）。 */
    val monthProgress: StateFlow<MonthProgress> = allDates
        .map { dates -> com.feiqi.data.model.monthProgress(dates, DateUtils.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthProgress(0, DateUtils.today().lengthOfMonth()))

    /**
     * `planId → 训练日列表` 缓存，供累计时长估算用。
     *
     * 只需覆盖**有打卡记录**的计划，故只加载 `allRecords` 里出现过的 planId。
     */
    private val planCache: StateFlow<Map<String, List<WorkoutPlan>>> =
        allRecords
            .map { records -> records.map { it.planId }.distinct() }
            .distinctUntilChanged()
            .mapLatest { planIds ->
                val loaded = planIds.associateWith { id ->
                    runCatching { catalogRepository.loadRuntimePlan(id).orEmpty() }.getOrDefault(emptyList())
                }
                loaded
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * 累计训练时长（**估算**，秒）。
     *
     * 打卡记录只存「第几天」，需按记录的 `planId` 找到对应训练日再估算；
     * 计划数据取不到时该条按 0 计（不猜、不阻塞）。
     */
    val totalEstimatedSeconds: StateFlow<Int> = combine(allRecords, planCache) { records, cache ->
        var total = 0
        val seen = HashSet<String>()
        for (record in records) {
            // 同一 (planId, dayNumber) 只计一次：官方计划的别名 id 会写出两条记录
            if (!seen.add("${record.planId}#${record.dayNumber}")) continue
            val day = cache[record.planId]?.firstOrNull { it.dayNumber == record.dayNumber }
            total += estimateDaySeconds(day)
        }
        total
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 当前计划第 N 天的估算时长（秒）；无数据返回 0。 */
    fun estimateSecondsOfDay(dayNumber: Int): Int =
        estimateDaySeconds(runtimePlan.value.firstOrNull { it.dayNumber == dayNumber })

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** 一次性提示（打卡成功 / 取消打卡）。 */
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** 今日训练（按当前进度推导；走完停在最后一天）。 */
    fun todayPlan(runtime: List<WorkoutPlan>): WorkoutPlan? =
        runtime.firstOrNull { it.dayNumber == progress.value.currentDay } ?: runtime.lastOrNull()

    /** 官方 30 天计划的第 N 天（兼容旧调用）。 */
    fun officialPlanOfDay(dayNumber: Int): WorkoutPlan? = planOfDay(dayNumber)

    /** 打卡某天（写入当前计划）。 */
    fun checkIn(dayNumber: Int) {
        viewModelScope.launch {
            repository.checkIn(_activePlanId.value, dayNumber)
            _events.tryEmit("Day $dayNumber 打卡完成，继续保持")
        }
    }

    /** 取消某天打卡（当前计划）。 */
    fun undo(dayNumber: Int) {
        viewModelScope.launch {
            repository.undo(_activePlanId.value, dayNumber)
            _events.tryEmit("已取消 Day $dayNumber 的打卡")
        }
    }

    companion object {
        /** 工厂（手动装配风格，与其它模块一致）。 */
        fun factory(
            repository: WorkoutRepository,
            catalogRepository: WorkoutCatalogRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CHECK_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WorkoutViewModel(repository, catalogRepository) as T
        }
    }
}