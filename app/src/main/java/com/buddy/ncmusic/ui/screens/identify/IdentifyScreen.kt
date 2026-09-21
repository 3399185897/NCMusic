package com.buddy.ncmusic.ui.screens.identify

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.Mic
import com.buddy.ncmusic.ui.components.SongListItem
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun IdentifyScreen(onBack: () -> Unit, onPlay: (List<Song>, Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var identifying by remember { mutableStateOf(false) }
    var identifiedSong by remember { mutableStateOf<Song?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var started by remember { mutableStateOf(false) }

    val startIdentify = {
        scope.launch {
            identifying = true
            identifiedSong = null
            errorMsg = null
            val song = recordAndIdentify(context)
            if (song != null) identifiedSong = song else errorMsg = "未识别到歌曲，请重试"
            identifying = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startIdentify() else errorMsg = "需要录音权限才能听歌识曲"
    }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "identify")
    val pulse by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val barHeights = List(5) { i ->
        transition.animateFloat(
            initialValue = 16f,
            targetValue = if (i % 2 == 0) 52f else 30f,
            animationSpec = infiniteRepeatable(tween(420 + i * 80, easing = LinearEasing), RepeatMode.Reverse),
            label = "bar$i",
        ).value
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("听歌识曲", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            Box(contentAlignment = Alignment.Center) {
                if (identifying) {
                    Box(
                        Modifier
                            .size(170.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.15f)),
                    )
                    Box(
                        Modifier
                            .size(170.dp)
                            .scale(2.0f - pulse)
                            .clip(CircleShape)
                            .background(primary.copy(alpha = 0.08f)),
                    )
                }
                Box(
                    Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = primary,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            if (identifying) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    barHeights.forEach { h ->
                        Box(
                            Modifier
                                .width(7.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("正在识别...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                errorMsg?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                        Text("重新识别")
                    }
                }
            }

            identifiedSong?.let { song ->
                Spacer(Modifier.height(16.dp))
                SongListItem(song = song, onClick = { onPlay(listOf(song), 0) })
            }
        }
    }
}

/** 录音 8 秒并上传识别，返回识别到的歌曲 */
private suspend fun recordAndIdentify(context: Context): Song? {
    val file = File(context.cacheDir, "identify.m4a")
    var recorder: MediaRecorder? = null
    return try {
        recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        delay(8000)
        recorder.stop()
        recorder.release()
        recorder = null
        val bytes = file.readBytes()
        val r = NCMusicApp.instance.musicRepository.identifySong(bytes)
        (r as? ApiResult.Success)?.data
    } catch (e: Exception) {
        null
    } finally {
        recorder?.runCatching { release() }
        file.delete()
    }
}
