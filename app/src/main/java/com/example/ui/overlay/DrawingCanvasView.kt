package com.example.ui.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.domain.model.AnnotationStamp
import com.example.domain.model.DrawTool
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

sealed class DrawCommand {
    data class Stroke(
        val path: Path,
        val color: Int,
        val strokeWidth: Float,
        val isHighlighter: Boolean = false,
        val isEraser: Boolean = false
    ) : DrawCommand()

    data class Arrow(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val color: Int,
        val strokeWidth: Float
    ) : DrawCommand()

    data class RectShape(
        val rect: RectF,
        val color: Int,
        val strokeWidth: Float
    ) : DrawCommand()

    data class CircleShape(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val color: Int,
        val strokeWidth: Float
    ) : DrawCommand()

    data class TextAnnotation(
        val text: String,
        val x: Float,
        val y: Float,
        val color: Int,
        val textSize: Float
    ) : DrawCommand()

    data class StampAnnotation(
        val stamp: AnnotationStamp,
        val x: Float,
        val y: Float,
        val color: Int,
        val sizePx: Float
    ) : DrawCommand()
}

class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    init {
        // Software layer ensures PorterDuff.Mode.CLEAR punches transparent holes cleanly
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private val commands = mutableListOf<DrawCommand>()
    private val redoStack = mutableListOf<DrawCommand>()

    private var activeCommand: DrawCommand? = null
    private var activePath: Path? = null

    var currentTool: DrawTool = DrawTool.PEN
    var currentColor: Int = Color.parseColor("#00E676")
    var currentStrokeWidth: Float = 10f
    var smoothDrawing: Boolean = true
    var currentStamp: AnnotationStamp = AnnotationStamp.CHECKMARK
    var pendingTextLabel: String = "Note"

    var onRequestTextAt: ((x: Float, y: Float) -> Unit)? = null
    var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean, count: Int) -> Unit)? = null

    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
    }

    private val textHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.argb(190, 10, 20, 12)
        isFakeBoldText = true
    }

    private val eraserXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    fun canUndo(): Boolean = commands.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    fun strokeCount(): Int = commands.size

    fun undo() {
        if (commands.isNotEmpty()) {
            redoStack.add(commands.removeAt(commands.lastIndex))
            notifyHistory()
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            commands.add(redoStack.removeAt(redoStack.lastIndex))
            notifyHistory()
            invalidate()
        }
    }

    fun clearAll() {
        if (commands.isNotEmpty() || redoStack.isNotEmpty()) {
            commands.clear()
            redoStack.clear()
            activeCommand = null
            notifyHistory()
            invalidate()
        }
    }

    fun addTextAnnotation(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        val size = (currentStrokeWidth * 3.8f).coerceIn(36f, 110f)
        commands.add(DrawCommand.TextAnnotation(text.trim(), x, y, currentColor, size))
        redoStack.clear()
        notifyHistory()
        invalidate()
    }

    fun exportBitmap(backgroundColor: Int = Color.TRANSPARENT): Bitmap {
        val w = width.coerceAtLeast(1080)
        val h = height.coerceAtLeast(1920)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawColor(backgroundColor)
        }
        draw(canvas)
        return bitmap
    }

    private fun notifyHistory() {
        onHistoryChanged?.invoke(canUndo(), canRedo(), commands.size)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = x
                startY = y
                lastX = x
                lastY = y

                when (currentTool) {
                    DrawTool.PEN, DrawTool.HIGHLIGHTER, DrawTool.ERASER -> {
                        val path = Path().apply { moveTo(x, y) }
                        activePath = path
                        activeCommand = DrawCommand.Stroke(
                            path = path,
                            color = currentColor,
                            strokeWidth = if (currentTool == DrawTool.HIGHLIGHTER) {
                                currentStrokeWidth * 2.4f
                            } else if (currentTool == DrawTool.ERASER) {
                                currentStrokeWidth * 2.2f
                            } else {
                                currentStrokeWidth
                            },
                            isHighlighter = currentTool == DrawTool.HIGHLIGHTER,
                            isEraser = currentTool == DrawTool.ERASER
                        )
                    }
                    DrawTool.ARROW -> {
                        activeCommand = DrawCommand.Arrow(x, y, x, y, currentColor, currentStrokeWidth)
                    }
                    DrawTool.RECTANGLE -> {
                        activeCommand = DrawCommand.RectShape(RectF(x, y, x, y), currentColor, currentStrokeWidth)
                    }
                    DrawTool.CIRCLE -> {
                        activeCommand = DrawCommand.CircleShape(x, y, 1f, currentColor, currentStrokeWidth)
                    }
                    DrawTool.STAMP -> {
                        val sizePx = (currentStrokeWidth * 4.2f).coerceIn(52f, 140f)
                        commands.add(DrawCommand.StampAnnotation(currentStamp, x, y, currentStamp.defaultColor, sizePx))
                        redoStack.clear()
                        notifyHistory()
                    }
                    DrawTool.TEXT -> {
                        val callback = onRequestTextAt
                        if (callback != null) {
                            callback(x, y)
                        } else {
                            addTextAnnotation(pendingTextLabel, x, y)
                        }
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                when (currentTool) {
                    DrawTool.PEN, DrawTool.HIGHLIGHTER, DrawTool.ERASER -> {
                        val path = activePath
                        if (path != null) {
                            if (smoothDrawing) {
                                val midX = (lastX + x) / 2f
                                val midY = (lastY + y) / 2f
                                path.quadTo(lastX, lastY, midX, midY)
                            } else {
                                path.lineTo(x, y)
                            }
                            lastX = x
                            lastY = y
                        }
                    }
                    DrawTool.ARROW -> {
                        activeCommand = DrawCommand.Arrow(startX, startY, x, y, currentColor, currentStrokeWidth)
                    }
                    DrawTool.RECTANGLE -> {
                        val rect = RectF(
                            min(startX, x),
                            min(startY, y),
                            kotlin.math.max(startX, x),
                            kotlin.math.max(startY, y)
                        )
                        activeCommand = DrawCommand.RectShape(rect, currentColor, currentStrokeWidth)
                    }
                    DrawTool.CIRCLE -> {
                        val dx = x - startX
                        val dy = y - startY
                        val radius = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        activeCommand = DrawCommand.CircleShape(startX, startY, radius, currentColor, currentStrokeWidth)
                    }
                    DrawTool.STAMP, DrawTool.TEXT -> Unit
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val finished = activeCommand
                if (finished != null) {
                    if (finished is DrawCommand.Stroke) {
                        finished.path.lineTo(x, y)
                    }
                    commands.add(finished)
                    redoStack.clear()
                    notifyHistory()
                }
                activeCommand = null
                activePath = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (cmd in commands) {
            drawCommand(canvas, cmd)
        }
        activeCommand?.let { drawCommand(canvas, it) }
    }

    private fun drawCommand(canvas: Canvas, cmd: DrawCommand) {
        when (cmd) {
            is DrawCommand.Stroke -> {
                strokePaint.xfermode = if (cmd.isEraser) eraserXfermode else null
                strokePaint.color = cmd.color
                strokePaint.alpha = if (cmd.isHighlighter) 115 else 255
                strokePaint.strokeWidth = cmd.strokeWidth
                canvas.drawPath(cmd.path, strokePaint)
                strokePaint.xfermode = null
                strokePaint.alpha = 255
            }

            is DrawCommand.Arrow -> {
                strokePaint.xfermode = null
                strokePaint.color = cmd.color
                strokePaint.alpha = 255
                strokePaint.strokeWidth = cmd.strokeWidth
                canvas.drawLine(cmd.startX, cmd.startY, cmd.endX, cmd.endY, strokePaint)

                val angle = atan2((cmd.endY - cmd.startY).toDouble(), (cmd.endX - cmd.startX).toDouble())
                val headLen = (cmd.strokeWidth * 3.2f).coerceIn(22f, 64f)
                val wingAngle = Math.toRadians(28.0)

                val x1 = (cmd.endX - headLen * cos(angle - wingAngle)).toFloat()
                val y1 = (cmd.endY - headLen * sin(angle - wingAngle)).toFloat()
                val x2 = (cmd.endX - headLen * cos(angle + wingAngle)).toFloat()
                val y2 = (cmd.endY - headLen * sin(angle + wingAngle)).toFloat()

                canvas.drawLine(cmd.endX, cmd.endY, x1, y1, strokePaint)
                canvas.drawLine(cmd.endX, cmd.endY, x2, y2, strokePaint)
            }

            is DrawCommand.RectShape -> {
                strokePaint.xfermode = null
                strokePaint.color = cmd.color
                strokePaint.alpha = 255
                strokePaint.strokeWidth = cmd.strokeWidth
                canvas.drawRoundRect(cmd.rect, 14f, 14f, strokePaint)
            }

            is DrawCommand.CircleShape -> {
                strokePaint.xfermode = null
                strokePaint.color = cmd.color
                strokePaint.alpha = 255
                strokePaint.strokeWidth = cmd.strokeWidth
                canvas.drawCircle(cmd.cx, cmd.cy, cmd.radius, strokePaint)
            }

            is DrawCommand.TextAnnotation -> {
                textHaloPaint.textSize = cmd.textSize
                textPaint.textSize = cmd.textSize
                textPaint.color = cmd.color
                canvas.drawText(cmd.text, cmd.x, cmd.y, textHaloPaint)
                canvas.drawText(cmd.text, cmd.x, cmd.y, textPaint)
            }

            is DrawCommand.StampAnnotation -> {
                val radius = cmd.sizePx * 0.56f
                fillPaint.color = Color.argb(215, 13, 27, 13)
                canvas.drawCircle(cmd.x, cmd.y, radius, fillPaint)

                strokePaint.xfermode = null
                strokePaint.color = cmd.color
                strokePaint.strokeWidth = 4f
                canvas.drawCircle(cmd.x, cmd.y, radius, strokePaint)

                textPaint.color = cmd.color
                textPaint.textSize = cmd.sizePx * 0.62f
                textPaint.textAlign = Paint.Align.CENTER
                val fm = textPaint.fontMetrics
                val textY = cmd.y - (fm.ascent + fm.descent) / 2f
                canvas.drawText(cmd.stamp.symbol, cmd.x, textY, textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
    }
}
