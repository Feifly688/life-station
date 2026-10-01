package com.feiqi.ui.home

/**
 * 首页网格的**布局规则**（纯函数，与 Compose 解耦，可直接单测）。
 *
 * 收敛两件事：
 * 1. **列数自适应**：超窄屏降为单列，避免双列被挤扁（长标题截断、卡片过窄）；
 * 2. **半宽瓷砖不成对时自动补满整行**：声明为 1 列的卡片如果后面没有另一张 1 列卡片，
 *    就提升为整行 —— 否则网格右半边会留出空白（视觉上的"错位/空隙"）。
 */
object HomeGridRules {

    /** 常规列数（手机竖屏）。 */
    const val DEFAULT_COLUMNS = 2

    /** 低于此宽度改用单列：320~340dp 的窄屏在双列下列宽不足，文字会被大量截断。 */
    const val SINGLE_COLUMN_MAX_WIDTH_DP = 340

    /** 按可用宽度决定列数。 */
    fun columnsFor(widthDp: Float): Int =
        if (widthDp < SINGLE_COLUMN_MAX_WIDTH_DP) 1 else DEFAULT_COLUMNS

    /**
     * 计算每张卡片的**实际占列数**。
     *
     * @param order 各卡片的声明占列数（1 = 半宽瓷砖，2 = 整行），顺序与展示顺序一致
     * @param columns 当前列数（见 [columnsFor]）
     * @return 与 [order] 等长的实际占列数：单列时恒为 1；双列时孤立的 1 列卡片提升为 2
     */
    fun spansFor(order: List<Int>, columns: Int): List<Int> {
        if (columns <= 1) return List(order.size) { 1 }
        val result = order.toMutableList()
        var i = 0
        while (i < order.size) {
            if (order[i] == 1) {
                if (order.getOrNull(i + 1) == 1) {
                    i++ // 成对：两张半宽共占一行，跳过下一张
                } else {
                    result[i] = columns // 孤立半宽 → 补满整行，避免右半边留白
                }
            }
            i++
        }
        return result
    }
}
