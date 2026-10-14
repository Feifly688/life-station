package com.feiqi.data.repository

import com.feiqi.data.database.ExerciseDao
import com.feiqi.data.database.WorkoutDayDao
import com.feiqi.data.database.WorkoutExerciseDao
import com.feiqi.data.database.WorkoutPlanDao
import com.feiqi.data.entity.ExerciseEntity
import com.feiqi.data.entity.WorkoutDayEntity
import com.feiqi.data.entity.WorkoutExerciseEntity
import com.feiqi.data.entity.WorkoutPlanEntity
import com.feiqi.data.model.BodyCategory
import com.feiqi.data.model.Exercise
import com.feiqi.data.model.ExerciseDifficulty
import com.feiqi.data.model.MAX_DAILY_EXERCISES
import com.feiqi.data.model.PlanDraft
import com.feiqi.data.model.WorkoutGoal
import com.feiqi.data.model.WorkoutPlanSeed
import com.feiqi.data.model.WorkoutPlanType
import com.feiqi.data.model.WorkoutPlan
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

    // ---------------- 用户自建计划（v2 阶段 2） ----------------

    /**
     * 保存用户自建计划：计划头 + 训练日 + 动作关联**一次事务写入**。
     *
     * ## 动作来源
     * 优先复用动作库中已有的动作（[exerciseDao.getByBodyParts]），
     * 库中缺失的动作会即时补建（[BodyCategory.detailParts] 覆盖的部位必然有官方种子动作，
     * 因此正常路径下全部命中复用）。
     *
     * @return 新建计划的 [WorkoutPlanEntity.planId]
     */
    suspend fun saveUserPlan(draft: PlanDraft, newPlanId: String): String {
        val detailParts = draft.categories.flatMap { it.detailParts }.distinct()
        val library = exerciseDao.getByBodyParts(detailParts)
        val byPart = library.groupBy { it.bodyPart }

        // 1) 逐个训练日挑动作（按部位取，难度低的优先，最多 MAX_DAILY_EXERCISES 个）
        val dayDrafts = draft.days.map { planned ->
            val parts = planned.category?.detailParts.orEmpty()
            val picked = parts
                .flatMap { byPart[it].orEmpty() }
                .distinctBy { it.name }
                .sortedBy { ExerciseDifficulty.fromCode(it.difficulty).ordinal }
                .take(MAX_DAILY_EXERCISES)
            planned to picked
        }

        // 2) 写计划头
        planDao.insertAll(
            listOf(
                WorkoutPlanEntity(
                    planId = newPlanId,
                    name = draft.name.trim(),
                    description = buildString {
                        append("每周训练 ")
                        append(draft.frequencyPerWeek)
                        append(" 天 · 共 ")
                        append(draft.trainingDayCount)
                        append(" 个训练日")
                    },
                    type = WorkoutPlanType.USER_CREATED.code,
                    goal = draft.goal.code,
                    cycleDays = draft.cycleDays,
                    createdTime = System.currentTimeMillis(),
                )
            )
        )

        // 3) 写训练日
        val dayEntities = dayDrafts.map { (planned, _) ->
            WorkoutDayEntity(
                planId = newPlanId,
                dayNumber = planned.dayNumber,
                title = planned.title,
                bodyParts = planned.category?.displayParts.orEmpty(),
                description = planned.category?.let { "第 ${planned.dayNumber} 天 · ${it.displayParts}" }
                    ?: "休息日 · 拉伸放松",
            )
        }
        dayDao.insertAll(dayEntities)

        // 4) 写关联（dayId 用插入后的自增 id 回查）
        val insertedDays = dayDao.getByPlan(newPlanId).associateBy { it.dayNumber }
        val links = dayDrafts.mapNotNull { (planned, picked) ->
            val dayId = insertedDays[planned.dayNumber]?.id ?: return@mapNotNull null
            picked.map { exercise ->
                WorkoutExerciseEntity(
                    dayId = dayId,
                    exerciseId = exercise.id,
                    sets = DEFAULT_SETS,
                    reps = DEFAULT_REPS,
                    duration = 0,
                    restTime = WorkoutPlanSeed.DEFAULT_REST_SECONDS,
                )
            }
        }.flatten()
        if (links.isNotEmpty()) linkDao.insertAll(links)

        return newPlanId
    }

    /**
     * 删除计划（**级联**：训练日 → 关联 → 计划头）。
     *
     * 打卡记录 `workout_records` **不删**（软删方案见阶段 3 文档建议）。
     */
    suspend fun deletePlanCascade(planId: String) {
        val dayIds = dayDao.getByPlan(planId).map { it.id }
        if (dayIds.isNotEmpty()) linkDao.deleteByDayIds(dayIds)
        dayDao.deleteByPlan(planId)
        planDao.deleteByPlanId(planId)
    }

    /** 按部位取动作库（创建页推荐动作用）。 */
    suspend fun exercisesByBodyParts(parts: List<String>): List<ExerciseEntity> =
        exerciseDao.getByBodyParts(parts)

    // ---------------- 运行时计划装载 ----------------

    /**
     * 装载某计划的运行时训练日（供主页/详情页渲染）。
     *
     * - **官方计划**直接返回静态 [com.feiqi.data.model.WORKOUT_PLANS]（保持既有内容与手感不变）；
     * - **用户计划**从四表装配：训练日 + 关联 + 动作库 → 与官方同构的 [WorkoutPlan]。
     *
     * @return null = 该计划没有训练日（脏数据或已被删除）
     */
    suspend fun loadRuntimePlan(planId: String): List<WorkoutPlan>? {
        if (planId == WorkoutPlanSeed.OFFICIAL_PLAN_ID) {
            return com.feiqi.data.model.WORKOUT_PLANS
        }
        val days = dayDao.getByPlan(planId)
        if (days.isEmpty()) return null
        return days.map { day ->
            val links = linkDao.getByDay(day.id)
            val exerciseIds = links.map { it.exerciseId }.distinct()
            val exerciseMap = if (exerciseIds.isEmpty()) {
                emptyMap()
            } else {
                exerciseDao.getByIds(exerciseIds).associateBy { it.id }
            }
            WorkoutPlan(
                planId = planId,
                dayNumber = day.dayNumber,
                bodyParts = day.bodyParts.ifBlank { "训练" },
                title = day.title,
                description = day.description,
                exercises = links.mapNotNull { link ->
                    val exercise = exerciseMap[link.exerciseId] ?: return@mapNotNull null
                    Exercise(
                        name = exercise.name,
                        sets = link.sets,
                        reps = link.reps,
                        durationSeconds = link.duration,
                        note = link.note,
                    )
                },
            )
        }
    }

    companion object {
        /** 用户计划统一组数。 */
        const val DEFAULT_SETS: Int = 3

        /** 用户计划统一次数描述。 */
        const val DEFAULT_REPS: String = "12次"
    }
}
