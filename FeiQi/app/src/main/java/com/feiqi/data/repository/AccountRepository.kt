package com.feiqi.data.repository

import com.feiqi.data.database.AccountDao
import com.feiqi.data.entity.AccountEntity
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.AccountRecordDto
import com.feiqi.data.model.AccountType
import com.feiqi.data.model.CategorySum
import com.feiqi.data.model.ImportResult
import com.feiqi.data.model.categoryColor
import com.feiqi.data.model.fingerprint
import com.feiqi.data.model.toDto
import com.feiqi.data.model.toModel
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val DEFAULT_EXPENSE_CATEGORIES = listOf("吃饭", "交通", "购物", "居住", "娱乐", "医疗", "学习", "其他")
val DEFAULT_INCOME_CATEGORIES = listOf("工资", "奖金", "理财", "兼职", "红包", "其他")

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

class AccountRepository(private val dao: AccountDao) {

    fun getRecordsForMonth(date: LocalDate): Flow<List<AccountRecord>> {
        val start = DateUtils.iso(DateUtils.monthStart(date))
        val end = DateUtils.iso(DateUtils.monthEnd(date))
        return dao.getByDateRange(start, end).map { list -> list.map { it.toModel() } }
    }

    fun getRecent(limit: Int): Flow<List<AccountRecord>> {
        return dao.getRecent(limit).map { list -> list.map { it.toModel() } }
    }

    fun countAll(): Flow<Int> = dao.countAll()

    fun hasRecordOnDate(date: LocalDate): Flow<Boolean> {
        return dao.countByDate(DateUtils.iso(date)).map { it > 0 }
    }

    fun getExpenseForMonth(date: LocalDate): Flow<Double> {
        val start = DateUtils.iso(DateUtils.monthStart(date))
        val end = DateUtils.iso(DateUtils.monthEnd(date))
        return dao.getExpenseSum(start, end).map { (it ?: 0) / 100.0 }
    }

    fun getIncomeForMonth(date: LocalDate): Flow<Double> {
        val start = DateUtils.iso(DateUtils.monthStart(date))
        val end = DateUtils.iso(DateUtils.monthEnd(date))
        return dao.getIncomeSum(start, end).map { (it ?: 0) / 100.0 }
    }

    fun getCategorySumsForMonth(date: LocalDate): Flow<List<CategorySum>> {
        val start = DateUtils.iso(DateUtils.monthStart(date))
        val end = DateUtils.iso(DateUtils.monthEnd(date))
        return dao.getCategorySums(start, end).map { list ->
            val total = list.sumOf { it.total } / 100.0
            list.mapIndexed { index, entity ->
                val amount = entity.total / 100.0
                CategorySum(
                    category = entity.category,
                    amount = amount,
                    color = categoryColor(index),
                    percent = if (total > 0) (amount / total).toFloat() else 0f
                )
            }
        }
    }

    suspend fun insert(record: AccountRecord): Long {
        return dao.insert(record.toEntity())
    }

    suspend fun update(record: AccountRecord) {
        dao.update(record.toEntity())
    }

    suspend fun delete(record: AccountRecord) {
        dao.delete(record.toEntity())
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun exportAll(): List<AccountRecordDto> {
        return dao.getAllOnce().map { it.toModel().toDto() }
    }

    suspend fun getAllOnce(): List<AccountRecord> {
        return dao.getAllOnce().map { it.toModel() }
    }

    /**
     * 导入时按业务指纹去重：已存在的记录原样保留，只写入库中没有的条目。
     * 同一份文件内部的重复条目也只会写入一次。
     */
    suspend fun importAll(records: List<AccountRecordDto>): ImportResult {
        if (records.isEmpty()) return ImportResult(0, 0, 0)
        val existing = dao.getAllOnce().map { it.toModel().fingerprint() }.toMutableSet()
        val pending = mutableListOf<AccountEntity>()
        var skipped = 0
        records.forEach { dto ->
            val model = runCatching { dto.toModel().copy(id = 0) }.getOrNull()
            if (model == null) {
                skipped++
                return@forEach
            }
            val key = model.fingerprint()
            if (existing.add(key)) {
                pending += model.toEntity()
            } else {
                skipped++
            }
        }
        if (pending.isNotEmpty()) dao.insertAll(pending)
        return ImportResult(
            total = records.size,
            inserted = pending.size,
            skipped = skipped
        )
    }
}

private fun AccountEntity.toModel(): AccountRecord {
    val localDate = LocalDate.parse(date)
    val localTime = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.MIDNIGHT)
    return AccountRecord(
        id = id,
        type = if (type == 1) AccountType.INCOME else AccountType.EXPENSE,
        amount = amount / 100.0,
        category = category,
        dateTime = LocalDateTime.of(localDate, localTime),
        note = note
    )
}

private fun AccountRecord.toEntity(): AccountEntity = AccountEntity(
    id = id,
    type = if (type == AccountType.INCOME) 1 else 0,
    amount = Math.round(amount * 100).toInt(),
    category = category,
    date = dateTime.toLocalDate().toString(),
    time = dateTime.toLocalTime().format(TIME_FORMATTER),
    note = note,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)
