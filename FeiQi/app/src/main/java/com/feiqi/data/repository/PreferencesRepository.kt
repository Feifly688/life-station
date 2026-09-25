package com.feiqi.data.repository

import android.content.Context
import com.feiqi.data.datastore.PreferencesDataStore
import com.feiqi.data.model.HomeCard
import com.feiqi.data.model.ShoppingItem
import com.feiqi.data.model.UserProfile
import com.feiqi.data.model.WeightRecord
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import com.feiqi.utils.AppLogger

class PreferencesRepository(context: Context) {

    private val dataStore = PreferencesDataStore(context)
    private val gson = Gson()
    private val tag = "PreferencesRepository"

    /**
     * 首页卡片排列顺序。存储的是逗号分隔的 id；解析时统一走 [HomeCard.normalize]，
     * 因此「新增了卡片种类」「存储串损坏」都不会丢卡片，只会把缺失项补到末尾。
     */
    val homeCardOrder: Flow<List<HomeCard>> = dataStore.homeCardOrder.map { raw ->
        HomeCard.normalize(raw.split(",").map { it.trim() }.filter { it.isNotEmpty() })
    }

    suspend fun setHomeCardOrder(order: List<HomeCard>) {
        dataStore.setHomeCardOrder(order.joinToString(",") { it.id })
    }

    val monthlyBudget: Flow<Double> = dataStore.monthlyBudget

    suspend fun setMonthlyBudget(value: Double) = dataStore.setMonthlyBudget(value)

    val notificationEnabled: Flow<Boolean> = dataStore.notificationEnabled

    suspend fun setNotificationEnabled(enabled: Boolean) = dataStore.setNotificationEnabled(enabled)

    val habitReminderEnabled: Flow<Boolean> = dataStore.habitReminderEnabled

    suspend fun setHabitReminderEnabled(enabled: Boolean) = dataStore.setHabitReminderEnabled(enabled)

    /** 健康页：体重记录列表（按日期倒序）。 */
    val weightRecords: Flow<List<WeightRecord>> = dataStore.weightRecordsJson.map { json ->
        parseWeightRecords(json)
    }

    /** 最新一条体重记录，用于首页展示。 */
    val latestWeight: Flow<WeightRecord?> = weightRecords.map { it.maxByOrNull { r -> r.date } }

    suspend fun addWeightRecord(weight: Double, date: LocalDate = LocalDate.now(), note: String = "") {
        val current = parseWeightRecords(dataStore.weightRecordsJson.first())
        val updated = current + WeightRecord(
            id = UUID.randomUUID().toString(),
            weight = weight,
            date = date,
            note = note
        )
        dataStore.setWeightRecordsJson(serializeWeightRecords(updated))
    }

    suspend fun deleteWeightRecord(id: String) {
        val current = parseWeightRecords(dataStore.weightRecordsJson.first())
        val updated = current.filter { it.id != id }
        dataStore.setWeightRecordsJson(serializeWeightRecords(updated))
    }

    /** 按 id 更新一条体重记录（用于补记/修正日期与数值）。 */
    suspend fun updateWeightRecord(record: WeightRecord) {
        val current = parseWeightRecords(dataStore.weightRecordsJson.first())
        val updated = current.map { if (it.id == record.id) record else it }
        dataStore.setWeightRecordsJson(serializeWeightRecords(updated))
    }

    private fun parseWeightRecords(json: String): List<WeightRecord> {
        val type = object : TypeToken<List<WeightRecord>>() {}.type
        return try {
            gson.fromJson<List<WeightRecord>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            AppLogger.w(tag, "体重记录解析失败，已降级为空列表", e)
            emptyList()
        }
    }

    private fun serializeWeightRecords(records: List<WeightRecord>): String {
        return gson.toJson(records)
    }

    // ---------------- 个人基本信息（健康页 BMI） ----------------

    val userProfile: Flow<UserProfile> = dataStore.userProfileJson.map { json ->
        if (json.isBlank()) UserProfile() else runCatching {
            gson.fromJson(json, UserProfile::class.java) ?: UserProfile()
        }.getOrDefault(UserProfile())
    }

    suspend fun setUserProfile(profile: UserProfile) {
        dataStore.setUserProfileJson(gson.toJson(profile))
    }

    // ---------------- 购买物品（待买 / 已买） ----------------

    val shoppingItems: Flow<List<ShoppingItem>> = dataStore.shoppingItemsJson.map { json ->
        parseShoppingItems(json)
    }

    suspend fun addShoppingItem(name: String, price: Double = 0.0, note: String = "") {
        val current = parseShoppingItems(dataStore.shoppingItemsJson.first())
        val updated = current + ShoppingItem(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            price = price,
            note = note.trim()
        )
        dataStore.setShoppingItemsJson(gson.toJson(updated))
    }

    /** 勾选 / 取消勾选：勾选后归入「已买」。 */
    suspend fun toggleShoppingItem(id: String) {
        val current = parseShoppingItems(dataStore.shoppingItemsJson.first())
        val updated = current.map {
            if (it.id == id) {
                val bought = !it.bought
                it.copy(bought = bought, boughtAt = if (bought) System.currentTimeMillis() else null)
            } else it
        }
        dataStore.setShoppingItemsJson(gson.toJson(updated))
    }

    suspend fun updateShoppingItem(item: ShoppingItem) {
        val current = parseShoppingItems(dataStore.shoppingItemsJson.first())
        val updated = current.map { if (it.id == item.id) item else it }
        dataStore.setShoppingItemsJson(gson.toJson(updated))
    }

    suspend fun deleteShoppingItem(id: String) {
        val current = parseShoppingItems(dataStore.shoppingItemsJson.first())
        dataStore.setShoppingItemsJson(gson.toJson(current.filter { it.id != id }))
    }

    /** 清空「已买」分区。 */
    suspend fun clearBoughtItems() {
        val current = parseShoppingItems(dataStore.shoppingItemsJson.first())
        dataStore.setShoppingItemsJson(gson.toJson(current.filter { !it.bought }))
    }

    private fun parseShoppingItems(json: String): List<ShoppingItem> {
        val type = object : TypeToken<List<ShoppingItem>>() {}.type
        return try {
            gson.fromJson<List<ShoppingItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            AppLogger.w(tag, "购物清单解析失败，已降级为空列表", e)
            emptyList()
        }
    }
}
