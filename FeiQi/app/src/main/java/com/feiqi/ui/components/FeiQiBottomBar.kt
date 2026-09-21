package com.feiqi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.feiqi.R

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

@Composable
fun FeiQiBottomBar(
    currentRoute: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        tonalElevation = androidx.compose.material3.NavigationBarDefaults.Elevation
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
