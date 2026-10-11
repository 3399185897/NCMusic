package com.buddy.ncmusic.ui.screens.local

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.NCMusicApp
import com.buddy.ncmusic.data.model.Album
import com.buddy.ncmusic.data.model.Artist
import com.buddy.ncmusic.data.model.Song
import com.buddy.ncmusic.ui.components.LoadingView
import com.buddy.ncmusic.ui.components.ListWithScrollIndicator
import com.buddy.ncmusic.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 本地音乐的两种来源 */
private enum class LocalTab(val label: String) {
    ALL("本地歌曲"),
    DOWNLOADED("已下载"),
}

/**
 * 已下载条目。
 *
 * 比 [Song] 多带**定位信息** —— 删除时需要知道文件在哪：
 * 私有目录可以直接用 [filePath]；用户通过 SAF 选的目录则要用 [docUri]。
 */
private data class DownloadedItem(
    val song: Song,
    val fileName: String,
    val filePath: String?,
    val docUri: String?,
)

@Composable
fun LocalMusicScreen(
    onPlay: (List<Song>, Int) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(LocalTab.ALL) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var downloaded by remember { mutableStateOf<List<DownloadedItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var denied by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadedItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

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

    // 已下载列表不需要媒体权限（两者都在应用可访问范围内），单独加载
    suspend fun reloadDownloaded() {
        val customUri = NCMusicApp.instance.userPreferences
            .userStateFlow.first().cacheDirUri
        downloaded = scanDownloaded(context, customUri)
    }

    LaunchedEffect(Unit) {
        reloadDownloaded()
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        launcher.launch(perm)
    }

    // 提示条自动消失
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2200)
            toast = null
        }
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

        // ---------- 分段切换：本地歌曲 / 已下载 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LocalTab.entries.forEach { t ->
                val selected = t == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppShapes.of(12.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                        .clickable { tab = t }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = t.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (tab) {
            LocalTab.ALL -> LocalSongList(
                loading = loading,
                denied = denied,
                songs = songs,
                listState = listState,
                onPlay = onPlay,
            )

            LocalTab.DOWNLOADED -> DownloadedList(
                items = downloaded,
                listState = listState,
                onPlay = { idx ->
                    val playable = downloaded.map { it.song }
                    onPlay(playable, idx)
                },
                onDelete = { pendingDelete = it },
            )
        }
    }

    // ---------- 删除确认 ----------
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除已下载文件") },
            text = { Text("将从设备中永久删除「${item.song.name}」，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val target = item
                    pendingDelete = null
                    scope.launch {
                        val ok = deleteDownloaded(context, target)
                        toast = if (ok) "已删除：${target.song.name}" else "删除失败"
                        reloadDownloaded()
                    }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    toast?.let { msg ->
        Text(
            text = msg,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ---------------- 列表 ----------------

@Composable
private fun LocalSongList(
    loading: Boolean,
    denied: Boolean,
    songs: List<Song>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (List<Song>, Int) -> Unit,
) {
    when {
        loading -> LoadingView(Modifier.fillMaxSize())
        denied -> Hint("需要媒体权限才能读取本地音乐，请在系统设置中授予")
        songs.isEmpty() -> Hint("未找到本地音乐")
        else -> {
            Text(
                text = "共 ${songs.size} 首",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            ListWithScrollIndicator(listState = listState, showIndicator = false) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(songs) { index, song ->
                        com.buddy.ncmusic.ui.components.SongListItem(
                            song = song,
                            onClick = { onPlay(songs, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedList(
    items: List<DownloadedItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlay: (Int) -> Unit,
    onDelete: (DownloadedItem) -> Unit,
) {
    if (items.isEmpty()) {
        Hint("还没有已下载的歌曲\n可在播放器或歌曲菜单里选择「下载」")
        return
    }
    Text(
        text = "共 ${items.size} 首 · 长按条目右侧可删除",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleSmall,
    )
    ListWithScrollIndicator(listState = listState, showIndicator = false) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(items) { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        com.buddy.ncmusic.ui.components.SongListItem(
                            song = item.song,
                            onClick = { onPlay(index) },
                        )
                    }
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------------- 扫描 ----------------

/** 通过 MediaStore 扫描设备上的本地音乐文件 */
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

/**
 * 扫描「已下载」的歌曲。
 *
 * 覆盖两处来源：
 * 1. **App 专属目录**（默认下载位置）
 * 2. **用户自选的 SAF 目录**（设置里指定过的话）
 *
 * 之所以不走 MediaStore：下载目录位于应用私有空间时，
 * MediaStore 可能尚未索引到，导致刚下载的歌曲在列表里看不到。
 */
private suspend fun scanDownloaded(context: Context, cacheDirUri: String): List<DownloadedItem> =
    withContext(Dispatchers.IO) {
        val out = mutableListOf<DownloadedItem>()

        // ---- 1) App 专属目录 ----
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        dir?.listFiles()?.forEach { f ->
            if (f.isFile && isAudioFile(f.name)) {
                out.add(
                    DownloadedItem(
                        song = fileToSong(f),
                        fileName = f.name,
                        filePath = f.absolutePath,
                        docUri = null,
                    ),
                )
            }
        }

        // ---- 2) 用户自选 SAF 目录 ----
        if (cacheDirUri.isNotBlank()) {
            runCatching {
                val treeUri = Uri.parse(cacheDirUri)
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    children,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_SIZE,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val name = c.getString(1) ?: continue
                        if (!isAudioFile(name)) continue
                        // 避免与 App 专属目录里的同名文件重复计数
                        if (out.any { it.fileName == name }) continue
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                        out.add(
                            DownloadedItem(
                                song = Song(
                                    id = -id.hashCode().toLong(),
                                    name = name.substringBeforeLast('.'),
                                    artists = emptyList(),
                                    duration = 0L,
                                    localPath = docUri.toString(),
                                ),
                                fileName = name,
                                filePath = null,
                                docUri = docUri.toString(),
                            ),
                        )
                    }
                }
            }
        }

        out.sortedBy { it.fileName }
    }

/** 删除一个已下载条目，返回是否成功 */
private suspend fun deleteDownloaded(context: Context, item: DownloadedItem): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            when {
                item.filePath != null -> File(item.filePath).delete()
                item.docUri != null -> DocumentsContract.deleteDocument(
                    context.contentResolver,
                    Uri.parse(item.docUri),
                )
                else -> false
            }
        }.getOrDefault(false)
    }

private fun isAudioFile(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".mp3") || n.endsWith(".flac") ||
        n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".wav") || n.endsWith(".ogg")
}

/** 从本地文件构造 Song（id 用负值，与在线歌曲区分） */
private fun fileToSong(f: File): Song = Song(
    id = -f.absolutePath.hashCode().toLong(),
    name = f.name.substringBeforeLast('.'),
    artists = emptyList(),
    duration = 0L,
    localPath = f.absolutePath,
)
