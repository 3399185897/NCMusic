package com.buddy.ncmusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import com.buddy.ncmusic.ui.util.Md3Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * 药丸式悬浮底栏。
 *
 * 设计参考 SukiSU / KernelSU-Next 的 Material 底栏（`BottomBarMaterial.kt`）：
 * 悬浮圆形药丸 + **选中项横向展开显示文字**。这是它最有辨识度的动效 ——
 * 未选中只有图标，选中后文字从图标右侧展开、胶囊随之加宽，切换时呈"拉伸"感。
 *
 * 关键参数取自其实现实测值：
 * ```
 * 容器        CircleShape 药丸，height 64dp，shadowElevation 6dp
 * 悬浮边距    水平 24dp / 垂直 12dp，widthIn(max = 480.dp)
 * 内边距      未选中 12dp → 选中 16dp
 * 文字        只在选中时显示，labelLarge + Bold
 * ```
 *
 * @param items 底栏项（至少 2 项）
 * @param selectedIndex 当前选中项索引
 * @param onSelect 点击某项
 */
@Composable
fun FloatingBottomBar(
    items: List<FloatingBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    // 底部内边距：有导航栏手势区时贴得紧一些，否则留足悬浮感
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // 水平边距与 MiniPlayer 保持一致（同为 16dp），
            // 否则两者宽度不同，悬浮岛与药丸左右错开，观感突兀。
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 480.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    FloatingBarItemContent(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { if (index != selectedIndex) onSelect(index) },
                    )
                }
            }
        }
    }
}

/** 底栏单项数据 */
data class FloatingBarItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * 单个底栏项：胶囊 + 图标 + 展开的标签。
 *
 * ## 与 SukiSU 逐项对齐
 *
 * 直接采用其 `BottomBarMaterial.kt` 的结构，而不是"自己更聪明的写法"：
 * ```
 * Row(
 *   Modifier.height(48.dp).clip(CircleShape).background(containerColor)
 *     .selectable(..., role = Role.Tab)
 *     .animateContentSize()                       // 默认 spring(400, 1.0)
 *     .padding(if (selected) 16.dp else 12.dp)   // 固定的两档，不做动画
 * ) { Icon; AnimatedVisibility(fade+expandHorizontally) { Text } }
 * ```
 *
 * 之前我们自己实现过两版，都不如它顺：
 *  · 第一版叠了 `animateDpAsState` 内边距（0.15 阻尼），与展开动画互相追目标值 → 抖动
 *  · 第二版用单一 progress 线性插值宽度，尺寸变化没有弹簧，起停生硬
 *
 * 关键细节：**未选中底色必须是 `surfaceContainerHighest` 而不是透明**。
 * 透明 → primary 的插值中途会出现"半透明红"，看起来发灰；
 * 不透明色之间插值才是 SukiSU 那种饱满的过渡。
 */
@Composable
private fun FloatingBarItemContent(
    item: FloatingBarItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            // 与药丸同色 → 视觉上"无底色"，但颜色插值全程不透明
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "bottom_bar_item_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bottom_bar_item_content",
    )
    // 选中气泡的"弹起"感：整颗胶囊轻微放大。
    //
    // SukiSU 的 Material 版底栏其实只有颜色 + 文字展开，没有缩放；
    // 它的 Miuix(liquid) 版才有指示器的形变。这里补一层极小的缩放
    // （1 → 1.07），配合 spring 的回弹，让"选中"像被手指按出来一样有实体感，
    // 同时幅度足够克制 —— 再大就会与相邻项抢位置。
    // 放大倍数 1.12：再小就看不出来（1.07 实测在 48dp 高的胶囊上只差 3dp，
    // 与"文字展开"带来的宽度变化混在一起，几乎无感）。
    // 胶囊外层容器高 64dp、内胶囊 48dp，还有 8dp 余量 —— 1.12 后
    // 上下各溢出约 2.9dp，仍在容器内，不会顶破药丸。
    val pillScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = 0.42f,
            stiffness = 620f,
        ),
        label = "bottom_bar_pill_scale",
    )

    // 按下时再叠一层"压扁"：和选中放大**相乘**，不能互相覆盖。
    // 未选中项原本只有涟漪，在大药丸上没有体积变化；
    // 加 0.92 的按压后，手指落下就有实体反馈，与选中弹起连成一条连续动作。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            // 按下要跟手（刚度高），松手要有余韵（刚度低）
            dampingRatio = 0.6f,
            stiffness = if (pressed) 1400f else 520f,
        ),
        label = "bottom_bar_press",
    )

    CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor,
    ) {
        Row(
            modifier = Modifier
                // 缩放放在 clip 之前：整颗胶囊（底色 + 图标 + 文字）一起放大，
                // 而不是内容在固定尺寸里被拉伸
                .graphicsLayer {
                    val s = pillScale * pressScale
                    scaleX = s
                    scaleY = s
                }
                .height(48.dp)
                .clip(CircleShape)
                .background(containerColor)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    // indication 用主题默认（Material3 的 Ripple）：
                    // rememberRipple 已废弃，且它返回的旧实现与新 Indication API 不兼容
                    onClick = onClick,
                    role = androidx.compose.ui.semantics.Role.Tab,
                )
                // ⚠️ 这里**不能**再用 animateContentSize。
                //
                // 选中态的文字是用 AnimatedVisibility(expandHorizontally) 展开的，
                // 宽度变化已经由它驱动；外面再套一层 animateContentSize 会对同一段
                // 宽度做第二次动画 —— 每帧 measure 两遍，而且两条曲线的进度不同步，
                // 视觉上就是"胶囊变宽时抖一下"。固定 padding 同理：
                // 按选中态切换 12/16dp 会引起一次额外的瞬时测量。
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 实心/描边图标之间做交叉淡入，而不是硬切：
            // 切换时图标形状本来就不同，直接替换会"闪"一下。
            // 用 AnimatedContent 的 sizeTransform=null 避免动画期间改变宽度。
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    fadeIn(tween(Md3Motion.Short3)) togetherWith
                        fadeOut(tween(Md3Motion.Short2))
                },
                label = "bottom_bar_icon",
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = if (isSelected) null else item.label,
                    modifier = Modifier.size(24.dp),
                )
            }
            // 退出必须比进入快：
            // 切换标签时旧标签与新标签会同时存在，若两者都用默认 spring，
            // 旧标签 linger 近 300ms，与正在展开的新标签在同一行里挤在一起
            // （截图里能看到「推荐」「发现」两个词叠在药丸上）。
            AnimatedVisibility(
                visible = selected,
                // 展开曲线与胶囊放大用同一条 spring：
                // 原来 pill 是弹性回弹（spring 0.42/620）、文字是线性缓动（tween 230），
                // 两条时间轴不同步 —— 观感上是"胶囊先胀开、文字后挤进来"的两段动作。
                // 阻尼取 0.62：有轻微回弹（与胶囊呼应），又不至于过冲把文字挤掉。
                enter = fadeIn(tween(150)) + expandHorizontally(
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 620f),
                ),
                exit = fadeOut(tween(80)) + shrinkHorizontally(
                    animationSpec = tween(110, easing = Md3Motion.EmphasizedAccelerate),
                ),
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
