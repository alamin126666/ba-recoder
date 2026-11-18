package com.example.recording

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.domain.model.VideoFormatOption
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OutputTarget(
    val uri: Uri,
    val filePath: String,
    val fileName: String,
    val parcelFileDescriptor: ParcelFileDescriptor?,
    val isMediaStore: Boolean
)

class RecordingFileManager(private val context: Context) {

    fun createVideoOutputTarget(
        format: VideoFormatOption,
        prefix: String = "BA_REC"
    ): OutputTarget {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = format.extension
        val fileName = "${prefix}_$timestamp.$ext"
        val mimeType = if (format == VideoFormatOption.WEBM) "video/webm" else "video/mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/BA Screen Record"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val pfd = resolver.openFileDescriptor(uri, "rw")
                if (pfd != null) {
                    return OutputTarget(
                        uri = uri,
                        filePath = "",
                        fileName = fileName,
                        parcelFileDescriptor = pfd,
                        isMediaStore = true
                    )
                }
            }
        }

        // App-scoped Movies directory (requires zero storage permissions on API 24-28 and fallback)
        val moviesDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "BA Screen Record"
        )
        if (!moviesDir.exists()) moviesDir.mkdirs()
        val file = File(moviesDir, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return OutputTarget(
            uri = uri,
            filePath = file.absolutePath,
            fileName = fileName,
            parcelFileDescriptor = null,
            isMediaStore = false
        )
    }

    fun finalizeVideoTarget(target: OutputTarget): Long {
        return try {
            target.parcelFileDescriptor?.close()
            if (target.isMediaStore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(target.uri, values, null, null)
                querySize(target.uri)
            } else {
                val file = File(target.filePath)
                if (file.exists()) file.length() else 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    fun discardTarget(target: OutputTarget) {
        try {
            target.parcelFileDescriptor?.close()
            if (target.isMediaStore) {
                context.contentResolver.delete(target.uri, null, null)
            } else if (target.filePath.isNotEmpty()) {
                File(target.filePath).delete()
            }
        } catch (_: Exception) {
        }
    }

    fun saveSketchBitmap(bitmap: Bitmap): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "BA_SKETCH_$timestamp.png"

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/BA Screen Record"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return uri
                }
            }

            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "BA Screen Record"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    fun querySize(uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.coerceAtLeast(0L)
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun getDeviceStoragePair(): Pair<Long, Long> {
        return try {
            val path = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(path.path)
            val free = stat.availableBytes
            val total = stat.totalBytes.coerceAtLeast(1L)
            free to total
        } catch (_: Exception) {
            0L to 1L
        }
    }
}
