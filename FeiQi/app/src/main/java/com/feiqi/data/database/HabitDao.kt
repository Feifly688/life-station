package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<HabitEntity>>

    @Insert
    suspend fun insert(entity: HabitEntity): Long

    @Update
    suspend fun update(entity: HabitEntity)

    @Delete
    suspend fun delete(entity: HabitEntity)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}
