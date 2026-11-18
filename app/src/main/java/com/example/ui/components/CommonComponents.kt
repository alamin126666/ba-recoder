package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.RecorderStatus
import com.example.ui.theme.AmberPause
import com.example.ui.theme.EmeraldBright
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.MintAccent
import com.example.ui.theme.RecordRed
import com.example.ui.theme.ShimmerGreenBase
import com.example.ui.theme.ShimmerGreenHighlight
import kotlin.math.sin

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    background(
        brush = Brush.linearGradient(
            colors = listOf(
                ShimmerGreenBase,
                ShimmerGreenHighlight,
                ShimmerGreenBase
            ),
            start = Offset(progress * 600f, 0f),
            end = Offset(progress * 600f + 350f, 130f) // ~20 deg diagonal sweep
        )
    )
}

@Composable
fun PulsingRecordButton(
    status: RecorderStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = status == RecorderStatus.RECORDING || status == RecorderStatus.PAUSED
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == RecorderStatus.RECORDING) 1.38f else 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (status == RecorderStatus.RECORDING) 950 else 1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (status == RecorderStatus.RECORDING) 950 else 1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val coreColor = when (status) {
        RecorderStatus.RECORDING -> RecordRed
        RecorderStatus.PAUSED -> AmberPause
        else -> EmeraldPrimary
    }

    val innerSize by animateDpAsState(
        targetValue = if (isActive) 34.dp else 44.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "innerSize"
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isActive) 8.dp else 22.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "innerCorner"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(164.dp)
            .scale(pressScale)
    ) {
        // Outer animated halo ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(coreColor.copy(alpha = pulseAlpha))
        )

        // Secondary subtle emerald border ring
        Box(
            modifier = Modifier
                .size(134.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(coreColor.copy(alpha = 0.65f), MintAccent.copy(alpha = 0.35f))
                    ),
                    shape = CircleShape
                )
        )

        // Main 120dp tappable circular button
        Surface(
            modifier = Modifier
                .size(120.dp)
                .testTag("main_record_button")
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ),
            shape = CircleShape,
            color = coreColor,
            shadowElevation = 12.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = if (isActive) {
                                listOf(coreColor, coreColor.copy(alpha = 0.85f))
                            } else {
                                listOf(EmeraldBright, EmeraldDeep)
                            }
                        )
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(innerSize)
                        .clip(RoundedCornerShape(innerCorner))
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    audioLevel: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val barCount = 18
    val primaryColor = if (isPaused) AmberPause else EmeraldBright

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val barWidth = size.width / (barCount * 2f)
        val spacing = barWidth
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val wave = if (isPaused) 0.15f else ((sin(phase + i * 0.55f) + 1f) * 0.5f)
            val amplitude = (0.18f + audioLevel * 0.82f * wave).coerceIn(0.12f, 1f)
            val barH = size.height * amplitude
            val x = i * (barWidth + spacing) + spacing * 0.5f
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.45f + amplitude * 0.55f),
                topLeft = Offset(x, centerY - barH / 2f),
                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun ResolutionBadge(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = label.contains("2K", ignoreCase = true) || label.contains("4K", ignoreCase = true)
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) EmeraldPrimary else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CountdownOverlay(
    secondsRemaining: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0D1B0D))
            .clickable(enabled = true, onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Starting 2K Recording",
                style = MaterialTheme.typography.titleMedium,
                color = MintAccent
            )
            Spacer(modifier = Modifier.height(24.dp))
            AnimatedContent(
                targetState = secondsRemaining,
                transitionSpec = {
                    (scaleIn(initialScale = 1.6f) + fadeIn()).togetherWith(scaleOut(targetScale = 0.6f) + fadeOut())
                },
                label = "countdownNumber"
            ) { count ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = 0.18f))
                        .border(3.dp, EmeraldBright, CircleShape)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_countdown_button"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
fun PermissionRationaleDialog(
    title: String,
    message: String,
    iconRes: Int,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = EmeraldPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}
