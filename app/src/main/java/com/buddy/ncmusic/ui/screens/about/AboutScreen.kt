package com.buddy.ncmusic.ui.screens.about

import kotlinx.coroutines.launch
import com.buddy.ncmusic.ui.components.ExpressiveLoadingIndicator
import com.buddy.ncmusic.data.update.UpdateChecker
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import android.os.Build
import com.buddy.ncmusic.ui.theme.AppShapes
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buddy.ncmusic.ui.components.CoverImage
import com.buddy.ncmusic.ui.components.SectionTitle

/**
 * 项目仓库地址。
 *
 * 预留：后续填入实际仓库地址后，下方「项目仓库」卡片会自动变为可点击跳转；
 * 留空时显示"待补充"。
 */
private const val PROJECT_REPO_URL = "https://github.com/3399185897/NCMusic"

/** 开源依赖及其许可（均随项目实际依赖梳理） */
private val OPEN_SOURCE_LICENSES = listOf(
    "Jetpack Compose (UI / Foundation / Material3)" to "Apache-2.0",
    "AndroidX Core KTX / Activity / Lifecycle" to "Apache-2.0",
    "AndroidX Navigation Compose" to "Apache-2.0",
    "AndroidX Media3 (ExoPlayer / Session)" to "Apache-2.0",
    "AndroidX DataStore" to "Apache-2.0",
    "AndroidX Palette" to "Apache-2.0",
    "Kotlin / Kotlin Coroutines" to "Apache-2.0",
    "KotlinX Serialization" to "Apache-2.0",
    "OkHttp" to "Apache-2.0",
    "Coil" to "Apache-2.0",
    "ZXing (条码/二维码)" to "Apache-2.0",
)

/**
 * 关于页面：应用信息 + 项目仓库 + 开源许可 + 声明。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.1.0" }
    }

    // ---------- 检查更新 ----------
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    /** 打开浏览器跳转到 Release 页面 */
    fun openRelease(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun runCheck() {
        if (checking) return
        checking = true
        checkResult = null
        scope.launch {
            val r = UpdateChecker.check(versionName)
            checkResult = r
            checking = false
            if (r is UpdateChecker.Result.Available) showUpdateDialog = true
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
                .padding(bottom = 32.dp),
        ) {
            // ---------- 应用信息 ----------
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoverImage(
                    url = null,
                    modifier = Modifier.size(76.dp).clip(AppShapes.of(20.dp)),
                    contentDescription = "应用图标",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "NCMusic",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "版本 v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))

                // 检查更新：检查结果就地以文字反馈，
                // 有新版时另弹对话框提供「前往下载」
                OutlinedButton(onClick = { runCheck() }, enabled = !checking) {
                    if (checking) {
                        ExpressiveLoadingIndicator(modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("检查中…")
                    } else {
                        Text("检查更新")
                    }
                }

                // 无更新 / 失败时就地反馈（有更新走对话框，此处不重复提示）
                val hint = when (val r = checkResult) {
                    is UpdateChecker.Result.UpToDate -> "已是最新版本"
                    is UpdateChecker.Result.Failed -> r.message
                    else -> null
                }
                hint?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (checkResult is UpdateChecker.Result.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ---------- 发现新版本 ----------
            val available = (checkResult as? UpdateChecker.Result.Available)?.info
            if (showUpdateDialog && available != null) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("发现新版本 ${available.tag}") },
                    text = {
                        // 更新说明可能很长，限制在可滚动区域内
                        Column(
                            modifier = Modifier
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = "当前版本 v$versionName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (available.notes.isNotBlank()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = available.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            openRelease(available.htmlUrl)
                        }) { Text("前往下载") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("稍后") }
                    },
                )
            }

            // ---------- 构建信息 ----------
            // 让用户（与 issue 反馈者）能一眼看到运行环境，便于定位问题
            SectionTitle("构建信息")
            Column(Modifier.padding(horizontal = 16.dp)) {
                InfoRow("应用包名", "com.buddy.ncmusic")
                InfoRow("技术栈", "Kotlin · Jetpack Compose · Material 3 · Media3")
                InfoRow(
                    "运行环境",
                    "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· " +
                        "${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
                )
                InfoRow("设计规范", "Material Design 3 · 支持莫奈动态取色")
            }

            // ---------- 项目仓库（预留） ----------
            SectionTitle("项目仓库")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(AppShapes.of(14.dp))
                    .clickable(enabled = PROJECT_REPO_URL.isNotBlank()) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_REPO_URL))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "开源仓库",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = PROJECT_REPO_URL.ifBlank { "待补充" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (PROJECT_REPO_URL.isNotBlank()) {
                    Text(
                        "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------- 项目许可 ----------
            SectionTitle("项目许可")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "本项目（GPL-3.0）",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "GPL-3.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "可自由使用、修改、分发；衍生作品须同样以 GPL-3.0 开源并提供完整源码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 开源许可 ----------
            SectionTitle("开源许可")
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "本应用使用了以下开源项目，在此致谢：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OPEN_SOURCE_LICENSES.forEach { (name, license) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = license,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // ---------- 声明 ----------
            Spacer(Modifier.height(24.dp))
            Text(
                text = "本应用为个人学习与技术研究项目，音乐数据来源于网易云音乐开放接口，" +
                    "版权归原平台及版权方所有，仅供学习交流，请勿用于商业用途。",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 构建信息行：左标签右取值 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
        )
    }
}
