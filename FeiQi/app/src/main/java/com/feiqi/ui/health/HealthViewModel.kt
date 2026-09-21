package com.feiqi.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.UserProfile
import com.feiqi.data.model.WeightRecord
import com.feiqi.data.repository.PreferencesRepository
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 体重曲线视图粒度。 */
enum class WeightChartRange { DAY, MONTH, YEAR }

/** 曲线上的一个点。 */
data class WeightPoint(
    val label: String,
    val value: Double
)

class HealthViewModel(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    val records: StateFlow<List<WeightRecord>> = preferencesRepository.weightRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val profile: StateFlow<UserProfile> = preferencesRepository.userProfile
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserProfile()
        )

    fun addRecord(weightText: String, date: LocalDate = DateUtils.today(), note: String = "") {
        val value = weightText.toDoubleOrNull()
        if (value == null || value <= 0) {
            viewModelScope.launch { _events.emit("请输入有效的体重数值") }
            return
        }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.addWeightRecord(weight = value, date = date, note = note)
            }.onSuccess {
                _events.emit("体重已记录")
            }.onFailure {
                _events.emit("记录失败：${it.message}")
            }
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            runCatching { preferencesRepository.deleteWeightRecord(id) }
                .onSuccess { _events.emit("已删除记录") }
                .onFailure { _events.emit("删除失败：${it.message}") }
        }
    }

    fun updateRecord(record: WeightRecord) {
        viewModelScope.launch {
            runCatching { preferencesRepository.updateWeightRecord(record) }
                .onSuccess { _events.emit("记录已更新") }
                .onFailure { _events.emit("更新失败：${it.message}") }
        }
    }

    /** 保存个人基本信息（性别 / 年龄 / 身高）。 */
    fun saveProfile(gender: String, ageText: String, heightText: String) {
        val age = ageText.trim().toIntOrNull() ?: 0
        val height = heightText.trim().toDoubleOrNull() ?: 0.0
        if (height > 0 && (height < 50 || height > 260)) {
            viewModelScope.launch { _events.emit("请输入合理的身高（50-260cm）") }
            return
        }
        if (age < 0 || age > 130) {
            viewModelScope.launch { _events.emit("请输入合理的年龄（0-130）") }
            return
        }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setUserProfile(
                    UserProfile(gender = gender, age = age, heightCm = height)
                )
            }.onSuccess { _events.emit("个人信息已保存") }
                .onFailure { _events.emit("保存失败：${it.message}") }
        }
    }

    companion object {

        /**
         * 把体重记录聚合成曲线数据点。
         * - DAY：最近 14 个有记录的自然日（同日多条取平均）
         * - MONTH：最近 12 个有记录的月份（月内取平均）
         * - YEAR：最近 6 个有记录的年份（年内取平均）
         */
        fun buildChartPoints(
            records: List<WeightRecord>,
            range: WeightChartRange
        ): List<WeightPoint> {
            if (records.isEmpty()) return emptyList()
            return when (range) {
                WeightChartRange.DAY -> records
                    .groupBy { it.date }
                    .toSortedMap()
                    .map { (date, list) ->
                        WeightPoint(
                            label = "${date.monthValue}/${date.dayOfMonth}",
                            value = list.map { it.weight }.average()
                        )
                    }
                    .takeLast(14)

                WeightChartRange.MONTH -> records
                    .groupBy { it.date.year * 100 + it.date.monthValue }
                    .toSortedMap()
                    .map { (key, list) ->
                        WeightPoint(
                            label = "${key % 100}月",
                            value = list.map { it.weight }.average()
                        )
                    }
                    .takeLast(12)

                WeightChartRange.YEAR -> records
                    .groupBy { it.date.year }
                    .toSortedMap()
                    .map { (year, list) ->
                        WeightPoint(
                            label = "${year}",
                            value = list.map { it.weight }.average()
                        )
                    }
                    .takeLast(6)
            }
        }
    }
}
