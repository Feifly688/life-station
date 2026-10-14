package com.feiqi.data.model

import com.feiqi.data.entity.ExerciseEntity

/**
 * **动作库种子**（健身 v2 第 3 阶段）。
 *
 * ## 为什么独立于 [WorkoutPlanSeed]
 * 阶段 1 的动作库来自官方 30 天计划的动作去重，只填了「部位 / 难度 / 要领」，
 * 缺大类、目标肌肉、器械、步骤、注意事项。
 * 阶段 3 起动作库成为**用户可见的独立功能**，必须有一份完整的、自洽的元数据，
 * 因此单独维护本文件。
 *
 * ## 一致性约束（有单测锁定）
 * 1. `name` 全局唯一；
 * 2. `category` 必须是 [BodyCategory] 的 7 类之一；
 * 3. `bodyPart`（具体部位）必须落在该 `category` 的 [BodyCategory.detailParts] 内；
 * 4. `targetMuscle`（展示用目标肌肉）非空；
 * 5. `steps` / `tips` 非空（动作详情页直接渲染）。
 *
 * ## 与官方计划的关系
 * 官方计划的动作名与本文件**同源**（都来自 `WORKOUT_PLANS`），
 * [WorkoutPlanSeed] 会复用本文件的元数据，避免两处重复维护。
 */
object ExerciseLibrarySeed {

    /** 默认器械：无器械（与 `ExerciseEquipment.NONE.code` 同值，此处用字面量保持编译期常量）。 */
    private const val NONE = "NONE"

    /**
     * 完整动作库（19 个动作，7 大类）。
     *
     * 数量刻意不凑：第 1 版重点是**结构完整**而非数量，
     * 后续按需增量追加（窄距俯卧撑 / 锤式弯举 / 反向臂屈伸 / 臀推 / 蚌式开合…）。
     */
    val exercises: List<ExerciseEntity> = listOf(
        // ---------------- 胸部 4 ----------------
        ex(
            name = "俯卧撑", category = BodyCategory.CHEST, bodyPart = "胸",
            muscle = "胸大肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "双手撑地、身体呈直线，做起落撑。胸部与手臂同时参与，最经典的居家胸肌动作。",
            steps = "双手比肩略宽撑地，身体从头到脚跟成一条直线\n屈肘下降身体至胸口接近地面\n手掌发力推起，伸直手臂但不要锁死肘关节",
            tips = "不要塌腰，核心收紧；肘部约与躯干呈 45°，不要完全外展",
        ),
        ex(
            name = "宽距俯卧撑", category = BodyCategory.CHEST, bodyPart = "胸",
            muscle = "胸大肌", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "双手间距明显宽于肩的外推，对胸外侧刺激更强。",
            steps = "双手间距约为肩宽的 1.5 倍撑地\n屈肘下降，肘部向外打开\n推起时感受胸部外侧发力",
            tips = "难度高于标准俯卧撑；腰部不要下沉，必要时先从跪姿开始",
        ),
        ex(
            name = "钻石俯卧撑", category = BodyCategory.CHEST, bodyPart = "三头",
            muscle = "肱三头肌", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "双手在胸口正下方合拢成菱形，主要刺激三头肌。",
            steps = "双手食指与拇指相触，在胸口正下方撑地\n屈肘下降，肘部贴近身体两侧\n三头发力推起",
            tips = "肘部不要外展；做不了标准动作可以改跪姿",
        ),
        ex(
            name = "跪姿俯卧撑", category = BodyCategory.CHEST, bodyPart = "胸",
            muscle = "胸大肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "膝盖着地的俯卧撑，降低负荷，适合初学者或核心力量不足者。",
            steps = "膝盖着地，双手比肩略宽撑地\n屈肘下降身体至胸口接近地面\n推起还原",
            tips = "躯干仍需保持一条直线，不要撅屁股",
        ),

        // ---------------- 背部 3 ----------------
        ex(
            name = "超人式", category = BodyCategory.BACK, bodyPart = "背",
            muscle = "竖脊肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "俯卧同时抬起手脚，激活整个背部链条。不需要任何器械。",
            steps = "俯卧床上或垫上，双手前伸\n同时抬起右手与左腿（交替进行）\n顶端停留 1 秒后缓慢落下",
            tips = "不要用力甩腿，抬到刚好离开地面即可；颈部保持中立",
        ),
        ex(
            name = "Y字伸展", category = BodyCategory.BACK, bodyPart = "背",
            muscle = "斜方肌下束", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "俯卧双臂呈 Y 字抬起，刺激下斜方与肩胛稳定肌。",
            steps = "俯卧，双臂前伸与身体约呈 45 度\n肩胛下沉后收，双臂同时抬离地面\n停1 秒后缓慢下放",
            tips = "不要耸肩，肩胛主动下沉比抬得高更重要",
        ),
        ex(
            name = "毛巾划船", category = BodyCategory.BACK, bodyPart = "背",
            muscle = "背阔肌", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "用毛巾绕过固定物模拟划船机，是居家练背最有效的动作之一。",
            steps = "毛巾绕过门把手或桌脚，双手各握一端\n双脚前撤，身体后倾绷紧\n拉近胸口，肩胛先后缩再收",
            tips = "毛巾务必固定牢靠；身体不要随发力前后晃动",
        ),

        // ---------------- 肩部 2 ----------------
        ex(
            name = "Pike 俯卧撑", category = BodyCategory.SHOULDER, bodyPart = "肩",
            muscle = "三角肌前束", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "臀部抬高成倒 V 形的俯卧撑，负荷更多压在肩部。",
            steps = "双手撑地，臀部抬高，身体成倒 V 形\n屈肘让头部向两手之间靠近\n推起还原",
            tips = "臀部抬得越高，肩部负荷越大；颈部保持中立不要塌头",
        ),
        ex(
            name = "侧平举", category = BodyCategory.SHOULDER, bodyPart = "肩",
            muscle = "三角肌中束", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "手臂侧向抬起至肩高，主要塑造肩部轮廓。无器械版可用水瓶。",
            steps = "双手持水瓶（或空手）自然下垂\n手肘微屈，手臂向两侧匀速抬至肩高\n缓慢下放至起始位",
            tips = "手肘始终微屈固定；抬到肩高即可，不要耸肩借力",
        ),

        // ---------------- 腿部 3 ----------------
        ex(
            name = "深蹲", category = BodyCategory.LEG, bodyPart = "腿",
            muscle = "股四头肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "下肢训练的核心动作，臀部、大腿前侧同时参与。",
            steps = "双脚与肩同宽站立，脚尖略外开\n臀部向后向下坐，同时膝盖对齐脚尖方向\n蹲到大腿接近平行后脚跟发力站起",
            tips = "膝盖不要内扣；重心落在脚跟中部，全程保持背部挺直",
        ),
        ex(
            name = "箭步蹲", category = BodyCategory.LEG, bodyPart = "腿",
            muscle = "股四头肌", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "前后分腿蹲，同时刺激单侧腿部与臀部，纠正左右不均。",
            steps = "站立，双手叉腰或持水瓶\n一腿向前迈出一大步，同时下蹲至后腿膝盖接近地面\n前腿发力站起，换另一侧",
            tips = "上身保持直立，不要前倾；前膝不超过脚尖过多",
        ),
        ex(
            name = "保加利亚分腿蹲", category = BodyCategory.LEG, bodyPart = "腿",
            muscle = "股四头肌", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "后脚搭在沙发或椅子上的分腿蹲，负荷大、刺激强。",
            steps = "背对沙发，一脚后撤搭在沙发边缘\n前腿站立，身体垂直下沉至后膝接近地面\n前腿发力起身，换另一侧",
            tips = "需要稳固不滑动的支撑物；重心尽量落在前脚跟",
        ),

        // ---------------- 臀部 1 ----------------
        ex(
            name = "臀桥", category = BodyCategory.HIP, bodyPart = "臀",
            muscle = "臀大肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "仰卧抬髋激活臀部，改善久坐导致的臀肌失忆。",
            steps = "仰卧屈膝，双脚踩地与髋同宽\n臀部发力把髋部顶起至肩-髋-膝成直线\n顶峰夹紧 1 秒后缓慢落下",
            tips = "用臀发力而不是腰；顶峰不要过度挺腰",
        ),

        // ---------------- 腹部 4 ----------------
        ex(
            name = "卷腹", category = BodyCategory.CORE, bodyPart = "腹",
            muscle = "腹直肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "仰卧屈膝卷起上半身，最基础的腹部动作。",
            steps = "仰卧屈膝，双脚踩地，双手轻放耳侧\n呼气同时卷起上背至肩胛离地\n吸气缓慢下放但不完全落地",
            tips = "不要用手拉脖子；下背始终贴地，不要拱起",
        ),
        ex(
            name = "俄罗斯转体", category = BodyCategory.CORE, bodyPart = "腹",
            muscle = "腹斜肌", difficulty = ExerciseDifficulty.INTERMEDIATE,
            desc = "坐姿躯干旋转，主要刺激腹部两侧的腹斜肌。",
            steps = "坐地屈膝，上身后倾约 45 度，双脚可离地\n双手交握，躯干向一侧转动\n左右交替进行",
            tips = "转动的是躯干不是手臂；腰不要塌，保持腹部张力",
        ),
        ex(
            name = "平板支撑", category = BodyCategory.CORE, bodyPart = "腹",
            muscle = "腹横肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "静态核心等长收缩，同时训练稳定性与耐力。",
            steps = "肘撑地，脚尖撑地，身体成一条直线\n收紧腹部与臀部，持续保持 30~60 秒\n缓慢放松还原",
            tips = "腰部不要塌陷或反弓；呼吸不要憋气",
        ),
        ex(
            name = "登山跑", category = BodyCategory.CORE, bodyPart = "腹",
            muscle = "核心稳定", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "俯卧交替快速屈膝收腹，同时带动心肺。",
            steps = "俯卧，双手撑地呈俯卧撑起始位\n交替快速收膝至胸口\n保持肩部在手腕正上方",
            tips = "速度可以快但动作不能垮；腰不要下沉",
        ),

        // ---------------- 手臂 2 ----------------
        ex(
            name = "二头弯举", category = BodyCategory.ARM, bodyPart = "二头",
            muscle = "肱二头肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "屈肘举起重物，塑造手臂前侧线条。",
            steps = "站立，手持水瓶（或空手）自然下垂\n固定大臂，屈肘将前臂举至肩部高度\n缓慢下放至完全伸直",
            tips = "大臂夹紧身体不要前后摆动借力；下放过程要慢",
        ),
        ex(
            name = "臂屈伸", category = BodyCategory.ARM, bodyPart = "三头",
            muscle = "肱三头肌", difficulty = ExerciseDifficulty.BEGINNER,
            desc = "椅子后撑式的三头肌训练，居家即可完成。",
            steps = "背对椅子，双手撑住椅面，身体前倾\n屈肘让身体下沉，三头控制下降\n三头发力撑起还原",
            tips = "椅子必须稳固防滑；肘部向后不外展",
        ),
    )

    /** 按动作名取种子（不存在返回 null）。 */
    fun byName(name: String): ExerciseEntity? = exercises.firstOrNull { it.name == name }

    /**
     * 具体部位 → 训练大类（用于给官方计划里尚未收录进本文件的动作补大类）。
     *
     * 同一个具体部位可能归属多个大类（如「三头」既在胸部也在手臂），
     * 这里返回**第一个**匹配项，保证映射确定、可单测。
     */
    fun categoryOfBodyPart(bodyPart: String): BodyCategory? =
        BodyCategory.entries.firstOrNull { bodyPart in it.detailParts }

    /**
     * 构造一条种子记录。
     *
     * `image` / `videoUrl` 保持空串（预留字段）。
     */
    private fun ex(
        name: String,
        category: BodyCategory,
        bodyPart: String,
        muscle: String,
        difficulty: ExerciseDifficulty,
        desc: String,
        steps: String,
        tips: String,
        equipment: String = NONE,
    ): ExerciseEntity = ExerciseEntity(
        id = 0L,
        name = name,
        bodyPart = bodyPart,
        difficulty = difficulty.code,
        description = desc,
        image = "",
        category = category.label,
        targetMuscle = muscle,
        equipment = equipment,
        steps = steps,
        tips = tips,
        videoUrl = "",
    )
}