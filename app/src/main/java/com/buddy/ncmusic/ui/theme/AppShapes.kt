package com.buddy.ncmusic.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * 平滑圆角（squircle）形状缓存。
 *
 * [AbsoluteSmoothCornerShape] 用**三次贝塞尔曲线**解析计算圆角过渡，
 * 比 [androidx.compose.foundation.shape.RoundedCornerShape] 的圆弧更柔和，
 * 呈现 iOS / Pixel 那种"超椭圆"观感。
 *
 * 由于贝塞尔计算显著更昂贵，若在 `LazyColumn` 中每项都构造新实例，
 * 首次组合会反复付出代价，因此这里统一缓存单例并复用。
 *
 * 用法：
 * ```
 * Modifier.clip(AppShapes.smooth16)
 * Modifier.background(color, AppShapes.smooth12)
 * ```
 */
object AppShapes {

    private const val SMOOTHNESS = 60

    /** 4dp — 极小的标签、指示器 */
    val smooth4 = AbsoluteSmoothCornerShape(4.dp, SMOOTHNESS)

    /** 8dp — 紧凑标签、小色块 */
    val smooth8 = AbsoluteSmoothCornerShape(8.dp, SMOOTHNESS)

    /** 10dp — 歌词聚焦行的遮罩 */
    val smooth10 = AbsoluteSmoothCornerShape(10.dp, SMOOTHNESS)

    /** 12dp — 列表项、小卡片 */
    val smooth12 = AbsoluteSmoothCornerShape(12.dp, SMOOTHNESS)

    /** 14dp — 封面小图 */
    val smooth14 = AbsoluteSmoothCornerShape(14.dp, SMOOTHNESS)

    /** 16dp — 专辑卡、歌单卡 */
    val smooth16 = AbsoluteSmoothCornerShape(16.dp, SMOOTHNESS)

    /** 20dp — 大卡片 */
    val smooth20 = AbsoluteSmoothCornerShape(20.dp, SMOOTHNESS)

    /** 24dp — 导航栏、对话框 */
    val smooth24 = AbsoluteSmoothCornerShape(24.dp, SMOOTHNESS)

    /** 28dp — 悬浮面板 */
    val smooth28 = AbsoluteSmoothCornerShape(28.dp, SMOOTHNESS)

    /** 32dp — 底部面板、大封面 */
    val smooth32 = AbsoluteSmoothCornerShape(32.dp, SMOOTHNESS)

    /** 胶囊形 — 按钮、MiniPlayer */
    val pill = AbsoluteSmoothCornerShape(50.dp, SMOOTHNESS)

    /**
     * 按圆角半径取最接近的缓存实例，用于圆角可变的场景
     * （避免每次重组都新建一个昂贵形状）。
     */
    fun of(radius: Dp): AbsoluteSmoothCornerShape = when {
        radius <= 4.dp -> smooth4
        radius <= 8.dp -> smooth8
        radius <= 10.dp -> smooth10
        radius <= 12.dp -> smooth12
        radius <= 14.dp -> smooth14
        radius <= 16.dp -> smooth16
        radius <= 20.dp -> smooth20
        radius <= 24.dp -> smooth24
        radius <= 28.dp -> smooth28
        radius <= 32.dp -> smooth32
        else -> pill
    }
}
