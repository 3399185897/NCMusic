package com.buddy.ncmusic.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle
import coil.imageLoader
import coil.request.ImageRequest
import com.buddy.ncmusic.MainActivity
import com.buddy.ncmusic.R
import com.buddy.ncmusic.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 媒体播放前台服务。
 *
 * 通知采用**显式构建 + startForeground**，保证在所有 ROM（含 HyperOS）上稳定显示；
 * 三个控制按钮使用应用内置矢量图标（避免系统图标在高版本不可见），
 * 点击通过 PendingIntent.getService → onStartCommand 直接调用 PlaybackManager，
 * 不依赖 MediaSession 的命令转发，因此不会出现"点击无反应"。
 */
class PlaybackService : MediaSessionService() {

    companion object {
        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PREV = "com.buddy.ncmusic.PREV"
        const val ACTION_PLAY_PAUSE = "com.buddy.ncmusic.PLAY_PAUSE"
        const val ACTION_NEXT = "com.buddy.ncmusic.NEXT"
    }

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        PlaybackManager.ensureInit()
        createNotificationChannel()

        val session = MediaSession.Builder(this, NcPlayer(PlaybackManager.player))
            .setSessionActivity(createActivityPendingIntent())
            .build()
        mediaSession = session

        scope.launch {
            PlaybackManager.state.collectLatest { state ->
                val song = state.song ?: return@collectLatest
                val cover = loadCover(song.coverUrl)
                startForegroundWithNotification(session, song, state.isPlaying, cover)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> PlaybackManager.previous()
            ACTION_PLAY_PAUSE -> PlaybackManager.togglePlayPause()
            ACTION_NEXT -> PlaybackManager.next()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (PlaybackManager.player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private suspend fun loadCover(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                imageLoader.execute(
                    ImageRequest.Builder(this@PlaybackService).data(url).size(512).build(),
                ).drawable?.toBitmap()
            }.getOrNull()
        }
    }

    private fun startForegroundWithNotification(
        session: MediaSession,
        song: Song,
        isPlaying: Boolean,
        cover: Bitmap?,
    ) {
        val notification = buildNotification(session, song, isPlaying, cover)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(
        session: MediaSession,
        song: Song,
        isPlaying: Boolean,
        cover: Bitmap?,
    ): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(song.name)
            .setContentText(song.artistNames)
            .setContentIntent(createActivityPendingIntent())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
            .addAction(R.drawable.ic_notif_prev, "上一首", serviceAction(ACTION_PREV))
            .addAction(
                if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
                "播放/暂停",
                serviceAction(ACTION_PLAY_PAUSE),
            )
            .addAction(R.drawable.ic_notif_next, "下一首", serviceAction(ACTION_NEXT))
        cover?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    /** 通知按钮 → 服务的 PendingIntent，由 onStartCommand 直接分发 */
    private fun serviceAction(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "播放控制",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "音乐播放控制"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
