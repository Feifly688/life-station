package com.feiqi.ui.home

/**
 * 首页拖拽排序的**纯计算部分**（与 Compose 无关，可直接单测）。
 *
 * 之所以单独抽出来：这里的两条规则正是「卡片被拖到边缘后丢失、只剩空白」的成因，
 * 放在纯函数里就能用单测把它们钉死，避免回归。
 */
internal object HomeDragRules {

    /** 位移上限系数：正常情况下每跨过一块就会扣掉一块的高度，位移天然有界，这里是最后一道保险。 */
    private const val OFFSET_LIMIT_FACTOR = 1.5f

    /**
     * 位移钳制：无论输入多大，返回值都不会超过 `maxBlockHeight × 1.5`。
     *
     * **不变量**：被拖卡片永远只在自身槽位附近偏移不超过一块半的高度 ——
     * 这是"卡片不会被推出可视区（即消失/空白）"的兜底。
     */
    fun clampOffset(offset: Float, maxBlockHeight: Float): Float =
        if (maxBlockHeight <= 0f) 0f else offset.coerceIn(
            -maxBlockHeight * OFFSET_LIMIT_FACTOR,
            maxBlockHeight * OFFSET_LIMIT_FACTOR
        )

    /**
     * 每帧滚动量（像素，正 = 向下滚，负 = 向上滚）。
     *
     * 规则：
     * - 指针在可视区中间 → 0（不滚）；
     * - 指针进入上/下 [zone] 感应区 → 按贴近程度在 [minStep]~[maxStep] 之间加速；
     * - **对应方向已无相邻区块（`canScrollUp` / `canScrollDown` 为 false）→ 恒为 0**：
     *   此时滚得动也换不了位，继续滚只会让位移单方面增长、卡片脱离槽位。
     */
    fun autoScrollStep(
        pointerRootY: Float,
        listTop: Float,
        listBottom: Float,
        zone: Float,
        minStep: Float,
        maxStep: Float,
        canScrollUp: Boolean,
        canScrollDown: Boolean
    ): Float {
        if (zone <= 0f) return 0f
        val span = (maxStep - minStep).coerceAtLeast(0f)
        return when {
            canScrollUp && pointerRootY < listTop + zone -> {
                val ratio = ((listTop + zone - pointerRootY) / zone).coerceIn(0f, 1f)
                -(minStep + ratio * span)
            }

            canScrollDown && pointerRootY > listBottom - zone -> {
                val ratio = ((pointerRootY - (listBottom - zone)) / zone).coerceIn(0f, 1f)
                minStep + ratio * span
            }

            else -> 0f
        }
    }
}
