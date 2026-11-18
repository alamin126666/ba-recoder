package com.example

import android.media.MediaRecorder
import com.example.recording.AudioRoutePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the "voice is recorded only sometimes" bug.
 *
 * The old code built a single microphone route and let `MediaRecorder.prepare()` throw, which made
 * the whole recording start fail on devices that reject that specific audio source. These tests
 * lock in the fallback-chain contract so the bug cannot come back silently.
 */
class AudioRoutePlanTest {

    @Test
    fun candidates_alwaysStartWithCamcorderForBestVoiceQuality() {
        val routes = AudioRoutePlan.candidates(stereoAudio = true)
        assertEquals(MediaRecorder.AudioSource.CAMCORDER, routes.first().audioSource)
    }

    @Test
    fun candidates_stereoRequested_usesTwoChannelsOnFirstRoute() {
        val routes = AudioRoutePlan.candidates(stereoAudio = true)
        assertEquals(2, routes.first().channels)
    }

    @Test
    fun candidates_monoRequested_neverExceedsOneChannel() {
        val routes = AudioRoutePlan.candidates(stereoAudio = false)
        assertTrue(routes.all { it.channels == 1 })
    }

    @Test
    fun candidates_alwaysEndWithMonoFallback() {
        // The last candidate must be the most tolerant configuration, otherwise constrained
        // devices can still end up with no voice track at all.
        listOf(true, false).forEach { stereo ->
            val routes = AudioRoutePlan.candidates(stereo)
            assertTrue("stereo=$stereo route list must not be empty", routes.isNotEmpty())
            assertEquals(1, routes.last().channels)
        }
    }

    @Test
    fun candidates_areDeduplicated() {
        val routes = AudioRoutePlan.candidates(stereoAudio = false)
        assertEquals(routes.size, routes.distinct().size)
    }

    @Test
    fun candidates_alwaysContainMicrophoneRoute() {
        // A missing MIC route means some devices would record silence with no fallback.
        listOf(true, false).forEach { stereo ->
            val sources = AudioRoutePlan.candidates(stereo).map { it.audioSource }
            assertTrue("stereo=$stereo must contain MIC", sources.contains(MediaRecorder.AudioSource.MIC))
        }
    }

    @Test
    fun candidates_provideMultipleFallbacksSoOneFailureCannotKillAudio() {
        val routes = AudioRoutePlan.candidates(stereoAudio = true)
        assertTrue("expected at least 3 fallback routes, got ${routes.size}", routes.size >= 3)
    }
}