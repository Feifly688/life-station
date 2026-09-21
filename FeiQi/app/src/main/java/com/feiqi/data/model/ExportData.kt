package com.feiqi.data.model

import com.google.gson.annotations.SerializedName

data class ExportData(
    @SerializedName("version")
    val version: Int = 2,
    @SerializedName("exportAt")
    val exportAt: Long = System.currentTimeMillis(),
    @SerializedName("accounts")
    val accounts: List<AccountRecordDto> = emptyList()
)

/** 导入结果统计，用于给用户明确反馈。 */
data class ImportResult(
    val total: Int,
    val inserted: Int,
    val skipped: Int
)
