package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.HabitRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitRecordDao {

    @Query("SELECT * FROM habit_records WHERE date BETWEEN :start AND :end")
    fun getBetween(start: String, end: String): Flow<List<HabitRecordEntity>>

    @Query("SELECT * FROM habit_records WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getByHabitAndDate(habitId: Long, date: String): HabitRecordEntity?

    @Insert
    suspend fun insert(entity: HabitRecordEntity): Long

    @Update
    suspend fun update(entity: HabitRecordEntity)

    @Delete
    suspend fun delete(entity: HabitRecordEntity)

    @Query("DELETE FROM habit_records")
    suspend fun deleteAll()
}
