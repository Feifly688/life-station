package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String, // movie / tv / book / music
    val status: String, // 想看 / 在看 / 已看
    val rating: Float = 0f,
    val date: String? = null,
    val note: String = "",
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
