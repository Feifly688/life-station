package com.feiqi.ui.home

/**
 * 首页拖拽排序的**纯计算部分**（与 Compose 无关，可直接单测）。
 *
 * 之所以单独抽出来：这里的两条规则正是「卡片被拖到边缘后丢失、只剩空白」的成因，
 * 放在纯函数里就能用单测把它们钉死，避免回归。
 */
internal object HomeDragRules {

    /**
     * 位移钳制：位移绝对值不超过**被拖卡片自身高度**。
     *
     * 为什么上限是"一张卡片的高度"：卡片视觉位置 = 槽位 + 位移。位移不超过一张卡片高，
     * 就保证**槽位与视觉位置始终重叠** —— 槽位不会跑到滚动视口之外被回收，卡片也就不会"消失"。
     */
    fun clampOffset(offset: Float, cardHeight: Float): Float =
        if (cardHeight <= 0f) 0f else offset.coerceIn(-cardHeight, cardHeight)

    /**
     * 视口钳制：保证卡片的**可见矩形**始终落在列表可视区内。
     *
     * 超出上边界 → 把位移往下推；超出下边界 → 往上推。
     * 这是"拖到屏幕顶端/底端附近卡片被滚动容器裁掉、看起来消失/显示异常"的直接修复。
     *
     * @param visualTop 卡片当前**可见矩形顶部**（根坐标，含位移）。
     * @param height 卡片高度。
     * @param listTop / [listBottom] 可视区上下边界（根坐标）。
     */
    fun clampToViewport(
        offset: Float,
        visualTop: Float,
        height: Float,
        listTop: Float,
        listBottom: Float
    ): Float {
        if (height <= 0f || listBottom <= listTop) return offset
        val viewport = listBottom - listTop
        return when {
            visualTop < listTop -> offset + (listTop - visualTop)
            // 卡片比视口还高时只保证顶部可见，避免上下同时钳制来回抖
            height <= viewport && visualTop + height > listBottom ->
                offset - (visualTop + height - listBottom)

            else -> offset
        }
    }

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
