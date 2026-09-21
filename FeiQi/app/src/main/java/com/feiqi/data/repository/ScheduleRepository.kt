package com.feiqi.data.repository

import com.feiqi.data.database.ScheduleDao
import com.feiqi.data.entity.ScheduleEntity
import com.feiqi.data.model.Recurrence
import com.feiqi.data.model.Schedule
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

val DEFAULT_SCHEDULE_GROUPS = listOf("生活", "工作", "学习", "健康", "其他")

class ScheduleRepository(private val dao: ScheduleDao) {

    fun getAll(): Flow<List<Schedule>> {
        return dao.getAll().map { list -> list.map { it.toModel() } }
    }

    fun getByDate(date: LocalDate): Flow<List<Schedule>> {
        return dao.getByDate(DateUtils.iso(date)).map { list -> list.map { it.toModel() } }
    }

    fun getOverdue(today: LocalDate): Flow<List<Schedule>> {
        return dao.getOverdue(DateUtils.iso(today)).map { list -> list.map { it.toModel() } }
    }

    fun getUpcoming(start: LocalDate, end: LocalDate): Flow<List<Schedule>> {
        return dao.getBetween(DateUtils.iso(start), DateUtils.iso(end)).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun insert(schedule: Schedule): Long {
        return dao.insert(schedule.toEntity())
    }

    suspend fun insertBatch(schedules: List<Schedule>): List<Long> {
        if (schedules.isEmpty()) return emptyList()
        return dao.insertAll(schedules.map { it.toEntity() })
    }

    suspend fun update(schedule: Schedule) {
        dao.update(schedule.toEntity())
    }

    suspend fun updateBatch(schedules: List<Schedule>) {
        if (schedules.isEmpty()) return
        dao.updateAll(schedules.map { it.toEntity() })
    }

    /** 提醒触发时取清单内未完成项（定向查询，避免全表加载）。 */
    suspend fun getPendingByListId(listId: String): List<Schedule> {
        return dao.getPendingByListId(listId).map { it.toModel() }
    }

    suspend fun delete(schedule: Schedule) {
        dao.delete(schedule.toEntity())
    }

    suspend fun deleteByListId(listId: String) {
        dao.deleteByListId(listId)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}

private fun ScheduleEntity.toModel(): Schedule {
    return Schedule(
        id = id,
        title = title,
        date = runCatching { LocalDate.parse(date) }.getOrDefault(DateUtils.today()),
        time = time?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        groupName = groupName,
        note = note,
        reminder = reminder,
        completed = completed,
        createdAt = createdAt,
        listId = listId,
        listTitle = listTitle.ifBlank { title },
        recurrence = Recurrence.from(recurrence),
        itemOrder = itemOrder,
        lastResetDate = lastResetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        completedDate = completedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    )
}

private fun Schedule.toEntity(): ScheduleEntity {
    return ScheduleEntity(
        id = id,
        title = title,
        date = DateUtils.iso(date),
        time = time?.let { DateUtils.hm(it) },
        groupName = groupName,
        note = note,
        reminder = reminder,
        completed = completed,
        createdAt = createdAt,
        listId = listId,
        listTitle = listTitle.ifBlank { title },
        recurrence = recurrence.code,
        itemOrder = itemOrder,
        lastResetDate = lastResetDate?.let { DateUtils.iso(it) },
        completedDate = completedDate?.let { DateUtils.iso(it) }
    )
}
