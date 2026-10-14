package com.feiqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.feiqi.data.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 动作库（v2）读写。
 *
 * ## ⚠️ 写入策略（v2 阶段 3 起）
 * 本表**禁止 REPLACE**：`name` 唯一索引 + REPLACE 会「删旧行 + 插新行」，
 * 自增 id 变化后`workout_exercises.exerciseId` 全部悬空，
 * 已保存计划的训练动作会变空白。
 *
 * - [insertAll]：仅用于**空库首次种子**（官方计划初始化），此时无任何引用，安全；
 * - 补字段/ 补内容一律用 [update]（保 id）或 [insertIgnore]（保已有行）。
 */
@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY id ASC")
    fun observeAll(): Flow<List<ExerciseEntity>>

    /** 一次性取全库（种子补全用，避免在 Flow 里做写操作）。 */
    @Query("SELECT * FROM exercises ORDER BY id ASC")
    suspend fun getAllOnce(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE name = :name")
    suspend fun getByName(name: String): ExerciseEntity?

    /** 按 id 批量取（装配某一天的动作时用）。 */
    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ExerciseEntity>

    /** 按部位批量取（生成用户计划时按部位挑动作）。 */
    @Query("SELECT * FROM exercises WHERE bodyPart IN (:parts) ORDER BY id ASC")
    suspend fun getByBodyParts(parts: List<String>): List<ExerciseEntity>

    /** 按训练大类取（动作库分类卡 / 动作选择页用）。 */
    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY id ASC")
    fun observeByCategory(category: String): Flow<List<ExerciseEntity>>

    /** 按训练大类 + 具体部位取（动作选择页「本类动作」）。 */
    @Query("SELECT * FROM exercises WHERE category = :category AND bodyPart IN (:parts) ORDER BY id ASC")
    suspend fun getByCategoryAndParts(category: String, parts: List<String>): List<ExerciseEntity>

    /** 按 id 取单个（动作详情页）。 */
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    /** 空库首次种子用：同名覆盖（此时无引用，安全）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    /**
     * 已存在则跳过，**保留原 id**。
     * 动作库增量补种走这条，绝不能用 REPLACE。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(exercise: ExerciseEntity): Long

    /** 补字段：**保 id** 更新（`seedExerciseLibrary` 用）。 */
    @Update
    suspend fun update(exercise: ExerciseEntity)
}