package com.example.service

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BARecorderApp
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AnnotationStamp
import com.example.domain.model.DrawTool
import com.example.ui.overlay.DrawingCanvasView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

class DrawOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager

    private var drawingCanvas: DrawingCanvasView? = null
    private var canvasParams: WindowManager.LayoutParams? = null
    private var toolbarContainer: View? = null
    private var toolbarParams: WindowManager.LayoutParams? = null

    private val presetColors = listOf(
        Color.parseColor("#00E676"),
        Color.parseColor("#FF1744"),
        Color.parseColor("#FFEA00"),
        Color.parseColor("#2979FF"),
        Color.WHITE,
        Color.parseColor("#FF9100"),
        Color.parseColor("#D500F9")
    )
    private var colorIndex = 0
    private val sizeSteps = listOf(5f, 10f, 18f, 28f)
    private var sizeIndex = 1
    private var stampIndex = 0

    private var colorButton: ImageButton? = null
    private val toolButtons = mutableMapOf<DrawTool, ImageButton>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val app = application as BARecorderApp
        app.container.screenRecordManager.setDrawOverlayActive(true)

        if (drawingCanvas == null) {
            serviceScope.launch {
                val config = app.container.settingsDataStore.configFlow.first()
                setupCanvasOverlay(config.defaultPenColor, config.defaultPenSize, config.smoothDrawing)
                setupToolbarOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun setupCanvasOverlay(defaultColor: Int, defaultSize: Float, smooth: Boolean) {
        val canvas = DrawingCanvasView(this).apply {
            currentColor = defaultColor
            currentStrokeWidth = defaultSize
            smoothDrawing = smooth
            onRequestTextAt = { x, y -> promptTextInput(x, y) }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        drawingCanvas = canvas
        canvasParams = params
        try {
            windowManager.addView(canvas, params)
        } catch (_: Exception) {
        }
    }

    private fun setupToolbarOverlay() {
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D1B0D"))
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.parseColor("#4D00E676"))
            }
            setPadding(dp(6), dp(8), dp(6), dp(8))
            elevation = dp(10).toFloat()
        }

        // Drag handle at top
        val handle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(4)).apply {
                bottomMargin = dp(6)
            }
            background = GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(Color.parseColor("#80FFFFFF"))
            }
        }
        panel.addView(handle)

        fun addTool(tool: DrawTool, iconRes: Int, label: String) {
            val btn = createToolButton(iconRes, label) {
                if (tool == DrawTool.STAMP) {
                    val stamps = AnnotationStamp.entries
                    stampIndex = (stampIndex + 1) % stamps.size
                    drawingCanvas?.currentStamp = stamps[stampIndex]
                }
                drawingCanvas?.currentTool = tool
                highlightSelectedTool(tool)
            }
            toolButtons[tool] = btn
            panel.addView(btn)
        }

        addTool(DrawTool.PEN, R.drawable.ic_draw_pen, "Pen")
        addTool(DrawTool.HIGHLIGHTER, R.drawable.ic_draw_highlight, "Highlighter")
        addTool(DrawTool.ARROW, R.drawable.ic_draw_arrow, "Arrow")
        addTool(DrawTool.RECTANGLE, R.drawable.ic_draw_shape, "Shape")
        addTool(DrawTool.TEXT, R.drawable.ic_draw_text, "Text")
        addTool(DrawTool.ERASER, R.drawable.ic_draw_eraser, "Eraser")

        // Color cycle button
        val colorBtn = createToolButton(R.drawable.ic_color_picker, "Cycle Color") {
            colorIndex = (colorIndex + 1) % presetColors.size
            val nextColor = presetColors[colorIndex]
            drawingCanvas?.currentColor = nextColor
            colorButton?.setColorFilter(nextColor)
        }
        colorBtn.setColorFilter(drawingCanvas?.currentColor ?: Color.GREEN)
        colorButton = colorBtn
        panel.addView(colorBtn)

        // Brush size cycle
        panel.addView(createToolButton(R.drawable.ic_brush_size, "Brush Size") {
            sizeIndex = (sizeIndex + 1) % sizeSteps.size
            drawingCanvas?.currentStrokeWidth = sizeSteps[sizeIndex]
        })

        // Undo / Redo / Clear
        panel.addView(createToolButton(R.drawable.ic_draw_undo, "Undo") {
            drawingCanvas?.undo()
        })
        panel.addView(createToolButton(R.drawable.ic_draw_redo, "Redo") {
            drawingCanvas?.redo()
        })
        panel.addView(createToolButton(R.drawable.ic_delete, "Clear All") {
            drawingCanvas?.clearAll()
        })

        // Close / hide draw overlay
        panel.addView(createToolButton(R.drawable.ic_close_cross, "Hide Draw Overlay", Color.parseColor("#FF5252")) {
            stopSelf()
        })

        scroll.addView(panel)
        highlightSelectedTool(DrawTool.PEN)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = dp(8)
            y = 0
        }

        var initialY = 0
        var initialTouchY = 0f
        var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dy) > 8) moved = true
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(scroll, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                else -> moved
            }
        }

        toolbarContainer = scroll
        toolbarParams = params
        try {
            windowManager.addView(scroll, params)
        } catch (_: Exception) {
        }
    }

    private fun createToolButton(
        iconRes: Int,
        desc: String,
        tint: Int = Color.WHITE,
        onClick: () -> Unit
    ): ImageButton {
        val density = resources.displayMetrics.density
        val size = (38 * density).toInt()
        val pad = (8 * density).toInt()
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = (3 * density).toInt()
                bottomMargin = (3 * density).toInt()
            }
            setImageResource(iconRes)
            setColorFilter(tint)
            contentDescription = desc
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1AFFFFFF"))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun highlightSelectedTool(selected: DrawTool) {
        toolButtons.forEach { (tool, btn) ->
            val bg = btn.background as? GradientDrawable
            if (tool == selected) {
                bg?.setColor(Color.parseColor("#00C853"))
            } else {
                bg?.setColor(Color.parseColor("#1AFFFFFF"))
            }
        }
    }

    private fun promptTextInput(x: Float, y: Float) {
        val input = EditText(this).apply {
            hint = "Enter annotation text"
            setTextColor(Color.BLACK)
        }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Add Annotation Text")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                drawingCanvas?.addTextAnnotation(input.text.toString(), x, y)
            }
            .setNegativeButton("Cancel", null)
            .create()
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        dialog.window?.setType(windowType)
        try {
            dialog.show()
        } catch (_: Exception) {
            drawingCanvas?.addTextAnnotation("Note", x, y)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            10,
            Intent(this, DrawOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this,
            11,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("BA Live Draw Overlay Active")
            .setContentText("Draw annotations anywhere on screen")
            .setColor(Color.parseColor("#00C853"))
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_record_stop, "Close Draw", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        (application as? BARecorderApp)?.container?.screenRecordManager?.setDrawOverlayActive(false)
        drawingCanvas?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        toolbarContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        drawingCanvas = null
        toolbarContainer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "draw_overlay_channel"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_STOP = "com.example.action.STOP_DRAW_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, DrawOverlayService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DrawOverlayService::class.java))
        }
    }
}
