package com.example.service

import android.app.Activity
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.BARecorderApp
import com.example.MainActivity
import com.example.R
import com.example.domain.model.RecorderStatus
import com.example.domain.model.RecordingConfig
import com.example.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

class ScreenRecordService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateObserverJob: Job? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var windowManager: WindowManager
    private var sensorManager: SensorManager? = null

    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var bubbleTimerText: TextView? = null
    private var bubbleDotView: View? = null
    private var expandedControlsLayout: LinearLayout? = null
    private var bubbleCollapsedPill: LinearLayout? = null
    private var pauseControlButton: ImageButton? = null
    private var isBubbleExpanded = false

    private var activeConfig: RecordingConfig = RecordingConfig()
    private var lastShakeMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as BARecorderApp
        val manager = app.container.screenRecordManager

        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_PROJECTION_DATA)
                }

                if (resultCode != Activity.RESULT_OK || projectionData == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                val initialNotification = buildNotification(
                    status = RecorderStatus.RECORDING,
                    elapsedMs = 0L,
                    config = activeConfig
                )

                // Decide the microphone up-front: Android 14+ silently mutes mic capture when the
                // foreground service does not declare the `microphone` type at startForeground().
                val micPermissionGranted = ContextCompat.checkSelfPermission(
                    this@ScreenRecordService,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val micRequested = intent.getBooleanExtra(EXTRA_ENABLE_MIC, false) && micPermissionGranted

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    // The microphone FGS type constant only exists from API 30 onwards.
                    if (micRequested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }
                    startForeground(NOTIFICATION_ID, initialNotification, types)
                } else {
                    startForeground(NOTIFICATION_ID, initialNotification)
                }

                serviceScope.launch {
                    activeConfig = app.container.settingsDataStore.configFlow.first()
                    val started = manager.startRecording(
                        resultCode = resultCode,
                        data = projectionData,
                        config = activeConfig,
                        micRequested = micRequested
                    )
                    if (!started) {
                        stopSelf()
                        return@launch
                    }

                    if (activeConfig.showFloatingBubble && Settings.canDrawOverlays(this@ScreenRecordService)) {
                        showFloatingBubble()
                    }
                    if (activeConfig.showDrawToolbarByDefault && Settings.canDrawOverlays(this@ScreenRecordService)) {
                        DrawOverlayService.start(this@ScreenRecordService)
                    }
                    if (activeConfig.shakeToStop) {
                        registerShakeSensor()
                    }
                    observeRuntimeState()
                }
            }

            ACTION_PAUSE_RESUME -> {
                val status = manager.runtimeState.value.status
                if (status == RecorderStatus.RECORDING) {
                    manager.pauseRecording()
                } else if (status == RecorderStatus.PAUSED) {
                    manager.resumeRecording()
                }
            }

            ACTION_TOGGLE_DRAW -> {
                if (Settings.canDrawOverlays(this)) {
                    if (manager.runtimeState.value.isDrawOverlayActive) {
                        DrawOverlayService.stop(this)
                    } else {
                        DrawOverlayService.start(this)
                    }
                }
            }

            ACTION_STOP -> {
                manager.stopRecording()
                DrawOverlayService.stop(this)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeRuntimeState() {
        val manager = (application as BARecorderApp).container.screenRecordManager
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            manager.runtimeState.collect { state ->
                when (state.status) {
                    RecorderStatus.IDLE -> {
                        removeFloatingBubble()
                        unregisterShakeSensor()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    RecorderStatus.RECORDING, RecorderStatus.PAUSED -> {
                        if (activeConfig.showRecordingNotification) {
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                buildNotification(state.status, state.elapsedMs, activeConfig)
                            )
                        }
                        updateFloatingBubble(state.status, state.elapsedMs)
                    }
                    RecorderStatus.COUNTDOWN -> Unit
                }
            }
        }
    }

    private fun showFloatingBubble() {
        if (bubbleView != null) return

        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D1B0D"))
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.parseColor("#00E676"))
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            elevation = dp(8).toFloat()
        }

        val collapsedPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF1744"))
            }
        }
        bubbleDotView = dot

        val timerText = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, dp(2), 0)
        }
        bubbleTimerText = timerText

        collapsedPill.addView(dot)
        collapsedPill.addView(timerText)
        bubbleCollapsedPill = collapsedPill

        val expandedLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(8), 0, 0, 0)
        }

        val pauseBtn = createOverlayIconButton(R.drawable.ic_record_pause, "Pause") {
            startService(Intent(this, ScreenRecordService::class.java).apply { action = ACTION_PAUSE_RESUME })
        }
        pauseControlButton = pauseBtn

        val stopBtn = createOverlayIconButton(R.drawable.ic_record_stop, "Stop", tint = Color.parseColor("#FF1744")) {
            startService(Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP })
        }

        val drawBtn = createOverlayIconButton(R.drawable.ic_draw_pen, "Draw", tint = Color.parseColor("#00E676")) {
            startService(Intent(this, ScreenRecordService::class.java).apply { action = ACTION_TOGGLE_DRAW })
        }

        expandedLayout.addView(pauseBtn)
        expandedLayout.addView(drawBtn)
        expandedLayout.addView(stopBtn)
        expandedControlsLayout = expandedLayout

        container.addView(collapsedPill)
        container.addView(expandedLayout)

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
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        isBubbleExpanded = !isBubbleExpanded
                        expandedControlsLayout?.visibility = if (isBubbleExpanded) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        bubbleParams = params
        bubbleView = container
        try {
            windowManager.addView(container, params)
        } catch (_: Exception) {
            bubbleView = null
        }
    }

    private fun createOverlayIconButton(
        iconRes: Int,
        desc: String,
        tint: Int = Color.WHITE,
        onClick: () -> Unit
    ): ImageButton {
        val density = resources.displayMetrics.density
        val size = (36 * density).toInt()
        val pad = (7 * density).toInt()
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (4 * density).toInt()
            }
            setImageResource(iconRes)
            setColorFilter(tint)
            contentDescription = desc
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun updateFloatingBubble(status: RecorderStatus, elapsedMs: Long) {
        bubbleTimerText?.text = FormatUtils.formatDuration(elapsedMs)
        val dotDrawable = bubbleDotView?.background as? GradientDrawable
        if (status == RecorderStatus.PAUSED) {
            dotDrawable?.setColor(Color.parseColor("#FFAB00"))
            pauseControlButton?.setImageResource(R.drawable.ic_record_start)
        } else {
            dotDrawable?.setColor(Color.parseColor("#FF1744"))
            pauseControlButton?.setImageResource(R.drawable.ic_record_pause)
        }
    }

    private fun removeFloatingBubble() {
        bubbleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        bubbleView = null
        bubbleTimerText = null
        bubbleDotView = null
        expandedControlsLayout = null
        bubbleCollapsedPill = null
        pauseControlButton = null
    }

    private fun registerShakeSensor() {
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager?.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
    }

    private fun unregisterShakeSensor() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val values = event?.values ?: return
        val gX = values[0] / SensorManager.GRAVITY_EARTH
        val gY = values[1] / SensorManager.GRAVITY_EARTH
        val gZ = values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce > 2.7f) {
            val now = System.currentTimeMillis()
            if (now - lastShakeMs > 1500L) {
                lastShakeMs = now
                val manager = (application as BARecorderApp).container.screenRecordManager
                manager.stopRecording()
                DrawOverlayService.stop(this)
                stopSelf()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        status: RecorderStatus,
        elapsedMs: Long,
        config: RecordingConfig
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_PAUSE_RESUME },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val drawIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_TOGGLE_DRAW },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (status == RecorderStatus.PAUSED) {
            "BA Screen Record — Paused"
        } else {
            "BA Screen Record — Recording"
        }
        val contentText = "${FormatUtils.formatTimerFull(elapsedMs)}  |  ${config.resolution.shortLabel}  |  ${config.bitrate.mbps} Mbps"
        val pauseLabel = if (status == RecorderStatus.PAUSED) "Resume" else "Pause"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setColor(Color.parseColor("#00C853"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_record_pause, pauseLabel, pauseIntent)

        if (Settings.canDrawOverlays(this)) {
            builder.addAction(R.drawable.ic_draw_pen, "Draw", drawIntent)
        }
        builder.addAction(R.drawable.ic_record_stop, "Stop", stopIntent)

        return builder.build()
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        removeFloatingBubble()
        unregisterShakeSensor()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_RECORDING"
        const val ACTION_PAUSE_RESUME = "com.example.action.PAUSE_RESUME"
        const val ACTION_TOGGLE_DRAW = "com.example.action.TOGGLE_DRAW"
        const val ACTION_STOP = "com.example.action.STOP_RECORDING"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"
        const val EXTRA_ENABLE_MIC = "extra_enable_mic"

        fun start(context: Context, resultCode: Int, data: Intent, enableMic: Boolean) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_PROJECTION_DATA, data)
                putExtra(EXTRA_ENABLE_MIC, enableMic)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenRecordService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun togglePauseResume(context: Context) {
            context.startService(
                Intent(context, ScreenRecordService::class.java).apply { action = ACTION_PAUSE_RESUME }
            )
        }
    }
}
