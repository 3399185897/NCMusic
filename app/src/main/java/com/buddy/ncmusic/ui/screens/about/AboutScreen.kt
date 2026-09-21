package com.buddy.ncmusic.ui.screens.about

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
private const val PROJECT_REPO_URL = "private const val PROJECT_REPO_URL = "https://github.com/3399185897/NCMusic""

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
        }.getOrNull().orEmpty().ifBlank { "1.0.0" }
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
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)),
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
                    text = "版本 $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---------- 项目仓库（预留） ----------
            SectionTitle("项目仓库")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
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
