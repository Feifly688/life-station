package com.feiqi.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.MAX_DAILY_EXERCISES
import com.feiqi.data.model.ExerciseSearchResult
import com.feiqi.data.model.exercisesOfCategory
import com.feiqi.data.model.filterExercises
import com.feiqi.data.repository.WorkoutCatalogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 动作库 ViewModel（v2 阶段 3）。
 *
 * 职责：全库动作的搜索 / 分类筛选 / 详情读取 + 创建计划时的动作勾选。
 * **只读**动作库，不新增 / 不修改动作（内容由 `seedExerciseLibrary` 维护）。
 */
class ExerciseViewModel(
    private val catalogRepository: WorkoutCatalogRepository,
) : ViewModel() {

    /** 全库动作。 */
    val allExercises: StateFlow<List<ExerciseEntity>> = catalogRepository.observeExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- 动作库主页：搜索 + 分类 ----------------

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    private val _selectedCategory = MutableStateFlow<BodyCategory?>(null)
    val selectedCategory: StateFlow<BodyCategory?> = _selectedCategory.asStateFlow()

    /** 搜索 + 分类过滤结果（分类卡与动作列表共用，保证计数一致）。 */
    val filtered: StateFlow<ExerciseSearchResult> =
        combine(allExercises, _keyword, _selectedCategory) { all, keyword, category ->
            filterExercises(all, keyword, category)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ExerciseSearchResult(emptyList(), emptyList()),
        )

    fun setKeyword(value: String) {
        _keyword.value = value
    }

    fun selectCategory(category: BodyCategory?) {
        _selectedCategory.value = category
    }

    /** 动作库为空（种子尚未跑完 / 异常）。 */
    val isEmptyLibrary: StateFlow<Boolean> = allExercises
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---------------- 动作详情 ----------------

    /** 按 id 观察单个动作（详情页）。 */
    fun exerciseById(id: Long): StateFlow<ExerciseEntity?> =
        catalogRepository.observeExerciseById(id)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---------------- 动作选择（创建计划联动） ----------------

    private val _pickedNames = MutableStateFlow<Set<String>>(emptySet())
    val pickedNames: StateFlow<Set<String>> = _pickedNames.asStateFlow()

    /** 进入选择页时重置（清掉上一次的勾选）。 */
    fun resetPicked(initial: Set<String> = emptySet()) {
        _pickedNames.value = initial
    }

    /**
     * 切换某个动作的勾选。
     *
     * 超过 [com.feiqi.data.model.MAX_DAILY_EXERCISES] 时**拒绝**并返回 false，
     * 由 UI 提示，避免用户点了没反应。
     */
    fun togglePicked(name: String): Boolean {
        val current = _pickedNames.value
        if (name in current) {
            _pickedNames.value = current - name
            return true
        }
        if (current.size >= MAX_DAILY_EXERCISES) return false
        _pickedNames.value = current + name
        return true
    }

    /** 某一大类下的动作（动作选择页按大类分组展示）。 */
    fun exercisesOf(category: BodyCategory): StateFlow<List<ExerciseEntity>> = allExercises
        .map { all -> exercisesOfCategory(all, category) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: WorkoutCatalogRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CHECK_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ExerciseViewModel(repository) as T
            }
    }
}