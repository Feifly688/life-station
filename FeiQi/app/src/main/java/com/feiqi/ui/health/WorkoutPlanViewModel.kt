package com.feiqi.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feiqi.data.entity.WorkoutPlanEntity
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.MAX_PLAN_NAME_LENGTH
import com.feiqi.data.model.PlanDraft
import com.feiqi.data.model.PlannedDay
import com.feiqi.data.model.WorkoutGoal
import com.feiqi.data.model.WorkoutPlanSeed
import com.feiqi.data.model.WorkoutPlanType
import com.feiqi.data.model.assignCategories
import com.feiqi.data.model.isPlanDraftSaveable
import com.feiqi.data.model.isValidPlanName
import com.feiqi.data.model.layoutTrainingDays
import com.feiqi.data.repository.WorkoutCatalogRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 训练计划目录 ViewModel（v2 阶段 2）：「我的训练计划」列表 + 创建向导状态机。
 *
 * 与 [WorkoutViewModel] 职责分离：这里管**计划定义**（目录与创建），那里管**打卡事实**。
 */
class WorkoutPlanViewModel(
    private val catalogRepository: WorkoutCatalogRepository,
) : ViewModel() {

    /** 全部计划（官方 + 用户自建，按周期升序）。 */
    val plans: StateFlow<List<WorkoutPlanEntity>> = catalogRepository.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 用户自建计划。 */
    val userPlans: StateFlow<List<WorkoutPlanEntity>> = plans
        .map { list -> list.filter { it.type == WorkoutPlanType.USER_CREATED.code } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 官方计划（用于列表页顶部展示）。 */
    val officialPlans: StateFlow<List<WorkoutPlanEntity>> = plans
        .map { list -> list.filter { it.type == WorkoutPlanType.OFFICIAL.code } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- 创建向导状态 ----------------

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _goal = MutableStateFlow(WorkoutGoal.SHAPE)
    val goal: StateFlow<WorkoutGoal> = _goal

    private val _cycleDays = MutableStateFlow(30)
    val cycleDays: StateFlow<Int> = _cycleDays

    private val _frequency = MutableStateFlow(3)
    val frequency: StateFlow<Int> = _frequency

    private val _categories = MutableStateFlow<List<BodyCategory>>(emptyList())
    val categories: StateFlow<List<BodyCategory>> = _categories

    /** 当前草稿（名称 + 部位齐全后才可用于保存）。 */
    private val _draft = MutableStateFlow(
        buildDraft(" ", WorkoutGoal.SHAPE, 30, 3, emptyList())
    )
    val draft: StateFlow<PlanDraft> = _draft

    private val _saveable = MutableStateFlow(false)
    val saveable: StateFlow<Boolean> = _saveable

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    /** 计划名是否合法（UI 用于实时提示）。 */
    val nameValid: StateFlow<Boolean> = name
        .map { isValidPlanName(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---------------- 向导操作 ----------------

    fun setName(value: String) {
        val trimmed = value.take(MAX_PLAN_NAME_LENGTH)
        _name.value = trimmed
        refreshDraft()
    }

    fun setGoal(value: WorkoutGoal) {
        _goal.value = value
        refreshDraft()
    }

    fun setCycleDays(value: Int) {
        _cycleDays.value = value.coerceIn(1, 365)
        refreshDraft()
    }

    fun setFrequency(value: Int) {
        _frequency.value = value.coerceIn(1, 7)
        refreshDraft()
    }

    /** 多选部位（再次点击取消）。 */
    fun toggleCategory(category: BodyCategory) {
        val current = _categories.value
        _categories.value = if (category in current) {
            current - category
        } else {
            // 保持选择顺序稳定（按枚举声明序），避免"点了两次顺序变了"
            BodyCategory.entries.filter { it == category || it in current }
        }
        refreshDraft()
    }

    /** 撤销全部已做修改（离开创建页时调用，避免脏状态带入下次创建）。 */
    fun resetDraft() {
        _name.value = ""
        _goal.value = WorkoutGoal.SHAPE
        _cycleDays.value = 30
        _frequency.value = 3
        _categories.value = emptyList()
        refreshDraft()
    }

    private fun refreshDraft() {
        val value = buildDraft(
            name = _name.value,
            goal = _goal.value,
            cycle = _cycleDays.value,
            freq = _frequency.value,
            cats = _categories.value,
        )
        _draft.value = value
        _saveable.value = isPlanDraftSaveable(value)
    }

    private fun buildDraft(
        name: String,
        goal: WorkoutGoal,
        cycle: Int,
        freq: Int,
        cats: List<BodyCategory>,
    ): PlanDraft {
        val layout: List<PlannedDay> = layoutTrainingDays(cycle, freq)
        val ordered = if (cats.isEmpty()) cats else BodyCategory.entries.filter { it in cats }
        return PlanDraft(
            name = name,
            goal = goal,
            cycleDays = cycle,
            frequencyPerWeek = freq,
            categories = ordered,
            days = assignCategories(layout, ordered),
        )
    }

    /**
     * 保存当前草稿。
     *
     * @param onSaved 新建计划的 planId（成功时回调，供 UI 切换过去）
     * @return true = 保存成功
     */
    fun save(onSaved: (String) -> Unit) {
        val value = _draft.value
        if (!isPlanDraftSaveable(value)) {
            _events.tryEmit("请完善计划名称与训练部位")
            return
        }
        viewModelScope.launch {
            val planId = "user_" + UUID.randomUUID().toString().take(8)
            catalogRepository.saveUserPlan(value, planId)
            _events.tryEmit("计划「${value.name.trim()}」已创建")
            resetDraft()
            onSaved(planId)
        }
    }

    companion object {
        fun factory(repository: WorkoutCatalogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CHECK_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WorkoutPlanViewModel(repository) as T
            }
    }
}