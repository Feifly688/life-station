package com.feiqi.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 设计令牌（Design Tokens）——**唯一的视觉数值来源**，见仓库根目录 `DESIGN.md`。
 *
 * 业务代码里不要再写裸数值（`16.dp`、`RoundedCornerShape(20.dp)`），
 * 一律取这里的令牌，改规范时只改一处、全局一致。
 */

/** 间距：4 的倍数，页面左右边距固定用 [lg]。 */
object FeiQiSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** 圆角：嵌套时内层 = 外层 − 内边距。 */
object FeiQiRadius {
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 28.dp
}

/** 阴影层级：卡片默认 [card]，浮层用 [floating]，弹窗用 [modal]。 */
object FeiQiElevation {
    val none = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val floating = 8.dp
    val modal = 12.dp
}

/** 动效时长：只对状态变化使用，不做装饰性动画。 */
object FeiQiMotion {
    const val fast = 120
    const val normal = 200
    const val slow = 320
}

/**
 * 液态玻璃强度。数值含义见 `DESIGN.md` §4：
 * - [fillAlpha]：半透明填充不透明度（越大越不透、可读性越好）；
 * - [borderAlpha]：1dp 高光边的不透明度；
 * - [highlightAlpha]：顶部内高光（模拟折射）的不透明度；
 * - [sheenAlpha]：斜向反光带（sheen）的不透明度 —— 让玻璃在浅色底上也能被看见；
 * - [blurRadius]：阴影模糊半径，越大越"浮"。
 */
enum class GlassStrength(
    val fillAlpha: Float,
    val borderAlpha: Float,
    val highlightAlpha: Float,
    val sheenAlpha: Float,
    val blurRadius: Int
) {
    /** 轻：贴在内容上方但信息密度高（如导航栏、海报卡）。 */
    Thin(0.55f, 0.55f, 0.34f, 0.26f, 16),

    /** 常规：多数浮层/卡片（默认）。 */
    Regular(0.68f, 0.62f, 0.40f, 0.20f, 20),

    /** 厚：需要强可读性的面（弹窗、表单面板）。 */
    Thick(0.82f, 0.68f, 0.44f, 0.15f, 24)
}

/** 玻璃面的高光边颜色：暖白而非纯白，避免发灰。 */
val GlassBorderLight = Color(0xFFFFFFFF)

/** 玻璃阴影用暖色（取自 OnSurface），比纯黑柔和。 */
val GlassShadowTint = Color(0xFF453C34)

/** MaterialTheme.shapes 全局形状：让既有组件自动符合规范。 */
val FeiQiShapes = Shapes(
    extraSmall = RoundedCornerShape(FeiQiRadius.sm),
    small = RoundedCornerShape(FeiQiRadius.sm),
    medium = RoundedCornerShape(FeiQiRadius.md),
    large = RoundedCornerShape(FeiQiRadius.lg),
    extraLarge = RoundedCornerShape(FeiQiRadius.xl)
)
