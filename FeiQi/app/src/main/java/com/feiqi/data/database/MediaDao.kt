package com.feiqi.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.MediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<MediaEntity>>

    @Insert
    suspend fun insert(entity: MediaEntity): Long

    @Update
    suspend fun update(entity: MediaEntity)

    @Delete
    suspend fun delete(entity: MediaEntity)

    @Query("DELETE FROM media")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM media WHERE date BETWEEN :start AND :end")
    suspend fun countBetween(start: String, end: String): Int?

    @Query("SELECT AVG(rating) FROM media WHERE rating > 0")
    suspend fun averageRating(): Float?

    @Query("SELECT type FROM media GROUP BY type ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun favoriteType(): String?

    @Query("SELECT CAST(rating AS INTEGER) as star, COUNT(*) as cnt FROM media WHERE rating > 0 GROUP BY CAST(rating AS INTEGER)")
    suspend fun starCounts(): List<StarCount>
}

data class StarCount(
    val star: Int,
    @ColumnInfo(name = "cnt")
    val count: Int
)
