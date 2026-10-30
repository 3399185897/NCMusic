package com.buddy.ncmusic.ui.screens.about

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.BuildConfig
import com.buddy.ncmusic.data.update.UpdateChecker
import com.buddy.ncmusic.ui.components.BrandMarkTile
import com.buddy.ncmusic.ui.util.Md3Motion
import com.buddy.ncmusic.ui.util.rememberReduceMotion
import kotlinx.coroutines.launch

private const val PROJECT_REPO_URL = "https://github.com/3399185897/NCMusic"
private const val PROJECT_ISSUES_URL = "https://github.com/3399185897/NCMusic/issues"

private val OPEN_SOURCE_LICENSES = listOf(
    "Jetpack Compose (UI / Foundation / Material3)" to "Apache-2.0",
    "AndroidX Core KTX / Activity / Lifecycle" to "Apache-2.0",
    "AndroidX Navigation Compose" to "Apache-2.0",
    "AndroidX Media3 (ExoPlayer / Session)" to "Apache-2.0",
    "AndroidX DataStore / Palette" to "Apache-2.0",
    "Kotlin / Kotlin Coroutines" to "Apache-2.0",
    "KotlinX Serialization" to "Apache-2.0",
    "OkHttp / Coil / ZXing" to "Apache-2.0",
)

/** 关于页：品牌 Hero、构建信息、项目链接与可折叠许可。 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // ⚠️ 区分两个用途：
    //  · updateVersion —— 纯版本号，交给更新检查做比较，**不能**掺别的字符
    //  · versionName    —— 展示用，附上 versionCode
    //
    // 之所以要显示构建号：versionName 长期停在 1.2.1，
    // 而同一版本号下已经出了十几个构建，出问题时无法判断装的是哪一个。
    val updateVersion = BuildConfig.VERSION_NAME
    val versionName = "$updateVersion (${BuildConfig.VERSION_CODE})"

    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        checkResult = null
        scope.launch {
            val result = UpdateChecker.check(updateVersion)
            checkResult = result
            checking = false
            if (result is UpdateChecker.Result.Available) showUpdateDialog = true
        }
    }

    if (showUpdateDialog) {
        val available = (checkResult as? UpdateChecker.Result.Available)?.info
        if (available != null) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
                title = { Text("发现新版本 ${available.tag}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "当前版本 $updateVersion，可更新至 ${available.version}。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (available.notes.isNotBlank()) {
                            Text(
                                text = available.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showUpdateDialog = false
                        openUrl(available.htmlUrl)
                    }) { Text("前往下载") }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("稍后") }
                },
            )
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
            Text("关于", style = MaterialTheme.typography.titleMedium)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AboutHero(
                versionName = versionName,
                checking = checking,
                checkResult = checkResult,
                onCheckUpdate = ::checkUpdate,
                onOpenRepo = { openUrl(PROJECT_REPO_URL) },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile("版本", versionName, Icons.Default.SystemUpdate, Modifier.weight(1f))
                    InfoTile("构建", BuildConfig.BUILD_TYPE, Icons.Default.Code, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoTile("系统", "Android 8.0+", Icons.Default.Info, Modifier.weight(1f))
                    InfoTile("协议", "GPL-3.0", Icons.Default.Security, Modifier.weight(1f))
                }
            }

            AboutSectionTitle("项目")
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column {
                    LinkRow(
                        icon = Icons.Default.Code,
                        title = "开源仓库",
                        subtitle = "查看源码与版本记录",
                        onClick = { openUrl(PROJECT_REPO_URL) },
                    )
                    LinkRow(
                        icon = Icons.Default.BugReport,
                        title = "问题反馈",
                        subtitle = "提交 Bug 或功能建议",
                        onClick = { openUrl(PROJECT_ISSUES_URL) },
                    )
                }
            }

            ExpandableCard(
                icon = Icons.Default.Security,
                title = "项目许可",
                subtitle = "GPL-3.0",
            ) {
                Text(
                    text = "本项目采用 GNU General Public License v3.0。你可以自由使用、修改和分发；" +
                        "若分发衍生作品，需同样以 GPL-3.0 开源并提供完整源码。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ExpandableCard(
                icon = Icons.Default.Code,
                title = "开源许可",
                subtitle = "${OPEN_SOURCE_LICENSES.size} 个依赖",
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OPEN_SOURCE_LICENSES.forEach { (name, license) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = license,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "云音为个人学习与技术研究项目，与网易云音乐官方无关联。" +
                            "音乐数据与版权归原平台及权利人所有，仅供学习交流，请勿用于商业用途。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "NCMusic · Built with Kotlin & Jetpack Compose",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AboutHero(
    versionName: String,
    checking: Boolean,
    checkResult: UpdateChecker.Result?,
    onCheckUpdate: () -> Unit,
    onOpenRepo: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMarkTile(size = 88.dp)
            Spacer(Modifier.height(18.dp))
            Text(
                text = "云音",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "极简 · 轻量的网易云音乐第三方客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onCheckUpdate, enabled = !checking) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("检查更新")
                }
                OutlinedButton(onClick = onOpenRepo) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub")
                }
            }
            when (val result = checkResult) {
                UpdateChecker.Result.UpToDate -> ResultLine("已是最新版本", false)
                is UpdateChecker.Result.Failed -> ResultLine(result.message, true)
                is UpdateChecker.Result.Available -> Unit
                null -> Unit
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "版本 $versionName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.64f),
            )
        }
    }
}

@Composable
private fun ResultLine(message: String, error: Boolean) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (error) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else Md3Motion.Short4),
        label = "aboutChevron",
    )
    val duration = if (reduceMotion) 0 else Md3Motion.Medium2

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Surface(
                onClick = { expanded = !expanded },
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(22.dp).rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(duration)) + fadeIn(tween(duration)),
                exit = shrinkVertically(animationSpec = tween(duration)) + fadeOut(tween(duration)),
            ) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}
