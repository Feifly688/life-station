package com.feiqi.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.feiqi.data.entity.AccountEntity
import com.feiqi.data.entity.HabitEntity
import com.feiqi.data.entity.LEGACY_CREATED_DATE
import com.feiqi.data.entity.HabitRecordEntity
import com.feiqi.data.entity.MediaEntity
import com.feiqi.data.entity.ScheduleCompletionEntity
import com.feiqi.data.entity.ScheduleEntity

@Database(
    entities = [
        AccountEntity::class,
        ScheduleEntity::class,
        ScheduleCompletionEntity::class,
        HabitEntity::class,
        HabitRecordEntity::class,
        MediaEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class FeiQiDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun scheduleCompletionDao(): ScheduleCompletionDao
    abstract fun habitDao(): HabitDao
    abstract fun habitRecordDao(): HabitRecordDao
    abstract fun mediaDao(): MediaDao

    companion object {
        /** v1 -> v2：记账记录增加精确到秒的时间列，老数据默认 00:00:00。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE account_records ADD COLUMN time TEXT NOT NULL DEFAULT '00:00:00'"
                )
            }
        }

        /** v2 -> v3：习惯增加创建日期列，老习惯默认 1970-01-01（热力图完整显示）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE habits ADD COLUMN createdDate TEXT NOT NULL DEFAULT '$LEGACY_CREATED_DATE'"
                )
            }
        }

        /** v3 -> v4：习惯增加备注列（替代原「目标次数」输入位），老数据默认空串。 */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v4 -> v5：日程去掉优先级列（UI 与数据层统一移除），重建表以彻底删除该列。 */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE schedules_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "date TEXT NOT NULL, " +
                        "time TEXT, " +
                        "groupName TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "reminder INTEGER NOT NULL, " +
                        "completed INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO schedules_new " +
                        "(id, title, date, time, groupName, note, reminder, completed, createdAt) " +
                        "SELECT id, title, date, time, groupName, note, reminder, completed, createdAt " +
                        "FROM schedules"
                )
                db.execSQL("DROP TABLE schedules")
                db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
            }
        }

        /** v5 -> v6：日程支持清单聚合与每日循环。 */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN listId TEXT")
                db.execSQL("ALTER TABLE schedules ADD COLUMN listTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE schedules ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedules ADD COLUMN itemOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedules ADD COLUMN lastResetDate TEXT")
            }
        }

        /** v6 -> v7：日程清单增加完成日期，用于在「已完成」区域展示。 */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE schedules ADD COLUMN completedDate TEXT")
            }
        }

        /** v7 -> v8：新增「待办完成记录」表，支撑循环提醒每次触发的完成记录。 */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE schedule_completions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scheduleId INTEGER NOT NULL, " +
                        "listId TEXT, " +
                        "date TEXT NOT NULL, " +
                        "time TEXT, " +
                        "title TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_schedule_completions_scheduleId " +
                        "ON schedule_completions(scheduleId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_schedule_completions_listId " +
                        "ON schedule_completions(listId)"
                )
            }
        }

        /**
         * v8 -> v9：日程重复规则由布尔列 `isRecurring`（只有「每日」一种）升级为枚举列 `recurrence`
         * （NONE / DAILY / WEEKDAYS / WEEKLY / MONTHLY / YEARLY）。
         *
         * 低版本 SQLite 不支持 DROP COLUMN，故沿用 4→5 的做法重建表；
         * 旧数据映射：isRecurring = 1 → 'DAILY'，否则 'NONE'。
         */
        /** v10：日程增加「过期后补完成」标记，让「已过期」标签在标记完成后仍然保留。 */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN completedLate INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE schedules_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "date TEXT NOT NULL, " +
                        "time TEXT, " +
                        "groupName TEXT NOT NULL, " +
                        "note TEXT NOT NULL, " +
                        "reminder INTEGER NOT NULL, " +
                        "completed INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "listId TEXT, " +
                        "listTitle TEXT NOT NULL, " +
                        "itemOrder INTEGER NOT NULL, " +
                        "lastResetDate TEXT, " +
                        "completedDate TEXT, " +
                        "recurrence TEXT NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO schedules_new " +
                        "(id, title, date, time, groupName, note, reminder, completed, createdAt, " +
                        "listId, listTitle, itemOrder, lastResetDate, completedDate, recurrence) " +
                        "SELECT id, title, date, time, groupName, note, reminder, completed, createdAt, " +
                        "listId, listTitle, itemOrder, lastResetDate, completedDate, " +
                        "CASE WHEN isRecurring = 1 THEN 'DAILY' ELSE 'NONE' END FROM schedules"
                )
                db.execSQL("DROP TABLE schedules")
                db.execSQL("ALTER TABLE schedules_new RENAME TO schedules")
            }
        }
    }
}
