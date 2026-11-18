package com.example.presentation.player

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.RecordingEntity
import com.example.data.repository.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VideoPlayerUiState(
    val recording: RecordingEntity? = null,
    val isLoading: Boolean = true,
    val isTrimming: Boolean = false,
    val message: String? = null
)

class VideoPlayerViewModel(
    private val recordingId: String,
    private val repository: RecordingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val rec = repository.getById(recordingId)
            _uiState.update { it.copy(recording = rec, isLoading = false) }
        }
    }

    fun trimVideo(startMs: Long, endMs: Long, onSuccess: (RecordingEntity) -> Unit) {
        val current = _uiState.value.recording ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTrimming = true, message = null) }
            val result = repository.trimRecording(current, startMs, endMs)
            result.fold(
                onSuccess = { trimmed ->
                    _uiState.update {
                        it.copy(
                            isTrimming = false,
                            recording = trimmed,
                            message = "Saved trimmed clip: ${trimmed.fileName}"
                        )
                    }
                    onSuccess(trimmed)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isTrimming = false,
                            message = err.localizedMessage ?: "Trim failed"
                        )
                    }
                }
            )
        }
    }

    fun createShareIntent(): Intent? {
        val rec = _uiState.value.recording ?: return null
        return repository.createShareIntent(listOf(rec))
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    class Factory(
        private val recordingId: String,
        private val repository: RecordingRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return VideoPlayerViewModel(recordingId, repository) as T
        }
    }
}
