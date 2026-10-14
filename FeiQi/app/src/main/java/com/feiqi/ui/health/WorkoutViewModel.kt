package com.feiqi.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.WORKOUT_PLANS
import com.feiqi.data.model.WorkoutPlan
import com.feiqi.data.model.WorkoutPlanSeed
import com.feiqi.data.model.WorkoutProgress
import com.feiqi.data.model.computeStreak
import com.feiqi.data.model.computeWorkoutProgress
import com.feiqi.data.model.planOfDay
import com.feiqi.data.repository.WorkoutCatalogRepository
import com.feiqi.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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