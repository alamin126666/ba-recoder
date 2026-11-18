package com.example.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.BitrateOption
import com.example.domain.model.FpsOption
import com.example.domain.model.OrientationLock
import com.example.domain.model.RecordingConfig
import com.example.domain.model.ResolutionPreset
import com.example.domain.model.ThemeMode
import com.example.domain.model.VideoFormatOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ba_recorder_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val RESOLUTION = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val BITRATE = intPreferencesKey("bitrate_bps")
        val FORMAT = stringPreferencesKey("video_format")
        val SYSTEM_AUDIO = booleanPreferencesKey("record_system_audio")
        val MIC_AUDIO = booleanPreferencesKey("record_mic")
        val STEREO_AUDIO = booleanPreferencesKey("stereo_audio")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate_kbps")
        val SHOW_DRAW_DEFAULT = booleanPreferencesKey("show_draw_default")
        val SAVE_DRAWINGS = booleanPreferencesKey("save_drawings_with_video")
        val DEFAULT_PEN_COLOR = intPreferencesKey("default_pen_color")
        val DEFAULT_PEN_SIZE = floatPreferencesKey("default_pen_size")
        val SMOOTH_DRAWING = booleanPreferencesKey("smooth_drawing")
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val SHOW_FLOATING_BUBBLE = booleanPreferencesKey("show_floating_bubble")
        val SHAKE_TO_STOP = booleanPreferencesKey("shake_to_stop")
        val SHOW_TOUCHES = booleanPreferencesKey("show_touches")
        val SHOW_NOTIFICATION = booleanPreferencesKey("show_notification")
        val ORIENTATION_LOCK = stringPreferencesKey("orientation_lock")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RECENT_COLORS = stringPreferencesKey("recent_colors")
    }

    val configFlow: Flow<RecordingConfig> = context.settingsDataStore.data.map { prefs ->
        val resName = prefs[Keys.RESOLUTION] ?: ResolutionPreset.QHD_2K_1440P.name
        val resolution = ResolutionPreset.entries.find { it.name == resName } ?: ResolutionPreset.QHD_2K_1440P

        val fpsVal = prefs[Keys.FPS] ?: FpsOption.FPS_60.fps
        val fps = FpsOption.entries.find { it.fps == fpsVal } ?: FpsOption.FPS_60

        val bpsVal = prefs[Keys.BITRATE] ?: BitrateOption.HIGH_16M.bps
        val bitrate = BitrateOption.entries.find { it.bps == bpsVal } ?: BitrateOption.HIGH_16M

        val fmtName = prefs[Keys.FORMAT] ?: VideoFormatOption.MP4_H264.name
        val format = VideoFormatOption.entries.find { it.name == fmtName } ?: VideoFormatOption.MP4_H264

        val orientName = prefs[Keys.ORIENTATION_LOCK] ?: OrientationLock.AUTO.name
        val orientation = OrientationLock.entries.find { it.name == orientName } ?: OrientationLock.AUTO

        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = ThemeMode.entries.find { it.name == themeName } ?: ThemeMode.SYSTEM

        val recentColorsStr = prefs[Keys.RECENT_COLORS]
        val recentColors = if (recentColorsStr.isNullOrBlank()) {
            RecordingConfig().recentColors
        } else {
            recentColorsStr.split(",").mapNotNull { it.toIntOrNull() }.ifEmpty { RecordingConfig().recentColors }
        }

        RecordingConfig(
            resolution = resolution,
            fps = fps,
            bitrate = bitrate,
            format = format,
            recordSystemAudio = prefs[Keys.SYSTEM_AUDIO] ?: true,
            recordMic = prefs[Keys.MIC_AUDIO] ?: true,
            stereoAudio = prefs[Keys.STEREO_AUDIO] ?: true,
            audioBitrateKbps = prefs[Keys.AUDIO_BITRATE] ?: 192,
            showDrawToolbarByDefault = prefs[Keys.SHOW_DRAW_DEFAULT] ?: false,
            saveDrawingsWithVideo = prefs[Keys.SAVE_DRAWINGS] ?: true,
            defaultPenColor = prefs[Keys.DEFAULT_PEN_COLOR] ?: 0xFF00E676.toInt(),
            defaultPenSize = prefs[Keys.DEFAULT_PEN_SIZE] ?: 10f,
            smoothDrawing = prefs[Keys.SMOOTH_DRAWING] ?: true,
            countdownSeconds = prefs[Keys.COUNTDOWN_SECONDS] ?: 3,
            showFloatingBubble = prefs[Keys.SHOW_FLOATING_BUBBLE] ?: true,
            shakeToStop = prefs[Keys.SHAKE_TO_STOP] ?: false,
            showTouches = prefs[Keys.SHOW_TOUCHES] ?: false,
            showRecordingNotification = prefs[Keys.SHOW_NOTIFICATION] ?: true,
            orientationLock = orientation,
            themeMode = theme,
            recentColors = recentColors
        )
    }

    suspend fun setResolution(preset: ResolutionPreset) {
        context.settingsDataStore.edit { it[Keys.RESOLUTION] = preset.name }
    }

    suspend fun setFps(option: FpsOption) {
        context.settingsDataStore.edit { it[Keys.FPS] = option.fps }
    }

    suspend fun setBitrate(option: BitrateOption) {
        context.settingsDataStore.edit { it[Keys.BITRATE] = option.bps }
    }

    suspend fun setFormat(option: VideoFormatOption) {
        context.settingsDataStore.edit { it[Keys.FORMAT] = option.name }
    }

    suspend fun setRecordSystemAudio(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SYSTEM_AUDIO] = enabled }
    }

    suspend fun setRecordMic(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MIC_AUDIO] = enabled }
    }

    suspend fun setStereoAudio(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.STEREO_AUDIO] = enabled }
    }

    suspend fun setAudioBitrate(kbps: Int) {
        context.settingsDataStore.edit { it[Keys.AUDIO_BITRATE] = kbps }
    }

    suspend fun setShowDrawToolbarByDefault(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_DRAW_DEFAULT] = enabled }
    }

    suspend fun setSaveDrawingsWithVideo(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SAVE_DRAWINGS] = enabled }
    }

    suspend fun setDefaultPenColor(color: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PEN_COLOR] = color
            val current = prefs[Keys.RECENT_COLORS]?.split(",")?.mapNotNull { it.toIntOrNull() }
                ?: RecordingConfig().recentColors
            val updated = (listOf(color) + current.filter { it != color }).take(5)
            prefs[Keys.RECENT_COLORS] = updated.joinToString(",")
        }
    }

    suspend fun setDefaultPenSize(size: Float) {
        context.settingsDataStore.edit { it[Keys.DEFAULT_PEN_SIZE] = size }
    }

    suspend fun setSmoothDrawing(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SMOOTH_DRAWING] = enabled }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.COUNTDOWN_SECONDS] = seconds }
    }

    suspend fun setShowFloatingBubble(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_FLOATING_BUBBLE] = enabled }
    }

    suspend fun setShakeToStop(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHAKE_TO_STOP] = enabled }
    }

    suspend fun setShowTouches(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_TOUCHES] = enabled }
    }

    suspend fun setShowRecordingNotification(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_NOTIFICATION] = enabled }
    }

    suspend fun setOrientationLock(lock: OrientationLock) {
        context.settingsDataStore.edit { it[Keys.ORIENTATION_LOCK] = lock.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
}
