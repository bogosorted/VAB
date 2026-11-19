package com.aragao.vab.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aragao.vab.MainActivity
import com.aragao.vab.VabApplication
import com.aragao.vab.core.buddy.BuddyDna
import com.aragao.vab.core.buddy.BuddyGenerator
import com.aragao.vab.core.buddy.BuddyVisualState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

class FloatingBubbleService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "vab_buddy_overlay"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, FloatingBubbleService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var recorder: WavAudioRecorder

    private var bubbleView: BuddyBubbleOverlayView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var quickInputCardView: View? = null

    private var animationLoopJob: Job? = null
    private var celebrationUntilMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        recorder = WavAudioRecorder(applicationContext)

        startForegroundSafely()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        attachBubbleOverlay()
        startBuddyAnimationLoop()
    }

    private fun startForegroundSafely() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VAB Buddy Flutuante",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém seu Buddy pronto para capturar pendências com 1 toque."
        }
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VAB Buddy ativo")
            .setContentText("1 toque para falar • Segure para digitar rápido")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasMicPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val fgType = if (hasMicPerm) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(NOTIFICATION_ID, notification, fgType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun attachBubbleOverlay() {
        val app = application as VabApplication
        val sizePx = dpToPx(66)

        val view = BuddyBubbleOverlayView(
            context = this,
            dna = app.buddyDna,
            onSingleTap = { handleBuddySingleTap() },
            onLongPress = { showQuickTextOverlay() },
            onDragDelta = { dx, dy ->
                val lp = bubbleParams ?: return@BuddyBubbleOverlayView
                lp.x += dx
                lp.y += dy
                runCatching { windowManager.updateViewLayout(bubbleView, lp) }
            },
            onDragEnd = {
                val lp = bubbleParams ?: return@BuddyBubbleOverlayView
                val screenWidth = resources.displayMetrics.widthPixels
                lp.x = if (lp.x + sizePx / 2 < screenWidth / 2) 12 else (screenWidth - sizePx - 12)
                runCatching { windowManager.updateViewLayout(bubbleView, lp) }
            }
        )

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - sizePx - 16
            y = resources.displayMetrics.heightPixels / 3
        }

        bubbleView = view
        bubbleParams = params
        windowManager.addView(view, params)

        lifecycleScope.launch {
            app.queueOrchestrator.lastCelebrationTimestamp.collect { ts ->
                if (ts > 0L) {
                    celebrationUntilMs = System.currentTimeMillis() + 2400L
                }
            }
        }
    }

    private fun startBuddyAnimationLoop() {
        val app = application as VabApplication
        animationLoopJob?.cancel()
        animationLoopJob = lifecycleScope.launch {
            var tick = 0
            var activeQueueCount = 0

            launch {
                app.queueOrchestrator.activeCountFlow.collect { count ->
                    activeQueueCount = count
                }
            }

            while (isActive) {
                val isRec = recorder.isRecording.value
                val amp = recorder.amplitude.value
                val now = System.currentTimeMillis()
                val urgentP1 = app.boardRepository.board.value.hasUrgentDueOrOverdueP1(
                    LocalDate.now().toString()
                )

                val visualState = when {
                    isRec -> BuddyVisualState.LISTENING
                    now < celebrationUntilMs -> BuddyVisualState.CELEBRATING
                    activeQueueCount > 0 -> BuddyVisualState.THINKING
                    urgentP1 -> BuddyVisualState.IDLE_STRESSED_P1
                    else -> BuddyVisualState.IDLE_CHILL
                }

                bubbleView?.updateFrame(
                    state = visualState,
                    tick = tick++,
                    amplitude = amp,
                    queueBadgeCount = activeQueueCount
                )

                delay(if (isRec) 110L else 280L)
            }
        }
    }

    private fun handleBuddySingleTap() {
        val app = application as VabApplication
        if (recorder.isRecording.value) {
            vibrateShort(35L)
            lifecycleScope.launch {
                val wavFile = recorder.stopRecording()
                if (wavFile != null) {
                    app.queueOrchestrator.enqueueAudio(wavFile)
                }
            }
        } else {
            val hasMic = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMic) {
                Toast.makeText(
                    this,
                    "Abra o VAB para conceder permissão de microfone.",
                    Toast.LENGTH_SHORT
                ).show()
                val openApp = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(openApp)
                return
            }
            vibrateShort(50L)
            recorder.startRecording()
        }
    }

    private fun showQuickTextOverlay() {
        if (quickInputCardView != null) return
        vibrateShort(40L)

        val app = application as VabApplication
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE181C24"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), app.buddyDna.primaryColor)
            }
        }

        val title = TextView(this).apply {
            text = "Nova pendência rápida (VAB)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val input = EditText(this).apply {
            hint = "Ex: Prova de Cálculo sexta, estudar cap 4..."
            setHintTextColor(Color.parseColor("#9AA4B2"))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(8), 0, 0)
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancelar"
            setTextColor(Color.parseColor("#B0B8C4"))
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener { dismissQuickTextOverlay() }
        }

        val sendBtn = TextView(this).apply {
            text = "Enfileirar"
            setTextColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(app.buddyDna.primaryColor)
                cornerRadius = dpToPx(10).toFloat()
            }
            setOnClickListener {
                val txt = input.text?.toString()?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    app.queueOrchestrator.enqueueText(txt)
                    vibrateShort(30L)
                }
                dismissQuickTextOverlay()
            }
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendBtn.performClick()
                true
            } else {
                false
            }
        }

        btnRow.addView(cancelBtn)
        btnRow.addView(sendBtn)
        container.addView(title)
        container.addView(input)
        container.addView(btnRow)

        val cardParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            dimAmount = 0.35f
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        quickInputCardView = container
        windowManager.addView(container, cardParams)
        input.requestFocus()
    }

    private fun dismissQuickTextOverlay() {
        val card = quickInputCardView ?: return
        quickInputCardView = null
        runCatching { windowManager.removeView(card) }
    }

    private fun vibrateShort(durationMs: Long) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onDestroy() {
        animationLoopJob?.cancel()
        dismissQuickTextOverlay()
        bubbleView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        bubbleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }
}

private class BuddyBubbleOverlayView(
    context: Context,
    private val dna: BuddyDna,
    private val onSingleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDragDelta: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit
) : View(context) {

    private val spriteBitmap = Bitmap.createBitmap(
        BuddyGenerator.GRID_SIZE,
        BuddyGenerator.GRID_SIZE,
        Bitmap.Config.ARGB_8888
    )
    private val pixelPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
        isDither = false
    }
    private val bgGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val dstRect = Rect()

    private var currentState = BuddyVisualState.IDLE_CHILL
    private var badgeCount = 0

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var longPressTriggered = false

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            onLongPress()
        }
    }

    fun updateFrame(
        state: BuddyVisualState,
        tick: Int,
        amplitude: Float,
        queueBadgeCount: Int
    ) {
        currentState = state
        badgeCount = queueBadgeCount
        val pixels = BuddyGenerator.renderFrame(dna, state, tick, amplitude)
        spriteBitmap.setPixels(
            pixels,
            0,
            BuddyGenerator.GRID_SIZE,
            0,
            0,
            BuddyGenerator.GRID_SIZE,
            BuddyGenerator.GRID_SIZE
        )
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) * 0.92f

        bgGlowPaint.color = when (currentState) {
            BuddyVisualState.LISTENING -> Color.argb(145, 255, 59, 48)
            BuddyVisualState.THINKING -> Color.argb(130, 90, 120, 255)
            BuddyVisualState.IDLE_STRESSED_P1 -> Color.argb(120, 255, 149, 0)
            else -> Color.argb(85, 20, 24, 34)
        }
        canvas.drawCircle(cx, cy, radius, bgGlowPaint)

        val pad = (width * 0.10f).toInt()
        dstRect.set(pad, pad, width - pad, height - pad)
        canvas.drawBitmap(spriteBitmap, null, dstRect, pixelPaint)

        if (badgeCount > 0) {
            val bx = width * 0.80f
            val by = height * 0.22f
            val br = width * 0.16f
            canvas.drawCircle(bx, by, br, badgePaint)
            badgeTextPaint.textSize = br * 1.25f
            canvas.drawText(
                badgeCount.coerceAtMost(9).toString(),
                bx,
                by + (badgeTextPaint.textSize * 0.36f),
                badgeTextPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                longPressTriggered = false
                handler?.postDelayed(longPressRunnable, 430L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDx = abs(event.rawX - downRawX)
                val totalDy = abs(event.rawY - downRawY)
                if (totalDx > 18f || totalDy > 18f) {
                    isDragging = true
                    handler?.removeCallbacks(longPressRunnable)
                }
                if (isDragging) {
                    val dx = (event.rawX - lastRawX).toInt()
                    val dy = (event.rawY - lastRawY).toInt()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    onDragDelta(dx, dy)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler?.removeCallbacks(longPressRunnable)
                if (isDragging) {
                    onDragEnd()
                } else if (!longPressTriggered && event.actionMasked == MotionEvent.ACTION_UP) {
                    onSingleTap()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
