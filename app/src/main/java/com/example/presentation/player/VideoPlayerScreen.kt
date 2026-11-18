package com.example.presentation.player

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.components.ResolutionBadge
import com.example.ui.theme.EmeraldPrimary
import com.example.utils.FormatUtils
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlayerScreen(
    uiState: VideoPlayerUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onTrim: (startMs: Long, endMs: Long) -> Unit,
    onMessageShown: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableIntStateOf(0) }
    var totalMs by remember { mutableIntStateOf(1) }
    var showTrimPanel by remember { mutableStateOf(false) }
    var trimRange by remember { mutableStateOf(0f..1f) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            onMessageShown()
        }
    }

    LaunchedEffect(isPlaying, videoViewRef) {
        while (isPlaying) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    positionMs = vv.currentPosition
                    if (vv.duration > 0) totalMs = vv.duration
                } else {
                    isPlaying = false
                }
            }
            delay(250L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0A140B)
    ) { padding ->
        val rec = uiState.recording
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EmeraldPrimary)
            }
        } else if (rec == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Recording not found", color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Back") }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Top Player Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = rec.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ResolutionBadge(label = rec.resolution)
                                Text(
                                    text = "${rec.fps} FPS • ${FormatUtils.formatFileSize(rec.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                            }
                        }
                    }

                    Row {
                        IconButton(onClick = { showTrimPanel = !showTrimPanel }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_trim),
                                contentDescription = "Trim Video",
                                tint = if (showTrimPanel) EmeraldPrimary else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = onShare) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_share),
                                contentDescription = "Share Video",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Video Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                val targetUri = if (rec.filePath.isNotEmpty() && File(rec.filePath).exists()) {
                                    Uri.fromFile(File(rec.filePath))
                                } else {
                                    Uri.parse(rec.fileUri)
                                }
                                setVideoURI(targetUri)
                                setOnPreparedListener { mp ->
                                    totalMs = mp.duration.coerceAtLeast(rec.durationMs.toInt()).coerceAtLeast(1)
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    positionMs = 0
                                }
                                videoViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Playback & Trim Controls
                Surface(
                    color = Color(0xFF132215),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(positionMs.toLong()),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                            Text(
                                text = FormatUtils.formatDuration(totalMs.toLong()),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        var sliderFraction by remember(positionMs, totalMs) {
                            mutableFloatStateOf((positionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f))
                        }

                        Slider(
                            value = sliderFraction,
                            onValueChange = { frac ->
                                sliderFraction = frac
                                val targetPos = (frac * totalMs).toInt()
                                positionMs = targetPos
                                videoViewRef?.seekTo(targetPos)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = EmeraldPrimary,
                                activeTrackColor = EmeraldPrimary
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = EmeraldPrimary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val vv = videoViewRef ?: return@IconButton
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (isPlaying) R.drawable.ic_record_pause else R.drawable.ic_record_start
                                        ),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        if (showTrimPanel) {
                            val startTrimMs = (trimRange.start * totalMs).toLong()
                            val endTrimMs = (trimRange.endInclusive * totalMs).toLong()
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Trim Range: ${FormatUtils.formatDuration(startTrimMs)} — ${FormatUtils.formatDuration(endTrimMs)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                RangeSlider(
                                    value = trimRange,
                                    onValueChange = { trimRange = it },
                                    colors = SliderDefaults.colors(
                                        thumbColor = EmeraldPrimary,
                                        activeTrackColor = EmeraldPrimary
                                    )
                                )
                                Button(
                                    onClick = { onTrim(startTrimMs, endTrimMs) },
                                    enabled = !uiState.isTrimming,
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (uiState.isTrimming) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Trimming Clip...")
                                    } else {
                                        Text("Export Trimmed Copy")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
