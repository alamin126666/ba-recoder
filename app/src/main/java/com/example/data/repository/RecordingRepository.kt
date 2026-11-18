package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.RecordingDao
import com.example.data.local.RecordingEntity
import com.example.domain.model.VideoFormatOption
import com.example.recording.RecordingFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

class RecordingRepository(
    private val context: Context,
    private val dao: RecordingDao,
    private val fileManager: RecordingFileManager
) {
    val allRecordings: Flow<List<RecordingEntity>> = dao.getAllRecordings()
    val recordingCount: Flow<Int> = dao.getRecordingCount()
    val totalSizeBytes: Flow<Long> = dao.getTotalSizeBytes()
    val totalDurationMs: Flow<Long> = dao.getTotalDurationMs()

    suspend fun getById(id: String): RecordingEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun insert(entity: RecordingEntity) = withContext(Dispatchers.IO) {
        dao.insert(entity)
    }

    suspend fun rename(recording: RecordingEntity, rawNewName: String) = withContext(Dispatchers.IO) {
        val cleanBase = rawNewName.trim().replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        if (cleanBase.isEmpty()) return@withContext
        val ext = recording.fileName.substringAfterLast('.', "mp4")
        val finalName = if (cleanBase.endsWith(".$ext", ignoreCase = true)) cleanBase else "$cleanBase.$ext"

        try {
            if (recording.filePath.isNotEmpty()) {
                val oldFile = File(recording.filePath)
                if (oldFile.exists()) {
                    val newFile = File(oldFile.parentFile, finalName)
                    if (oldFile.renameTo(newFile)) {
                        val newUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            newFile
                        )
                        dao.delete(recording)
                        dao.insert(
                            recording.copy(
                                fileName = finalName,
                                filePath = newFile.absolutePath,
                                fileUri = newUri.toString()
                            )
                        )
                        return@withContext
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = Uri.parse(recording.fileUri)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, finalName)
                }
                context.contentResolver.update(uri, values, null, null)
            }
        } catch (_: Exception) {
        }
        dao.updateFileName(recording.id, finalName)
    }

    suspend fun delete(recording: RecordingEntity) = withContext(Dispatchers.IO) {
        try {
            if (recording.filePath.isNotEmpty()) {
                val file = File(recording.filePath)
                if (file.exists()) file.delete()
            } else {
                context.contentResolver.delete(Uri.parse(recording.fileUri), null, null)
            }
        } catch (_: Exception) {
        }
        dao.delete(recording)
    }

    suspend fun deleteMultiple(recordings: List<RecordingEntity>) = withContext(Dispatchers.IO) {
        recordings.forEach { rec ->
            try {
                if (rec.filePath.isNotEmpty()) {
                    File(rec.filePath).delete()
                } else {
                    context.contentResolver.delete(Uri.parse(rec.fileUri), null, null)
                }
            } catch (_: Exception) {
            }
        }
        dao.deleteByIds(recordings.map { it.id })
    }

    fun createShareIntent(recordings: List<RecordingEntity>): Intent? {
        if (recordings.isEmpty()) return null
        val uris = ArrayList<Uri>(recordings.mapNotNull { rec ->
            try {
                if (rec.filePath.isNotEmpty()) {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(rec.filePath)
                    )
                } else {
                    Uri.parse(rec.fileUri)
                }
            } catch (_: Exception) {
                null
            }
        })
        if (uris.isEmpty()) return null

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, "Share Recording")
    }

    suspend fun extractThumbnail(recording: RecordingEntity): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            if (recording.filePath.isNotEmpty() && File(recording.filePath).exists()) {
                retriever.setDataSource(recording.filePath)
            } else {
                retriever.setDataSource(context, Uri.parse(recording.fileUri))
            }
            val frameTimeUs = ((recording.durationMs / 4L).coerceAtLeast(300L)) * 1000L
            retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun trimRecording(
        source: RecordingEntity,
        startMs: Long,
        endMs: Long
    ): Result<RecordingEntity> = withContext(Dispatchers.IO) {
        if (endMs <= startMs + 500L) {
            return@withContext Result.failure(IllegalArgumentException("Trim range must be at least 0.5 seconds"))
        }

        val target = fileManager.createVideoOutputTarget(
            format = VideoFormatOption.MP4_H264,
            prefix = "BA_TRIM"
        )
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            if (source.filePath.isNotEmpty() && File(source.filePath).exists()) {
                extractor.setDataSource(source.filePath)
            } else {
                context.contentResolver.openFileDescriptor(Uri.parse(source.fileUri), "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                } ?: return@withContext Result.failure(IllegalStateException("Unable to open source video"))
            }

            muxer = if (target.parcelFileDescriptor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                MediaMuxer(target.parcelFileDescriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else {
                MediaMuxer(target.filePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val trackIndexMap = HashMap<Int, Int>()
            var maxBufferSize = 1024 * 1024
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    val dstIndex = muxer.addTrack(format)
                    trackIndexMap[i] = dstIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val size = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (size > maxBufferSize) maxBufferSize = size
                    }
                }
            }

            if (trackIndexMap.isEmpty()) {
                fileManager.discardTarget(target)
                return@withContext Result.failure(IllegalStateException("No audio or video tracks found"))
            }

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var firstSampleOffsetUs = -1L

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs > endUs) break

                if (sampleTimeUs >= startUs) {
                    if (firstSampleOffsetUs < 0L) firstSampleOffsetUs = sampleTimeUs
                    bufferInfo.presentationTimeUs = (sampleTimeUs - firstSampleOffsetUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags
                    val dstTrack = trackIndexMap[extractor.sampleTrackIndex]
                    if (dstTrack != null) {
                        muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    }
                }
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            val finalSize = fileManager.finalizeVideoTarget(target)
            val trimmedEntity = RecordingEntity(
                fileUri = target.uri.toString(),
                filePath = target.filePath,
                fileName = target.fileName,
                durationMs = (endMs - startMs).coerceAtLeast(500L),
                fileSizeBytes = finalSize,
                resolution = source.resolution,
                width = source.width,
                height = source.height,
                fps = source.fps,
                bitrateMbps = source.bitrateMbps,
                hasDrawing = source.hasDrawing
            )
            dao.insert(trimmedEntity)
            Result.success(trimmedEntity)
        } catch (e: Exception) {
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            fileManager.discardTarget(target)
            Result.failure(e)
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }
}
