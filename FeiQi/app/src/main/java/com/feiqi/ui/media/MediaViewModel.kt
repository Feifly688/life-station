package com.feiqi.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.Media
import com.feiqi.data.model.MediaFilter
import com.feiqi.data.model.MediaStatus
import com.feiqi.data.model.MediaType
import com.feiqi.data.model.MediaUiState
import com.feiqi.data.repository.MediaRepository
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class MediaViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _items = MutableStateFlow<List<Media>>(emptyList())
    private val _filter = MutableStateFlow(MediaFilter.ALL)

    val uiState: StateFlow<MediaUiState> = combine(_items, _filter) { items, filter ->
        val filtered = when (filter) {
            MediaFilter.ALL -> items
            MediaFilter.MOVIE -> items.filter { it.type == MediaType.MOVIE }
            MediaFilter.TV -> items.filter { it.type == MediaType.TV }
            MediaFilter.BOOK -> items.filter { it.type == MediaType.BOOK }
            MediaFilter.MUSIC -> items.filter { it.type == MediaType.MUSIC }
        }

        val currentYear = LocalDate.now().year
        val ratedItems = items.filter { it.rating > 0 }
        val average = if (ratedItems.isNotEmpty()) {
            ratedItems.map { it.rating }.average().toFloat()
        } else 0f

        val typeCounts = items.groupingBy { it.type }.eachCount()
        val favorite = typeCounts.maxByOrNull { it.value }?.key?.label ?: "-"

        val distribution = MutableList(5) { 0 }
        ratedItems.forEach { media ->
            val index = media.rating.roundToInt().coerceIn(1, 5) - 1
            distribution[index] += 1
        }

        MediaUiState(
            items = items,
            filteredItems = filtered,
            filter = filter,
            thisYearCount = items.count { it.date?.year == currentYear },
            averageRating = average,
            favoriteType = favorite,
            starDistribution = distribution
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MediaUiState()
    )

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.getAll().collect { list ->
                _items.value = list
            }
        }
    }

    fun setFilter(filter: MediaFilter) {
        _filter.value = filter
    }

    fun addMedia(
        title: String,
        typeLabel: String,
        statusLabel: String,
        rating: Float,
        date: LocalDate,
        note: String,
        coverUri: String?
    ) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch { _events.emit("请输入作品名称") }
            return
        }

        val type = MediaType.entries.find { it.label == typeLabel } ?: MediaType.OTHER
        val status = MediaStatus.entries.find { it.label == statusLabel } ?: MediaStatus.WISH

        val media = Media(
            title = trimmed,
            type = type,
            status = status,
            rating = rating,
            date = date,
            note = note.trim(),
            coverUri = coverUri?.trim()?.takeIf { it.isNotEmpty() }
        )

        viewModelScope.launch {
            runCatching { repository.insert(media) }
                .onSuccess { _events.emit("已加入书影音") }
                .onFailure { _events.emit("保存失败：${it.message}") }
        }
    }

    fun deleteMedia(media: Media) {
        viewModelScope.launch {
            runCatching { repository.delete(media) }
                .onSuccess { _events.emit("已删除") }
                .onFailure { _events.emit("删除失败：${it.message}") }
        }
    }

    fun updateMedia(media: Media) {
        viewModelScope.launch {
            runCatching { repository.update(media) }
                .onSuccess { _events.emit("已更新") }
                .onFailure { _events.emit("更新失败：${it.message}") }
        }
    }
}
