package com.feiqi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.feiqi.ui.theme.FeiQiElevation
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.GlassBorderLight
import com.feiqi.ui.theme.GlassShadowTint
import com.feiqi.ui.theme.GlassStrength

/**
 * 液态玻璃承载面（见 `DESIGN.md` §4）。
 *
 * ## 为什么之前"看不到效果"
 * 米白页面底（`Background #FFFBF5`）+ 纯白半透明填充（`Surface #FFFFFF`）**明度几乎相同**，
 * 无论怎么调 alpha 都是"白叠白"，视觉上等于没变。所以这里做三件事让玻璃**真的看得见**：
 * 1. **暖调填充**：`Surface` 与 `Secondary`（沙色）按 12% 混合 → 偏暖的"奶玻璃"，与纯白底拉开可辨差异；
 *    填充走对角渐变（左上更亮更实 → 右下更暖更透），做出厚度差；
 * 2. **斜向高光（sheen）**：表面覆一道对角反光带，模拟玻璃的定向反射；
 * 3. **双色调边**：左上白高光 + 右下极淡暗边，形成"玻璃断面"，比单色描边立体得多。
 *
 * ## 关于背景模糊
 * Compose 原生没有 backdrop blur（`Modifier.blur` 模糊的是自身内容，不是身后画面），
 * 因此这是**视觉磨砂**而非真实折射：全 API 一致（minSdk 24 可呈现）、无额外性能开销。
 * 将来若引入 `haze` 等库，只需替换本组件实现，所有调用点不用改。
 *
 * @param interactive 可点击面用更浅的阴影，避免"糊"在背景上。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(FeiQiRadius.lg),
    strength: GlassStrength = GlassStrength.Regular,
    elevated: Boolean = true,
    interactive: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val glassFill = lerp(scheme.surface, scheme.secondary, 0.12f)   // 暖调玻璃基色
    val ink = scheme.onSurface

    Box(
        modifier = modifier
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = if (interactive) FeiQiElevation.floating else FeiQiElevation.modal,
                        shape = shape,
                        clip = false,
                        ambientColor = GlassShadowTint.copy(alpha = 0.16f),
                        spotColor = GlassShadowTint.copy(alpha = 0.22f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            // ① 主体填充：对角渐变
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        glassFill.copy(alpha = (strength.fillAlpha + 0.12f).coerceAtMost(1f)),
                        glassFill.copy(alpha = (strength.fillAlpha - 0.06f).coerceAtLeast(0.35f))
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            // ② 斜向高光：集中在左上，向右下淡出
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        GlassBorderLight.copy(alpha = strength.sheenAlpha),
                        GlassBorderLight.copy(alpha = strength.sheenAlpha * 0.25f),
                        Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            // ③ 双色调边：左上高光 + 右下暗边
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassBorderLight.copy(alpha = strength.borderAlpha),
                        ink.copy(alpha = 0.07f)
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * 给**已有不透明容器**（如 `AlertDialog` 的 Surface）补玻璃边。
 * 与 [GlassSurface] 同一套边缘语言：左上白高光 + 右下淡暗边。
 */
@Composable
fun Modifier.glassEdge(
    shape: Shape = RoundedCornerShape(FeiQiRadius.xl),
    strength: GlassStrength = GlassStrength.Thick
): Modifier = this.border(
    width = 1.dp,
    brush = Brush.linearGradient(
        colors = listOf(
            GlassBorderLight.copy(alpha = strength.borderAlpha),
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    ),
    shape = shape
)

/**
 * 玻璃容器色：给使用自身容器色 API 的组件（如 `AlertDialog.containerColor`）复用，
 * 保证与 [GlassSurface] 的暖调基色一致 —— 纯白半透明在米白底上"看不见"，暖调才能显出玻璃感。
 */
@Composable
fun glassContainerColor(strength: GlassStrength = GlassStrength.Thick): Color =
    lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.secondary, 0.12f)
        .copy(alpha = strength.fillAlpha)

/** 玻璃面上的文字/图标统一用这个颜色（深棕，避免玻璃透底后对比度不足）。 */
@Composable
fun glassContentColor(): Color = MaterialTheme.colorScheme.onSurface
