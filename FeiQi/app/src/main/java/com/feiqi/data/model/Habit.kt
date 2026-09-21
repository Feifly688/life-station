package com.feiqi.data.model

import com.feiqi.data.entity.HabitEntity
import com.feiqi.data.entity.HabitRecordEntity
import java.time.LocalDate

data class Habit(
    val id: Long = 0,
    val name: String,
    val targetCount: Int = 1,
    val unit: String = "次",
    val colorHex: String = "#6B4F5B",
    val sortOrder: Int = 0,
    /** 习惯创建日期。创建日之前不参与热力图与完成率统计。 */
    val createdDate: LocalDate = LocalDate.now(),
    /** 习惯备注（原「目标次数」输入位）。 */
    val note: String = ""
) {
    val isNumeric: Boolean get() = unit.isNotBlank() && targetCount > 1

    /** 该日期是否处于本习惯的追踪范围内（创建日及之后）。 */
    fun isTracked(date: LocalDate): Boolean = !date.isBefore(createdDate)
}

fun HabitEntity.toModel(): Habit = Habit(
    id = id,
    name = name,
    targetCount = targetCount,
    unit = unit,
    colorHex = colorHex,
    sortOrder = sortOrder,
    createdDate = runCatching { LocalDate.parse(createdDate) }.getOrElse { LocalDate.now() },
    note = note
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    targetCount = targetCount,
    unit = unit,
    colorHex = colorHex,
    sortOrder = sortOrder,
    createdDate = createdDate.toString(),
    note = note
)

data class HabitRecord(
    val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val count: Int = 0
)

fun HabitRecordEntity.toModel(): HabitRecord = HabitRecord(
    id = id,
    habitId = habitId,
    date = LocalDate.parse(date),
    count = count
)

fun HabitRecord.toEntity(): HabitRecordEntity = HabitRecordEntity(
    id = id,
    habitId = habitId,
    date = date.toString(),
    count = count
)

data class HabitWithRecords(
    val habit: Habit,
    val todayCount: Int = 0,
    val streak: Int = 0,
    val records: Map<LocalDate, Int> = emptyMap()
) {
    val isCompletedToday: Boolean get() = todayCount >= habit.targetCount
}

data class HabitUiState(
    val habits: List<HabitWithRecords> = emptyList(),
    val totalHabits: Int = 0,
    val todayDone: Int = 0,
    val bestStreak: Int = 0,
    val last30CompletionRate: Int = 0,
    val showAddDialog: Boolean = false,
    val heatmapDates: List<LocalDate> = emptyList()
)
