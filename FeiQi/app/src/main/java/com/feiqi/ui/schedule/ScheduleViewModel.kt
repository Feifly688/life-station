package com.feiqi.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feiqi.data.model.Recurrence
import com.feiqi.data.model.Schedule
import com.feiqi.data.model.ScheduleFilter
import com.feiqi.data.model.ScheduleListItem
import com.feiqi.data.model.ScheduleUiState
import com.feiqi.data.repository.ScheduleRepository
import com.feiqi.utils.DateUtils
import com.feiqi.utils.RecurrenceUtils
import com.feiqi.utils.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    private val schedules = repository.getAll()

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    private val _filter = MutableStateFlow(ScheduleFilter.ALL)

    val uiState: StateFlow<ScheduleUiState> = combine(
        schedules,
        _selectedDate,
        _filter
    ) { all, selected, filter ->
        val today = DateUtils.today()
        val end7 = today.plusDays(7)

        val listItems = buildListItems(all)

        val todayCount = all.count { it.date == today && !it.completed && it.listId == null }
        // 只有「已设置提醒」的条目才判定逾期：未设提醒即无时间状态，由用户手动完成或删除。
        val overdueCount = all.count {
            it.date < today && !it.completed && it.listId == null && it.reminder
        }
        val upcoming7Count = all.count {
            it.listId == null && !it.completed && it.date in today..end7
        }
        val completedCount = all.count { it.completed }

        ScheduleUiState(
            schedules = all,
            filteredSchedules = all,
            listItems = listItems,
            todayCount = todayCount,
            overdueCount = overdueCount,
            upcoming7Count = upcoming7Count,
            completedCount = completedCount,
            selectedDate = selected,
            filter = filter
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScheduleUiState()
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun setFilter(filter: ScheduleFilter) {
        _filter.value = filter
    }

    /**
     * 批量添加日程。
     * - 只有一条：作为独立单条日程，不循环。
     * - 多条：聚合成一个清单；清单标题默认「待办清单」。
     *   [recurrence] 仅对清单生效，单条日程强制 NONE；未开启提醒时恒为 NONE。
     */
    fun addSchedules(
        titles: List<String>,
        date: LocalDate,
        time: LocalTime?,
        reminder: Boolean,
        recurrence: Recurrence = Recurrence.NONE,
        listTitle: String = "待办清单"
    ) {
        val trimmed = titles.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) {
            viewModelScope.launch { _events.emit("请填写待办事项") }
            return
        }

        viewModelScope.launch {
            runCatching {
                if (trimmed.size == 1) {
                    val schedule = Schedule(
                        title = trimmed.first(),
                        date = date,
                        time = time,
                        reminder = reminder,
                        listId = null,
                        listTitle = trimmed.first(),
                        recurrence = recurrence
                    )
                    val id = repository.insert(schedule)
                    if (id > 0) reminderScheduler.schedule(schedule.copy(id = id))
                } else {
                    val listId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val schedules = trimmed.mapIndexed { index, title ->
                        Schedule(
                            title = title,
                            date = date,
                            time = time,
                            reminder = reminder,
                            createdAt = now + index,
                            listId = listId,
                            listTitle = listTitle,
                            recurrence = recurrence,
                            itemOrder = index
                        )
                    }
                    val ids = repository.insertBatch(schedules)
                    val inserted = schedules.zip(ids)
                        .map { (schedule, id) -> if (id > 0) schedule.copy(id = id) else schedule }
                    reminderScheduler.scheduleList(listId, inserted)
                }
            }.onSuccess {
                _events.emit("已加入日程")
            }.onFailure {
                _events.emit("添加失败：${it.message}")
            }
        }
    }

    /**
     * 循环清单「手动标记完成」的循环处理：
     * 1. 生成「已完成快照副本」：新 listId（原listId#done#iso今天#时分秒），所有 item
     *    completed=true、completedDate=今天、date=今天、recurrence=NONE（快照不再循环），
     *    time/listTitle 不变 → 进入已完成区，作为本次完成记录留存。
     * 2. 原清单所有 item：按重复规则顺延到下一个应提醒日（每天 +1 天 / 周一至周五跳过周末 /
     *    每周 +7 天 / 每月 +1 月 / 每年 +1 年）、completed=false、completedDate=null、
     *    lastResetDate=null → 留在待完成区继续循环，提醒时刻不变。
     * 3. 重排原清单提醒。
     *
     * 循环清单**不回补历史欠账**：错过的周期直接落到下一个应提醒日。
     * 与 ReminderReceiver（到点只弹通知、不自动循环）+ FeiQiApplication 启动补推配合，
     * 实现用户预期的循环语义。
     */
    private suspend fun advanceRecurringList(
        listId: String,
        group: List<Schedule>,
        today: LocalDate
    ) {
        val todayIso = DateUtils.iso(today)
        // 1. 生成已完成快照副本：新 listId 加 HHmmss 时间戳后缀，确保每次完成独立 listId
        //    （即使同一原清单在同一天内多次完成也不会被 buildListItems 聚合成同一 Group），
        //    用户可按日期+时间逐条查阅每一次的完成记录。
        val snapshotListId = "${listId}#done#${todayIso}#${DateUtils.hms(java.time.LocalTime.now())}"
        val now = System.currentTimeMillis()
        val snapshot = group.mapIndexed { index, item ->
            item.copy(
                id = 0, // 让 Room autoGenerate 新 id
                listId = snapshotListId,
                completed = true,
                completedDate = today,
                date = today,
                recurrence = Recurrence.NONE, // 快照不再循环
                lastResetDate = null,
                itemOrder = index,
                createdAt = now + index
            )
        }
        repository.insertBatch(snapshot)
        // 2. 原清单按重复规则顺延，重置为未完成继续循环。
        val advanced = group.map {
            it.copy(
                date = RecurrenceUtils.nextOccurrence(it.date, it.recurrence),
                completed = false,
                completedDate = null,
                lastResetDate = null
            )
        }
        repository.updateBatch(advanced)
        // 3. 重排原清单提醒（下次提醒=下一个应提醒日的同一时刻）。
        val all = repository.getAll().first()
        reminderScheduler.scheduleList(listId, all)
    }

    /** 循环清单完成后的提示文案：告诉用户下次何时继续提醒。 */
    private fun nextReminderTip(nextDate: LocalDate, time: LocalTime?): String {
        val today = DateUtils.today()
        val day = when (nextDate) {
            today -> "今天"
            today.plusDays(1) -> "明日"
            else -> DateUtils.monthDay(nextDate)
        }
        val at = time?.let { " ${DateUtils.hm(it)}" }.orEmpty()
        return "已完成，$day$at 继续提醒"
    }

    fun toggleComplete(schedule: Schedule) {
        val markCompleted = !schedule.completed
        val today = DateUtils.today()
        // 单条「已设提醒 + 重复」待办：完成时**本条转为完成记录**（不再循环、进已完成列表），
        // 同时新增一条内容相同的副本按重复规则顺延，继续提醒。
        val repeatingSingle = schedule.listId == null && schedule.reminder && schedule.isRecurring
        val updated = schedule.copy(
            completed = markCompleted,
            completedDate = if (markCompleted) today else null,
            // 完成记录不再循环（由副本负责继续），与「清单完成快照」的口径保持一致。
            recurrence = if (markCompleted && repeatingSingle) Recurrence.NONE else schedule.recurrence
        )
        viewModelScope.launch {
            runCatching {
                repository.update(updated)
                if (updated.listId != null) {
                    val listId = updated.listId
                    val all = repository.getAll().first()
                    val group = all.filter { it.listId == listId }
                    if (group.isNotEmpty()) {
                        reminderScheduler.scheduleList(listId, all)
                        if (markCompleted && group.all { it.completed }) {
                            if (group.any { it.isRecurring }) {
                                // 循环清单：生成已完成快照副本 + 原清单按重复规则顺延继续循环。
                                val sample = group.firstOrNull()
                                val nextDate = sample
                                    ?.let { RecurrenceUtils.nextOccurrence(it.date, it.recurrence) }
                                    ?: today
                                advanceRecurringList(listId, group, today)
                                _events.emit(nextReminderTip(nextDate, sample?.time))
                            } else {
                                // 普通清单：统一记录完成日期
                                repository.updateBatch(group.map { it.copy(completedDate = today) })
                                _events.emit("清单「${updated.listTitle}」已完成")
                            }
                        } else if (!markCompleted && group.any { it.completedDate != null }) {
                            // 取消勾选时清空清单的完成日期
                            repository.updateBatch(group.map { it.copy(completedDate = null) })
                        }
                    }
                } else if (markCompleted && repeatingSingle) {
                    // 本条 update 已把完成记录写入已完成区；这里取消它的闹钟，
                    // 再新增一条内容相同的副本（按重复规则顺延提醒时间）继续循环。
                    reminderScheduler.cancel(updated.id)
                    val nextDate = nextRecurringDate(schedule)
                    val successor = schedule.copy(
                        id = 0, // 让 Room autoGenerate 新 id
                        date = nextDate,
                        completed = false,
                        completedDate = null,
                        lastResetDate = null,
                        createdAt = System.currentTimeMillis()
                    )
                    val newId = repository.insert(successor)
                    if (newId > 0) reminderScheduler.schedule(successor.copy(id = newId))
                    _events.emit(nextReminderTip(nextDate, schedule.time))
                } else {
                    reminderScheduler.schedule(updated)
                }
            }.onFailure {
                _events.emit("更新失败：${it.message}")
            }
        }
    }

    /**
     * 单条循环待办完成后，副本的应提醒日。
     * 常规情况即「按规则顺延」（每天 = +1 天、每周 = +7 天…）；逾期多日时 nextOccurrence 可能
     * 仍早于今天，此时按启动补推的口径落到今天，避免副本一出生就是逾期状态。
     */
    private fun nextRecurringDate(schedule: Schedule): LocalDate {
        val today = DateUtils.today()
        val next = RecurrenceUtils.nextOccurrence(schedule.date, schedule.recurrence)
        return if (next < today) {
            RecurrenceUtils.advanceToOnOrAfter(schedule.date, schedule.recurrence, today)
        } else {
            next
        }
    }

    /**
     * 整份清单一次性勾选 / 取消勾选。
     * 之前 UI 层用「逐条 toggle」实现，条件写反会导致点击毫无反应，这里改为 ViewModel 批量写入。
     */
    fun setGroupCompleted(listId: String, completed: Boolean) {
        viewModelScope.launch {
            runCatching {
                val all = repository.getAll().first()
                val today = DateUtils.today()
                val isRecurring = all.any { it.listId == listId && it.isRecurring }
                if (completed && isRecurring) {
                    // 循环清单标记完成：记完成记录 + 按重复规则顺延 + 重置为未完成继续循环。
                    // 用全量 group（含已完成项），不限于「需切换」的 target。
                    val fullGroup = all.filter { it.listId == listId }
                    if (fullGroup.isEmpty()) return@runCatching
                    val sample = fullGroup.firstOrNull()
                    val nextDate = sample
                        ?.let { RecurrenceUtils.nextOccurrence(it.date, it.recurrence) }
                        ?: today
                    advanceRecurringList(listId, fullGroup, today)
                    _events.emit(nextReminderTip(nextDate, sample?.time))
                } else {
                    val target = all.filter { it.listId == listId && it.completed != completed }
                    if (target.isEmpty()) return@runCatching
                    val updated = target.map {
                        it.copy(completed = completed, completedDate = if (completed) today else null)
                    }
                    repository.updateBatch(updated)
                    reminderScheduler.scheduleList(listId, all)
                }
            }.onFailure {
                _events.emit("更新失败：${it.message}")
            }
        }
    }

    /**
     * 编辑**单条日程**：标题 + 提醒（时间 / 重复）。
     *
     * 提醒关闭时清空时间与重复（无时间待办：不参与逾期判定、不排提醒），
     * 提醒开启时写入所选时间与重复规则。清单条目（listId != null）不在此路径编辑——
     * 它们的提醒属于整份清单，统一走 [saveListEdit]。
     */
    fun saveItemEdit(
        schedule: Schedule,
        newTitle: String,
        reminderDateTime: java.time.LocalDateTime?,
        reminderEnabled: Boolean,
        recurrence: Recurrence
    ) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            viewModelScope.launch { _events.emit("待办内容不能为空") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val updated = schedule.copy(
                    title = title,
                    listTitle = if (schedule.listId == null) title else schedule.listTitle,
                    date = reminderDateTime?.toLocalDate() ?: schedule.date,
                    time = if (reminderEnabled) reminderDateTime?.toLocalTime() else null,
                    reminder = reminderEnabled,
                    recurrence = if (reminderEnabled) recurrence else Recurrence.NONE
                )
                repository.update(updated)
                reminderScheduler.schedule(updated)
            }.onSuccess { _events.emit("已保存") }
                .onFailure { _events.emit("保存失败：${it.message}") }
        }
    }

    /** 长按编辑：修改单条日程 / 清单条目的标题。 */
    fun updateTitle(schedule: Schedule, newTitle: String) {        val title = newTitle.trim()
        if (title.isEmpty()) {
            viewModelScope.launch { _events.emit("标题不能为空") }
            return
        }
        if (title == schedule.title) return
        viewModelScope.launch {
            runCatching {
                val updated = if (schedule.listId == null) {
                    schedule.copy(title = title, listTitle = title)
                } else {
                    schedule.copy(title = title)
                }
                repository.update(updated)
                if (updated.listId != null) {
                    val all = repository.getAll().first()
                    reminderScheduler.scheduleList(updated.listId, all)
                } else {
                    reminderScheduler.schedule(updated)
                }
            }.onSuccess { _events.emit("已修改") }
                .onFailure { _events.emit("修改失败：${it.message}") }
        }
    }

    /** 长按清单标题：重命名整个清单。 */
    fun renameList(listId: String, newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            viewModelScope.launch { _events.emit("清单名不能为空") }
            return
        }
        viewModelScope.launch {
            runCatching {
                val all = repository.getAll().first()
                val target = all.filter { it.listId == listId }
                if (target.isEmpty()) return@runCatching
                repository.updateBatch(target.map { it.copy(listTitle = title) })
                reminderScheduler.scheduleList(listId, all.map {
                    if (it.listId == listId) it.copy(listTitle = title) else it
                })
            }.onSuccess { _events.emit("已修改") }
                .onFailure { _events.emit("修改失败：${it.message}") }
        }
    }

    /** 往已有清单里追加一条待办。 */
    fun appendToList(listId: String, title: String) {
        val text = title.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val all = repository.getAll().first()
                val siblings = all.filter { it.listId == listId }
                val sample = siblings.firstOrNull() ?: return@runCatching
                val schedule = Schedule(
                    title = text,
                    date = sample.date,
                    time = sample.time,
                    reminder = sample.reminder,
                    listId = listId,
                    listTitle = sample.listTitle,
                    recurrence = sample.recurrence,
                    itemOrder = (siblings.maxOfOrNull { it.itemOrder } ?: 0) + 1
                )
                val id = repository.insert(schedule)
                if (id > 0) {
                    val all = repository.getAll().first()
                    reminderScheduler.scheduleList(listId, all)
                }
            }.onSuccess { _events.emit("已添加") }
                .onFailure { _events.emit("添加失败：${it.message}") }
        }
    }

    /**
     * 保存清单编辑结果（重命名、增删改子项、提醒/重复设置）。
     * 传入的 items 顺序即为最终顺序；id 为 null 表示新增待办。
     */
    fun saveListEdit(
        listId: String,
        newTitle: String,
        items: List<EditListItem>,
        deletedIds: Set<Long>,
        reminderDateTime: java.time.LocalDateTime?,
        reminderEnabled: Boolean,
        recurrence: Recurrence
    ) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            viewModelScope.launch { _events.emit("清单名不能为空") }
            return
        }
        val validItems = items.map { it.copy(title = it.title.trim()) }
            .filter { it.title.isNotEmpty() }
        if (validItems.isEmpty()) {
            viewModelScope.launch { _events.emit("至少保留一条待办") }
            return
        }

        viewModelScope.launch {
            runCatching {
                val all = repository.getAll().first()
                val existing = all.filter { it.listId == listId }
                val existingIds = existing.map { it.id }.toSet()

                // 删除被移除的条目（旧版本可能为单条注册过独立闹钟，先尝试取消）
                deletedIds.intersect(existingIds).forEach { id ->
                    reminderScheduler.cancel(id)
                    repository.delete(existing.first { it.id == id })
                }

                val date = reminderDateTime?.toLocalDate()
                    ?: existing.firstOrNull()?.date
                    ?: DateUtils.today()
                // 未开启提醒 → 无时间状态（不参与逾期判定，由用户手动完成或删除）。
                val time = if (reminderEnabled) reminderDateTime?.toLocalTime() else null
                val effectiveRecurrence = if (reminderEnabled) recurrence else Recurrence.NONE

                // 更新已有条目：标题、顺序、提醒、重复
                val updatedExisting = validItems.filter { it.id != null && it.id in existingIds }
                    .mapIndexed { index, editItem ->
                        val original = existing.first { it.id == editItem.id }
                        original.copy(
                            title = editItem.title,
                            listTitle = title,
                            date = date,
                            time = time,
                            reminder = reminderEnabled,
                            recurrence = effectiveRecurrence,
                            itemOrder = index
                        )
                    }
                repository.updateBatch(updatedExisting)

                // 新增条目
                val newItems = validItems.filter { it.id == null || it.id !in existingIds }
                val created = newItems.mapIndexed { index, editItem ->
                    Schedule(
                        title = editItem.title,
                        date = date,
                        time = time,
                        reminder = reminderEnabled,
                        listId = listId,
                        listTitle = title,
                        recurrence = effectiveRecurrence,
                        itemOrder = updatedExisting.size + index
                    )
                }
                val newIds = repository.insertBatch(created)

                // 重新排定清单汇总提醒
                val allUpdated = repository.getAll().first()
                reminderScheduler.scheduleList(listId, allUpdated)
            }.onSuccess {
                _events.emit("已保存")
            }.onFailure {
                _events.emit("保存失败：${it.message}")
            }
        }
    }

    /** 删除整个清单。 */
    fun deleteList(listId: String) {
        viewModelScope.launch {
            runCatching {
                val all = repository.getAll().first()
                // 取消旧版可能残留的独立闹钟
                all.filter { it.listId == listId }.forEach {
                    reminderScheduler.cancel(it.id)
                }
                reminderScheduler.cancelList(listId)
                repository.deleteByListId(listId)
            }.onSuccess { _events.emit("已删除") }
                .onFailure { _events.emit("删除失败：${it.message}") }
        }
    }

    /** 删除单条日程；如果它属于清单且是最后一条，则整个清单会被清理（UI 层按 listId 重新删除）。 */
    fun deleteSchedule(schedule: Schedule, deleteWholeList: Boolean = false) {
        reminderScheduler.cancel(schedule.id)
        viewModelScope.launch {
            runCatching {
                if (deleteWholeList && schedule.listId != null) {
                    val all = repository.getAll().first()
                    all.filter { it.listId == schedule.listId }.forEach {
                        reminderScheduler.cancel(it.id)
                    }
                    reminderScheduler.cancelList(schedule.listId)
                    repository.deleteByListId(schedule.listId)
                } else {
                    repository.delete(schedule)
                    if (schedule.listId != null) {
                        val all = repository.getAll().first()
                        reminderScheduler.scheduleList(schedule.listId, all)
                    }
                }
            }.onSuccess { _events.emit("已删除") }
                .onFailure { _events.emit("删除失败：${it.message}") }
        }
    }

    /** 批量删除选中的日程与清单。 */
    fun deleteSelected(scheduleIds: Set<Long>, listIds: Set<String>) {
        viewModelScope.launch {
            runCatching {
                val all = repository.getAll().first()
                // 删除整个清单
                listIds.forEach { listId ->
                    all.filter { it.listId == listId }.forEach {
                        reminderScheduler.cancel(it.id)
                    }
                    reminderScheduler.cancelList(listId)
                    repository.deleteByListId(listId)
                }
                // 删除独立单条
                scheduleIds.forEach { id ->
                    val schedule = all.find { it.id == id }
                    schedule?.let {
                        reminderScheduler.cancel(it.id)
                        repository.delete(it)
                    }
                }
            }.onSuccess {
                _events.emit("已删除 ${scheduleIds.size + listIds.size} 项")
            }.onFailure {
                _events.emit("删除失败：${it.message}")
            }
        }
    }

    /** 当前是否具备精确闹钟授权（供 UI 决定是否引导用户开启）。 */
    fun canScheduleExact(): Boolean = reminderScheduler.canScheduleExact()

    private fun buildListItems(all: List<Schedule>): List<ScheduleListItem> {
        val (withList, singles) = all.partition { it.listId != null }
        val groups = withList.groupBy { it.listId!! }
            .map { (listId, items) ->
                ScheduleListItem.Group(
                    listId = listId,
                    title = items.firstOrNull { it.listTitle.isNotBlank() }?.listTitle
                        ?: items.first().title,
                    items = items.sortedBy { it.itemOrder },
                    recurrence = items.firstOrNull { it.recurrence.isRepeating }?.recurrence
                        ?: Recurrence.NONE
                )
            }
        val singleItems = singles.map { ScheduleListItem.Single(it) }
        // 排序：未完成的在前，按日期；已完成的在后。
        val sortedGroups = groups.sortedWith(
            compareByDescending<ScheduleListItem.Group> { !it.allCompleted }
                .thenBy { it.items.firstOrNull()?.date ?: LocalDate.MAX }
        )
        val sortedSingles = singleItems.sortedWith(
            compareByDescending<ScheduleListItem.Single> { !it.schedule.completed }
                .thenBy { it.schedule.date }
        )
        return sortedSingles + sortedGroups
    }
}

/** 清单编辑时单条待办的传输对象。 */
data class EditListItem(
    val id: Long? = null,
    val title: String,
    val completed: Boolean = false
)
