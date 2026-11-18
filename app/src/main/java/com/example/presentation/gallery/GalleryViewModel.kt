package com.example.presentation.gallery

import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecordingEntity
import com.example.data.repository.RecordingRepository
import com.example.domain.model.GalleryFilterOption
import com.example.domain.model.GallerySortOption
import com.example.domain.model.StorageStats
import com.example.recording.RecordingFileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class GalleryUiState(
    val isLoading: Boolean = true,
    val recordings: List<RecordingEntity> = emptyList(),
    val searchQuery: String = "",
    val sortOption: GallerySortOption = GallerySortOption.DATE_DESC,
    val filterOption: GalleryFilterOption = GalleryFilterOption.ALL,
    val selectedIds: Set<String> = emptySet(),
    val isMultiSelectMode: Boolean = false,
    val storageStats: StorageStats = StorageStats(),
    val message: String? = null
)

class GalleryViewModel(
    private val repository: RecordingRepository,
    private val fileManager: RecordingFileManager
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(GallerySortOption.DATE_DESC)
    private val filterOption = MutableStateFlow(GalleryFilterOption.ALL)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val initialLoaded = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    init {
        viewModelScope.launch {
            delay(400L)
            initialLoaded.value = true
        }
    }

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.allRecordings,
        searchQuery,
        sortOption,
        filterOption,
        selectedIds,
        initialLoaded,
        _message
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val all = values[0] as List<RecordingEntity>
        val query = values[1] as String
        val sort = values[2] as GallerySortOption
        val filter = values[3] as GalleryFilterOption
        @Suppress("UNCHECKED_CAST")
        val selected = values[4] as Set<String>
        val loaded = values[5] as Boolean
        val msg = values[6] as String?

        val filtered = applyFilterAndSearch(all, query, filter, sort)
        val (free, total) = fileManager.getDeviceStoragePair()
        val stats = StorageStats(
            totalCount = all.size,
            totalDurationMs = all.sumOf { it.durationMs },
            totalSizeBytes = all.sumOf { it.fileSizeBytes },
            deviceFreeBytes = free,
            deviceTotalBytes = total
        )

        GalleryUiState(
            isLoading = !loaded,
            recordings = filtered,
            searchQuery = query,
            sortOption = sort,
            filterOption = filter,
            selectedIds = selected,
            isMultiSelectMode = selected.isNotEmpty(),
            storageStats = stats,
            message = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GalleryUiState()
    )

    fun loadThumbnailIfNeeded(recording: RecordingEntity) {
        if (_thumbnails.value.containsKey(recording.id)) return
        viewModelScope.launch {
            val bmp = repository.extractThumbnail(recording)
            if (bmp != null) {
                _thumbnails.update { it + (recording.id to bmp) }
            }
        }
    }

    fun onSearchChange(q: String) {
        searchQuery.value = q
    }

    fun onSortSelected(option: GallerySortOption) {
        sortOption.value = option
    }

    fun onFilterSelected(option: GalleryFilterOption) {
        filterOption.value = option
    }

    fun toggleSelection(id: String) {
        selectedIds.update { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun renameRecording(recording: RecordingEntity, newName: String) {
        viewModelScope.launch {
            repository.rename(recording, newName)
            _message.value = "Renamed recording"
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            repository.delete(recording)
            selectedIds.update { it - recording.id }
            _message.value = "Recording deleted"
        }
    }

    fun deleteSelected() {
        val ids = selectedIds.value
        val targets = uiState.value.recordings.filter { it.id in ids }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            repository.deleteMultiple(targets)
            selectedIds.value = emptySet()
            _message.value = "Deleted ${targets.size} recording(s)"
        }
    }

    fun createShareIntent(recordings: List<RecordingEntity>): Intent? {
        return repository.createShareIntent(recordings)
    }

    fun createShareSelectedIntent(): Intent? {
        val ids = selectedIds.value
        val targets = uiState.value.recordings.filter { it.id in ids }
        return repository.createShareIntent(targets)
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun applyFilterAndSearch(
        list: List<RecordingEntity>,
        query: String,
        filter: GalleryFilterOption,
        sort: GallerySortOption
    ): List<RecordingEntity> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        val startOfWeek = now - 7L * 24 * 60 * 60 * 1000
        val startOfMonth = now - 30L * 24 * 60 * 60 * 1000

        val timeFiltered = when (filter) {
            GalleryFilterOption.ALL -> list
            GalleryFilterOption.TODAY -> list.filter { it.createdAt >= startOfToday }
            GalleryFilterOption.THIS_WEEK -> list.filter { it.createdAt >= startOfWeek }
            GalleryFilterOption.THIS_MONTH -> list.filter { it.createdAt >= startOfMonth }
        }

        val q = query.trim().lowercase()
        val searched = if (q.isEmpty()) {
            timeFiltered
        } else {
            timeFiltered.filter {
                it.fileName.lowercase().contains(q) || it.resolution.lowercase().contains(q)
            }
        }

        return when (sort) {
            GallerySortOption.DATE_DESC -> searched.sortedByDescending { it.createdAt }
            GallerySortOption.DATE_ASC -> searched.sortedBy { it.createdAt }
            GallerySortOption.DURATION_DESC -> searched.sortedByDescending { it.durationMs }
            GallerySortOption.SIZE_DESC -> searched.sortedByDescending { it.fileSizeBytes }
            GallerySortOption.NAME_ASC -> searched.sortedBy { it.fileName.lowercase() }
        }
    }

    class Factory(
        private val repository: RecordingRepository,
        private val fileManager: RecordingFileManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GalleryViewModel(repository, fileManager) as T
        }
    }
}
