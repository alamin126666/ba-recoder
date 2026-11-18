package com.example.presentation.home

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.SettingsDataStore
import com.example.data.local.RecordingEntity
import com.example.data.repository.RecordingRepository
import com.example.domain.model.FpsOption
import com.example.domain.model.RecorderRuntimeState
import com.example.domain.model.RecordingConfig
import com.example.domain.model.ResolutionPreset
import com.example.domain.model.StorageStats
import com.example.recording.RecordingFileManager
import com.example.recording.ScreenRecordManager
import com.example.service.DrawOverlayService
import com.example.service.ScreenRecordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoadingStats: Boolean = true,
    val stats: StorageStats = StorageStats(),
    val recentRecordings: List<RecordingEntity> = emptyList(),
    val config: RecordingConfig = RecordingConfig(),
    val runtime: RecorderRuntimeState = RecorderRuntimeState()
)

class HomeViewModel(
    private val repository: RecordingRepository,
    private val settingsDataStore: SettingsDataStore,
    private val fileManager: RecordingFileManager,
    private val recordManager: ScreenRecordManager
) : ViewModel() {

    private val initialDelayDone = MutableStateFlow(false)
    private var countdownJob: Job? = null

    init {
        viewModelScope.launch {
            delay(450L) // Brief shimmer reveal for stats & recent cards
            initialDelayDone.value = true
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repository.allRecordings,
        settingsDataStore.configFlow,
        recordManager.runtimeState,
        initialDelayDone
    ) { recordings, config, runtime, loaded ->
        val (freeBytes, totalBytes) = fileManager.getDeviceStoragePair()
        val stats = StorageStats(
            totalCount = recordings.size,
            totalDurationMs = recordings.sumOf { it.durationMs },
            totalSizeBytes = recordings.sumOf { it.fileSizeBytes },
            deviceFreeBytes = freeBytes,
            deviceTotalBytes = totalBytes
        )
        HomeUiState(
            isLoadingStats = !loaded,
            stats = stats,
            recentRecordings = recordings.take(8),
            config = config,
            runtime = runtime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun startWithCountdown(seconds: Int, onCountdownComplete: () -> Unit) {
        if (seconds <= 0) {
            onCountdownComplete()
            return
        }
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in seconds downTo 1) {
                recordManager.setCountdown(sec)
                delay(1000L)
            }
            recordManager.cancelCountdown()
            onCountdownComplete()
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        recordManager.cancelCountdown()
    }

    fun startProjectionService(
        context: Context,
        resultCode: Int,
        data: Intent,
        enableMic: Boolean = uiState.value.config.recordMic
    ) {
        ScreenRecordService.start(
            context = context,
            resultCode = resultCode,
            data = data,
            enableMic = enableMic && hasMicPermission(context)
        )
    }

    private fun hasMicPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun togglePauseResume(context: Context) {
        ScreenRecordService.togglePauseResume(context)
    }

    fun stopRecording(context: Context) {
        ScreenRecordService.stop(context)
    }

    fun toggleDrawOverlay(context: Context) {
        if (recordManager.runtimeState.value.isDrawOverlayActive) {
            DrawOverlayService.stop(context)
        } else {
            DrawOverlayService.start(context)
        }
    }

    fun cycleResolution() {
        viewModelScope.launch {
            val all = ResolutionPreset.entries
            val next = all[(all.indexOf(uiState.value.config.resolution) + 1) % all.size]
            settingsDataStore.setResolution(next)
        }
    }

    fun cycleFps() {
        viewModelScope.launch {
            val all = FpsOption.entries
            val next = all[(all.indexOf(uiState.value.config.fps) + 1) % all.size]
            settingsDataStore.setFps(next)
        }
    }

    fun toggleMic() {
        viewModelScope.launch {
            settingsDataStore.setRecordMic(!uiState.value.config.recordMic)
        }
    }

    fun toggleDrawToolbarDefault() {
        viewModelScope.launch {
            settingsDataStore.setShowDrawToolbarByDefault(!uiState.value.config.showDrawToolbarByDefault)
        }
    }

    fun clearError() {
        recordManager.clearError()
    }

    class Factory(
        private val repository: RecordingRepository,
        private val settingsDataStore: SettingsDataStore,
        private val fileManager: RecordingFileManager,
        private val recordManager: ScreenRecordManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, settingsDataStore, fileManager, recordManager) as T
        }
    }
}
