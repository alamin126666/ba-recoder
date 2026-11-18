package com.example.recording

import android.media.MediaRecorder

/**
 * A single microphone capture attempt: which `MediaRecorder.AudioSource` to use and how many
 * PCM channels to request.
 */
data class AudioRoute(val audioSource: Int, val channels: Int)

/**
 * Builds the ordered fallback chain of microphone routes used when starting a recording.
 *
 * Why a fallback chain is required:
 *
 *  * Some OEM devices reject [MediaRecorder.AudioSource.MIC] while the app is in the background
 *    (screen recording almost always means the app is in the background), yet accept
 *    [MediaRecorder.AudioSource.CAMCORDER].
 *  * Some devices refuse to `prepare()` a stereo microphone stream but accept mono.
 *  * A few devices only expose [MediaRecorder.AudioSource.DEFAULT].
 *
 * Without the chain, `prepare()` throws for those devices, the whole recording start fails, and the
 * user experiences "sometimes the voice is recorded, sometimes it is not".
 *
 * This object is intentionally free of Android runtime calls (it only reads compile-time int
 * constants) so it can be unit tested on the JVM.
 */
object AudioRoutePlan {

    /**
     * @param stereoAudio `true` when the user picked stereo audio in settings.
     * @return ordered, de-duplicated candidate routes. The list is never empty when a microphone
     *   is requested, and the final entry always requests mono audio so it is the most tolerant
     *   configuration on constrained devices.
     */
    fun candidates(stereoAudio: Boolean): List<AudioRoute> {
        val preferredChannels = if (stereoAudio) 2 else 1
        return listOf(
            // Two-mic / camcorder route first: best voice quality and background-capture tolerant.
            AudioRoute(MediaRecorder.AudioSource.CAMCORDER, preferredChannels),
            AudioRoute(MediaRecorder.AudioSource.MIC, preferredChannels),
            // Mono fallbacks — some devices reject anything above one channel.
            AudioRoute(MediaRecorder.AudioSource.MIC, 1),
            AudioRoute(MediaRecorder.AudioSource.DEFAULT, 1)
        ).distinct()
    }
}