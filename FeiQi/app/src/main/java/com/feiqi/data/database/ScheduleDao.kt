package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules ORDER BY date ASC, time ASC, itemOrder ASC, createdAt ASC")
    fun getAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE date = :date ORDER BY time ASC, itemOrder ASC")
    fun getByDate(date: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE completed = 0 AND date < :today ORDER BY date DESC")
    fun getOverdue(today: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC")
    fun getBetween(start: String, end: String): Flow<List<ScheduleEntity>>

    @Insert
    suspend fun insert(entity: ScheduleEntity): Long

    @Update
    suspend fun update(entity: ScheduleEntity)

    @Delete
    suspend fun delete(entity: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE listId = :listId")
    suspend fun deleteByListId(listId: String)

    @Query("DELETE FROM schedules")
    suspend fun deleteAll()
}
