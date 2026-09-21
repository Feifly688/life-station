package com.feiqi.data.model

import com.feiqi.data.entity.MediaEntity
import com.feiqi.utils.DateUtils
import java.time.LocalDate

enum class MediaType(val label: String) {
    MOVIE("电影"),
    TV("剧集"),
    BOOK("书"),
    MUSIC("音乐"),
    OTHER("其他")
}

enum class MediaStatus(val label: String) {
    WISH("想看"),
    DOING("在看"),
    DONE("已看")
}

enum class MediaFilter(val label: String) {
    ALL("全部"),
    MOVIE("电影"),
    TV("剧集"),
    BOOK("书"),
    MUSIC("音乐")
}

data class Media(
    val id: Long = 0,
    val title: String,
    val type: MediaType,
    val status: MediaStatus,
    val rating: Float = 0f,
    val date: LocalDate? = null,
    val note: String = "",
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val hasRating: Boolean get() = rating > 0
}

data class MediaUiState(
    val items: List<Media> = emptyList(),
    val filteredItems: List<Media> = emptyList(),
    val filter: MediaFilter = MediaFilter.ALL,
    val thisYearCount: Int = 0,
    val averageRating: Float = 0f,
    val favoriteType: String = "-",
    val starDistribution: List<Int> = List(5) { 0 }
)

fun MediaEntity.toModel(): Media = Media(
    id = id,
    title = title,
    type = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.OTHER),
    status = runCatching { MediaStatus.valueOf(status) }.getOrDefault(MediaStatus.WISH),
    rating = rating,
    date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    note = note,
    coverUri = coverUri,
    createdAt = createdAt
)

fun Media.toEntity(): MediaEntity = MediaEntity(
    id = id,
    title = title,
    type = type.name,
    status = status.name,
    rating = rating,
    date = date?.let { DateUtils.iso(it) },
    note = note,
    coverUri = coverUri,
    createdAt = createdAt
)
