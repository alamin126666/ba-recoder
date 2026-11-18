package com.example.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.SettingsDataStore
import com.example.domain.model.BitrateOption
import com.example.domain.model.FpsOption
import com.example.domain.model.OrientationLock
import com.example.domain.model.RecordingConfig
import com.example.domain.model.ResolutionPreset
import com.example.domain.model.ThemeMode
import com.example.domain.model.VideoFormatOption
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val config: StateFlow<RecordingConfig> = settingsDataStore.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordingConfig()
    )

    fun setResolution(preset: ResolutionPreset) = viewModelScope.launch {
        settingsDataStore.setResolution(preset)
    }

    fun setFps(option: FpsOption) = viewModelScope.launch {
        settingsDataStore.setFps(option)
    }

    fun setBitrate(option: BitrateOption) = viewModelScope.launch {
        settingsDataStore.setBitrate(option)
    }

    fun setFormat(option: VideoFormatOption) = viewModelScope.launch {
        settingsDataStore.setFormat(option)
    }

    fun setRecordSystemAudio(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setRecordSystemAudio(enabled)
    }

    fun setRecordMic(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setRecordMic(enabled)
    }

    fun setStereoAudio(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setStereoAudio(enabled)
    }

    fun setAudioBitrate(kbps: Int) = viewModelScope.launch {
        settingsDataStore.setAudioBitrate(kbps)
    }

    fun setShowDrawToolbarByDefault(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setShowDrawToolbarByDefault(enabled)
    }

    fun setSaveDrawingsWithVideo(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setSaveDrawingsWithVideo(enabled)
    }

    fun setDefaultPenColor(color: Int) = viewModelScope.launch {
        settingsDataStore.setDefaultPenColor(color)
    }

    fun setDefaultPenSize(size: Float) = viewModelScope.launch {
        settingsDataStore.setDefaultPenSize(size)
    }

    fun setSmoothDrawing(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setSmoothDrawing(enabled)
    }

    fun setCountdownSeconds(seconds: Int) = viewModelScope.launch {
        settingsDataStore.setCountdownSeconds(seconds)
    }

    fun setShowFloatingBubble(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setShowFloatingBubble(enabled)
    }

    fun setShakeToStop(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setShakeToStop(enabled)
    }

    fun setShowTouches(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setShowTouches(enabled)
    }

    fun setShowRecordingNotification(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setShowRecordingNotification(enabled)
    }

    fun setOrientationLock(lock: OrientationLock) = viewModelScope.launch {
        settingsDataStore.setOrientationLock(lock)
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsDataStore.setThemeMode(mode)
    }

    class Factory(
        private val settingsDataStore: SettingsDataStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsDataStore) as T
        }
    }
}
