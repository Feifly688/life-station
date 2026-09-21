package com.feiqi

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.feiqi.data.database.FeiQiDatabase
import com.feiqi.data.repository.AccountRepository
import com.feiqi.data.repository.HabitRepository
import com.feiqi.data.repository.MediaRepository
import com.feiqi.data.repository.PreferencesRepository
import com.feiqi.data.repository.ScheduleCompletionRepository
import com.feiqi.data.repository.ScheduleRepository
import com.feiqi.utils.BackupManager
import com.feiqi.utils.ExcelExporter
import com.feiqi.utils.NotificationUtils
import com.feiqi.utils.ReminderScheduler
import com.feiqi.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FeiQiApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationUtils.createChannel(this)
        restoreReminders()
    }

    /**
     * 手机重启或进程被杀会丢失 AlarmManager 里的闹钟，启动时按数据库重排一次。
     *
     * 每日重复清单的次日重置：若 isRecurring && reminder && !completed 且 date 已早于今天
     * （即昨天/前天该循环清单未被手动完成），把 date 顺延到今天（时分秒不变）、状态保持
     * 待完成，使清单在新的一天继续循环提醒。这与用户预期一致——「当天没加入已完成，第二天
     * 自动重置为待完成，提醒时间顺延到当天」。
     */
    private fun restoreReminders() {
        appScope.launch {
            runCatching {
                val repo = container.scheduleRepository
                val today = com.feiqi.utils.DateUtils.today()
                val schedules = repo.getAll().first()
                // 过期循环清单：date < today 的，顺延到今天（只顺延到当天，不无脑推到未来）。
                val advanced = schedules
                    .filter { it.isRecurring && it.reminder && !it.completed && it.date < today }
                    .map { it.copy(date = today, lastResetDate = null) }
                // 批量写回：整体收敛到一个事务（原本逐条 update 会各开一次事务）。
                repo.updateBatch(advanced)
                val advancedById = advanced.associateBy { it.id }
                val adjusted = schedules.map { advancedById[it.id] ?: it }
                container.reminderScheduler.rescheduleAll(adjusted)
                AppLogger.i("ReminderRestore", "启动重排提醒完成，共 ${adjusted.size} 条")
            }.onFailure { AppLogger.e("ReminderRestore", "启动重排提醒失败", it) }
        }
    }
}

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database = Room.databaseBuilder(
        appContext,
        FeiQiDatabase::class.java,
        "feiqi_database"
    )
        .addMigrations(
            FeiQiDatabase.MIGRATION_1_2,
            FeiQiDatabase.MIGRATION_2_3,
            FeiQiDatabase.MIGRATION_3_4,
            FeiQiDatabase.MIGRATION_4_5,
            FeiQiDatabase.MIGRATION_5_6,
            FeiQiDatabase.MIGRATION_6_7,
            FeiQiDatabase.MIGRATION_7_8
        )
        .build()

    val accountRepository = AccountRepository(database.accountDao())
    val scheduleRepository = ScheduleRepository(database.scheduleDao())
    val scheduleCompletionRepository = ScheduleCompletionRepository(database.scheduleCompletionDao())
    val habitRepository = HabitRepository(database.habitDao(), database.habitRecordDao())
    val mediaRepository = MediaRepository(database.mediaDao())
    val preferencesRepository = PreferencesRepository(appContext)
    val backupManager = BackupManager(appContext)
    val excelExporter = ExcelExporter(appContext)
    val reminderScheduler = ReminderScheduler(appContext)
}
