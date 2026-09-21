package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.AccountEntity
import com.feiqi.data.entity.CategorySumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM account_records WHERE date BETWEEN :start AND :end ORDER BY date DESC, time DESC, createdAt DESC")
    fun getByDateRange(start: String, end: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account_records ORDER BY date DESC, time DESC, createdAt DESC")
    suspend fun getAllOnce(): List<AccountEntity>

    @Query("SELECT * FROM account_records ORDER BY date DESC, time DESC, createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM account_records")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM account_records WHERE date = :date")
    fun countByDate(date: String): Flow<Int>

    @Query("SELECT * FROM account_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT SUM(amount) FROM account_records WHERE type = 0 AND date BETWEEN :start AND :end")
    fun getExpenseSum(start: String, end: String): Flow<Int?>

    @Query("SELECT SUM(amount) FROM account_records WHERE type = 1 AND date BETWEEN :start AND :end")
    fun getIncomeSum(start: String, end: String): Flow<Int?>

    @Query(
        "SELECT category, SUM(amount) as total " +
        "FROM account_records WHERE type = 0 AND date BETWEEN :start AND :end " +
        "GROUP BY category ORDER BY total DESC"
    )
    fun getCategorySums(start: String, end: String): Flow<List<CategorySumEntity>>

    @Insert
    suspend fun insert(record: AccountEntity): Long

    @Insert
    suspend fun insertAll(records: List<AccountEntity>)

    @Update
    suspend fun update(record: AccountEntity)

    @Delete
    suspend fun delete(record: AccountEntity)

    @Query("DELETE FROM account_records")
    suspend fun deleteAll()
}
