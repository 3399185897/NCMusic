package com.buddy.ncmusic.ui.screens.artist

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.SongListItem
import com.buddy.ncmusic.util.ApiResult

@Composable
fun ArtistScreen(
    artistId: Long,
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("歌手") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(artistId) {
        when (val r = NCMusicApp.instance.musicRepository.artistDetail(artistId)) {
            is ApiResult.Success -> {
                name = r.data.first.ifEmpty { "歌手" }
                songs = r.data.second
            }
            is ApiResult.Error -> Unit
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        }

        if (loading) {
            LoadingView(Modifier.fillMaxSize())
        } else if (songs.isEmpty()) {
            Text(
                text = "暂无热门作品",
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "热门作品 · ${songs.size} 首",
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
