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
     * 卡片中心点（屏幕坐标）。用普通数据类而不是 Compose 的 `Offset`，
     * 让规则层与 UI 解耦、可直接单测。
     */
    data class CellCenter(val x: Float, val y: Float)

    /**
     * 网格让位判定（**2D 版**）：被拖卡片中心**越过相邻卡片中心**才让位。
     *
     * 「越过」的含义：
     * - 纵向上被拖中心已明显低于邻卡中心（超过 [rowTolerance]）→ 视为跨行越过；或
     * - 与邻卡处于同一行（纵向差在 [rowTolerance] 内）且横向上已在其右侧 → 视为同行越过。
     *
     * 2D 判定让"网格里左右移动"与"上下换行"都正确：左右拖只会在同行内左右换位，
     * 不会莫名跑到上下行；上下拖跨行时再按行比较。
     *
     * 与旧版"位移 > 邻块整块高度"是同一语义的推广（等行等高的两卡，两者等价），
     * 因此仍满足"不提前让位、换位后不来回跳"。
     *
     * @param dragged 被拖卡片**当前可见中心**（含拖拽位移）
     * @param next 下一张卡片的中心（已到队尾时传 null）
     * @param prev 上一张卡片的中心（已到队首时传 null）
     * @param nextSameRow 下一张是否与**被拖卡片的槽位**同一行（由父层用槽位矩形判定，不能靠中心距离猜）
     * @param prevSameRow 上一张是否与被拖卡片的槽位同一行
     * @return +1 与下一张交换、-1 与上一张交换、0 不动
     */
    fun gridSwapDirection(
        dragged: CellCenter,
        next: CellCenter?,
        prev: CellCenter?,
        nextSameRow: Boolean,
        prevSameRow: Boolean
    ): Int {
        fun passed(target: CellCenter, sameRow: Boolean): Boolean =
            if (sameRow) {
                // 同一行：比横向（左右拖动只会在同行内左右换位，不会跑到上下行）
                dragged.x > target.x
            } else {
                // 不同行：比纵向（被拖中心更低才算越过）
                dragged.y > target.y
            }

        return when {
            next != null && passed(next, nextSameRow) -> 1
            prev != null && !passed(prev, prevSameRow) -> -1
            else -> 0
        }
    }

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
