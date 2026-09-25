package com.feiqi.data.model

/**
 * 首页可排序区块的**唯一定义**：顺序、默认值、以及每个区块的数据来源 / 交互 / 状态 / 边界。
 *
 * 首页采用「区块」为单位排序（不是单个小卡片）：统计行、待办区、购物区等作为一个整体移动，
 * 避免把同一区块内部拆散后出现「半截区块」。
 *
 * | 区块 | 数据来源（Room / DataStore / 内存） | 交互 | 状态变化 | 边界情况 |
 * | --- | --- | --- | --- | --- |
 * | [LIFE_INDEX] | `HomeUiState.lifeIndex`（由当月支出/体重/待办实时算出） | 纯展示，无点击 | 任一来源数据变化 → 重新计算并刷新 | 无数据时显示默认分与描述文案 |
 * | [STATS] | `monthExpense` / `latestWeight` / `todayTodoCount` | 点击分别跳记账 / 健康 / 日程页 | 三个来源各自 Flow 更新 | 无体重记录显示 `--`；金额为 0 显示 `¥0` |
 * | [TODAY_TODO] | `todayTodoItems`（日程表按今天聚合，含单条与清单） | 点条目 → 日程页；空态有引导卡片 | 完成/取消、增删改后自动刷新 | 空 → 显示空态；过期条目不进首页（首页只取今天） |
 * | [SHOPPING] | `shoppingItems`（DataStore，按 bought 分组） | 待买/已买切换、勾选、删除单条、清空已买、新增 | 勾选即改分组；清空需二次确认 | 两组都空 → 列表区显示空态；已买为空时清空按钮不显示 |
 * | [MEDIA] | `recentMedia`（书影音表最近 4 条） | 点击预览弹窗 | 新增/编辑后自动刷新 | 为空 → 显示空态；封面文件缺失回退占位 |
 * | [RECENT_TRACE] | `recentRecords`（记账表最近 5 条） | 点击/长按 → 编辑记录弹窗；右上角「全部」→ 记账页 | 增删改后自动刷新 | 为空 → 显示空态 |
 * | [QUOTE] | `quoteState`（远程/缓存/内置三层降级） | 纯展示（长按无操作） | 每周首次打开 App 静默更新，更新后自动刷新 | 内容为空 → 回退内置语录；作者缺失只显示正文 |
 *
 * **顺序与状态的关系**：区块顺序只影响渲染次序，不参与数据计算 —— 每个区块的数据与回调
 * 都由 `HomeViewModel` 的 `uiState` 独立提供，**不依赖自己在列表中的位置**，
 * 因此任意顺序下交互行为完全一致（这条是「布局调整后功能仍正确」的实现前提）。
 */
enum class HomeCard(val id: String) {
    LIFE_INDEX("life_index"),
    STATS("stats"),
    TODAY_TODO("today_todo"),
    SHOPPING("shopping"),
    MEDIA("media"),
    RECENT_TRACE("recent_trace"),
    QUOTE("quote");

    companion object {

        /** 默认顺序（首次安装 / 存储为空时使用）。 */
        val DEFAULT_ORDER: List<HomeCard> = entries.toList()

        /**
         * 把存储的 id 序列规整为一条完整、可用的顺序：
         * - 未知 id（旧版本遗留 / 手工改坏）直接丢弃；
         * - 重复 id 只保留首次出现；
         * - 缺失的区块按 [DEFAULT_ORDER] 顺序补到末尾。
         *
         * 这样无论存储内容如何，**渲染结果永远是全部区块且不重不漏**（边界安全）。
         */
        fun normalize(ids: List<String>): List<HomeCard> {
            val seen = LinkedHashSet<HomeCard>()
            ids.forEach { id -> entries.firstOrNull { it.id == id }?.let(seen::add) }
            DEFAULT_ORDER.forEach(seen::add)
            return seen.toList()
        }

        /**
         * 把 [from] 位置的区块移动到 [to] 位置（纯函数，供拖拽与单测使用）。
         * 下标越界或原地移动时原样返回，保证调用方无需自己做边界判断。
         */
        fun move(order: List<HomeCard>, from: Int, to: Int): List<HomeCard> {
            if (from == to) return order
            if (from !in order.indices || to !in order.indices) return order
            return order.toMutableList().apply { add(to, removeAt(from)) }
        }
    }
}
