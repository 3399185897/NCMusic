package com.buddy.ncmusic.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 播放控制图标（Pause / SkipNext / SkipPrevious）。
 *
 * 这三个图标不在 material-icons-core 基础集中，
 * 为避免引入 material-icons-extended（约 +25MB 依赖），
 * 此处直接内嵌 Material 官方标准矢量路径，保持 APK 轻量。
 */

private var pause: ImageVector? = null
val Icons.Filled.Pause: ImageVector
    get() {
        if (pause != null) return pause!!
        pause = materialIcon(name = "Filled.Pause") {
            materialPath {
                // M6 19h4V5H6v14z
                moveTo(6.0f, 19.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(6.0f)
                verticalLineTo(19.0f)
                close()
                // M14 5v14h4V5h-4z
                moveTo(14.0f, 5.0f)
                verticalLineTo(19.0f)
                horizontalLineTo(18.0f)
                verticalLineTo(5.0f)
                horizontalLineTo(14.0f)
                close()
            }
        }
        return pause!!
    }

private var skipNext: ImageVector? = null
val Icons.Filled.SkipNext: ImageVector
    get() {
        if (skipNext != null) return skipNext!!
        skipNext = materialIcon(name = "Filled.SkipNext") {
            materialPath {
                // M6 18l8.5-6L6 6v12z
                moveTo(6.0f, 18.0f)
                lineToRelative(8.5f, -6.0f)
                lineTo(6.0f, 6.0f)
                verticalLineTo(18.0f)
                close()
                // M16 6v12h2V6h-2z
                moveTo(16.0f, 6.0f)
                verticalLineTo(18.0f)
                horizontalLineTo(18.0f)
                verticalLineTo(6.0f)
                horizontalLineTo(16.0f)
                close()
            }
        }
        return skipNext!!
    }

private var skipPrevious: ImageVector? = null
val Icons.Filled.SkipPrevious: ImageVector
    get() {
        if (skipPrevious != null) return skipPrevious!!
        skipPrevious = materialIcon(name = "Filled.SkipPrevious") {
            materialPath {
                // M6 6h2v12H6z
                moveTo(6.0f, 6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(18.0f)
                horizontalLineTo(6.0f)
                close()
                // M9.5 12l8.5 6V6z
                moveTo(9.5f, 12.0f)
                lineToRelative(8.5f, 6.0f)
                verticalLineTo(6.0f)
                close()
            }
        }
        return skipPrevious!!
    }

private var mic: ImageVector? = null
val Icons.Filled.Mic: ImageVector
    get() {
        if (mic != null) return mic!!
        mic = ImageVector.Builder(
            name = "Filled.Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = addPathNodes(
                    "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28c3.28,-0.49 6,-3.31 6,-6.72h-1.7z",
                ),
                fill = SolidColor(Color.Black),
            )
        }.build()
        return mic!!
    }

private fun simpleIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
    }.build()

private var repeat: ImageVector? = null
val Icons.Filled.Repeat: ImageVector
    get() {
        if (repeat == null) {
            repeat = simpleIcon(
                "Filled.Repeat",
                "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4z",
            )
        }
        return repeat!!
    }

private var repeatOne: ImageVector? = null
val Icons.Filled.RepeatOne: ImageVector
    get() {
        if (repeatOne == null) {
            repeatOne = simpleIcon(
                "Filled.RepeatOne",
                "M7,7h10v3l4,-4 -4,-4v3H5v6h2V7zM17,17H7v-3l-4,4 4,4v-3h12v-6h-2v4zM13,15V9h-1l-2,1v1h1.5v4H13z",
            )
        }
        return repeatOne!!
    }

private var shuffle: ImageVector? = null
val Icons.Filled.Shuffle: ImageVector
    get() {
        if (shuffle == null) {
            shuffle = simpleIcon(
                "Filled.Shuffle",
                "M10.59,9.17 5.41,4 4,5.41l5.17,5.17 1.42,-1.41zM14.5,4l2.04,2.04L4,18.59 5.41,20 17.96,7.46 20,9.5V4h-5.5zM14.83,14.83l-1.41,1.41 3.13,3.13L14.5,22H20v-5.5l-2.04,2.04 -3.13,-3.13z",
            )
        }
        return shuffle!!
    }

private var queueMusic: ImageVector? = null
val Icons.Filled.QueueMusic: ImageVector
    get() {
        if (queueMusic == null) {
            queueMusic = simpleIcon(
                "Filled.QueueMusic",
                "M15,6H3v2h12V6zM15,10H3v2h12v-2zM3,16h8v-2H3v2zM17,6v8.18c-0.31,-0.11 -0.65,-0.18 -1,-0.18 -1.66,0 -3,1.34 -3,3s1.34,3 3,3 3,-1.34 3,-3V8h3V6h-5z",
            )
        }
        return queueMusic!!
    }
