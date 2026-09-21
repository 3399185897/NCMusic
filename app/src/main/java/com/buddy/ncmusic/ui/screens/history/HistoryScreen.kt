package com.buddy.ncmusic.ui.screens.history

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.SongListItem
import com.buddy.ncmusic.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    private val repo get() = NCMusicApp.instance.musicRepository
    private val prefs get() = NCMusicApp.instance.userPreferences

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            val state = prefs.userStateFlow.first()
            if (state.isLoggedIn) {
                when (val r = repo.playHistory(state.userId)) {
                    is ApiResult.Success -> _songs.value = r.data
                    is ApiResult.Error -> Unit
                }
            }
            _loading.value = false
        }
    }
}

@Composable
fun HistoryScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    vm: HistoryViewModel = viewModel(),
) {
    val songs by vm.songs.collectAsState()
    val loading by vm.loading.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("最近播放", style = MaterialTheme.typography.titleMedium)
        }

        if (loading) {
            LoadingView(Modifier.fillMaxSize())
        } else if (songs.isEmpty()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "暂无播放记录",
                    modifier = Modifier.padding(top = 40.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(songs) { index, song ->
                    SongListItem(song = song, onClick = { onPlay(songs, index) }, onArtistClick = onOpenArtist)
                }
            }
        }
    }
}
