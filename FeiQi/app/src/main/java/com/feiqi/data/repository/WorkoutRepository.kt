package com.feiqi.data.repository

import com.feiqi.data.database.WorkoutDao
import com.feiqi.data.entity.WorkoutRecordEntity
import com.feiqi.data.model.PLAN_ID
import com.feiqi.data.model.WorkoutPlanSeed
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * 健身打卡仓库（v2 阶段 2：**多计划**）。
 *
 * 只负责**打卡记录的读写**，训练计划定义在 [WorkoutCatalogRepository]。
 *
 * ## planId 兼容
 * 官方计划在 v2 换了 id（`home30` → `home_beginner_30`），但历史打卡记录仍写在
 * `home30` 下。这里在**读取层**做别名展开（见 [WorkoutPlanSeed.readAliases]），
 * 两个 id 的完成记录**合并计算**，保证老用户升级后进度不清零；
 * **写入统一用当前展示的 planId**，不做任何历史数据迁移。
 */
class WorkoutRepository(private val dao: WorkoutDao) {

    /** 全部打卡记录（原始，不做别名展开）。 */
    fun records(planId: String = PLAN_ID): Flow<List<WorkoutRecordEntity>> =
        dao.observeByPlan(planId)

    /**
     * **全部**计划的打卡记录（v2 阶段 4）。
     *
     * 顶部「连续训练 / 本月完成 / 累计时长」都是**跨计划**口径 ——
     * 换计划不清零、切官方与自建计划时统计连续。
     */
    fun allRecords(): Flow<List<WorkoutRecordEntity>> = dao.observeAll()

    /**
     * 已完成的天数（含别名合并）。
     *
     * 官方计划会同时读取 `home_beginner_30` 与 `home30`，取并集。
     */
    fun completedDays(planId: String = PLAN_ID): Flow<Set<Int>> {
        val aliases = WorkoutPlanSeed.readAliases(planId)
        val sources = aliases.map { dao.observeCompletedDays(it) }
        return when (sources.size) {
            1 -> sources.first().map { it.toSet() }
            else -> combine(sources) { arrays -> arrays.flatMap { it }.toSet() }
        }
    }

    /**
     * 打卡某天（重复打卡走 REPLACE，依赖 `planId + dayNumber` 唯一索引）。
     *
     * 官方计划若在旧 id（`home30`）下已有同天记录，会同步补写一份到新 id，
     * 这样"取消打卡"时两处都能删干净（[undo] 会清理全部别名）。
     */
    suspend fun checkIn(planId: String = PLAN_ID, dayNumber: Int) {
        val now = System.currentTimeMillis()
        val today = DateUtils.today().toString()
        dao.upsert(newRecord(planId, dayNumber, today, now))
        val legacyId = WorkoutPlanSeed.LEGACY_PLAN_ID
        if (planId == WorkoutPlanSeed.OFFICIAL_PLAN_ID &&
            dao.countByDay(legacyId, dayNumber) > 0
        ) {
            dao.upsert(newRecord(legacyId, dayNumber, today, now))
        }
    }

    /** 取消某天打卡（同时清理别名下的同天记录）。 */
    suspend fun undo(planId: String = PLAN_ID, dayNumber: Int) {
        dao.deleteByDay(planId, dayNumber)
        WorkoutPlanSeed.readAliases(planId)
            .filter { it != planId }
            .forEach { dao.deleteByDay(it, dayNumber) }
    }

    private fun newRecord(planId: String, dayNumber: Int, date: String, at: Long) =
        WorkoutRecordEntity(
            planId = planId,
            dayNumber = dayNumber,
            date = date,
            completedAt = at,
        )
}