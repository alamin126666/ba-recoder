package com.example.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.RecordingEntity
import com.example.domain.model.RecorderStatus
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.PulsingRecordButton
import com.example.ui.components.ResolutionBadge
import com.example.ui.components.shimmerEffect
import com.example.ui.theme.AmberPause
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import com.example.ui.theme.RecordRed
import com.example.utils.FormatUtils

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRecordAction: () -> Unit,
    onPauseResume: () -> Unit,
    onToggleDrawOverlay: () -> Unit,
    onCycleResolution: () -> Unit,
    onCycleFps: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleDrawDefault: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = uiState.runtime.status == RecorderStatus.RECORDING ||
        uiState.runtime.status == RecorderStatus.PAUSED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Brand Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_logo_main),
                        contentDescription = "BA Screen Record icon",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Column {
                        Text(
                            text = "BA Screen Record",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.config.resolution.shortLabel} • ${uiState.config.fps.fps} FPS • Free",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldPrimary
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings),
                            contentDescription = "Open Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Quick Stats Hero Card (with Shimmer loading)
            if (uiState.isLoadingStats) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .shimmerEffect()
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(EmeraldDeep, EmeraldPrimary)
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                label = "Recordings",
                                value = uiState.stats.totalCount.toString()
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(38.dp)
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                            StatColumn(
                                label = "Total Time",
                                value = FormatUtils.formatDuration(uiState.stats.totalDurationMs)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(38.dp)
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                            StatColumn(
                                label = "Storage",
                                value = FormatUtils.formatFileSize(uiState.stats.totalSizeBytes)
                            )
                        }
                    }
                }
            }

            // Big Record Control Card
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (uiState.runtime.status == RecorderStatus.PAUSED) AmberPause else RecordRed
                                    )
                            )
                            Text(
                                text = if (uiState.runtime.status == RecorderStatus.PAUSED) "PAUSED" else "RECORDING LIVE",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (uiState.runtime.status == RecorderStatus.PAUSED) AmberPause else RecordRed
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = FormatUtils.formatTimerFull(uiState.runtime.elapsedMs),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioWaveformVisualizer(
                            audioLevel = uiState.runtime.audioLevel,
                            isPaused = uiState.runtime.status == RecorderStatus.PAUSED,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    } else {
                        Text(
                            text = "Ready for 2K Capture",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PulsingRecordButton(
                        status = uiState.runtime.status,
                        onClick = onRecordAction
                    )

                    Text(
                        text = when (uiState.runtime.status) {
                            RecorderStatus.RECORDING -> "Tap Stop to save recording"
                            RecorderStatus.PAUSED -> "Tap Stop or Resume below"
                            else -> "Tap to Record Screen"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(
                        visible = isActive,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                        ) {
                            FilledTonalButton(
                                onClick = onPauseResume,
                                modifier = Modifier.testTag("home_pause_resume_button")
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (uiState.runtime.status == RecorderStatus.PAUSED) {
                                            R.drawable.ic_record_start
                                        } else {
                                            R.drawable.ic_record_pause
                                        }
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.runtime.status == RecorderStatus.PAUSED) "Resume" else "Pause")
                            }

                            Button(
                                onClick = onToggleDrawOverlay,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.runtime.isDrawOverlayActive) EmeraldDeep else EmeraldPrimary
                                ),
                                modifier = Modifier.testTag("home_toggle_draw_button")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_draw_pen),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (uiState.runtime.isDrawOverlayActive) "Hide Draw" else "Draw")
                            }
                        }
                    }

                    if (!isActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            QuickToggleChip(
                                label = uiState.config.resolution.shortLabel,
                                iconRes = R.drawable.ic_quality,
                                selected = true,
                                onClick = onCycleResolution,
                                testTag = "chip_resolution"
                            )
                            QuickToggleChip(
                                label = "${uiState.config.fps.fps} FPS",
                                iconRes = R.drawable.ic_timer,
                                selected = true,
                                onClick = onCycleFps,
                                testTag = "chip_fps"
                            )
                            QuickToggleChip(
                                label = if (uiState.config.recordMic) "Mic On" else "Muted",
                                iconRes = R.drawable.ic_mic,
                                selected = uiState.config.recordMic,
                                onClick = onToggleMic,
                                testTag = "chip_mic"
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        QuickToggleChip(
                            label = if (uiState.config.showDrawToolbarByDefault) "Auto Draw: On" else "Auto Draw: Off",
                            iconRes = R.drawable.ic_draw_pen,
                            selected = uiState.config.showDrawToolbarByDefault,
                            onClick = onToggleDrawDefault,
                            testTag = "chip_auto_draw"
                        )
                    }
                }
            }

            // Recent Recordings Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Recordings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = onOpenGallery,
                        modifier = Modifier.testTag("view_all_recordings_button")
                    ) {
                        Text("Open Gallery", color = EmeraldPrimary)
                    }
                }

                if (uiState.isLoadingStats) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .width(184.dp)
                                    .height(132.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                } else if (uiState.recentRecordings.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldPrimary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_gallery),
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "No recordings yet",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Tap the green button above to capture your first 2K video.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        items(uiState.recentRecordings, key = { it.id }) { rec ->
                            RecentRecordingCard(
                                recording = rec,
                                onClick = { onPlayRecording(rec) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun QuickToggleChip(
    label: String,
    iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = EmeraldPrimary.copy(alpha = 0.14f),
            selectedLabelColor = EmeraldPrimary,
            selectedLeadingIconColor = EmeraldPrimary
        )
    )
}

@Composable
private fun RecentRecordingCard(
    recording: RecordingEntity,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .width(196.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(EmeraldDeep, EmeraldPrimary.copy(alpha = 0.85f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_gallery),
                    contentDescription = "Play ${recording.fileName}",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                ResolutionBadge(
                    label = recording.resolution,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = FormatUtils.formatDuration(recording.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recording.fileName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${FormatUtils.formatFileSize(recording.fileSizeBytes)} • ${recording.fps} FPS",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
