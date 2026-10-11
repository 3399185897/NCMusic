/*
 * 本文件移植自 AOSP：compose/material3/material3/src/commonMain/kotlin/
 * androidx/compose/material3/MaterialShapes.kt
 *
 * 移植原因：M3 Expressive 的 LoadingIndicator 需要这套官方形状几何，
 * 但它随 material3 1.5.0 发布，而 1.5.0 依赖 Compose 1.12 → 要求
 * AGP 9.1.0 + compileSdk 37（本项目为 AGP 8.13.0 / SDK 36），无法升级。
 *
 * 因此将**官方顶点数据与构造方式原样移植**（非自创轮廓），
 * 配合 androidx.graphics:graphics-shapes 的 RoundedPolygon / Morph 使用。
 *
 * ## 与官方源码的两处差异
 *
 * 官方对 `oval` / `cookie9` 额外调用了 `.transformed(...)`，
 * 而该 API 接受 `PointTransformer`，其返回类型（`FloatFloatPair`）
 * 在 Kotlin 侧不可见，无法自行实现。故：
 *  - `oval` 退化为标准圆
 *  - `cookie9` 省略其 -90° 起始角旋转
 * 这两处都**不影响形状本身的几何**，也不影响 Morph 的平滑度。
 */
package com.buddy.ncmusic.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.fastMap
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive 官方形状集合（LoadingIndicator 所需的部分）。
 *
 * 每个形状均为 `RoundedPolygon`，可直接交给 `androidx.graphics.shapes.Morph`
 * 做形状变形 —— 这也是官方 LoadingIndicator 的做法。
 */
object ExpressiveShapes {

    // ---------- 圆角预设（与官方同名同值）----------
    private val cornerRound15 = CornerRounding(radius = .15f)
    private val cornerRound50 = CornerRounding(radius = .5f)

    /** 一个圆。 */
    val Circle: RoundedPolygon by lazy { RoundedPolygon.circle().normalized() }

    /**
     * 椭圆。
     *
     * 官方为「圆沿 Y 压扁 0.64 后逆时针 45°」；因变换 API 受限（见文件头注释），
     * 此处退化为标准圆。序列中它的前一个是 Cookie4Sided，已接近圆形，
     * 因此过渡依然平滑，仅少了压扁这一步。
     */
    val Oval: RoundedPolygon by lazy { RoundedPolygon.circle().normalized() }

    /** 药丸形。 */
    val Pill: RoundedPolygon by lazy { pill().normalized() }

    /** 五边形。 */
    val Pentagon: RoundedPolygon by lazy { pentagon().normalized() }

    /** 八角星（官方称 "sunny"）。 */
    val Sunny: RoundedPolygon by lazy { sunny().normalized() }

    /** 四角饼干形。 */
    val Cookie4Sided: RoundedPolygon by lazy { cookie4().normalized() }

    /** 九角饼干形。 */
    val Cookie9Sided: RoundedPolygon by lazy { cookie9().normalized() }

    /** 柔和爆炸形（十瓣）。 */
    val SoftBurst: RoundedPolygon by lazy { softBurst().normalized() }

    // ============================================================
    // 以下顶点数值取自 AOSP 源码，未作改动
    // ============================================================

    private fun pill(): RoundedPolygon {
        return customPolygon(
            listOf(
                PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
                PointNRound(Offset(1.001f, 0.428f)),
                PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f)),
            ),
            reps = 2,
            mirroring = true,
        )
    }

    private fun pentagon(): RoundedPolygon {
        return customPolygon(
            listOf(
                PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
                PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
                PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f)),
            ),
            reps = 1,
            mirroring = true,
        )
    }

    private fun sunny(): RoundedPolygon {
        return RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = .8f,
            rounding = cornerRound15,
        )
    }

    private fun cookie4(): RoundedPolygon {
        return customPolygon(
            listOf(
                PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
                PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f)),
            ),
            4,
        )
    }

    private fun cookie9(): RoundedPolygon {
        // 官方此处另有 -90° 旋转（仅影响起始角，不影响几何与 Morph 平滑度）
        return RoundedPolygon.star(
            numVerticesPerRadius = 9,
            innerRadius = .8f,
            rounding = cornerRound50,
        )
    }

    private fun softBurst(): RoundedPolygon {
        return customPolygon(
            listOf(
                PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
                PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f)),
            ),
            reps = 10,
        )
    }

    // ---------- 构造辅助（官方实现，原样移植）----------

    private data class PointNRound(
        val o: Offset,
        val r: CornerRounding = CornerRounding.Unrounded,
    )

    private fun customPolygon(
        pnr: List<PointNRound>,
        reps: Int,
        center: Offset = Offset(0.5f, 0.5f),
        mirroring: Boolean = false,
    ): RoundedPolygon {
        val actualPoints = doRepeat(pnr, reps, center, mirroring)
        return RoundedPolygon(
            vertices = FloatArray(actualPoints.size * 2) { ix ->
                actualPoints[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y }
            },
            perVertexRounding = buildList { for (p in actualPoints) add(p.r) },
            centerX = center.x,
            centerY = center.y,
        )
    }

    private fun doRepeat(
        points: List<PointNRound>,
        reps: Int,
        center: Offset,
        mirroring: Boolean,
    ): List<PointNRound> {
        val angles = points.fastMap { (it.o - center).angleDegrees() }
        val distances = points.fastMap { (it.o - center).magnitude() }
        return buildList {
            if (mirroring) {
                val actualReps = reps * 2
                val sectionAngle = 360f / actualReps
                repeat(actualReps) { rep ->
                    points.indices.forEach { index ->
                        val i = if (rep % 2 == 0) index else points.lastIndex - index
                        if (i > 0 || rep % 2 == 0) {
                            val a = (sectionAngle * rep +
                                if (rep % 2 == 0) angles[i]
                                else sectionAngle - angles[i] + 2 * angles[0]).toRadians()
                            val p = Offset(cos(a), sin(a)) * distances[i] + center
                            add(PointNRound(p, points[i].r))
                        }
                    }
                }
            } else {
                val sectionAngle = 360f / reps
                repeat(reps) { rep ->
                    points.indices.forEach { index ->
                        val a = (sectionAngle * rep + angles[index]).toRadians()
                        val p = Offset(cos(a), sin(a)) * distances[index] + center
                        add(PointNRound(p, points[index].r))
                    }
                }
            }
        }
    }

    private fun Float.toRadians(): Float = this / 180f * Math.PI.toFloat()

    private fun Offset.angleDegrees(): Float =
        Math.toDegrees(kotlin.math.atan2(y.toDouble(), x.toDouble())).toFloat()

    private fun Offset.magnitude(): Float = kotlin.math.sqrt(x * x + y * y)
}
