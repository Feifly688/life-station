package com.feiqi.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetCount: Int = 1,
    val unit: String = "次",
    val colorHex: String = "#6B4F57",
    val sortOrder: Int = 0,
    /** 习惯创建日期（yyyy-MM-dd）。热力图从这一天开始显示，之前的格子留空。 */
    @ColumnInfo(defaultValue = LEGACY_CREATED_DATE)
    val createdDate: String = LocalDate.now().toString(),
    /** 习惯备注，替代原「目标次数」的展示位。 */
    @ColumnInfo(defaultValue = "")
    val note: String = ""
)

/** 迁移前已存在的老习惯统一用这个日期，表示「一直都在」，热力图完整显示 30 天。 */
const val LEGACY_CREATED_DATE = "1970-01-01"
