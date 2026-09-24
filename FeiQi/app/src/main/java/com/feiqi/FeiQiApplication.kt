package com.feiqi

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.feiqi.data.database.FeiQiDatabase
import com.feiqi.data.repository.AccountRepository
import com.feiqi.data.repository.MediaRepository
import com.feiqi.data.repository.PreferencesRepository
import com.feiqi.data.repository.QuoteRepository
import com.feiqi.data.repository.ScheduleRepository
import com.feiqi.utils.BackupManager
import com.feiqi.utils.ExcelExporter
import com.feiqi.utils.NotificationUtils
import com.feiqi.utils.ReminderScheduler
import com.feiqi.utils.RecurrenceUtils
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
        syncQuotes()
    }

    /**
     * 语录集：先载入本地缓存保证首页立刻有内容，再静默更新「基础集」（7 天周期）。
     * 自动收集新语录放在 `MainActivity.onStart`（每次打开 App 都跑一次，内部有节流）。
     * 全程在 IO 线程、失败不打扰用户，仅记录到日志。
     */
    private fun syncQuotes() {
        appScope.launch {
            runCatching {
                container.quoteRepository.loadCache()
                container.quoteRepository.refreshBase(force = false)
            }.onFailure { AppLogger.e("QuoteSync", "语录集同步失败", it) }
        }
    }

    /**
     * 手机重启或进程被杀会丢失 AlarmManager 里的闹钟，启动时按数据库重排一次。
     *
     * 循环清单的「错过后补推」：若 isRecurring && reminder && !completed 且 date 已早于今天
     * （即该循环清单的应提醒日已过、尚未手动完成），按重复规则把 date 推进到**不早于今天的
     * 下一个应提醒日**（每天→今天；周一至周五→下一个工作日；每周→本周/下周同星期几；
     * 每月→本月/下月同日；每年→今年/明年同月日），状态保持待完成，提醒时刻不变。
     * 与用户预期一致——循环清单不回补历史欠账，直接落到下一个应提醒日继续提醒。
     */
    private fun restoreReminders() {
        appScope.launch {
            runCatching {
                val repo = container.scheduleRepository
                val today = com.feiqi.utils.DateUtils.today()
                val schedules = repo.getAll().first()
                val advanced = schedules
                    .filter { it.isRecurring && it.reminder && !it.completed && it.date < today }
                    .map {
                        it.copy(
                            date = RecurrenceUtils.advanceToOnOrAfter(it.date, it.recurrence, today),
                            lastResetDate = null
                        )
                    }
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
            FeiQiDatabase.MIGRATION_7_8,
            FeiQiDatabase.MIGRATION_8_9,
            FeiQiDatabase.MIGRATION_9_10
        )
        .build()

    val accountRepository = AccountRepository(database.accountDao())
    val scheduleRepository = ScheduleRepository(database.scheduleDao())
    val mediaRepository = MediaRepository(database.mediaDao())
    val preferencesRepository = PreferencesRepository(appContext)
    val backupManager = BackupManager(appContext)
    val excelExporter = ExcelExporter(appContext)
    val reminderScheduler = ReminderScheduler(appContext)
    val quoteRepository = QuoteRepository(appContext)
}
