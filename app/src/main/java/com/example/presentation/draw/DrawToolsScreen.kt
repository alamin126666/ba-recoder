package com.example.presentation.draw

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.domain.model.AnnotationStamp
import com.example.domain.model.DrawTool
import com.example.ui.components.BrushSizeDialog
import com.example.ui.components.ColorPickerDialog
import com.example.ui.overlay.DrawingCanvasView
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.RecordRed
import kotlin.math.roundToInt

@Composable
fun DrawToolsScreen(
    uiState: DrawToolsUiState,
    onSelectTool: (DrawTool) -> Unit,
    onSelectColor: (Int) -> Unit,
    onSelectStrokeWidth: (Float) -> Unit,
    onSelectStamp: (AnnotationStamp) -> Unit,
    onHistoryChanged: (Boolean, Boolean, Int) -> Unit,
    onToggleLiveOverlay: () -> Unit,
    onSaveSketch: (android.graphics.Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasViewRef by remember { mutableStateOf<DrawingCanvasView?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showBrushPicker by remember { mutableStateOf(false) }
    var pendingTextCoords by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Live Overlay Launch Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Draw Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Practice annotations or launch over any app",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onToggleLiveOverlay,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isLiveOverlayActive) RecordRed else EmeraldPrimary
                ),
                modifier = Modifier.testTag("launch_live_draw_overlay_button")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_draw_pen),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (uiState.isLiveOverlayActive) "Stop Overlay" else "Live Overlay")
            }
        }

        // Tool Selector Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tools = listOf(
                DrawTool.PEN to R.drawable.ic_draw_pen,
                DrawTool.HIGHLIGHTER to R.drawable.ic_draw_highlight,
                DrawTool.ARROW to R.drawable.ic_draw_arrow,
                DrawTool.RECTANGLE to R.drawable.ic_draw_shape,
                DrawTool.CIRCLE to R.drawable.ic_draw_shape,
                DrawTool.TEXT to R.drawable.ic_draw_text,
                DrawTool.STAMP to R.drawable.ic_quality,
                DrawTool.ERASER to R.drawable.ic_draw_eraser
            )
            tools.forEach { (tool, iconRes) ->
                val selected = uiState.selectedTool == tool
                FilterChip(
                    selected = selected,
                    onClick = { onSelectTool(tool) },
                    label = { Text(tool.label) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldPrimary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }
        }

        // Annotation Stamps Row (visible when STAMP tool is selected)
        if (uiState.selectedTool == DrawTool.STAMP) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnnotationStamp.entries.forEach { stamp ->
                    val active = uiState.selectedStamp == stamp
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) EmeraldPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .border(
                                width = if (active) 2.dp else 0.dp,
                                color = if (active) EmeraldPrimary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectStamp(stamp) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(stamp.symbol, fontWeight = FontWeight.Bold)
                            Text(stamp.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Interactive Canvas Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF102013))
            ) {
                if (uiState.strokeCount == 0) {
                    Text(
                        text = "Draw freely here with smooth Bezier curves, arrows, shapes, text & stamps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                AndroidView(
                    factory = { ctx ->
                        DrawingCanvasView(ctx).apply {
                            this.onHistoryChanged = { undo, redo, count ->
                                onHistoryChanged(undo, redo, count)
                            }
                            this.onRequestTextAt = { x, y ->
                                pendingTextCoords = x to y
                                textInput = ""
                            }
                            canvasViewRef = this
                        }
                    },
                    update = { view ->
                        view.currentTool = uiState.selectedTool
                        view.currentColor = uiState.selectedColor
                        view.currentStrokeWidth = uiState.strokeWidth
                        view.smoothDrawing = uiState.smoothDrawing
                        view.currentStamp = uiState.selectedStamp
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Controls Bar: Color, Size, Undo, Redo, Clear, Save PNG
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Color Swatch Button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(uiState.selectedColor))
                            .border(2.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showColorPicker = true }
                            .testTag("draw_color_picker_button")
                    )

                    // Brush Size Button
                    OutlinedButton(
                        onClick = { showBrushPicker = true },
                        modifier = Modifier.testTag("draw_brush_size_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_brush_size),
                            contentDescription = "Brush Size",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${uiState.strokeWidth.roundToInt()}dp")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(
                        onClick = { canvasViewRef?.undo() },
                        enabled = uiState.canUndo,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_draw_undo),
                            contentDescription = "Undo",
                            tint = if (uiState.canUndo) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { canvasViewRef?.redo() },
                        enabled = uiState.canRedo,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_draw_redo),
                            contentDescription = "Redo",
                            tint = if (uiState.canRedo) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { canvasViewRef?.clearAll() },
                        enabled = uiState.strokeCount > 0,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Clear All",
                            tint = if (uiState.strokeCount > 0) RecordRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = {
                            canvasViewRef?.let { view ->
                                val bmp = view.exportBitmap(AndroidColor.parseColor("#102013"))
                                onSaveSketch(bmp)
                            }
                        },
                        enabled = uiState.strokeCount > 0,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = EmeraldPrimary.copy(alpha = 0.16f)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Save Sketch PNG",
                            tint = EmeraldDeep,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = uiState.selectedColor,
            recentColors = uiState.recentColors,
            onColorSelected = {
                onSelectColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showBrushPicker) {
        BrushSizeDialog(
            initialSize = uiState.strokeWidth,
            previewColor = uiState.selectedColor,
            onSizeSelected = {
                onSelectStrokeWidth(it)
                showBrushPicker = false
            },
            onDismiss = { showBrushPicker = false }
        )
    }

    pendingTextCoords?.let { (x, y) ->
        AlertDialog(
            onDismissRequest = { pendingTextCoords = null },
            title = { Text("Add Canvas Text") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Annotation text") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        canvasViewRef?.addTextAnnotation(textInput, x, y)
                        pendingTextCoords = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Text("Place Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextCoords = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
