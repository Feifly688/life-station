package com.feiqi.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class AccountType {
    EXPENSE, INCOME
}

data class AccountRecord(
    val id: Long = 0,
    val type: AccountType,
    val amount: Double,
    val category: String,
    val dateTime: LocalDateTime,
    val note: String = ""
) {
    val date: LocalDate get() = dateTime.toLocalDate()
    val time: LocalTime get() = dateTime.toLocalTime()
}

private val DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

data class AccountRecordDto(
    val id: Long,
    val type: String,
    val amount: Double,
    val category: String,
    val date: String,
    val time: String? = null,
    val note: String
)

fun AccountRecord.toDto(): AccountRecordDto = AccountRecordDto(
    id = id,
    type = if (type == AccountType.EXPENSE) "expense" else "income",
    amount = amount,
    category = category,
    date = date.toString(),
    time = time.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
    note = note
)

fun AccountRecordDto.toModel(): AccountRecord {
    val localDate = LocalDate.parse(date)
    val localTime = time?.takeIf { it.isNotBlank() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrDefault(LocalTime.MIDNIGHT) }
        ?: LocalTime.MIDNIGHT
    return AccountRecord(
        id = id,
        type = if (type == "income") AccountType.INCOME else AccountType.EXPENSE,
        amount = amount,
        category = category,
        dateTime = LocalDateTime.of(localDate, localTime),
        note = note
    )
}

/** 用于导入去重的业务指纹，忽略主键 id。 */
fun AccountRecord.fingerprint(): String = listOf(
    if (type == AccountType.EXPENSE) "0" else "1",
    (amount * 100).toLong().toString(),
    category,
    dateTime.format(DATE_TIME_FORMATTER),
    note.trim()
).joinToString("|")
