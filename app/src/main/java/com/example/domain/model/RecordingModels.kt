package com.example.domain.model

enum class ResolutionPreset(
    val shortLabel: String,
    val displayName: String,
    val shortSidePx: Int,
    val longSidePx: Int
) {
    SD_480P("480p", "480p (SD)", 480, 854),
    HD_720P("720p", "720p (HD)", 720, 1280),
    FHD_1080P("1080p", "1080p (Full HD)", 1080, 1920),
    QHD_2K_1440P("2K", "1440p (2K QHD)", 1440, 2560),
    UHD_4K_2160P("4K", "2160p (4K UHD)", 2160, 3840)
}

enum class FpsOption(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS"),
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS"),
    FPS_120(120, "120 FPS")
}

enum class BitrateOption(val bps: Int, val mbps: Int, val label: String) {
    LOW_4M(4_000_000, 4, "Low (4 Mbps)"),
    MEDIUM_8M(8_000_000, 8, "Medium (8 Mbps)"),
    HIGH_16M(16_000_000, 16, "High (16 Mbps)"),
    ULTRA_24M(24_000_000, 24, "Ultra (24 Mbps)")
}

enum class VideoFormatOption(val label: String, val extension: String) {
    MP4_H264("MP4 (H.264)", "mp4"),
    MP4_HEVC("MP4 (H.265/HEVC)", "mp4"),
    WEBM("WebM (VP8)", "webm")
}

enum class OrientationLock(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class RecordingConfig(
    val resolution: ResolutionPreset = ResolutionPreset.QHD_2K_1440P,
    val fps: FpsOption = FpsOption.FPS_60,
    val bitrate: BitrateOption = BitrateOption.HIGH_16M,
    val format: VideoFormatOption = VideoFormatOption.MP4_H264,
    val recordSystemAudio: Boolean = true,
    val recordMic: Boolean = true,
    val stereoAudio: Boolean = true,
    val audioBitrateKbps: Int = 192,
    val showDrawToolbarByDefault: Boolean = false,
    val saveDrawingsWithVideo: Boolean = true,
    val defaultPenColor: Int = 0xFF00E676.toInt(),
    val defaultPenSize: Float = 10f,
    val smoothDrawing: Boolean = true,
    val countdownSeconds: Int = 3,
    val showFloatingBubble: Boolean = true,
    val shakeToStop: Boolean = false,
    val showTouches: Boolean = false,
    val showRecordingNotification: Boolean = true,
    val orientationLock: OrientationLock = OrientationLock.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recentColors: List<Int> = listOf(
        0xFFFF1744.toInt(),
        0xFF00E676.toInt(),
        0xFFFFEA00.toInt(),
        0xFF2979FF.toInt(),
        0xFFFFFFFF.toInt()
    )
)

enum class RecorderStatus {
    IDLE,
    COUNTDOWN,
    RECORDING,
    PAUSED
}

data class RecorderRuntimeState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val countdownRemaining: Int = 0,
    val elapsedMs: Long = 0L,
    val audioLevel: Float = 0f,
    val isDrawOverlayActive: Boolean = false,
    val isMicMuted: Boolean = false,
    val currentOutputLabel: String = "",
    val lastError: String? = null
)

data class StorageStats(
    val totalCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val deviceFreeBytes: Long = 0L,
    val deviceTotalBytes: Long = 1L
)

enum class DrawTool(val label: String) {
    PEN("Pen"),
    ARROW("Arrow"),
    RECTANGLE("Rect"),
    CIRCLE("Circle"),
    TEXT("Text"),
    HIGHLIGHTER("Highlight"),
    STAMP("Stamp"),
    ERASER("Eraser")
}

enum class AnnotationStamp(val symbol: String, val label: String, val defaultColor: Int) {
    CHECKMARK("✓", "Check", 0xFF00E676.toInt()),
    CROSS("✕", "Cross", 0xFFFF1744.toInt()),
    STAR("★", "Star", 0xFFFFD600.toInt()),
    EXCLAMATION("!", "Alert", 0xFFFF9100.toInt()),
    PIN("📍", "Pin", 0xFFFF1744.toInt()),
    LIGHTBULB("💡", "Idea", 0xFFFFEA00.toInt()),
    NUM_1("①", "Step 1", 0xFF00E676.toInt()),
    NUM_2("②", "Step 2", 0xFF00E676.toInt()),
    NUM_3("③", "Step 3", 0xFF00E676.toInt())
}

enum class GallerySortOption(val label: String) {
    DATE_DESC("Newest"),
    DATE_ASC("Oldest"),
    DURATION_DESC("Longest"),
    SIZE_DESC("Largest"),
    NAME_ASC("Name A–Z")
}

enum class GalleryFilterOption(val label: String) {
    ALL("All"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}
