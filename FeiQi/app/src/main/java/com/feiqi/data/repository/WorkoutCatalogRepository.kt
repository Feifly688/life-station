package com.feiqi.data.repository

import com.feiqi.data.database.ExerciseDao
import com.feiqi.data.database.WorkoutDayDao
import com.feiqi.data.database.WorkoutExerciseDao
import com.feiqi.data.database.WorkoutPlanDao
import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.entity.WorkoutDayEntity
import com.feiqi.data.entity.WorkoutExerciseEntity
import com.feiqi.data.entity.WorkoutPlanEntity
import com.feiqi.data.model.WorkoutPlanSeed
import kotlinx.coroutines.flow.Flow

/**
 * 训练计划目录（v2）：计划 / 训练日 / 动作库 / 关联。
 *
 * 与 [WorkoutRepository]（打卡记录）**职责分离**：这里管"计划定义"，那里管"打卡事实"。
 *
 * ## 官方计划种子
 * [seedOfficialPlanIfEmpty] **幂等**：已有计划就不再写，
 * 因此重复调用安全，也不会覆盖用户后续自建的计划。
 */
class WorkoutCatalogRepository(
    private val planDao: WorkoutPlanDao,
    private val dayDao: WorkoutDayDao,
    private val exerciseDao: ExerciseDao,
    private val linkDao: WorkoutExerciseDao,
) {

    // ---------------- 计划 ----------------

    fun observePlans(): Flow<List<WorkoutPlanEntity>> = planDao.observeAll()

    fun observePlan(planId: String): Flow<WorkoutPlanEntity?> = planDao.observeByPlanId(planId)

    suspend fun getPlan(planId: String): WorkoutPlanEntity? = planDao.getByPlanId(planId)

    /** 插入或更新计划（自建计划走这里）。 */
    suspend fun savePlan(plan: WorkoutPlanEntity) {
        val existing = planDao.getByPlanId(plan.planId)
        if (existing == null) {
            planDao.insertAll(listOf(plan))
        } else {
            planDao.update(plan.copy(id = existing.id))
        }
    }

    /** 删除计划。训练日/关联的级联清理留到下一阶段（自建计划上线时一并处理）。 */
    suspend fun deletePlan(planId: String) = planDao.deleteByPlanId(planId)

    // ---------------- 训练日 ----------------

    fun observeDays(planId: String): Flow<List<WorkoutDayEntity>> = dayDao.observeByPlan(planId)

    suspend fun getDay(planId: String, dayNumber: Int): WorkoutDayEntity? =
        dayDao.getByDay(planId, dayNumber)

    // ---------------- 动作 ----------------

    fun observeExercises(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    suspend fun getExerciseByName(name: String): ExerciseEntity? = exerciseDao.getByName(name)

    // ---------------- 关联 ----------------

    fun observeLinks(dayId: Long): Flow<List<WorkoutExerciseEntity>> = linkDao.observeByDay(dayId)

    suspend fun getLinks(dayId: Long): List<WorkoutExerciseEntity> = linkDao.getByDay(dayId)

    // ---------------- 官方计划种子 ----------------

    /**
     * 官方 30 天计划落库（**幂等**）。
     *
     * @return true = 本次真的写入了；false = 已有数据，跳过
     */
    suspend fun seedOfficialPlanIfEmpty(): Boolean {
        if (planDao.count() > 0) return false
        planDao.insertAll(listOf(WorkoutPlanSeed.plan))
        dayDao.insertAll(WorkoutPlanSeed.days)
        exerciseDao.insertAll(WorkoutPlanSeed.exercises)
        linkDao.insertAll(WorkoutPlanSeed.links)
        return true
    }
}
