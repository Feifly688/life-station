package com.feiqi.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.feiqi.FeiQiApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 日程提醒到点时被 AlarmManager 唤起，负责弹出系统通知。
 *
 * 循环清单的「顺延 +1 天 / 记完成记录」逻辑**不**在此自动触发：
 * - 提醒触发只弹通知，清单保持原状（用户可见「已过期/到点」状态）。
 * - 用户手动标记完成时，由 [com.feiqi.ui.schedule.ScheduleViewModel] 生成已完成快照副本
 *   + 原清单顺延继续循环。
 * - 当天未手动完成，次日 App 启动时由 FeiQiApplication 把 date 顺延到当天、状态保持待完成。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val listId = intent.getStringExtra(ReminderScheduler.EXTRA_LIST_ID)
        if (listId != null) {
            showListReminder(context, listId, intent)
        } else {
            showSingleReminder(context, intent)
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

    private fun showListReminder(context: Context, listId: String, intent: Intent) {
        val fallbackTitle = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE).orEmpty()
        val app = context.applicationContext as? FeiQiApplication ?: return
        val repository = app.container.scheduleRepository

        val items = runBlocking {
            runCatching {
                repository.getAll().first().filter {
                    it.listId == listId && !it.completed
                }.sortedBy { it.itemOrder }
            }.getOrDefault(emptyList())
        }

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
