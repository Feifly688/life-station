package com.feiqi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.feiqi.R
import com.feiqi.ui.theme.GlassBorderLight
import com.feiqi.ui.theme.GlassStrength
import com.feiqi.ui.theme.GlassShadowTint

sealed class BottomTab(val route: String, val labelRes: Int, val icon: @Composable () -> Unit) {
    data object Home : BottomTab("home", R.string.tab_home, { Icon(Icons.Default.Home, contentDescription = null) })
    data object Accounting : BottomTab("accounting", R.string.tab_accounting, { Icon(Icons.Default.Wallet, contentDescription = null) })
    data object Schedule : BottomTab("schedule", R.string.tab_schedule, { Icon(Icons.Default.CalendarMonth, contentDescription = null) })
    data object Health : BottomTab("health", R.string.tab_health, { Icon(Icons.Default.MonitorWeight, contentDescription = null) })
    data object Media : BottomTab("media", R.string.tab_media, { Icon(Icons.Default.Bookmark, contentDescription = null) })
}

val bottomTabs = listOf(
    BottomTab.Home,
    BottomTab.Accounting,
    BottomTab.Schedule,
    BottomTab.Health,
    BottomTab.Media
)

/**
 * 底部导航栏（液态玻璃，见 `DESIGN.md` §4/§6）。
 *
 * 结构：**玻璃底（上淡下浓的竖直渐变）+ 顶部 1dp 高光边 + 透明 NavigationBar**。
 * 不直接把 `NavigationBar` 的 containerColor 设成半透明，是因为 M3 只接受纯色，
 * 而玻璃感来自"上缘更透、下缘更实"的渐变；把渐变画在底层、让 NavigationBar 透明叠上去，
 * 既保留 M3 的手势/无障碍/insets 行为，又能拿到玻璃观感。
 */
@Composable
fun FeiQiBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    // 暖调玻璃基色（纯白在米白底上"看不见"，必须带暖调才显出玻璃感）
    val glassFill = lerp(scheme.surface, scheme.secondary, 0.16f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 顶部抬升阴影：导航栏读起来是"浮起来的玻璃条"，而不是贴平的一块白
            .shadow(
                elevation = 12.dp,
                shape = RectangleShape,
                clip = false,
                ambientColor = GlassShadowTint.copy(alpha = 0.18f),
                spotColor = GlassShadowTint.copy(alpha = 0.24f)
            )
    ) {
        // 玻璃底：上缘更透（隐约透出内容），下缘更实（保证标签可读）
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            glassFill.copy(alpha = GlassStrength.Thin.fillAlpha - 0.14f),
                            glassFill.copy(alpha = 0.95f)
                        )
                    )
                )
        )
        // 顶部双色调边：上白高光 + 下淡暗边，做出玻璃断面
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            GlassBorderLight.copy(alpha = GlassStrength.Regular.borderAlpha),
                            scheme.onSurface.copy(alpha = 0.06f)
                        )
                    )
                )
        )

        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            bottomTabs.forEach { tab ->
                NavigationBarItem(
                    selected = currentRoute == tab.route,
                    onClick = { onTabSelected(tab.route) },
                    icon = tab.icon,
                    label = { Text(stringResource(tab.labelRes)) }
                )
            }
        }
    }
}
