package com.example

import com.example.domain.model.BitrateOption
import com.example.domain.model.FpsOption
import com.example.domain.model.RecordingConfig
import com.example.domain.model.ResolutionPreset
import com.example.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun defaultRecordingConfig_is2K60Fps16Mbps() {
        val config = RecordingConfig()
        assertEquals(ResolutionPreset.QHD_2K_1440P, config.resolution)
        assertEquals(FpsOption.FPS_60, config.fps)
        assertEquals(BitrateOption.HIGH_16M, config.bitrate)
    }

    @Test
    fun formatUtils_formatsDurationAndSize() {
        assertEquals("03:24", FormatUtils.formatDuration(204_000L))
        assertEquals("01:02:05", FormatUtils.formatDuration(3_725_000L))
        assertEquals("00:03:24", FormatUtils.formatTimerFull(204_000L))
        assertEquals("1.0 MB", FormatUtils.formatFileSize(1024L * 1024L))
    }
}
