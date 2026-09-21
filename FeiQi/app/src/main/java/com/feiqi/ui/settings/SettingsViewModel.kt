package com.feiqi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.ExportData
import com.feiqi.data.repository.AccountRepository
import com.feiqi.data.repository.PreferencesRepository
import com.feiqi.data.repository.QuoteRepository
import com.feiqi.data.repository.QuoteState
import com.feiqi.utils.AppConfig
import com.feiqi.utils.BackupManager
import com.feiqi.utils.NotificationUtils
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val notificationEnabled: Boolean = true,
    val accountCount: Int = 0,
    val busy: Boolean = false
)

class SettingsViewModel(
    private val accountRepository: AccountRepository,
    private val preferencesRepository: PreferencesRepository,
    private val backupManager: BackupManager,
    private val quoteRepository: QuoteRepository
) : ViewModel() {

    /** 语录集状态：来源（内置/缓存/远程）、条数、最近更新时间、错误信息。 */
    val quoteState: StateFlow<QuoteState> = quoteRepository.state

    /** 手动更新语录集（强制联网，不受 7 天周期限制）。 */
    fun refreshQuotes() {
        viewModelScope.launch {
            val result = quoteRepository.refresh(force = true)
            _events.emit(
                if (result.success) result.message else "语录更新失败：${result.message}"
            )
        }
    }

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val _busy = MutableStateFlow(false)

    private val _lastBackupPath = MutableStateFlow<String?>(null)
    val lastBackupPath: StateFlow<String?> = _lastBackupPath

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.notificationEnabled,
        accountRepository.countAll(),
        _busy
    ) { values ->
        SettingsUiState(
            notificationEnabled = values[0] as Boolean,
            accountCount = values[1] as Int,
            busy = values[2] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun setNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotificationEnabled(enabled) }
    }

    fun exportData() {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val accounts = accountRepository.exportAll()
                if (accounts.isEmpty()) throw IllegalStateException("暂无数据可导出")
                val json = Gson().toJson(ExportData(accounts = accounts))
                backupManager.exportJson(json).getOrThrow()
            }.onSuccess { path ->
                _lastBackupPath.value = path
                _events.emit("已导出 Download/${AppConfig.BACKUP_FOLDER}/，共备份完成")
            }.onFailure { e ->
                _events.emit("导出失败：${e.message ?: "请检查存储权限后重试"}")
            }
            _busy.value = false
        }
    }

    fun importData(json: String) {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                val export = Gson().fromJson(json, ExportData::class.java)
                    ?: throw JsonParseException("文件内容无法解析")
                accountRepository.importAll(export.accounts)
            }.onSuccess { result ->
                _events.emit("导入完成：新增 ${result.inserted} 条，跳过重复 ${result.skipped} 条")
            }.onFailure { e ->
                _events.emit("导入失败：${e.message ?: "请确认选择的是翡栖导出的 JSON"}")
            }
            _busy.value = false
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { accountRepository.deleteAll() }
                .onSuccess { _events.emit("数据已清空") }
                .onFailure { _events.emit("清空失败：${it.message}") }
        }
    }

    fun notificationHint(enabled: Boolean): String = if (enabled) {
        NotificationUtils.ENABLED_HINT
    } else {
        NotificationUtils.DISABLED_HINT
    }
}
