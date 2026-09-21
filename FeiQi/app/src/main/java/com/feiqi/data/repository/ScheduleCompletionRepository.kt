package com.feiqi.data.repository

import com.feiqi.data.database.ScheduleCompletionDao
import com.feiqi.data.entity.ScheduleCompletionEntity
import kotlinx.coroutines.flow.Flow

class ScheduleCompletionRepository(private val dao: ScheduleCompletionDao) {

    fun getBySchedule(scheduleId: Long): Flow<List<ScheduleCompletionEntity>> =
        dao.getBySchedule(scheduleId)

    fun getByList(listId: String): Flow<List<ScheduleCompletionEntity>> =
        dao.getByList(listId)

    fun getByDate(date: String): Flow<List<ScheduleCompletionEntity>> =
        dao.getByDate(date)

    suspend fun insert(entity: ScheduleCompletionEntity): Long = dao.insert(entity)

    suspend fun deleteBySchedule(scheduleId: Long) = dao.deleteBySchedule(scheduleId)

    suspend fun deleteByList(listId: String) = dao.deleteByList(listId)

    suspend fun deleteAll() = dao.deleteAll()
}
