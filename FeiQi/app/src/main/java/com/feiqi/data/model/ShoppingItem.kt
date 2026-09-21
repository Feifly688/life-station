package com.feiqi.data.model

/**
 * 购买物品条目（首页「购买物品」入口）。
 * 通过 DataStore 以 JSON 数组形式持久化，分为待买 / 已买两部分。
 */
data class ShoppingItem(
    val id: String,
    val name: String,
    val price: Double = 0.0,
    val bought: Boolean = false,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val boughtAt: Long? = null
)
