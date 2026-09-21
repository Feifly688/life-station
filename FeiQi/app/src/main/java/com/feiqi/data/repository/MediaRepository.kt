package com.feiqi.data.repository

import com.feiqi.data.database.MediaDao
import com.feiqi.data.model.Media
import com.feiqi.data.model.MediaType
import com.feiqi.data.model.toEntity
import com.feiqi.data.model.toModel
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class MediaRepository(private val dao: MediaDao) {

    fun getAll(): Flow<List<Media>> {
        return dao.getAll().map { list -> list.map { it.toModel() } }
    }

    fun getByType(type: MediaType): Flow<List<Media>> {
        return dao.getByType(type.name).map { list -> list.map { it.toModel() } }
    }

    /** 首页展示：最近添加的若干作品（按添加时间倒序）。 */
    fun getRecent(limit: Int = 4): Flow<List<Media>> {
        return dao.getRecent(limit).map { list -> list.map { it.toModel() } }
    }

    suspend fun insert(media: Media): Long {
        return dao.insert(media.toEntity())
    }

    suspend fun update(media: Media) {
        dao.update(media.toEntity())
    }

    suspend fun delete(media: Media) {
        dao.delete(media.toEntity())
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun getThisYearCount(year: Int = LocalDate.now().year): Int {
        val start = DateUtils.iso(LocalDate.of(year, 1, 1))
        val end = DateUtils.iso(LocalDate.of(year, 12, 31))
        return dao.countBetween(start, end) ?: 0
    }

    suspend fun getAverageRating(): Float {
        return dao.averageRating() ?: 0f
    }

    suspend fun getFavoriteType(): String {
        return dao.favoriteType() ?: "-"
    }

    suspend fun getStarDistribution(): List<Int> {
        val distribution = MutableList(5) { 0 }
        dao.starCounts().forEach { (star, count) ->
            val index = star.coerceIn(1, 5) - 1
            distribution[index] = count
        }
        return distribution
    }
}
