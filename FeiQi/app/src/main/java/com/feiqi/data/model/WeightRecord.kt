package com.feiqi.data.model

import java.time.LocalDate

/**
 * 体重记录，用于健康功能页。
 * 使用 DataStore 以 JSON 数组形式持久化。
 */
data class WeightRecord(
    val id: String,
    val weight: Double,
    val date: LocalDate,
    val note: String = ""
)
