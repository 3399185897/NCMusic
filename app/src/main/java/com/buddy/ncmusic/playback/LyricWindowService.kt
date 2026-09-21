package com.buddy.ncmusic.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 桌面歌词悬浮窗：
 * - 完全透明背景，文字带阴影描边
 * - 左侧**锁定按钮**：锁定后不可拖动；解锁后可自由拖动并自动保存位置
 * - 自定义颜色 / 字号；双行显示（当前行高亮加粗、下一行半透明）
 */
class LyricWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "lyric_overlay_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private lateinit var windowManager: WindowManager
    private var container: LinearLayout? = null
    private var lyricView: TextView? = null
    private var lockView: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var textColor: Int = Color.WHITE
    private var fontSize: Int = 18
    private var locked: Boolean = false

    /** 锁图标自动隐藏 */
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideLockRunnable = Runnable {
        lockView?.animate()?.alpha(0f)?.setDuration(400)?.start()
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundCompat()
        addOverlay()
        observePrefs()
        observeLyrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "歌词悬浮窗", NotificationManager.IMPORTANCE_MIN),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("歌词悬浮窗")
            .setContentText("正在显示桌面歌词")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

        when {
            Build.VERSION.SDK_INT >= 34 ->
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)

            else -> startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addOverlay() {
        if (container != null) return
        val prefs = NCMusicApp.instance.userPreferences

        // 锁定按钮（左侧）：使用矢量图标，避免 emoji 在大字号下显示粗糙
        val lock = ImageView(this).apply {
            setImageResource(R.drawable.ic_lyric_unlock)
            setColorFilter(Color.WHITE)
            setPadding(24, 12, 8, 12)
            alpha = 0.75f
            scaleType = ImageView.ScaleType.CENTER
        }
        lock.setOnClickListener {
            val next = !locked
            scope.launch { prefs.setLyricLocked(next) }
        }

        // 歌词文本
        val tv = TextView(this).apply {
            setTextColor(textColor)
            textSize = fontSize.toFloat()
            setPadding(8, 28, 48, 28)
            setBackgroundColor(Color.TRANSPARENT)
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            maxLines = 3
            gravity = Gravity.CENTER
            text = "暂无歌词"
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }
        box.addView(
            lock,
            LinearLayout.LayoutParams(dp(36), dp(48)),
        )
        box.addView(
            tv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 320
        }

        // 拖动（锁定时不响应）
        box.setOnTouchListener { v, e ->
            if (locked) return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    // 交互时让锁图标重新显现
                    if (!locked) showLockTemporarily()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - downRawX).toInt()
                    lp.y = startY + (e.rawY - downRawY).toInt()
                    runCatching { windowManager.updateViewLayout(v, lp) }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scope.launch { prefs.setLyricPosition(lp.x, lp.y) }
                    true
                }

                else -> false
            }
        }

        runCatching { windowManager.addView(box, lp) }
        container = box
        lyricView = tv
        lockView = lock
        params = lp

        // 进入后短暂展示锁图标，随后自动淡出
        showLockTemporarily()
    }

    /** 显示锁图标并在 3 秒后淡出，避免长期遮挡歌词 */
    private fun showLockTemporarily() {
        val target = if (locked) 1f else 0.6f
        lockView?.animate()?.alpha(target)?.setDuration(180)?.start()
        uiHandler.removeCallbacks(hideLockRunnable)
        uiHandler.postDelayed(hideLockRunnable, 3000)
    }

    private fun observePrefs() {
        val prefs = NCMusicApp.instance.userPreferences
        scope.launch {
            prefs.userStateFlow.collect { s ->
                textColor = s.lyricColor.toInt()
                fontSize = s.lyricFontSize
                locked = s.lyricLocked
                lyricView?.apply {
                    setTextColor(textColor)
                    textSize = fontSize.toFloat()
                }
                lockView?.apply {
                    setImageResource(
                        if (locked) R.drawable.ic_lyric_lock else R.drawable.ic_lyric_unlock,
                    )
                }
                showLockTemporarily()
                params?.let { lp ->
                    if (s.lyricX != 0 || s.lyricY != 320) {
                        if (lp.x == 0 && lp.y == 320) {
                            lp.x = s.lyricX
                            lp.y = s.lyricY
                            container?.let { v -> runCatching { windowManager.updateViewLayout(v, lp) } }
                        }
                    }
                }
            }
        }
    }

    private fun observeLyrics() {
        scope.launch {
            PlaybackManager.state.collect { s ->
                val view = lyricView ?: return@collect
                if (s.lyrics.isEmpty()) {
                    view.text = s.song?.name.orEmpty().ifBlank { "暂无歌词" }
                    return@collect
                }
                val index = s.lyrics.indexOfLast { it.time <= s.position }.coerceAtLeast(0)
                val current = s.lyrics.getOrNull(index)?.text.orEmpty()
                val next = s.lyrics.getOrNull(index + 1)?.text.orEmpty()

                if (next.isBlank()) {
                    view.text = current
                } else {
                    val text = "$current\n$next"
                    val span = SpannableString(text)
                    span.setSpan(
                        ForegroundColorSpan(textColor), 0, current.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    span.setSpan(
                        StyleSpan(Typeface.BOLD), 0, current.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    val faded = (textColor and 0x00FFFFFF) or (0x99 shl 24)
                    span.setSpan(
                        ForegroundColorSpan(faded), current.length + 1, text.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    view.text = span
                }
            }
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(hideLockRunnable)
        container?.let { v -> runCatching { windowManager.removeView(v) } }
        container = null
        lyricView = null
        lockView = null
        params = null
        super.onDestroy()
    }
}
