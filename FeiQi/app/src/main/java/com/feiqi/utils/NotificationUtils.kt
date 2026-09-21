package com.feiqi.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.feiqi.R

object NotificationUtils {
    /** 渠道 ID 带版本号：老版本渠道的重要性无法修改，换 ID 才能升到「高」并弹横幅。 */
    private const val CHANNEL_ID = "feiqi_reminder_v2"

    const val ENABLED_HINT = "已开启，到期提醒会通过系统通知送达。"
    const val DISABLED_HINT = "已关闭，到期提醒将不会推送。"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "翡栖提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "日程与习惯的到期提醒"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /** 是否已获得通知权限（Android 13 以下默认有）。 */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun showReminder(
        context: Context,
        title: String,
        content: String,
        notificationId: Int = System.currentTimeMillis().toInt()
    ) {
        if (!hasPermission(context)) return
        createChannel(context)

        val launchIntent = Intent().apply {
            setClassName(context.packageName, "com.feiqi.ui.MainActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(notificationId, notification) }
    }

    /** Android 12+ 是否已获「精确闹钟」授权（未授权时闹钟会退化为窗口式，可能延迟）。 */
    fun hasExactAlarmPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 跳转到本应用的通知设置页。 */
    fun openNotificationSettings(context: Context) {
        runCatching {
            val intent = Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * 跳转精确闹钟授权。
     * Android 15+（含小米15 Pro / Android 16）直接弹系统授权对话框，体验最好；
     * Android 12~14 回退到「闹钟和提醒」设置页。
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 35) {
                val intent = Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM").apply {
                    setData(Uri.fromParts("package", context.packageName, null))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent("android.settings.ACTION_SCHEDULE_EXACT_ALARM").apply {
                    setData(Uri.fromParts("package", context.packageName, null))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    /** 跳转到本应用详情页（自启动、省电策略、通知管理等都集中在这里）。 */
    fun openAppDetails(context: Context) {
        runCatching {
            val intent = Intent("android.settings.APPLICATION_DETAILS_SETTINGS").apply {
                setData(Uri.fromParts("package", context.packageName, null))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
