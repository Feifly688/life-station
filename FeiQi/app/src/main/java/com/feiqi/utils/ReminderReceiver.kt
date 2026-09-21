package com.feiqi.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.feiqi.FeiQiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 日程提醒到点时被 AlarmManager 唤起，负责弹出系统通知。
 *
 * 循环清单的「顺延 +1 天 / 记完成记录」逻辑**不**在此自动触发：
 * - 提醒触发只弹通知，清单保持原状（用户可见「已过期/到点」状态）。
 * - 用户手动标记完成时，由 [com.feiqi.ui.schedule.ScheduleViewModel] 生成已完成快照副本
 *   + 原清单顺延继续循环。
 * - 当天未手动完成，下次 App 启动时由 FeiQiApplication 按重复规则把 date 推进到
 *   不早于今天的下一个应提醒日（每天/周一至周五/每周/每月/每年），状态保持待完成。
 *
 * 线程模型：`onReceive` 运行在主线程且只有约 10s 预算，因此**不做任何阻塞式查库**
 * （历史上这里用 runBlocking 读全表，冷启动/大清单下有 ANR 风险）。清单提醒需要读库，
 * 统一走 [goAsync] + IO 协程，结束后再 `finish()`；单条日程提醒无 IO，直接同步弹通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val listId = intent.getStringExtra(ReminderScheduler.EXTRA_LIST_ID)
        if (listId == null) {
            // 单条日程：文案已在 intent 里，零 IO，同步完成。
            showSingleReminder(context, intent)
            return
        }

        // 清单提醒需要查库取待办文案：转异步，避免阻塞主线程。
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            try {
                showListReminder(appContext, listId, intent)
            } catch (t: Throwable) {
                AppLogger.e("Reminder", "清单提醒发送失败 listId=$listId", t)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }

    private fun showSingleReminder(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, System.currentTimeMillis())
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val content = intent.getStringExtra(ReminderScheduler.EXTRA_CONTENT).orEmpty()

        NotificationUtils.showReminder(
            context = context,
            title = title.ifBlank { "翡栖提醒" },
            content = content.ifBlank { "到时间啦，去看看吧" },
            notificationId = id.toInt()
        )
    }

    private suspend fun showListReminder(context: Context, listId: String, intent: Intent) {
        val fallbackTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val app = context.applicationContext as? FeiQiApplication ?: return
        val repository = app.container.scheduleRepository

        // 定向查询：只取该清单未完成项，不再全表加载 + 内存过滤。
        val items = runCatching {
            repository.getPendingByListId(listId)
        }.getOrDefault(emptyList())

        val title = items.firstOrNull()?.listTitle?.ifBlank { null }
            ?: fallbackTitle.ifBlank { "待办清单" }

        val content = if (items.isEmpty()) {
            "清单里的待办都完成啦"
        } else {
            val preview = items.take(3).joinToString("、") { it.title }
            if (items.size <= 3) {
                "还有 ${items.size} 项待办：$preview"
            } else {
                "还有 ${items.size} 项待办：$preview 等"
            }
        }

        NotificationUtils.showReminder(
            context = context,
            title = title,
            content = content,
            notificationId = listId.hashCode()
        )
    }
}
