package com.feiqi.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * **动作库**：一个动作一条记录，可被多天、多计划复用（v2 阶段 3 扩展）。
 *
 * 注意：动作本身**不含组数/次数** —— 那是"某一天怎么练"的配置，
 * 记录在 [WorkoutExerciseEntity]，避免同一动作在不同天无法配不同组数。
 *
 * ## 部位两层（v2 阶段 3）
 * - [category]：**训练大类**（胸部/背部/肩部/腿部/臀部/手臂/腹部），面向用户、可直接展示；
 * - [bodyPart]：**具体部位**（胸/三头/腹/背/二头/腿/臀/肩），面向动作库内部筛选。
 *
 * 两者关系由 `BodyCategory.detailParts` 定义；创建计划时"按大类挑动作"就是查 [bodyPart]。
 *
 * ## 器械与资源字段
 * - [equipment] 存英文码（见 `ExerciseEquipment.code`），**无器械 = `NONE`**；
 * - [image] / [videoUrl] 是**预留字段**，当前统一写空串，后续接资源再填。
 *
 * `name` 唯一：同名动作只入库一次。
 *
 * ## ⚠️ 写入纪律
 * **禁止对本表使用 `OnConflictStrategy.REPLACE`** —— REPLACE 语义是「删旧行 + 插新行」，
 * 自增 `id` 会变；而 `workout_exercises.exerciseId` 按 id 硬引用，
 * 结果是所有已保存计划的训练动作全部指向不存在的 id，训练日动作变空白。
 *
 * 补字段请走 `ExerciseDao.update()`（见 `WorkoutCatalogRepository.seedExerciseLibrary`）。
 */
@Entity(
    tableName = "exercises",
    indices = [Index(value = ["name"], unique = true)]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 动作名，如「俯卧撑」。 */
    val name: String,

    /** 具体部位（中文，如「胸」「三头」）。 */
    val bodyPart: String,

    /** 难度，取值见 `ExerciseDifficulty.code`。 */
    val difficulty: String,

    /** 动作要领（复用旧计划里的 note）。 */
    val description: String,

    /** 图片资源位：**预留字段**，当前统一写空串，后续阶段再补资源。 */
    val image: String = "",

    // ---------------- v2 阶段 3 新增 ----------------

    /** 训练大类，取值见 `BodyCategory.label`（胸部/背部/肩部/腿部/臀部/手臂/腹部）。 */
    val category: String = "",

    /** 具体目标肌肉，如「胸」「三头」「腹直肌」。 */
    val targetMuscle: String = "",

    /** 器械，取值见 `ExerciseEquipment.code`。 */
    val equipment: String = EQUIPMENT_NONE,

    /** 动作步骤，按行分隔（每行一步）。 */
    val steps: String = "",

    /** 注意事项。 */
    val tips: String = "",

    /** 视频地址：**预留字段**。 */
    val videoUrl: String = "",
) {
    companion object {
        /** 无器械（等价于 `ExerciseEquipment.code`，此处用常量避免 entity 反向依赖 model 层）。 */
        const val EQUIPMENT_NONE: String = "NONE"
    }
}