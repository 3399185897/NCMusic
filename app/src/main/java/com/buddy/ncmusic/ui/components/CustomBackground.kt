package com.buddy.ncmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

/**
 * 自定义背景图层。
 *
 * ## 为什么是"复制到私有目录"而不是直接用 content://
 *
 * 相册返回的 Uri 只在本次授权内可读，进程重启后往往失效 ——
 * 直接存 Uri 会导致「设置成功、下次打开变空白」。
 * 因此选择时把图片复制进 `filesDir/theme/custom_bg`，之后按文件路径加载。
 *
 * ## 为什么盖一层渐变遮罩
 *
 * 背景图直接铺在内容下面会与文字抢对比度。遮罩用主题 surface 色做
 * 上下渐隐（底部更实、顶部略透），既保留图片氛围，又保证标题/列表可读。
 */
@Composable
fun CustomBackgroundLayer(
    path: String,
    modifier: Modifier = Modifier,
) {
    if (path.isBlank()) return
    val surface = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val model = remember(path) { File(path) }
    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 遮罩：顶部 62%、中部 72%、底部 86% 的表面色 ——
        // 越靠下内容越密（列表/底栏），遮罩随之加重。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0.62f),
                        0.45f to surface.copy(alpha = 0.72f),
                        1f to surface.copy(alpha = 0.86f),
                    ),
                ),
        )
    }
}
