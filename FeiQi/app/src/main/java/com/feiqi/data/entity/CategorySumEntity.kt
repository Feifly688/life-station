package com.feiqi.data.entity

import androidx.room.ColumnInfo

class CategorySumEntity(
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "total")
    val total: Long
)
