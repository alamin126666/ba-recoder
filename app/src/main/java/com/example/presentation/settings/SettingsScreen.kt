package com.example.presentation.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.BitrateOption
import com.example.domain.model.FpsOption
import com.example.domain.model.OrientationLock
import com.example.domain.model.RecordingConfig
import com.example.domain.model.ResolutionPreset
import com.example.domain.model.ThemeMode
import com.example.domain.model.VideoFormatOption
import com.example.ui.components.ColorPickerDialog
import com.example.ui.theme.EmeraldPrimary
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    config: RecordingConfig,
    viewModel: SettingsViewModel,
    onRequestOverlayPermission: () -> Unit,
    onRequestMicPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showColorDialog by remember { mutableStateOf(false) }

    val hasOverlay = Settings.canDrawOverlays(context)
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Video Quality Section
            SettingsSectionCard(title = "Video Quality (2K Default • Always Free)") {
                Text("Resolution", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResolutionPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = config.resolution == preset,
                            onClick = { viewModel.setResolution(preset) },
                            label = { Text(preset.displayName) },
                            colors = chipColors()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Frame Rate", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FpsOption.entries.forEach { fps ->
                        FilterChip(
                            selected = config.fps == fps,
                            onClick = { viewModel.setFps(fps) },
                            label = { Text(fps.label) },
                            colors = chipColors()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Video Bitrate", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BitrateOption.entries.forEach { bitrate ->
                        FilterChip(
                            selected = config.bitrate == bitrate,
                            onClick = { viewModel.setBitrate(bitrate) },
                            label = { Text(bitrate.label) },
                            colors = chipColors()
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text("Video Format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFormatOption.entries.forEach { fmt ->
                        FilterChip(
                            selected = config.format == fmt,
                            onClick = { viewModel.setFormat(fmt) },
                            label = { Text(fmt.label) },
                            colors = chipColors()
                        )
                    }
                }
            }

            // Audio Section
            SettingsSectionCard(title = "Audio Capture") {
                SwitchPreferenceRow(
                    title = "Record Microphone",
                    subtitle = "Capture voice narration while recording",
                    checked = config.recordMic,
                    onCheckedChange = viewModel::setRecordMic
                )
                SwitchPreferenceRow(
                    title = "Record System Audio",
                    subtitle = "Allow internal app audio capture where supported",
                    checked = config.recordSystemAudio,
                    onCheckedChange = viewModel::setRecordSystemAudio
                )
                SwitchPreferenceRow(
                    title = "Stereo Audio",
                    subtitle = "2-channel AAC audio encoding",
                    checked = config.stereoAudio,
                    onCheckedChange = viewModel::setStereoAudio
                )
                Text("Audio Bitrate", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(128, 192, 256).forEach { kbps ->
                        FilterChip(
                            selected = config.audioBitrateKbps == kbps,
                            onClick = { viewModel.setAudioBitrate(kbps) },
                            label = { Text("$kbps kbps") },
                            colors = chipColors()
                        )
                    }
                }
            }

            // Draw Settings
            SettingsSectionCard(title = "Draw & Annotation") {
                SwitchPreferenceRow(
                    title = "Show Draw Toolbar by Default",
                    subtitle = "Open floating draw overlay automatically on record start",
                    checked = config.showDrawToolbarByDefault,
                    onCheckedChange = viewModel::setShowDrawToolbarByDefault
                )
                SwitchPreferenceRow(
                    title = "Save Drawings with Video",
                    subtitle = "Capture live annotations directly inside your screen recording",
                    checked = config.saveDrawingsWithVideo,
                    onCheckedChange = viewModel::setSaveDrawingsWithVideo
                )
                SwitchPreferenceRow(
                    title = "Smooth Bezier Drawing",
                    subtitle = "Apply quadratic curve smoothing on pen strokes",
                    checked = config.smoothDrawing,
                    onCheckedChange = viewModel::setSmoothDrawing
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showColorDialog = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Default Pen Color", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Tap to open spectrum wheel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(config.defaultPenColor))
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Default Pen Size (${config.defaultPenSize.roundToInt()} dp)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = config.defaultPenSize,
                        onValueChange = { viewModel.setDefaultPenSize(it) },
                        valueRange = 2f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = EmeraldPrimary,
                            activeTrackColor = EmeraldPrimary
                        )
                    )
                }
            }

            // General & Behavior
            SettingsSectionCard(title = "General & Controls") {
                Text("Countdown Before Recording", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Off", 3 to "3s", 5 to "5s", 10 to "10s").forEach { (sec, label) ->
                        FilterChip(
                            selected = config.countdownSeconds == sec,
                            onClick = { viewModel.setCountdownSeconds(sec) },
                            label = { Text(label) },
                            colors = chipColors()
                        )
                    }
                }
                SwitchPreferenceRow(
                    title = "Floating Control Bubble",
                    subtitle = "Draggable overlay bubble with Pause, Draw, and Stop",
                    checked = config.showFloatingBubble,
                    onCheckedChange = viewModel::setShowFloatingBubble
                )
                SwitchPreferenceRow(
                    title = "Shake to Stop Recording",
                    subtitle = "Shake device firmly to end active capture",
                    checked = config.shakeToStop,
                    onCheckedChange = viewModel::setShakeToStop
                )
                SwitchPreferenceRow(
                    title = "Recording Notification",
                    subtitle = "Persistent controls in notification shade",
                    checked = config.showRecordingNotification,
                    onCheckedChange = viewModel::setShowRecordingNotification
                )

                Text("Orientation Lock", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrientationLock.entries.forEach { lock ->
                        FilterChip(
                            selected = config.orientationLock == lock,
                            onClick = { viewModel.setOrientationLock(lock) },
                            label = { Text(lock.label) },
                            colors = chipColors()
                        )
                    }
                }

                Text("App Theme", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = config.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label) },
                            colors = chipColors()
                        )
                    }
                }
            }

            // Permissions Status Card
            SettingsSectionCard(title = "Permissions") {
                PermissionStatusRow(
                    title = "Display Over Other Apps",
                    granted = hasOverlay,
                    onGrantClick = onRequestOverlayPermission
                )
                PermissionStatusRow(
                    title = "Microphone Access",
                    granted = hasMic,
                    onGrantClick = onRequestMicPermission
                )
                PermissionStatusRow(
                    title = "Notifications",
                    granted = hasNotif,
                    onGrantClick = onRequestNotificationPermission
                )
            }

            // About Section
            SettingsSectionCard(title = "About") {
                Text(
                    text = "BA Screen Record • v1.0.0",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Developer: BA Apps  •  2K 60fps capture & live annotations with zero ads or paywalls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showColorDialog) {
        ColorPickerDialog(
            initialColor = config.defaultPenColor,
            recentColors = config.recentColors,
            onColorSelected = {
                viewModel.setDefaultPenColor(it)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EmeraldPrimary
            )
            content()
        }
    }
}

@Composable
private fun SwitchPreferenceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldPrimary
            )
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (granted) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmeraldPrimary.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "Granted",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        } else {
            TextButton(onClick = onGrantClick) {
                Text("Grant", color = EmeraldPrimary)
            }
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = EmeraldPrimary,
    selectedLabelColor = Color.White
)
