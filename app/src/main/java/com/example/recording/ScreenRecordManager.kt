package com.example.recording

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.example.data.local.RecordingEntity
import com.example.data.repository.RecordingRepository
import com.example.domain.model.OrientationLock
import com.example.domain.model.RecorderRuntimeState
import com.example.domain.model.RecorderStatus
import com.example.domain.model.RecordingConfig
import com.example.domain.model.VideoFormatOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ScreenRecordManager(
    private val context: Context,
    private val fileManager: RecordingFileManager,
    private val repository: RecordingRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _runtimeState = MutableStateFlow(RecorderRuntimeState())
    val runtimeState: StateFlow<RecorderRuntimeState> = _runtimeState.asStateFlow()

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var currentTarget: OutputTarget? = null
    private var activeConfig: RecordingConfig = RecordingConfig()
    private var actualWidth: Int = 1440
    private var actualHeight: Int = 2560
    private var usedDrawOverlay: Boolean = false

    private var timerJob: Job? = null
    private var accumulatedMs: Long = 0L
    private var segmentStartMs: Long = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            scope.launch {
                if (_runtimeState.value.status == RecorderStatus.RECORDING ||
                    _runtimeState.value.status == RecorderStatus.PAUSED
                ) {
                    stopRecording()
                }
            }
        }
    }

    fun setCountdown(secondsRemaining: Int) {
        _runtimeState.update {
            it.copy(
                status = RecorderStatus.COUNTDOWN,
                countdownRemaining = secondsRemaining,
                lastError = null
            )
        }
    }

    fun cancelCountdown() {
        if (_runtimeState.value.status == RecorderStatus.COUNTDOWN) {
            _runtimeState.update {
                it.copy(status = RecorderStatus.IDLE, countdownRemaining = 0)
            }
        }
    }

    fun setDrawOverlayActive(active: Boolean) {
        if (active) usedDrawOverlay = true
        _runtimeState.update { it.copy(isDrawOverlayActive = active) }
    }

    fun clearError() {
        _runtimeState.update { it.copy(lastError = null) }
    }

    fun startRecording(
        resultCode: Int,
        data: Intent,
        config: RecordingConfig,
        micRequested: Boolean = config.recordMic
    ): Boolean {
        if (_runtimeState.value.status == RecorderStatus.RECORDING ||
            _runtimeState.value.status == RecorderStatus.PAUSED
        ) {
            return false
        }

        activeConfig = config
        usedDrawOverlay = config.showDrawToolbarByDefault
        accumulatedMs = 0L

        val metrics = getScreenMetrics()
        val (targetW, targetH) = computeTargetResolution(
            screenW = metrics.widthPixels,
            screenH = metrics.heightPixels,
            shortSideTarget = config.resolution.shortSidePx,
            orientationLock = config.orientationLock
        )
        actualWidth = targetW
        actualHeight = targetH

        val target = fileManager.createVideoOutputTarget(config.format)
        currentTarget = target

        return try {
            val projectionManager =
                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("MediaProjection permission was not granted.")
            mediaProjection = projection
            projection.registerCallback(projectionCallback, null)

            val hasMicPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val enableMic = micRequested && hasMicPermission

            val recorderStart = buildRecorderWithAudioFallback(target, config, targetW, targetH, enableMic)
            val recorder = recorderStart.recorder
            val audioAttached = recorderStart.audioAttached
            mediaRecorder = recorder

            val surface = recorder.surface
            virtualDisplay = projection.createVirtualDisplay(
                "BAScreenRecordDisplay",
                targetW,
                targetH,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            recorder.start()
            segmentStartMs = System.currentTimeMillis()
            _runtimeState.update {
                it.copy(
                    status = RecorderStatus.RECORDING,
                    countdownRemaining = 0,
                    elapsedMs = 0L,
                    isMicMuted = !audioAttached,
                    currentOutputLabel = "${config.resolution.shortLabel} • ${config.fps.fps} FPS",
                    lastError = when {
                        !micRequested -> null
                        !hasMicPermission -> "Microphone permission missing — recording video without voice."
                        !audioAttached -> "No usable microphone route on this device — recording video without voice."
                        else -> null
                    }
                )
            }
            startTimerLoop()
            true
        } catch (e: Exception) {
            cleanupFailedStart(target)
            _runtimeState.update {
                it.copy(
                    status = RecorderStatus.IDLE,
                    countdownRemaining = 0,
                    lastError = e.localizedMessage ?: "Failed to start screen recording."
                )
            }
            false
        }
    }

    /**
     * Result of the audio route negotiation: the prepared recorder plus whether a microphone
     * track was actually attached to it.
     *
     * @param recorder the prepared [MediaRecorder] (never `null`; throws if video could not start).
     * @param audioAttached `true` only when one of the microphone routes prepared successfully.
     *   When `false` the recording still proceeds, but with a silent video track — callers use
     *   this flag to surface an accurate warning instead of silently dropping the voice track.
     */
    private data class RecorderStart(val recorder: MediaRecorder, val audioAttached: Boolean)

    /**
     * Some devices intermittently reject [MediaRecorder.AudioSource.MIC] while the app is in the
     * background, or refuse stereo PCM capture. This walks a fallback chain so voice capture never
     * silently disappears: CAMCORDER (stereo) -> MIC (stereo) -> MIC (mono) -> DEFAULT (mono),
     * and finally records video only if every audio route fails.
     */
    private fun buildRecorderWithAudioFallback(
        target: OutputTarget,
        config: RecordingConfig,
        width: Int,
        height: Int,
        enableMic: Boolean
    ): RecorderStart {
        if (!enableMic) {
            return RecorderStart(
                recorder = buildMediaRecorder(target, config, width, height, audioSource = null, channels = 0),
                audioAttached = false
            )
        }

        var lastError: Exception? = null
        for (route in AudioRoutePlan.candidates(config.stereoAudio)) {
            try {
                val recorder = buildMediaRecorder(
                    target = target,
                    config = config,
                    width = width,
                    height = height,
                    audioSource = route.audioSource,
                    channels = route.channels
                )
                return RecorderStart(recorder = recorder, audioAttached = true)
            } catch (e: Exception) {
                lastError = e
            }
        }

        // Never fail the whole recording because of audio — fall back to a silent video track and
        // report `audioAttached = false` so the UI can warn the user honestly.
        return try {
            RecorderStart(
                recorder = buildMediaRecorder(target, config, width, height, audioSource = null, channels = 0),
                audioAttached = false
            )
        } catch (e: Exception) {
            throw lastError ?: e
        }
    }

    private fun buildMediaRecorder(
        target: OutputTarget,
        config: RecordingConfig,
        width: Int,
        height: Int,
        audioSource: Int?,
        channels: Int
    ): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            recorder.apply {
                if (audioSource != null) {
                    setAudioSource(audioSource)
                }
                setVideoSource(MediaRecorder.VideoSource.SURFACE)

                val outputFormat = if (config.format == VideoFormatOption.WEBM) {
                    MediaRecorder.OutputFormat.WEBM
                } else {
                    MediaRecorder.OutputFormat.MPEG_4
                }
                setOutputFormat(outputFormat)

                if (target.parcelFileDescriptor != null) {
                    setOutputFile(target.parcelFileDescriptor.fileDescriptor)
                } else {
                    setOutputFile(target.filePath)
                }

                val videoEncoder = when (config.format) {
                    VideoFormatOption.MP4_H264 -> MediaRecorder.VideoEncoder.H264
                    VideoFormatOption.MP4_HEVC -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            MediaRecorder.VideoEncoder.HEVC
                        } else {
                            MediaRecorder.VideoEncoder.H264
                        }
                    }
                    VideoFormatOption.WEBM -> MediaRecorder.VideoEncoder.VP8
                }
                setVideoEncoder(videoEncoder)
                setVideoSize(width, height)
                setVideoFrameRate(config.fps.fps)
                setVideoEncodingBitRate(config.bitrate.bps)

                if (audioSource != null) {
                    val audioEncoder = if (config.format == VideoFormatOption.WEBM) {
                        MediaRecorder.AudioEncoder.VORBIS
                    } else {
                        MediaRecorder.AudioEncoder.AAC
                    }
                    setAudioEncoder(audioEncoder)
                    setAudioEncodingBitRate(config.audioBitrateKbps * 1000)
                    setAudioSamplingRate(44100)
                    setAudioChannels(channels.coerceAtLeast(1))
                }

                prepare()
            }
            return recorder
        } catch (e: Exception) {
            try {
                recorder.reset()
                recorder.release()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    fun pauseRecording() {
        if (_runtimeState.value.status != RecorderStatus.RECORDING) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
                accumulatedMs += (System.currentTimeMillis() - segmentStartMs).coerceAtLeast(0L)
                timerJob?.cancel()
                _runtimeState.update {
                    it.copy(
                        status = RecorderStatus.PAUSED,
                        elapsedMs = accumulatedMs,
                        audioLevel = 0f
                    )
                }
            }
        } catch (e: Exception) {
            _runtimeState.update { it.copy(lastError = "Pause failed: ${e.localizedMessage}") }
        }
    }

    fun resumeRecording() {
        if (_runtimeState.value.status != RecorderStatus.PAUSED) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
                segmentStartMs = System.currentTimeMillis()
                _runtimeState.update { it.copy(status = RecorderStatus.RECORDING) }
                startTimerLoop()
            }
        } catch (e: Exception) {
            _runtimeState.update { it.copy(lastError = "Resume failed: ${e.localizedMessage}") }
        }
    }

    fun stopRecording() {
        val status = _runtimeState.value.status
        if (status != RecorderStatus.RECORDING && status != RecorderStatus.PAUSED) return

        timerJob?.cancel()
        timerJob = null

        val finalDurationMs = if (status == RecorderStatus.RECORDING) {
            accumulatedMs + (System.currentTimeMillis() - segmentStartMs).coerceAtLeast(0L)
        } else {
            accumulatedMs
        }

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        val target = currentTarget
        currentTarget = null

        _runtimeState.update {
            it.copy(
                status = RecorderStatus.IDLE,
                elapsedMs = 0L,
                audioLevel = 0f,
                countdownRemaining = 0,
                currentOutputLabel = ""
            )
        }

        if (target != null) {
            scope.launch(Dispatchers.IO) {
                val finalSize = fileManager.finalizeVideoTarget(target)
                if (finalSize > 0L || target.isMediaStore) {
                    val entity = RecordingEntity(
                        fileUri = target.uri.toString(),
                        filePath = target.filePath,
                        fileName = target.fileName,
                        durationMs = finalDurationMs.coerceAtLeast(1000L),
                        fileSizeBytes = finalSize,
                        resolution = activeConfig.resolution.shortLabel,
                        width = actualWidth,
                        height = actualHeight,
                        fps = activeConfig.fps.fps,
                        bitrateMbps = activeConfig.bitrate.mbps,
                        hasDrawing = usedDrawOverlay
                    )
                    repository.insert(entity)
                }
            }
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                val currentElapsed = accumulatedMs + (System.currentTimeMillis() - segmentStartMs).coerceAtLeast(0L)
                val amp = try {
                    if (!_runtimeState.value.isMicMuted) {
                        ((mediaRecorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0.05f, 1f)
                    } else {
                        0.08f
                    }
                } catch (_: Exception) {
                    0.08f
                }
                _runtimeState.update {
                    it.copy(
                        elapsedMs = currentElapsed,
                        audioLevel = amp
                    )
                }
                delay(180L)
            }
        }
    }

    private fun cleanupFailedStart(target: OutputTarget) {
        timerJob?.cancel()
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null
        currentTarget = null
        fileManager.discardTarget(target)
    }

    private fun getScreenMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun computeTargetResolution(
        screenW: Int,
        screenH: Int,
        shortSideTarget: Int,
        orientationLock: OrientationLock
    ): Pair<Int, Int> {
        val isLandscape = when (orientationLock) {
            OrientationLock.LANDSCAPE -> true
            OrientationLock.PORTRAIT -> false
            OrientationLock.AUTO -> screenW > screenH
        }
        val rawShort = min(screenW, screenH).coerceAtLeast(1)
        val rawLong = max(screenW, screenH).coerceAtLeast(1)
        val aspect = rawLong.toFloat() / rawShort.toFloat()

        // Clamp short side to at most 1440 (2K) / 2160 (4K) while keeping even dimensions
        val clampedShort = ((shortSideTarget / 2) * 2).coerceAtLeast(480)
        val computedLong = (((clampedShort * aspect).roundToInt() / 2) * 2).coerceAtLeast(640)

        return if (isLandscape) computedLong to clampedShort else clampedShort to computedLong
    }
}
