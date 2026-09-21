package com.feiqi.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.feiqi.data.model.Schedule
import com.feiqi.utils.AppLogger
import java.time.LocalTime
import java.time.ZoneId

/**
 * 日程提醒排程器：把勾选了「到时间提醒我」的日程注册到系统 AlarmManager，
 * 到点由 [ReminderReceiver] 弹出通知。
 *
 * 行为约定：
 * - 单条日程（listId == null）：每条单独注册一个闹钟，单独发通知。
 * - 待办清单（listId != null）：整个清单只注册一个闹钟，到点后合并为一条汇总通知。
 */
class ReminderScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val tag = "ReminderScheduler"

    /** Android 12+ 精确闹钟需要用户单独授权，未授权时退化为 10 分钟窗口的非精确提醒。 */
    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    /** 提醒时间戳；日程没填时间时按当天 09:00 处理。 */
    fun triggerAtMillis(schedule: Schedule): Long {
        val time = schedule.time ?: LocalTime.of(9, 0)
        return schedule.date.atTime(time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /** 该日程是否还能排上提醒（勾了提醒、未完成、时间还没到）。 */
    fun isSchedulable(schedule: Schedule): Boolean =
        schedule.reminder && !schedule.completed && triggerAtMillis(schedule) > System.currentTimeMillis()

    /**
     * 排程单个日程。
     * - 单条日程：注册独立闹钟。
     * - 清单条目：不单独处理，由 [scheduleList] 统一注册。
     */
    fun schedule(schedule: Schedule) {
        if (schedule.listId != null) {
            // 清单条目由清单统一排程，避免每个条目各发一条通知
            return
        }
        if (!isSchedulable(schedule)) {
            cancel(schedule.id)
            return
        }
        val triggerAt = triggerAtMillis(schedule)
        val pendingIntent = buildPendingIntent(schedule)
        runCatching {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    WINDOW_MILLIS,
                    pendingIntent
                )
            }
        }.onFailure { AppLogger.e(tag, "注册单条提醒闹钟失败 id=${schedule.id}", it) }
    }

    /**
     * 为整个待办清单排程一个汇总闹钟。
     * 同一个 listId 始终只保留一个闹钟；若清单下没有可提醒条目则取消。
     */
    fun scheduleList(listId: String, schedules: List<Schedule>) {
        val group = schedules.filter { it.listId == listId }
        val schedulable = group.filter { isSchedulable(it) }
        if (schedulable.isEmpty()) {
            cancelList(listId)
            return
        }
        // 清单内所有条目时间应相同，取第一条代表即可
        val representative = schedulable.minByOrNull { it.itemOrder } ?: schedulable.first()
        val triggerAt = triggerAtMillis(representative)
        val pendingIntent = buildListPendingIntent(listId, representative)
        runCatching {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    WINDOW_MILLIS,
                    pendingIntent
                )
            }
        }.onFailure { AppLogger.e(tag, "注册清单提醒闹钟失败 listId=$listId", it) }
    }

    fun cancel(scheduleId: Long) {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun cancelList(listId: String) {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_LIST_ID, listId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            listId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /** App 启动时重排全部提醒（覆盖手机重启、应用被杀导致闹钟丢失的情况）。 */
    fun rescheduleAll(schedules: List<Schedule>) {
        // 先排单条，再按清单分组排汇总
        schedules.filter { it.listId == null }.forEach { schedule(it) }
        schedules.filter { it.listId != null }
            .groupBy { it.listId!! }
            .forEach { (listId, group) -> scheduleList(listId, group) }
    }

    private fun buildPendingIntent(schedule: Schedule): PendingIntent {
        val timeText = schedule.time?.let { DateUtils.hm(it) }.orEmpty()
        val content = listOf(timeText, schedule.groupName, schedule.note)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_ID, schedule.id)
            putExtra(EXTRA_TITLE, schedule.title)
            putExtra(EXTRA_CONTENT, content.ifBlank { "到时间啦，去看看吧" })
        }
        return PendingIntent.getBroadcast(
            appContext,
            schedule.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildListPendingIntent(listId: String, representative: Schedule): PendingIntent {
        val intent = Intent(appContext, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_LIST_ID, listId)
            putExtra(EXTRA_TITLE, representative.listTitle.ifBlank { "待办清单" })
        }
        return PendingIntent.getBroadcast(
            appContext,
            listId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_REMIND = "com.feiqi.action.REMIND"
        const val EXTRA_ID = "extra_schedule_id"
        const val EXTRA_LIST_ID = "extra_list_id"
        const val EXTRA_TITLE = "extra_schedule_title"
        const val EXTRA_CONTENT = "extra_schedule_content"
        private const val WINDOW_MILLIS = 10 * 60 * 1000L
    }
}
