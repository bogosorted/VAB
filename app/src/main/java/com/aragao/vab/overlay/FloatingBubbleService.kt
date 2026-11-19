package com.aragao.vab.overlay

import android.Manifest
import android.animation.ValueAnimator
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
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class FloatingBubbleService : LifecycleService() {

    companion object {
        private const val CHANNEL_ID = "vab_buddy_overlay"
        private const val ALERT_CHANNEL_ID = "vab_buddy_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val INVALID_NOTIFICATION_ID = 1002
        private const val ORGANIZED_NOTIFICATION_ID = 1003
        private const val REMINDER_NOTIFICATION_ID = 1004
        private const val EDGE_TUCK_FRACTION = 0.22f // 22% recolhido dentro da borda (stealth)
        private const val STEALTH_ALPHA = 0.46f

        private val COOL_TITLES = listOf(
            "Tudo organizado no fichário, chefe! ✍️",
            "Tá na mão, chefe! Já anotei na aba certa.",
            "Prontinho! Seu Buddy já deixou tudo no esquema.",
            "Anotado e guardado no fichário! 🚀"
        )

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
    private var actionPillView: View? = null
    private var actionPillParams: WindowManager.LayoutParams? = null
    private var quickInputCardView: View? = null

    private var dockedOnRight: Boolean = true
    private var isActionMenuOpen: Boolean = false
    private var isTypingOverlayOpen: Boolean = false
    private var recordingStartMs: Long = 0L
    private var edgeAnimator: ValueAnimator? = null

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
        observeInvalidInputs()
    }

    private fun startForegroundSafely() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "VAB Buddy Flutuante",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém seu Buddy em modo stealth pronto para anotar."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ALERT_CHANNEL_ID,
                "Avisos do Buddy (VAB)",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avise quando uma fala/texto não tiver tarefa identificável."
            }
        )

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seu Buddy está por perto")
            .setContentText("Toque abre o menu • Segure para gravar voz direto")
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

    private fun observeInvalidInputs() {
        val app = application as VabApplication
        lifecycleScope.launch {
            app.queueOrchestrator.invalidEvents.collect { notice ->
                vibrateShort(65L)
                Toast.makeText(
                    this@FloatingBubbleService,
                    "Buddy pulou: ${notice.reason}",
                    Toast.LENGTH_LONG
                ).show()

                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val openIntent = PendingIntent.getActivity(
                    this@FloatingBubbleService,
                    1,
                    Intent(this@FloatingBubbleService, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val alert = NotificationCompat.Builder(this@FloatingBubbleService, ALERT_CHANNEL_ID)
                    .setContentTitle("Hmm, o Buddy não achou tarefa nisso")
                    .setContentText(notice.reason)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("\"${notice.inputText}\"\n${notice.reason}"))
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentIntent(openIntent)
                    .setAutoCancel(true)
                    .build()
                nm.notify(INVALID_NOTIFICATION_ID, alert)
            }
        }
    }

    private fun statusBarHeightPx(): Int {
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(28)
    }

    private fun navigationBarHeightPx(): Int {
        val resId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else dpToPx(42)
    }

    private fun safeMinY(): Int = statusBarHeightPx() + dpToPx(64)

    private fun safeMaxY(sizePx: Int): Int {
        val screenH = resources.displayMetrics.heightPixels
        val min = safeMinY()
        return (screenH - navigationBarHeightPx() - sizePx - dpToPx(24)).coerceAtLeast(min + dpToPx(20))
    }

    private fun targetStealthX(sizePx: Int, onRight: Boolean): Int {
        val screenW = resources.displayMetrics.widthPixels
        val tucked = (sizePx * EDGE_TUCK_FRACTION).toInt()
        return if (onRight) screenW - sizePx + tucked else -tucked
    }

    private fun targetExpandedX(sizePx: Int, onRight: Boolean): Int {
        val screenW = resources.displayMetrics.widthPixels
        val margin = dpToPx(12)
        return if (onRight) screenW - sizePx - margin else margin
    }

    private fun attachBubbleOverlay() {
        val app = application as VabApplication
        val sizePx = dpToPx(68)
        val screenW = resources.displayMetrics.widthPixels

        val view = BuddyBubbleOverlayView(
            context = this,
            dna = app.buddyDna,
            onSingleTap = { handleBuddySingleTap() },
            onLongPress = { handleBuddyLongPressToRecord() },
            onDragStart = {
                dismissActionPill(returnToStealth = false)
                edgeAnimator?.cancel()
                bubbleView?.animate()?.alpha(1.0f)?.setDuration(100L)?.start()
            },
            onDragDelta = { dx, dy ->
                val lp = bubbleParams ?: return@BuddyBubbleOverlayView
                val minX = -(sizePx * 0.15f).toInt()
                val maxX = screenW - sizePx + (sizePx * 0.15f).toInt()
                lp.x = (lp.x + dx).coerceIn(minX, maxX)
                lp.y = (lp.y + dy).coerceIn(safeMinY(), safeMaxY(sizePx))
                bubbleView?.onDragVelocity(dx.toFloat(), dy.toFloat())
                runCatching { windowManager.updateViewLayout(bubbleView, lp) }
            },
            onDragEnd = {
                val lp = bubbleParams ?: return@BuddyBubbleOverlayView
                dockedOnRight = (lp.x + sizePx / 2) >= (screenW / 2)
                lp.y = lp.y.coerceIn(safeMinY(), safeMaxY(sizePx))
                updateBuddyDockPosition(
                    active = isActionMenuOpen || recorder.isRecording.value || isTypingOverlayOpen,
                    withSnapDeformation = true
                )
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
            x = targetStealthX(sizePx, onRight = true)
            y = (resources.displayMetrics.heightPixels / 3).coerceIn(safeMinY(), safeMaxY(sizePx))
        }

        view.alpha = STEALTH_ALPHA
        bubbleView = view
        bubbleParams = params
        windowManager.addView(view, params)

        lifecycleScope.launch {
            var coolIdx = 0
            app.queueOrchestrator.lastCelebrationTimestamp.collect { ts ->
                if (ts > 0L) {
                    celebrationUntilMs = System.currentTimeMillis() + 2400L
                    bubbleView?.triggerJiggle()
                    val title = COOL_TITLES[coolIdx++ % COOL_TITLES.size]
                    postCoolOrganizedNotification(title)
                }
            }
        }

        lifecycleScope.launch {
            app.buddyDnaFlow.collect { newDna ->
                bubbleView?.setDna(newDna)
            }
        }

        startPeriodicReminderCheck()
    }

    private fun postCoolOrganizedNotification(title: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Toque para conferir como o Buddy pensou e organizou.")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(ORGANIZED_NOTIFICATION_ID, notif)
    }

    private fun startPeriodicReminderCheck() {
        val app = application as VabApplication
        val notifiedTicketSchedules = mutableSetOf<String>()
        lifecycleScope.launch {
            while (isActive) {
                delay(45_000L) // verifica a cada 45s para disparar no minuto certo antes do evento
                val nowMs = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val openTickets = app.boardRepository.board.value.openTicketsWithSection()

                for ((secName, ticket) in openTickets) {
                    // Se não houver notificação configurada (0) ou não tiver data/hora, não notifica
                    if (ticket.reminderHours <= 0) continue
                    if (ticket.deadline == null && ticket.time == null) continue

                    val eventDate = ticket.deadline?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    } ?: today
                    val eventTime = ticket.time?.let {
                        runCatching { LocalTime.parse(it) }.getOrNull()
                    } ?: LocalTime.of(9, 0)

                    val eventEpochMs = LocalDateTime.of(eventDate, eventTime)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()

                    val leadMs = ticket.reminderHours * 60_000L
                    val triggerEpochMs = eventEpochMs - leadMs
                    val scheduleKey = "${ticket.id}_${ticket.deadline}_${ticket.time}_${ticket.reminderHours}"

                    // Notifica UMA vez dentro da janela [triggerEpochMs .. eventEpochMs] (ex: 30min antes) e NUNCA depois que o horário já passou!
                    if (nowMs in triggerEpochMs..eventEpochMs && scheduleKey !in notifiedTicketSchedules) {
                        notifiedTicketSchedules.add(scheduleKey)
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        val openIntent = PendingIntent.getActivity(
                            this@FloatingBubbleService,
                            3,
                            Intent(this@FloatingBubbleService, MainActivity::class.java),
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                        val leadLabel = when {
                            ticket.reminderHours % 1440 == 0 -> "em ${ticket.reminderHours / 1440} dia(s)"
                            ticket.reminderHours % 60 == 0 -> "em ${ticket.reminderHours / 60}h"
                            else -> "em ${ticket.reminderHours} min"
                        }
                        val timeLabel = ticket.time?.let { " às $it" }.orEmpty()
                        val notif = NotificationCompat.Builder(this@FloatingBubbleService, ALERT_CHANNEL_ID)
                            .setContentTitle("Chefe, $leadLabel: ${ticket.title} ⏰")
                            .setContentText("Aba $secName • [${ticket.priority.name}]${ticket.deadline?.let { " 📅 $it" }.orEmpty()}$timeLabel")
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentIntent(openIntent)
                            .setAutoCancel(true)
                            .build()
                        nm.notify(REMINDER_NOTIFICATION_ID + (ticket.id.hashCode() and 0x7FF), notif)
                    }
                }
            }
        }
    }

    private fun updateBuddyDockPosition(active: Boolean, withSnapDeformation: Boolean = false) {
        val lp = bubbleParams ?: return
        val view = bubbleView ?: return
        val sizePx = lp.width
        val startX = lp.x
        val targetX = if (active) {
            targetExpandedX(sizePx, dockedOnRight)
        } else {
            targetStealthX(sizePx, dockedOnRight)
        }
        val travelDist = abs(targetX - startX).toFloat()
        val targetAlpha = if (active) 1.0f else STEALTH_ALPHA

        view.animate().alpha(targetAlpha).setDuration(220L).start()

        edgeAnimator?.cancel()
        edgeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (withSnapDeformation) 280L else 230L
            interpolator = DecelerateInterpolator(1.5f)
            var prevX = startX
            addUpdateListener { anim ->
                val frac = anim.animatedValue as Float
                val curX = (startX + (targetX - startX) * frac).toInt()
                val stepDx = (curX - prevX).toFloat()
                prevX = curX
                lp.x = curX
                if (withSnapDeformation && travelDist > dpToPx(24)) {
                    // Estica horizontalmente durante o voo de um lado para o outro
                    val flightStretch = sin(frac * Math.PI).toFloat() * (travelDist / resources.displayMetrics.widthPixels).coerceIn(0.08f, 0.34f)
                    view.setSquashStretch(
                        sx = 1f + flightStretch,
                        sy = 1f - flightStretch * 0.62f,
                        tiltDeg = (stepDx * 0.35f).coerceIn(-12f, 12f)
                    )
                }
                runCatching { windowManager.updateViewLayout(view, lp) }
                if (frac >= 1f && withSnapDeformation) {
                    // Ao bater na borda, faz o squash & stretch elástico ("gelatina")
                    val impactStrength = (travelDist / resources.displayMetrics.widthPixels).coerceIn(0.14f, 0.32f)
                    view.triggerWallImpactSquash(impactStrength)
                }
            }
            start()
        }
    }

    private fun startBuddyAnimationLoop() {
        val app = application as VabApplication
        animationLoopJob?.cancel()
        animationLoopJob = lifecycleScope.launch {
            var tick = 0
            var activeQueueCount = 0
            var wasActive = false

            launch {
                app.queueOrchestrator.activeCountFlow.collect { count ->
                    activeQueueCount = count
                }
            }

            // Ciclo idle bem calmo: maior parte do tempo em IDLE_CHILL
            val idleCycle = listOf(
                BuddyVisualState.IDLE_CHILL,
                BuddyVisualState.IDLE_CHILL,
                BuddyVisualState.IDLE_CHILL,
                BuddyVisualState.IDLE_CURIOUS,
                BuddyVisualState.IDLE_CHILL,
                BuddyVisualState.IDLE_SLEEPY
            )

            while (isActive) {
                val isRec = recorder.isRecording.value
                val amp = recorder.amplitude.value
                val now = System.currentTimeMillis()
                val urgentP1 = app.boardRepository.board.value.hasUrgentDueOrOverdueP1(
                    LocalDate.now().toString()
                )

                val isActiveNow = isRec || isActionMenuOpen || isTypingOverlayOpen || activeQueueCount > 0 || now < celebrationUntilMs
                if (isActiveNow != wasActive) {
                    wasActive = isActiveNow
                    updateBuddyDockPosition(active = isActiveNow)
                }

                val visualState = when {
                    isRec -> BuddyVisualState.LISTENING
                    isTypingOverlayOpen -> BuddyVisualState.NOTING
                    now < celebrationUntilMs -> BuddyVisualState.CELEBRATING
                    activeQueueCount > 0 -> BuddyVisualState.ORGANIZING
                    urgentP1 -> BuddyVisualState.IDLE_STRESSED_P1
                    else -> idleCycle[(tick / 18) % idleCycle.size]
                }

                bubbleView?.updateFrame(
                    state = visualState,
                    tick = tick++,
                    amplitude = amp,
                    queueBadgeCount = activeQueueCount,
                    stealthMode = !isActiveNow
                )

                if (isRec) {
                    updateRecordingPillTimer()
                }

                // Animação rápida quando falando/anotando/organizando, e bem lenta/calma em idle
                delay(if (isRec || isTypingOverlayOpen || activeQueueCount > 0) 130L else 680L)
            }
        }
    }

    private fun handleBuddySingleTap() {
        bubbleView?.triggerJiggle()
        vibrateShort(28L)

        if (recorder.isRecording.value) {
            stopRecordingAndEnqueue()
            return
        }

        if (isActionMenuOpen) {
            dismissActionPill(returnToStealth = true)
        } else {
            showActionPill()
        }
    }

    private fun handleBuddyLongPressToRecord() {
        bubbleView?.triggerJiggle()
        if (recorder.isRecording.value) {
            stopRecordingAndEnqueue()
        } else {
            startVoiceFromMenu()
        }
    }

    private fun openMainAppFromOverlay() {
        dismissActionPill(returnToStealth = true)
        val openApp = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(openApp)
    }

    private fun showActionPill() {
        dismissActionPill(returnToStealth = false)
        isActionMenuOpen = true
        updateBuddyDockPosition(active = true)

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FAF8F5"))
                cornerRadius = dpToPx(22).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#E2E0D8"))
            }
            elevation = dpToPx(6).toFloat()
        }

        if (recorder.isRecording.value) {
            val sendRecBtn = createPillButton("● 0:00 Enviar", "#C96442", "#FFFFFF") {
                stopRecordingAndEnqueue()
            }
            sendRecBtn.tag = "REC_TIMER"
            val cancelRecBtn = createPillButton("✕", "#EFEDE7", "#5E6C84") {
                cancelRecording()
            }
            pill.addView(sendRecBtn)
            pill.addView(spacerView(6))
            pill.addView(cancelRecBtn)
        } else {
            val micBtn = createPillButton("🎤 Falar", "#C96442", "#FFFFFF") {
                startVoiceFromMenu()
            }
            val textBtn = createPillButton("✍️ Escrever", "#E9F2FF", "#0052CC") {
                dismissActionPill(returnToStealth = false)
                showQuickTextOverlay()
            }
            val appBtn = createPillButton("↗ App", "#EFEDE7", "#1F1E1D") {
                openMainAppFromOverlay()
            }
            val closeBtn = createPillButton("✕", "#EFEDE7", "#5E6C84") {
                dismissActionPill(returnToStealth = true)
            }
            pill.addView(micBtn)
            pill.addView(spacerView(6))
            pill.addView(textBtn)
            pill.addView(spacerView(6))
            pill.addView(appBtn)
            pill.addView(spacerView(6))
            pill.addView(closeBtn)
        }

        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pillW = pill.measuredWidth
        val pillH = pill.measuredHeight

        val bp = bubbleParams ?: return
        val sizePx = bp.width
        val screenW = resources.displayMetrics.widthPixels
        val expandedBuddyX = targetExpandedX(sizePx, dockedOnRight)
        val pillX = if (dockedOnRight) {
            (expandedBuddyX - pillW - dpToPx(8)).coerceAtLeast(dpToPx(8))
        } else {
            (expandedBuddyX + sizePx + dpToPx(8)).coerceAtMost(screenW - pillW - dpToPx(8))
        }
        val pillY = (bp.y + (sizePx - pillH) / 2).coerceIn(safeMinY(), safeMaxY(pillH))

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pillX
            y = pillY
        }

        actionPillView = pill
        actionPillParams = lp
        windowManager.addView(pill, lp)
    }

    private fun updateRecordingPillTimer() {
        val pill = actionPillView as? LinearLayout ?: return
        val btn = pill.findViewWithTag<TextView>("REC_TIMER") ?: return
        val elapsedSec = ((System.currentTimeMillis() - recordingStartMs) / 1000L).coerceAtLeast(0L)
        val mm = elapsedSec / 60
        val ss = elapsedSec % 60
        btn.text = String.format("● %d:%02d Enviar", mm, ss)
    }

    private fun startVoiceFromMenu() {
        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            dismissActionPill(returnToStealth = true)
            Toast.makeText(this, "Abra o VAB para conceder permissão de microfone.", Toast.LENGTH_SHORT).show()
            openMainAppFromOverlay()
            return
        }
        vibrateShort(45L)
        recordingStartMs = System.currentTimeMillis()
        recorder.startRecording()
        showActionPill()
    }

    private fun stopRecordingAndEnqueue() {
        val app = application as VabApplication
        vibrateShort(35L)
        dismissActionPill(returnToStealth = true)
        lifecycleScope.launch {
            val wavFile = recorder.stopRecording()
            if (wavFile != null) {
                app.queueOrchestrator.enqueueAudio(wavFile)
            }
        }
    }

    private fun cancelRecording() {
        vibrateShort(25L)
        dismissActionPill(returnToStealth = true)
        lifecycleScope.launch {
            val wav = recorder.stopRecording()
            wav?.delete()
        }
    }

    private fun dismissActionPill(returnToStealth: Boolean) {
        actionPillView?.let { v ->
            runCatching { windowManager.removeView(v) }
        }
        actionPillView = null
        actionPillParams = null
        isActionMenuOpen = false
        if (returnToStealth && !recorder.isRecording.value && !isTypingOverlayOpen) {
            updateBuddyDockPosition(active = false)
        }
    }

    private fun createPillButton(
        label: String,
        bgHex: String,
        fgHex: String,
        onClick: () -> Unit
    ): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.parseColor(fgHex))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(11), dpToPx(8), dpToPx(11), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = dpToPx(16).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun spacerView(dp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(dp), 1)
        }
    }

    private fun showQuickTextOverlay() {
        if (quickInputCardView != null) return
        isTypingOverlayOpen = true
        updateBuddyDockPosition(active = true)
        vibrateShort(35L)

        val app = application as VabApplication
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#E2E0D8"))
            }
            elevation = dpToPx(8).toFloat()
        }

        val title = TextView(this).apply {
            text = "Manda pro Buddy"
            setTextColor(Color.parseColor("#1F1E1D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
        }

        val input = EditText(this).apply {
            hint = "O que você não quer esquecer?"
            setHintTextColor(Color.parseColor("#5E6C84"))
            setTextColor(Color.parseColor("#1F1E1D"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F7F5F0"))
                cornerRadius = dpToPx(10).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#E2E0D8"))
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    bubbleView?.triggerJiggle()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }

        val cancelBtn = TextView(this).apply {
            text = "Cancelar"
            setTextColor(Color.parseColor("#5E6C84"))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            setOnClickListener { dismissQuickTextOverlay() }
        }

        val sendBtn = TextView(this).apply {
            text = "Mandar"
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#C96442"))
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

        val titleParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dpToPx(10) }

        btnRow.addView(cancelBtn)
        btnRow.addView(spacerView(6))
        btnRow.addView(sendBtn)
        container.addView(title, titleParams)
        container.addView(input)
        container.addView(btnRow)

        val cardParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            dimAmount = 0.25f
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusBarHeightPx() + dpToPx(72)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        }

        quickInputCardView = container
        windowManager.addView(container, cardParams)
        input.requestFocus()
    }

    private fun dismissQuickTextOverlay() {
        val card = quickInputCardView ?: return
        quickInputCardView = null
        isTypingOverlayOpen = false
        runCatching { windowManager.removeView(card) }
        updateBuddyDockPosition(active = isActionMenuOpen || recorder.isRecording.value)
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
        edgeAnimator?.cancel()
        dismissActionPill(returnToStealth = false)
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
    private var dna: BuddyDna,
    private val onSingleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onDragStart: () -> Unit,
    private val onDragDelta: (Int, Int) -> Unit,
    private val onDragEnd: () -> Unit
) : View(context) {

    fun setDna(newDna: BuddyDna) {
        dna = newDna
        postInvalidate()
    }

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
    private val bgRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C96442")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val micBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C96442")
    }
    private val micIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val micStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dstRect = Rect()
    private val ovalRect = RectF()
    private val micRect = RectF()

    private var currentState = BuddyVisualState.IDLE_CHILL
    private var currentTick = 0
    private var badgeCount = 0
    private var isStealth = true
    private var jiggleBurstUntilMs = 0L

    // Squash & Stretch (deformação elástica ao arrastar ou jogar de um lado pro outro)
    private var squashScaleX = 1f
    private var squashScaleY = 1f
    private var dragTiltDeg = 0f
    private var squashAnimator: ValueAnimator? = null

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

    fun triggerJiggle() {
        jiggleBurstUntilMs = System.currentTimeMillis() + 480L
        postInvalidate()
    }

    fun onDragVelocity(dx: Float, dy: Float) {
        squashAnimator?.cancel()
        val speedX = abs(dx)
        val speedY = abs(dy)
        val stretchX = ((speedX - speedY) * 0.011f).coerceIn(-0.22f, 0.26f)
        squashScaleX = 1f + stretchX
        squashScaleY = 1f - stretchX * 0.68f
        dragTiltDeg = (dx * 0.35f).coerceIn(-13f, 13f)
        postInvalidate()
    }

    fun setSquashStretch(sx: Float, sy: Float, tiltDeg: Float) {
        squashScaleX = sx
        squashScaleY = sy
        dragTiltDeg = tiltDeg
        postInvalidate()
    }

    fun triggerWallImpactSquash(strength: Float) {
        squashAnimator?.cancel()
        squashAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 460L
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                // Mola amortecida: aperta na parede primeiro (-cos) e volta oscilando
                val env = exp(-5.0 * t).toFloat()
                val wave = -cos(t * Math.PI * 2.5).toFloat() * strength * env
                squashScaleX = 1f + wave
                squashScaleY = 1f - wave * 0.72f
                dragTiltDeg *= (1f - t)
                postInvalidate()
            }
            start()
        }
    }

    fun updateFrame(
        state: BuddyVisualState,
        tick: Int,
        amplitude: Float,
        queueBadgeCount: Int,
        stealthMode: Boolean
    ) {
        currentState = state
        currentTick = tick
        badgeCount = queueBadgeCount
        isStealth = stealthMode
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
        val radius = (width.coerceAtMost(height) / 2f) * 0.88f

        val now = System.currentTimeMillis()
        val burstActive = now < jiggleBurstUntilMs
        val angle = when {
            burstActive -> sin(now * 0.04).toFloat() * 8f
            abs(dragTiltDeg) > 0.2f -> dragTiltDeg
            currentState == BuddyVisualState.LISTENING || currentState == BuddyVisualState.NOTING ->
                sin(currentTick * 0.7).toFloat() * 2.5f
            currentState == BuddyVisualState.ORGANIZING || currentState == BuddyVisualState.CELEBRATING ->
                sin(currentTick * 0.8).toFloat() * 3.5f
            else -> 0f // Idle calmo, sem balançar à toa
        }

        val baseScale = when {
            burstActive -> 1.06f
            currentState == BuddyVisualState.CELEBRATING -> 1.04f
            !isStealth -> 1.0f
            else -> 0.96f
        }

        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.scale(baseScale * squashScaleX, baseScale * squashScaleY, cx, cy)

        bgGlowPaint.color = when (currentState) {
            BuddyVisualState.LISTENING -> Color.argb(235, 250, 232, 224)
            BuddyVisualState.NOTING -> Color.argb(235, 233, 242, 255)
            BuddyVisualState.ORGANIZING -> Color.argb(235, 250, 232, 224)
            BuddyVisualState.IDLE_STRESSED_P1 -> Color.argb(225, 255, 244, 229)
            else -> if (isStealth) Color.argb(175, 250, 248, 245) else Color.argb(242, 255, 255, 255)
        }
        bgRingPaint.color = when (currentState) {
            BuddyVisualState.LISTENING -> Color.parseColor("#C96442")
            BuddyVisualState.NOTING, BuddyVisualState.ORGANIZING -> Color.parseColor("#0052CC")
            else -> Color.parseColor("#D5D2C8")
        }

        ovalRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawOval(ovalRect, bgGlowPaint)
        canvas.drawOval(ovalRect, bgRingPaint)

        val pad = (width * 0.13f).toInt()
        dstRect.set(pad, pad, width - pad, height - pad)
        canvas.drawBitmap(spriteBitmap, null, dstRect, pixelPaint)

        // Símbolo de Microfone vetorial quando estiver ouvindo
        if (currentState == BuddyVisualState.LISTENING) {
            val mx = width * 0.22f
            val my = height * 0.22f
            val mr = width * 0.16f
            canvas.drawCircle(mx, my, mr, micBadgeBgPaint)

            // Cápsula do microfone
            val capW = mr * 0.42f
            val capH = mr * 0.62f
            micRect.set(mx - capW / 2f, my - capH * 0.72f, mx + capW / 2f, my + capH * 0.25f)
            canvas.drawRoundRect(micRect, capW / 2f, capW / 2f, micIconPaint)

            // Arco e haste do microfone
            micStrokePaint.strokeWidth = (mr * 0.17f).coerceAtLeast(2f)
            val arcR = mr * 0.44f
            micRect.set(mx - arcR, my - arcR * 0.75f, mx + arcR, my + arcR * 0.65f)
            canvas.drawArc(micRect, 15f, 150f, false, micStrokePaint)
            canvas.drawLine(mx, my + arcR * 0.65f, mx, my + mr * 0.58f, micStrokePaint)
            canvas.drawLine(mx - mr * 0.28f, my + mr * 0.58f, mx + mr * 0.28f, my + mr * 0.58f, micStrokePaint)
        }

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

        canvas.restore()
        if (burstActive) {
            postInvalidateOnAnimation()
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
                handler?.postDelayed(longPressRunnable, 420L)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val totalDx = abs(event.rawX - downRawX)
                val totalDy = abs(event.rawY - downRawY)
                if (!isDragging && (totalDx > 18f || totalDy > 18f)) {
                    isDragging = true
                    handler?.removeCallbacks(longPressRunnable)
                    onDragStart()
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
