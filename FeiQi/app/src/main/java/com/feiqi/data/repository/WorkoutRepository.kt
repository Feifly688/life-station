package com.feiqi.data.repository

import com.feiqi.data.database.WorkoutDao
import com.feiqi.data.entity.WorkoutRecordEntity
import com.feiqi.data.model.PLAN_ID
import com.feiqi.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 健身（30 天居家训练）仓库。
 *
 * 只负责**打卡记录的读写**，训练计划是静态数据（`WorkoutPlan`），不在此层。
 */
class WorkoutRepository(private val dao: WorkoutDao) {

    /** 全部打卡记录。 */
    fun records(planId: String = PLAN_ID): Flow<List<WorkoutRecordEntity>> =
        dao.observeByPlan(planId)

    /** 已完成的天数（供进度与连续天数派生）。 */
    fun completedDays(planId: String = PLAN_ID): Flow<Set<Int>> =
        dao.observeCompletedDays(planId).map { it.toSet() }

    /**
     * 打卡某天。
     *
     * 重复打卡走 REPLACE（唯一索引 `planId + dayNumber`），不会产生重复记录。
     */
    suspend fun checkIn(planId: String = PLAN_ID, dayNumber: Int) {
        val today = DateUtils.today()
        dao.upsert(
            WorkoutRecordEntity(
                planId = planId,
                dayNumber = dayNumber,
                date = today.toString(),
                completedAt = System.currentTimeMillis(),
            )
        )
    }

    /** 取消某天打卡。 */
    suspend fun undo(planId: String = PLAN_ID, dayNumber: Int) {
        dao.deleteByDay(planId, dayNumber)
    }
}