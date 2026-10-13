package com.feiqi.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.PLAN_ID
import com.feiqi.data.model.WORKOUT_PLANS
import com.feiqi.data.model.WorkoutProgress
import com.feiqi.data.model.computeStreak
import com.feiqi.data.model.computeWorkoutProgress
import com.feiqi.data.model.planOfDay
import com.feiqi.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 健身（30 天居家训练）ViewModel。
 *
 * 只做三件事：读打卡记录 → 派生进度/连续天数；打卡/取消打卡。
 * 训练计划是静态数据（`WORKOUT_PLANS`），**不经过 ViewModel 变更**。
 */
class WorkoutViewModel(private val repository: WorkoutRepository) : ViewModel() {

    /** 已完成的天数。 */
    val completedDays: StateFlow<Set<Int>> = repository.completedDays(PLAN_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 进度（当前训练天数 / 完成次数 / 百分比）。 */
    val progress: StateFlow<WorkoutProgress> = completedDays
        .map { computeWorkoutProgress(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), computeWorkoutProgress(emptySet()))

    /** 连续训练天数。 */
    val streak: StateFlow<Int> = completedDays
        .map { computeStreak(it, computeWorkoutProgress(it).currentDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 已完成的天（用于历史记录打勾）。 */
    val completedDayList: StateFlow<List<Int>> = completedDays
        .map { it.toList().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** 一次性提示（打卡成功 / 取消打卡）。 */
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** 今日训练计划（按进度推导，30 天走完后停在最后一天）。 */
    val todayPlan = progress.value.let { planOfDay(it.currentDay) }
        ?: WORKOUT_PLANS.last()

    /** 打卡某天。 */
    fun checkIn(dayNumber: Int) {
        viewModelScope.launch {
            repository.checkIn(PLAN_ID, dayNumber)
            _events.tryEmit("Day $dayNumber 打卡完成，继续保持")
        }
    }

    /** 取消某天打卡。 */
    fun undo(dayNumber: Int) {
        viewModelScope.launch {
            repository.undo(PLAN_ID, dayNumber)
            _events.tryEmit("已取消 Day $dayNumber 的打卡")
        }
    }

    companion object {
        /** 工厂（与其他模块一致的手动装配风格）。 */
        fun factory(repository: WorkoutRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CHECK_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutViewModel(repository) as T
            }
    }
}