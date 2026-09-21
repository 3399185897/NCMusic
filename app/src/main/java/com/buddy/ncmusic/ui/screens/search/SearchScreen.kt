package com.buddy.ncmusic.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.ErrorView
import com.buddy.ncmusic.ui.components.SongListItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onPlay: (List<Song>, Int) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val query by vm.query.collectAsState()
    val hotWords by vm.hotWords.collectAsState()
    val results by vm.results.collectAsState()
    val searching by vm.searching.collectAsState()
    val error by vm.error.collectAsState()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索歌曲") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (query.isBlank()) {
            // 热搜词
            if (hotWords.isNotEmpty()) {
                Text(
                    text = "热门搜索",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    hotWords.forEach { word ->
                        SuggestionChip(
                            onClick = { vm.searchNow(word) },
                            label = { Text(word) },
                            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                        )
                    }
                }
            }
        } else if (error != null) {
            ErrorView(message = error.orEmpty(), onRetry = { vm.searchNow(query) })
        } else if (results.isEmpty()) {
            Text(
                text = "没有找到相关歌曲",
                modifier = Modifier.padding(24.dp),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(results) { song ->
                    SongListItem(
                        song = song,
                        onClick = { onPlay(results, results.indexOf(song)) },
                    )
                }
            }
        }
    }
}
