package com.buddy.ncmusic.ui.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 悬浮岛间距的「实时预览」通道。
 *
 * ## 解决的问题
 *
 * 间距滑块此前只在**松手时**落盘，用户在设置页拖动时看不到实际效果，
 * 必须先关掉弹窗回到首页才知道调成了什么样 —— 反馈链路太长，
 * 很容易被理解成"设置不生效"。
 *
 * 这里放一个全局可观察的临时值：拖动时写入，页面上的悬浮岛立刻跟着升降；
 * 松手落盘后清空（`null` 表示"用持久化的值"）。
 *
 * 用 `mutableStateOf` 而不是 StateFlow：它只在组合/布局阶段被读取，
 * 不需要额外的协程与缓冲，改动一次就能在同一帧反映到 `padding`。
 */
object IslandGapPreview {
    /** 拖动中的临时间距（dp）；null = 使用用户保存的值 */
    var live by mutableStateOf<Int?>(null)
}
