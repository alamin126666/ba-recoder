package com.example.presentation.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.datastore.SettingsDataStore
import com.example.domain.model.AnnotationStamp
import com.example.domain.model.DrawTool
import com.example.recording.RecordingFileManager
import com.example.recording.ScreenRecordManager
import com.example.service.DrawOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DrawToolsUiState(
    val selectedTool: DrawTool = DrawTool.PEN,
    val selectedColor: Int = Color.parseColor("#00E676"),
    val strokeWidth: Float = 10f,
    val smoothDrawing: Boolean = true,
    val selectedStamp: AnnotationStamp = AnnotationStamp.CHECKMARK,
    val recentColors: List<Int> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val strokeCount: Int = 0,
    val isLiveOverlayActive: Boolean = false,
    val statusMessage: String? = null
)

class DrawViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val fileManager: RecordingFileManager,
    private val recordManager: ScreenRecordManager
) : ViewModel() {

    private val localTool = MutableStateFlow(DrawTool.PEN)
    private val localColor = MutableStateFlow<Int?>(null)
    private val localStroke = MutableStateFlow<Float?>(null)
    private val localStamp = MutableStateFlow(AnnotationStamp.CHECKMARK)
    private val historyState = MutableStateFlow(Triple(false, false, 0))
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DrawToolsUiState> = combine(
        settingsDataStore.configFlow,
        recordManager.runtimeState,
        localTool,
        localColor,
        localStroke,
        localStamp,
        historyState,
        _statusMessage
    ) { values ->
        val config = values[0] as com.example.domain.model.RecordingConfig
        val runtime = values[1] as com.example.domain.model.RecorderRuntimeState
        val tool = values[2] as DrawTool
        val colorOverride = values[3] as Int?
        val strokeOverride = values[4] as Float?
        val stamp = values[5] as AnnotationStamp
        @Suppress("UNCHECKED_CAST")
        val hist = values[6] as Triple<Boolean, Boolean, Int>
        val msg = values[7] as String?

        DrawToolsUiState(
            selectedTool = tool,
            selectedColor = colorOverride ?: config.defaultPenColor,
            strokeWidth = strokeOverride ?: config.defaultPenSize,
            smoothDrawing = config.smoothDrawing,
            selectedStamp = stamp,
            recentColors = config.recentColors,
            canUndo = hist.first,
            canRedo = hist.second,
            strokeCount = hist.third,
            isLiveOverlayActive = runtime.isDrawOverlayActive,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DrawToolsUiState()
    )

    fun selectTool(tool: DrawTool) {
        localTool.value = tool
    }

    fun selectColor(color: Int) {
        localColor.value = color
        viewModelScope.launch {
            settingsDataStore.setDefaultPenColor(color)
        }
    }

    fun selectStrokeWidth(width: Float) {
        localStroke.value = width
        viewModelScope.launch {
            settingsDataStore.setDefaultPenSize(width)
        }
    }

    fun selectStamp(stamp: AnnotationStamp) {
        localStamp.value = stamp
        localTool.value = DrawTool.STAMP
    }

    fun updateHistory(canUndo: Boolean, canRedo: Boolean, count: Int) {
        historyState.value = Triple(canUndo, canRedo, count)
    }

    fun toggleLiveOverlay(context: Context) {
        if (recordManager.runtimeState.value.isDrawOverlayActive) {
            DrawOverlayService.stop(context)
        } else {
            DrawOverlayService.start(context)
        }
    }

    fun saveSketch(bitmap: Bitmap) {
        viewModelScope.launch {
            val uri = fileManager.saveSketchBitmap(bitmap)
            _statusMessage.update {
                if (uri != null) "Sketch saved to Pictures/BA Screen Record" else "Failed to save sketch"
            }
        }
    }

    fun clearMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val settingsDataStore: SettingsDataStore,
        private val fileManager: RecordingFileManager,
        private val recordManager: ScreenRecordManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DrawViewModel(settingsDataStore, fileManager, recordManager) as T
        }
    }
}
