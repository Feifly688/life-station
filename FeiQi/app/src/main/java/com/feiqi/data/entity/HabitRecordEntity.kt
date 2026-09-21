package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_records",
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: Long,
    val date: String, // yyyy-MM-dd
    val count: Int = 1
)
