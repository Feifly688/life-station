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
import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.entity.WorkoutDayEntity
import com.feiqi.data.entity.WorkoutExerciseEntity
import com.feiqi.data.entity.WorkoutPlanEntity
import com.feiqi.data.entity.WorkoutRecordEntity

@Database(
    entities = [
        AccountEntity::class,
        ScheduleEntity::class,
        ScheduleCompletionEntity::class,
        HabitEntity::class,
        HabitRecordEntity::class,
        MediaEntity::class,
        WorkoutRecordEntity::class,
        // 健身 v2：计划目录（计划 / 训练日 / 动作库 / 关联），workout_records 保持不变
        WorkoutPlanEntity::class,
        WorkoutDayEntity::class,
        ExerciseEntity::class,
        WorkoutExerciseEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class FeiQiDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun scheduleCompletionDao(): ScheduleCompletionDao
    abstract fun habitDao(): HabitDao
    abstract fun habitRecordDao(): HabitRecordDao
    abstract fun mediaDao(): MediaDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun workoutPlanDao(): WorkoutPlanDao

    abstract fun workoutDayDao(): WorkoutDayDao

    abstract fun exerciseDao(): ExerciseDao

    abstract fun workoutExerciseDao(): WorkoutExerciseDao

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

        /**
         * v10 -> v11：新增健身打卡表 `workout_records`。
         *
         * **只新建表，既有表零改动** —— 待办/记账/习惯/书影音数据完全不受影响。
         * 索引与 [WorkoutRecordEntity] 的 `@Entity(indices=…)` 严格一致：
         * Room 打开数据库时会逐列/逐索引比对，多一个或少一个索引都会启动即崩。
         */
        /**
         * v11 -> v12：健身 v2 的计划目录四张表。
         *
         * **只新建表**：`todos` / `accounts` / `habit_records` / `medias` / `workout_records`
         * 一律不 ALTER，老用户数据零影响。
         *
         * 索引名与列名**必须**与实体 `@Entity(indices = …)` 完全一致 ——
         * 本库 `exportSchema = false`（无编译期校验），Room 打开数据库时逐项比对，
         * 多一个或少一个索引都会 `IllegalStateException` 启动即崩。
         */
        /**
         * v12 -> v13：**动作库扩展**（健身 v2 第3 阶段）。
         *
         * ## 只加列，不动已有列
         * 全部使用 `ALTER TABLE ... ADD COLUMN` + `NOT NULL DEFAULT`：
         * SQLite 直接追加列，**不重写表、不动已有行**，
         * 因此 `workout_exercises.exerciseId` 的 id 引用完全不受影响，
         * `workout_plans` / `workout_days` / `workout_records` / `workout_exercises`
         * 四张表**零改动**。
         *
         * ## 列名必须逐字一致
         * `exportSchema = false` 没有编译期校验，迁移 SQL 与 [ExerciseEntity]
         * 的属性名对不上会在升级校验时闪退。改完请 grep
         * `app/build/generated/**/FeiQiDatabase_Impl.java` 核对 CREATE TABLE。
         *
         * ## 默认值
         * 老数据的 `category` / `targetMuscle` / `steps` / `tips` 迁移后为空串，
         * 由启动时的 `seedExerciseLibrary()` 补齐（保 id 更新）。
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `category` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `targetMuscle` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `equipment` TEXT NOT NULL DEFAULT 'NONE'"
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `steps` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `tips` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE `exercises` ADD COLUMN `videoUrl` TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_plans` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`planId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`goal` TEXT NOT NULL, " +
                        "`cycleDays` INTEGER NOT NULL, " +
                        "`createdTime` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_plans_planId` " +
                        "ON `workout_plans` (`planId`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_days` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`planId` TEXT NOT NULL, " +
                        "`dayNumber` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`bodyParts` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_days_planId_dayNumber` " +
                        "ON `workout_days` (`planId`, `dayNumber`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercises` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`bodyPart` TEXT NOT NULL, " +
                        "`difficulty` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`image` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_exercises_name` " +
                        "ON `exercises` (`name`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_exercises` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dayId` INTEGER NOT NULL, " +
                        "`exerciseId` INTEGER NOT NULL, " +
                        "`sets` INTEGER NOT NULL, " +
                        "`reps` TEXT NOT NULL, " +
                        "`duration` INTEGER NOT NULL, " +
                        "`restTime` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_exercises_dayId_exerciseId` " +
                        "ON `workout_exercises` (`dayId`, `exerciseId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_exercises_dayId` " +
                        "ON `workout_exercises` (`dayId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_exercises_exerciseId` " +
                        "ON `workout_exercises` (`exerciseId`)"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `workout_records` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`planId` TEXT NOT NULL, " +
                        "`dayNumber` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, " +
                        "`completedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_workout_records_planId_dayNumber` " +
                        "ON `workout_records` (`planId`, `dayNumber`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_records_completedAt` " +
                        "ON `workout_records` (`completedAt`)"
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
