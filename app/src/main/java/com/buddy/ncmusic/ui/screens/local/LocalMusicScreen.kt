package com.buddy.ncmusic.ui.screens.local

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.data.model.Album
import com.buddy.ncmusic.data.model.Artist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.SongListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocalMusicScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            scope.launch {
                songs = scanLocalMusic(context)
                loading = false
            }
        } else {
            denied = true
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        launcher.launch(perm)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("本地歌单", style = MaterialTheme.typography.titleMedium)
        }

        when {
            loading -> LoadingView(Modifier.fillMaxSize())
            denied -> Text(
                text = "需要媒体权限才能读取本地音乐，请在系统设置中授予",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            songs.isEmpty() -> Text(
                text = "未找到本地音乐",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Text(
                    text = "共 ${songs.size} 首",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(songs) { index, song ->
                        SongListItem(song = song, onClick = { onPlay(songs, index) })
                    }
                }
            }
        }
    }
}

/** 通过 MediaStore 扫描本地音乐文件 */
private suspend fun scanLocalMusic(context: Context): List<Song> = withContext(Dispatchers.IO) {
    val out = mutableListOf<Song>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.ALBUM,
    )
    runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val title = c.getString(1) ?: "未知"
                val artist = c.getString(2) ?: "未知"
                val duration = c.getLong(3)
                val path = c.getString(4) ?: continue
                val albumId = c.getLong(5)
                val albumName = c.getString(6) ?: ""
                out.add(
                    Song(
                        id = -id,
                        name = title,
                        artists = listOf(Artist(id = 0, name = artist)),
                        album = Album(
                            id = albumId,
                            name = albumName,
                            picUrl = "content://media/external/audio/albumart/$albumId",
                        ),
                        duration = duration,
                        localPath = path,
                    ),
                )
            }
        }
    }
    out
}
