package com.feiqi.data.repository

import com.feiqi.data.database.HabitDao
import com.feiqi.data.database.HabitRecordDao
import com.feiqi.data.entity.HabitEntity
import com.feiqi.data.entity.HabitRecordEntity
import com.feiqi.data.model.Habit
import com.feiqi.data.model.HabitRecord
import com.feiqi.data.model.HabitWithRecords
import com.feiqi.data.model.toEntity
import com.feiqi.data.model.toModel
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class HabitRepository(
    private val habitDao: HabitDao,
    private val recordDao: HabitRecordDao
) {

    companion object {
        /** 连续天数所需的历史回溯窗口（天）。 */
        private const val STREAK_WINDOW_DAYS = 400L
    }

    /**
     * 所有习惯 + 记录窗口，按 sortOrder 排序。
     *
     * 记录窗口取近 [STREAK_WINDOW_DAYS] 天（而非 30 天）：连续天数（streak）可能远超 30 天，
     * 若只取 30 天，`computeStreak` 会在窗口边界被 0 值截断，把「连续 87 天」误报成「连续 30 天」。
     * 完成率与热力图都按各自的具体日期取数（见 [computeLast30Rate]），窗口变长不影响其结果。
     */
    fun getHabitsWithRecords(): Flow<List<HabitWithRecords>> {
        val today = DateUtils.today()
        val start = today.minusDays(STREAK_WINDOW_DAYS - 1)
        return combine(
            habitDao.getAll(),
            recordDao.getBetween(start.toString(), today.toString())
        ) { habitEntities, recordEntities ->
            val recordsByHabit = recordEntities.groupBy { it.habitId }
            habitEntities.map { entity ->
                val habit = entity.toModel()
                val records = recordsByHabit[habit.id].orEmpty()
                val recordMap = records.associate { LocalDate.parse(it.date) to it.count }
                val streak = computeStreak(recordMap, habit.targetCount, today)
                HabitWithRecords(
                    habit = habit,
                    todayCount = recordMap[today] ?: 0,
                    streak = streak,
                    records = recordMap
                )
            }
        }
    }

    /** 查询单个习惯某天记录数量，不存在返回 0。 */
    suspend fun getRecordCount(habitId: Long, date: LocalDate): Int {
        return recordDao.getByHabitAndDate(habitId, date.toString())?.count ?: 0
    }

    /** 为某个习惯设置某天的完成数量（自动插入或更新）。 */
    suspend fun setRecordCount(habitId: Long, date: LocalDate, count: Int) {
        val existing = recordDao.getByHabitAndDate(habitId, date.toString())
        if (existing != null) {
            recordDao.update(existing.copy(count = count.coerceAtLeast(0)))
        } else {
            recordDao.insert(
                HabitRecordEntity(
                    habitId = habitId,
                    date = date.toString(),
                    count = count.coerceAtLeast(0)
                )
            )
        }
    }

    /** 数字型习惯：在当前数量基础上 +1。 */
    suspend fun increment(habitId: Long, date: LocalDate, max: Int = Int.MAX_VALUE) {
        val current = getRecordCount(habitId, date)
        setRecordCount(habitId, date, (current + 1).coerceAtMost(max))
    }

    /** 数字型习惯：在当前数量基础上 -1。 */
    suspend fun decrement(habitId: Long, date: LocalDate) {
        val current = getRecordCount(habitId, date)
        setRecordCount(habitId, date, (current - 1).coerceAtLeast(0))
    }

    /** 布尔型/计数型习惯：直接切换完成状态（完成 = targetCount，未完成 = 0）。 */
    suspend fun toggleComplete(habitId: Long, date: LocalDate, targetCount: Int) {
        val current = getRecordCount(habitId, date)
        val next = if (current >= targetCount) 0 else targetCount
        setRecordCount(habitId, date, next)
    }

    suspend fun insertHabit(habit: Habit): Long = habitDao.insert(habit.toEntity())

    suspend fun updateHabit(habit: Habit) = habitDao.update(habit.toEntity())

    suspend fun deleteHabit(habit: Habit) = habitDao.delete(habit.toEntity())

    /** 计算某个习惯从 [today] 往历史推的最大连续完成天数。 */
    fun computeStreak(records: Map<LocalDate, Int>, targetCount: Int, today: LocalDate): Int {
        // 目标数 <= 0 时 `count >= targetCount` 恒真（缺失日按 0 计），while 会无限回溯 → 死循环/ANR。
        // 未设目标即视为无连续，直接返回 0。
        if (targetCount <= 0) return 0
        var streak = 0
        var date = today
        while (true) {
            val count = records[date] ?: 0
            if (count >= targetCount) {
                streak++
                date = date.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    /**
     * 计算近 30 天完成率：完成量 / 目标量（按每个习惯每天计）。
     * 只统计习惯创建日及之后的天数，新加的习惯不会被历史空白拖低完成率。
     */
    fun computeLast30Rate(
        habits: List<HabitWithRecords>,
        today: LocalDate
    ): Int {
        if (habits.isEmpty()) return 0
        var completed = 0
        var total = 0
        for (day in 0..29) {
            val date = today.minusDays(day.toLong())
            for (item in habits) {
                if (!item.habit.isTracked(date)) continue
                total += item.habit.targetCount
                completed += (item.records[date] ?: 0).coerceAtMost(item.habit.targetCount)
            }
        }
        if (total == 0) return 0
        return ((completed * 100.0 / total).toInt()).coerceIn(0, 100)
    }
}
