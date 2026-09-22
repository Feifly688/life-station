package com.feiqi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feiqi.AppContainer
import com.feiqi.FeiQiApplication
import kotlinx.coroutines.launch
import com.feiqi.ui.accounting.AccountingScreen
import com.feiqi.ui.accounting.AccountingViewModel
import com.feiqi.ui.components.FeiQiBottomBar
import com.feiqi.ui.health.HealthScreen
import com.feiqi.ui.health.HealthViewModel
import com.feiqi.ui.home.HomeScreen
import com.feiqi.ui.home.HomeViewModel
import com.feiqi.ui.media.MediaScreen
import com.feiqi.ui.media.MediaViewModel
import com.feiqi.ui.schedule.ScheduleScreen
import com.feiqi.ui.schedule.ScheduleViewModel
import com.feiqi.ui.settings.SettingsScreen
import com.feiqi.ui.settings.SettingsViewModel
import com.feiqi.ui.theme.FeiQiTheme

class MainActivity : ComponentActivity() {

    /**
     * 语录集周更入口：**仅当今天是周一且本周还没更新过**时才真正联网（每次新增 5~10 条）。
     * 其余时间调用会立即返回，因此这里挂在 onStart 上没有额外开销。
     * 仓库内部自行切 IO、失败只写日志 —— 不打扰用户、无需任何手动操作。
     */
    override fun onStart() {
        super.onStart()
        val repository = (application as FeiQiApplication).container.quoteRepository
        lifecycleScope.launch {
            runCatching { repository.collectNewQuotesIfDue() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FeiQiApplication).container

        setContent {
            FeiQiTheme {
                var currentRoute by rememberSaveable { mutableStateOf("home") }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        FeiQiBottomBar(
                            currentRoute = currentRoute,
                            onTabSelected = { currentRoute = it }
                        )
                    }
                ) { innerPadding ->
                    val baseModifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)

                    val homeViewModel: HomeViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return HomeViewModel(
                                    container.accountRepository,
                                    container.scheduleRepository,
                                    container.preferencesRepository,
                                    container.mediaRepository,
                                    container.quoteRepository
                                ) as T
                            }
                        }
                    )
                    val accountingViewModel: AccountingViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return AccountingViewModel(
                                    container.accountRepository,
                                    container.preferencesRepository,
                                    container.excelExporter
                                ) as T
                            }
                        }
                    )
                    val scheduleViewModel: ScheduleViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return ScheduleViewModel(
                                    container.scheduleRepository,
                                    container.reminderScheduler
                                ) as T
                            }
                        }
                    )
                    val healthViewModel: HealthViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return HealthViewModel(container.preferencesRepository) as T
                            }
                        }
                    )
                    val mediaViewModel: MediaViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return MediaViewModel(container.mediaRepository) as T
                            }
                        }
                    )
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return SettingsViewModel(
                                    container.accountRepository,
                                    container.preferencesRepository,
                                    container.backupManager
                                ) as T
                            }
                        }
                    )

                    // 设置页是子页面：在设置路由下拦截系统返回键，回到首页而非退出 App（修复 H1）
                    if (currentRoute == "settings") {
                        BackHandler(enabled = true) { currentRoute = "home" }
                    }

                    // 全部 Tab 常驻组合树，仅按当前路由切换显隐（fillMaxSize / size(0.dp)），
                    // 切 Tab 不再把其他页面移出组合树，保留各自的 remember 状态（滚动位置、输入、弹窗等），
                    // 解决切 Tab 后状态丢失、跳回顶部的问题（修复 H2）
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = if (currentRoute == "home") Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onOpenAccounting = { currentRoute = "accounting" },
                                onOpenSchedule = { currentRoute = "schedule" },
                                onOpenHealth = { currentRoute = "health" },
                                onOpenMedia = { currentRoute = "media" },
                                onSettings = { currentRoute = "settings" },
                                modifier = baseModifier
                            )
                        }
                        Box(modifier = if (currentRoute == "accounting") Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            AccountingScreen(
                                viewModel = accountingViewModel,
                                modifier = baseModifier
                            )
                        }
                        Box(modifier = if (currentRoute == "schedule") Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            ScheduleScreen(
                                viewModel = scheduleViewModel,
                                modifier = baseModifier
                            )
                        }
                        Box(modifier = if (currentRoute == "health") Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            HealthScreen(
                                viewModel = healthViewModel,
                                modifier = baseModifier
                            )
                        }
                        Box(modifier = if (currentRoute == "media") Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            MediaScreen(
                                viewModel = mediaViewModel,
                                modifier = baseModifier
                            )
                        }
                        Box(modifier = if (currentRoute == "settings") Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { currentRoute = "home" },
                                modifier = baseModifier
                            )
                        }
                    }
                }
            }
        }
    }
}
