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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.feiqi.ui.theme.FeiQiElevation
import com.feiqi.ui.theme.FeiQiRadius
import com.feiqi.ui.theme.GlassBorderLight
import com.feiqi.ui.theme.GlassShadowTint
import com.feiqi.ui.theme.GlassStrength

/**
 * 液态玻璃承载面（见 `DESIGN.md` §4）。
 *
 * 由三层叠加而成：
 * 1. **半透明填充**：`Surface` 按 [GlassStrength.fillAlpha] 混合，并叠一道左上更亮、右下更暗的线性渐变，
 *    形成"厚度差"的错觉；
 * 2. **高光边**：1dp 白色描边（alpha 由强度决定），玻璃边缘的折射高光；
 * 3. **暖色阴影**：用 [GlassShadowTint] 而非纯黑，避免发灰、保持暖调。
 *
 * ### 关于背景模糊
 * Compose 原生没有 backdrop blur（`Modifier.blur` 模糊的是自身内容），
 * 所以这里用"半透明 + 渐变 + 高光边"实现**视觉上的磨砂玻璃**，在 minSdk 24 上也能稳定呈现。
 * 将来若引入 `haze` 等库，只需替换本组件实现，调用点无需改动。
 *
 * ### 可读性
 * 玻璃面会透出后方内容，因此内容色请用 `onSurface`（深棕）而非纯黑；
 * 若玻璃面叠加在图片/彩色内容上，请把 [strength] 升一档（[GlassStrength.Thin] → [GlassStrength.Regular]）。
 *
 * @param interactive 是否为可点击面（可点击面阴影更浅，避免"糊"）。
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
    val surface = MaterialTheme.colorScheme.surface
    val fillTop = surface.copy(alpha = (strength.fillAlpha + 0.06f).coerceAtMost(1f))
    val fillBottom = surface.copy(alpha = strength.fillAlpha)

    Box(
        modifier = modifier
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = if (interactive) FeiQiElevation.raised else FeiQiElevation.floating,
                        shape = shape,
                        clip = false,
                        ambientColor = GlassShadowTint.copy(alpha = 0.10f),
                        spotColor = GlassShadowTint.copy(alpha = 0.14f)
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
            // 高光边：外层 1dp 白边 + 内层更亮的顶部高光，模拟玻璃折射
            .border(1.dp, GlassBorderLight.copy(alpha = strength.borderAlpha), shape)
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    listOf(GlassBorderLight.copy(alpha = strength.highlightAlpha), Color.Transparent)
                ),
                shape = shape
            ),
        content = content
    )
}

/**
 * 玻璃高光边（1dp 白边 + 顶部 0.5dp 更亮的高光），用于**自身已是不透明容器**的面
 * （如 AlertDialog：容器色由 Material 组件提供，只需补边缘高光）。
 */
@Composable
fun Modifier.glassEdge(
    shape: Shape = RoundedCornerShape(FeiQiRadius.xl),
    strength: GlassStrength = GlassStrength.Thick
): Modifier = this
    .border(1.dp, GlassBorderLight.copy(alpha = strength.borderAlpha), shape)
    .border(
        width = 0.5.dp,
        brush = Brush.verticalGradient(
            listOf(GlassBorderLight.copy(alpha = strength.highlightAlpha), Color.Transparent)
        ),
        shape = shape
    )

/** 玻璃面上的文字/图标统一用这个颜色（深棕，避免玻璃透底后对比度不足）。 */
@Composable
fun glassContentColor(): Color = MaterialTheme.colorScheme.onSurface
