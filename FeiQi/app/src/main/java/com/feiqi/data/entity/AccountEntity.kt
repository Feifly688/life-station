package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_records")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: Int, // 0 支出，1 收入
    val amount: Int, // 单位：分，避免浮点误差
    val category: String,
    val date: String, // ISO-8601: yyyy-MM-dd，用于按月/按日聚合
    val time: String = "00:00:00", // HH:mm:ss，精确到秒
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
