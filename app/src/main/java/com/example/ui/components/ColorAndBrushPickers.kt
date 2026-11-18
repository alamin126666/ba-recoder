package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldPrimary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val presetColorHexes = listOf(
    "#00E676", "#00C853", "#FF1744", "#FF9100",
    "#FFEA00", "#2979FF", "#00E5FF", "#D500F9",
    "#FF4081", "#FFFFFF", "#B0BEC5", "#1A1A1A"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    recentColors: List<Int>,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).apply { AndroidColor.colorToHSV(initialColor, this) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var sat by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableFloatStateOf(AndroidColor.alpha(initialColor) / 255f) }

    val currentArgb = remember(hue, sat, value, alpha) {
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        AndroidColor.argb(
            (alpha * 255f).roundToInt().coerceIn(25, 255),
            AndroidColor.red(rgb),
            AndroidColor.green(rgb),
            AndroidColor.blue(rgb)
        )
    }

    var hexText by remember(currentArgb) {
        mutableStateOf(String.format("#%06X", 0xFFFFFF and currentArgb))
    }

    fun applyColor(argb: Int) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(argb, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        alpha = (AndroidColor.alpha(argb) / 255f).coerceIn(0.15f, 1f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(currentArgb))
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Color Picker", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive HSV Spectrum Color Wheel
                val wheelColors = remember {
                    listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
                        Color.Blue, Color.Magenta, Color.Red
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(164.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(164.dp)
                            .pointerInput(Unit) {
                                fun updateFromOffset(pos: Offset) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val radius = size.width / 2f
                                    val dx = pos.x - cx
                                    val dy = pos.y - cy
                                    val dist = hypot(dx, dy).coerceAtMost(radius)
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0f) angle += 360f
                                    hue = angle
                                    sat = (dist / radius).coerceIn(0f, 1f)
                                }
                                detectTapGestures { offset -> updateFromOffset(offset) }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val radius = size.width / 2f
                                    val dx = change.position.x - cx
                                    val dy = change.position.y - cy
                                    val dist = hypot(dx, dy).coerceAtMost(radius)
                                    var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                    if (angle < 0f) angle += 360f
                                    hue = angle
                                    sat = (dist / radius).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        val radius = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.sweepGradient(wheelColors),
                            radius = radius
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                radius = radius
                            ),
                            radius = radius
                        )
                        val rad = Math.toRadians(hue.toDouble())
                        val selX = center.x + (sat * radius * cos(rad)).toFloat()
                        val selY = center.y + (sat * radius * sin(rad)).toFloat()
                        drawCircle(
                            color = Color.White,
                            radius = 10.dp.toPx(),
                            center = Offset(selX, selY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }

                // Brightness slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Brightness ${(value * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                    )
                }

                // Opacity slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Opacity ${(alpha * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                    )
                }

                // Hex input
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { typed ->
                        hexText = typed
                        if (typed.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                            try {
                                applyColor(AndroidColor.parseColor(typed))
                            } catch (_: Exception) {
                            }
                        }
                    },
                    label = { Text("Hex Color") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 12 Preset Swatches
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetColorHexes.forEach { hex ->
                        val c = AndroidColor.parseColor(hex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if ((c and 0xFFFFFF) == (currentArgb and 0xFFFFFF)) 2.5.dp else 1.dp,
                                    color = if ((c and 0xFFFFFF) == (currentArgb and 0xFFFFFF)) EmeraldPrimary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { applyColor(c) }
                        )
                    }
                }

                if (recentColors.isNotEmpty()) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        recentColors.take(5).forEach { rc ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(rc))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { applyColor(rc) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorSelected(currentArgb) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Apply Color")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private val brushPresets = listOf(
    "Fine" to 4f,
    "Thin" to 8f,
    "Medium" to 14f,
    "Thick" to 24f,
    "Giant" to 38f
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BrushSizeDialog(
    initialSize: Float,
    previewColor: Int,
    onSizeSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var sizeVal by remember { mutableFloatStateOf(initialSize.coerceIn(2f, 50f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Brush Size", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(sizeVal.dp.coerceIn(4.dp, 54.dp))
                                .clip(CircleShape)
                                .background(Color(previewColor))
                        )
                    }
                }

                Text(
                    text = "${sizeVal.roundToInt()} dp",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldPrimary
                )

                Slider(
                    value = sizeVal,
                    onValueChange = { sizeVal = it },
                    valueRange = 2f..50f,
                    colors = SliderDefaults.colors(thumbColor = EmeraldPrimary, activeTrackColor = EmeraldPrimary)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    brushPresets.forEach { (label, preset) ->
                        FilterChip(
                            selected = kotlin.math.abs(sizeVal - preset) < 1.5f,
                            onClick = { sizeVal = preset },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSizeSelected(sizeVal) },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                Text("Set Size")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
