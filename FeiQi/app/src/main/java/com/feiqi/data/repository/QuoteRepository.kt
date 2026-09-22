package com.feiqi.data.repository

import android.content.Context
import android.os.NetworkOnMainThreadException
import com.feiqi.data.model.Quote
import com.feiqi.utils.AppLogger
import com.feiqi.utils.Quotes
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.random.Random
import javax.net.ssl.SSLException

/** 生效语录集的来源（内置兜底 / 本地缓存 / 远程基础集）。 */
enum class QuoteSource { BUILT_IN, CACHE, REMOTE }

/**
 * 语录集状态。
 *
 * [quotes] = 基础集 + 自动收集集（已按归一化文本去重）；[baseCount]/[collectedCount] 供日志与排查。
 */
data class QuoteState(
    val quotes: List<Quote> = Quotes.builtIn,
    val baseCount: Int = Quotes.builtIn.size,
    val collectedCount: Int = 0,
    val source: QuoteSource = QuoteSource.BUILT_IN
) {
    /** 取当天语录：同一天稳定同一条，跨零点自然切换。 */
    fun daily(today: LocalDate): Quote {
        if (quotes.isEmpty()) return Quote("今天，慢慢来。")
        return quotes[today.toEpochDay().mod(quotes.size.toLong()).toInt()]
    }
}

/**
 * 语录集仓库：**基础集（可远程更新）+ 自动收集（静默联网搜索）+ 本地缓存 + 内置兜底**。
 *
 * ## 两份集合
 * | 集合 | 来源 | 更新方式 |
 * | --- | --- | --- |
 * | 基础集 | 内置 [Quotes.builtIn] 或仓库根目录 `quotes.json` | 距上次成功 ≥ [BASE_REFRESH_INTERVAL_MS]（7 天）时静默更新 |
 * | 自动收集集 | 一言接口「文学 + 诗词」类别随机取句 | **每周首次打开 App**静默收集 5~10 条，去重后追加（[collectNewQuotesIfDue]） |
 *
 * 生效集合 = 两者按 [normalizeKey] 合并去重；基础集更新**不会**丢掉已收集的内容。
 *
 * ## 去重
 * [normalizeKey] 只保留字母/数字/汉字（丢掉空白与全部标点），因此
 * 「慢慢来，比较快。」「慢慢来比较快」「慢慢来, 比较快。」会被视为同一条；
 * 既与现有集合比对，也在同一批内部互相比对。
 *
 * ## 静默与降级
 * 网络/文件 IO 全在 [Dispatchers.IO]；任何失败只写日志（无弹窗、无状态提示），
 * 现有语录照常展示。基础集失败后 [FAIL_RETRY_INTERVAL_MS] 内不再重试。
 *
 * ## 数据源
 * - 基础集：`quotes.json`（GitHub raw → jsDelivr 镜像）
 * - 自动收集：`https://v1.hitokoto.cn/?c=d,i`（文学 / 诗词）
 */
class QuoteRepository(private val appContext: Context) {

    private val gson = Gson()
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(QuoteState())
    val state: StateFlow<QuoteState> = _state.asStateFlow()

    /** 基础集（内置或远程）。 */
    private var baseQuotes: List<Quote> = Quotes.builtIn
    /** 自动收集集（网络搜索去重后追加，长期累积）。 */
    private var collectedQuotes: List<Quote> = emptyList()
    private var baseUpdatedAt = 0L
    private var baseLastAttemptAt = 0L
    private var lastCollectedAt = 0L
    /** 已完成自动收集的那一周（存该周周一的 ISO 日期），用于「每周只收集一次」。 */
    private var lastCollectWeek: String? = null

    // ---------------- 对外接口 ----------------

    /** 启动时载入本地缓存（基础集 + 已收集集）。不存在/损坏时保持内置语录。 */
    suspend fun loadCache() = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext
        runCatching {
            gson.fromJson(cacheFile.readText(Charsets.UTF_8), CacheDto::class.java)
        }.onSuccess { cached ->
            // 先记收集节奏：即使基础集无效也要保留节流时间，避免重复硬撞接口。
            lastCollectedAt = cached?.lastCollectedAt ?: 0L
            lastCollectWeek = cached?.lastCollectWeek
            collectedQuotes = clean(cached?.collected, NEW_QUOTE_MAX_LENGTH).take(MAX_COLLECTED)
            // 兼容 v1.4.x 旧格式（当时只有一份集合，字段名 quotes）。
            val base = clean(cached?.base ?: cached?.quotes, BASE_TEXT_MAX_LENGTH)
            val source = if (base.size >= MIN_BASE_QUOTES) {
                baseQuotes = base
                baseUpdatedAt = cached?.fetchedAt ?: 0L
                QuoteSource.CACHE
            } else {
                QuoteSource.BUILT_IN
            }
            publish(source)
            AppLogger.i(TAG, "语录缓存载入：基础集 ${baseQuotes.size} 条 + 收集 ${collectedQuotes.size} 条")
        }.onFailure {
            AppLogger.e(TAG, "语录缓存解析失败，改用内置语录", it)
        }
    }

    /**
     * 静默更新**基础集**（远程 `quotes.json`）。
     *
     * 网络与文件 IO 全部在 [Dispatchers.IO]，可安全地从任何上下文调用（含主线程）。
     * @param force 为 false 时，仅当「该更新了」才真正发起请求（见 [isBaseDue]）。
     * @return 是否成功取到并应用了新基础集。
     */
    suspend fun refreshBase(force: Boolean): Boolean = mutex.withLock {
        if (!force && !isBaseDue()) return@withLock false
        baseLastAttemptAt = System.currentTimeMillis()

        val outcome = withContext(Dispatchers.IO) {
            var failure: String? = null
            for (url in BASE_SOURCE_URLS) {
                for (attempt in 1..ATTEMPTS_PER_SOURCE) {
                    val result = runCatching { fetchBase(url) }
                    val quotes = result.getOrNull()
                    if (quotes != null) return@withContext FetchOutcome.Success(quotes)
                    failure = describe(result.exceptionOrNull())
                    if (attempt < ATTEMPTS_PER_SOURCE) delay(RETRY_DELAY_MS)
                }
                AppLogger.e(TAG, "基础集源不可用（$url）：$failure")
            }
            FetchOutcome.Failure(failure ?: "网络不可用")
        }

        return@withLock when (outcome) {
            is FetchOutcome.Success -> {
                baseQuotes = outcome.quotes
                baseUpdatedAt = System.currentTimeMillis()
                persist()
                publish(QuoteSource.REMOTE)
                AppLogger.i(TAG, "基础集更新成功：${outcome.quotes.size} 条")
                true
            }

            is FetchOutcome.Failure -> {
                // 失败：保留现有基础集与全部已收集语录，只记日志。
                AppLogger.e(TAG, "基础集更新失败：${outcome.message}")
                false
            }
        }
    }

    /**
     * **静默收集新语录**（每周一次）：本周**第一次**打开 App 时才联网 —— 不限定周几，
     * 周一没打开就在本周首次打开时补做，本周做过一次之后不再触发。
     * 每轮随机新增 [COLLECT_MIN_PER_ROUND]~[COLLECT_MAX_PER_ROUND] 条（去重后追加）。
     *
     * 触发时机：App 每次进入前台（`MainActivity.onStart`）调用，但内部只在「本周尚未收集」时
     * 才真正执行，其余时间立即返回 —— 避免每次打开都请求接口。用户无需任何操作，也没有界面反馈。
     *
     * 若本次联网全部失败（一条都没取到），**不标记本周已完成**，稍后（≥ [MIN_COLLECT_GAP_MS]）
     * 再打开会重试；接口正常但内容全是重复（无可新增），则标记本周已完成，不再重复请求。
     *
     * @return 本次新增条数；0 表示本周已完成 / 未到最小间隔 / 全是重复 / 网络失败。
     */
    suspend fun collectNewQuotesIfDue(): Int = mutex.withLock {
        val today = LocalDate.now()
        val thisWeek = weekKey(today).toString()
        val now = System.currentTimeMillis()

        // 触发条件：本周尚未收集 + 距上次尝试超过最小间隔（防时钟/时区漂移导致同周重复触发）。
        // 「本周」以该周周一的日期标识，因此跨周自动重置、同一周内只触发一次（不限定周几）。
        if (lastCollectWeek == thisWeek) return@withLock 0
        // 仅在「有过上一次尝试」时才受间隔约束（首次运行为 0，不应被拦）。
        if (lastCollectedAt > 0L && now - lastCollectedAt < MIN_COLLECT_GAP_MS) return@withLock 0

        // 本轮目标条数：5~10 条之间随机（每次略有不同）。
        val target = Random.nextInt(COLLECT_MIN_PER_ROUND, COLLECT_MAX_PER_ROUND + 1)
        val result = withContext(Dispatchers.IO) {
            // 去重集合 = 当前生效集合 + 本批已取（seen.add 同时承担批内去重）。
            val seen = merge().mapTo(mutableSetOf()) { normalizeKey(it.text) }
            val picked = mutableListOf<Quote>()
            var responded = false
            var attempts = 0
            // 目标条数可能因重复/超长被跳过，故允许 attempts 超出 target（上限 target*2）。
            while (picked.size < target && attempts < target * MAX_ATTEMPT_FACTOR) {
                if (attempts > 0) delay(COLLECT_REQUEST_SPACING_MS)
                attempts++
                val candidate = runCatching { fetchOneQuote() }.getOrNull()
                if (candidate == null) {
                    // 连续两次都拿不到响应 → 判定为网络不可用，立即收工，
                    // 避免离线时白等 target*2 次超时（每次 8s）。
                    if (!responded && attempts >= OFFLINE_ABORT_ATTEMPTS) break
                    continue
                }
                responded = true
                val text = candidate.text
                if (text.length > NEW_QUOTE_MAX_LENGTH) continue
                if (!seen.add(normalizeKey(text))) continue
                picked += candidate
            }
            picked to responded
        }
        val added = result.first
        val anyResponse = result.second

        lastCollectedAt = System.currentTimeMillis()
        // 接口有响应（哪怕全是重复）就算本周完成；完全没响应该重试。
        if (anyResponse) lastCollectWeek = thisWeek

        if (added.isNotEmpty()) {
            collectedQuotes = (collectedQuotes + added).takeLast(MAX_COLLECTED)
        }
        persist()
        if (added.isNotEmpty()) publish(_state.value.source)
        AppLogger.i(
            TAG,
            "本周语录收集：目标 $target 条，接口有响应=$anyResponse，新增 ${added.size} 条，当前共计 ${_state.value.quotes.size} 条"
        )
        added.size
    }

    /** 当天语录（按当前生效的语录集取模）。 */
    fun daily(today: LocalDate = LocalDate.now()): Quote = _state.value.daily(today)

    // ---------------- 内部实现 ----------------

    /** 某天所在周的周一（ISO：周一为一周第一天）—— 用它作为「本周」的唯一标识，与周几无关。 */
    private fun weekKey(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 基础集是否到了该更新的时间点（从未成功则按失败重试间隔节流）。 */
    private fun isBaseDue(): Boolean {
        val now = System.currentTimeMillis()
        if (baseUpdatedAt <= 0L) return now - baseLastAttemptAt >= FAIL_RETRY_INTERVAL_MS
        return now - baseUpdatedAt >= BASE_REFRESH_INTERVAL_MS
    }

    /** 生效集合 = 基础集 + 收集集，按归一化文本去重（基础集优先保留）。 */
    private fun merge(): List<Quote> = (baseQuotes + collectedQuotes).distinctBy { normalizeKey(it.text) }

    private fun publish(source: QuoteSource) {
        _state.value = QuoteState(
            quotes = merge(),
            baseCount = baseQuotes.size,
            collectedCount = collectedQuotes.size,
            source = source
        )
    }

    /** 去重用的归一化键：只保留字母 / 数字 / 汉字，丢掉空白与标点。 */
    private fun normalizeKey(text: String): String = text.filter { it.isLetterOrDigit() }

    /** 清洗：压平换行、去空白、丢弃空/超长条目、按文本去重。 */
    private fun clean(raw: List<RawQuote>?, maxLength: Int): List<Quote> {
        return raw.orEmpty().asSequence()
            .mapNotNull { item ->
                val text = item.text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
                if (text.isEmpty() || text.length > maxLength) {
                    null
                } else {
                    Quote(text = text, author = item.author?.trim()?.ifBlank { null })
                }
            }
            .distinctBy { normalizeKey(it.text) }
            .toList()
    }

    /** 取远程基础集（条数不足视为无效，避免坏数据顶掉好数据）。 */
    private fun fetchBase(url: String): List<Quote> {
        val body = httpGet(url)
        val dto = runCatching { gson.fromJson(body, BaseSetDto::class.java) }
            .getOrElse { throw IOException("响应不是合法 JSON") }
            ?: throw IOException("响应为空")
        val quotes = clean(dto.quotes, BASE_TEXT_MAX_LENGTH)
        if (quotes.size < MIN_BASE_QUOTES) {
            throw IOException("语录条数过少（${quotes.size} < $MIN_BASE_QUOTES）")
        }
        return quotes
    }

    /** 取一条网络语录（一言接口：文学 + 诗词）。 */
    private fun fetchOneQuote(): Quote {
        // 带时间戳参数：接口/CDN 对同一 URL 有短时缓存，否则连续请求会反复拿到同一句。
        val body = httpGet(HITOKOTO_URL + System.currentTimeMillis())
        val dto = runCatching { gson.fromJson(body, HitokotoDto::class.java) }
            .getOrElse { throw IOException("响应不是合法 JSON") }
            ?: throw IOException("响应为空")
        val text = dto.text?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (text.isEmpty()) throw IOException("响应缺少语录内容")
        val author = dto.fromWho?.trim()?.ifBlank { null } ?: dto.from?.trim()?.ifBlank { null }
        return Quote(text = text, author = author)
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            // 显式要求不压缩：HttpURLConnection 不会自动解压，拿到 gzip 字节会解析失败。
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** 把异常翻译成可读原因（只进日志）。 */
    private fun describe(t: Throwable?): String = when (t) {
        null -> "未知错误"
        is UnknownHostException -> "网络不可用"
        is SocketTimeoutException -> "连接超时"
        is ConnectException -> "无法连接到服务器"
        is SSLException -> "安全连接建立失败"
        is NetworkOnMainThreadException -> "网络请求误在主线程执行（内部错误）"
        else -> t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "未知错误"
    }

    private fun persist() {
        runCatching {
            val tmp = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
            tmp.writeText(
                gson.toJson(
                    CacheDto(
                        fetchedAt = baseUpdatedAt,
                        lastCollectedAt = lastCollectedAt,
                        lastCollectWeek = lastCollectWeek,
                        base = baseQuotes.map { RawQuote(text = it.text, author = it.author) },
                        collected = collectedQuotes.map { RawQuote(text = it.text, author = it.author) }
                    )
                ),
                Charsets.UTF_8
            )
            // 先写临时文件再改名：避免写一半被中断留下半截 JSON。
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        }.onFailure { AppLogger.e(TAG, "语录缓存写入失败", it) }
    }

    /** 本次基础集拉取的结果。 */
    private sealed interface FetchOutcome {
        data class Success(val quotes: List<Quote>) : FetchOutcome
        data class Failure(val message: String) : FetchOutcome
    }

    /** 本地缓存解析模型（字段全部可空，容忍脏数据与旧格式）。 */
    private data class CacheDto(
        val fetchedAt: Long? = null,
        val lastCollectedAt: Long? = null,
        val lastCollectWeek: String? = null,
        val base: List<RawQuote>? = null,
        /** v1.4.x 旧格式字段（当时只有一份集合）。 */
        val quotes: List<RawQuote>? = null,
        val collected: List<RawQuote>? = null
    )

    /** 远程 quotes.json 解析模型。 */
    private data class BaseSetDto(val version: Int? = null, val quotes: List<RawQuote>? = null)

    /** 一言接口解析模型。 */
    private data class HitokotoDto(
        @SerializedName("hitokoto") val text: String? = null,
        @SerializedName("from") val from: String? = null,
        @SerializedName("from_who") val fromWho: String? = null
    )

    private data class RawQuote(val text: String? = null, val author: String? = null)

    companion object {
        private const val TAG = "QuoteRepository"
        private const val CACHE_FILE_NAME = "quotes_cache.json"

        /** 基础集更新周期：7 天。 */
        private const val BASE_REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        /** 基础集失败后多久才再自动重试（10 分钟）。 */
        private const val FAIL_RETRY_INTERVAL_MS = 10L * 60 * 1000
        /** 单个源的最大尝试次数与重试间隔。 */
        private const val ATTEMPTS_PER_SOURCE = 2
        private const val RETRY_DELAY_MS = 600L

        /** 每轮静默收集的目标条数区间（每次随机 5~10 条）与请求间隔（礼貌限速）。 */
        private const val COLLECT_MIN_PER_ROUND = 5
        private const val COLLECT_MAX_PER_ROUND = 10
        private const val COLLECT_REQUEST_SPACING_MS = 600L
        /** 为凑够目标条数，允许的最大请求次数倍数（重复/超长会被跳过）。 */
        private const val MAX_ATTEMPT_FACTOR = 2
        /** 两次收集之间的最小间隔（3 小时）：给「本周首次尝试失败」留出重试窗口，同时防止同周重复触发。 */
        private const val MIN_COLLECT_GAP_MS = 3L * 60 * 60 * 1000
        /** 连续这么多次拿不到响应就判定离线并放弃本轮（避免离线空转超时）。 */
        private const val OFFLINE_ABORT_ATTEMPTS = 2
        /** 收集集上限与单条长度上限（过长的句子卡片放不下）。 */
        private const val MAX_COLLECTED = 400
        private const val NEW_QUOTE_MAX_LENGTH = 60
        private const val BASE_TEXT_MAX_LENGTH = 160
        /** 基础集少于这个条数视为无效（防止坏数据顶掉好数据）。 */
        private const val MIN_BASE_QUOTES = 10

        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val USER_AGENT = "FeiQi-Android"

        /** 基础集地址（人工维护的精选集），按顺序尝试。 */
        private val BASE_SOURCE_URLS = listOf(
            "https://raw.githubusercontent.com/Feifly688/life-station/main/quotes.json",
            "https://cdn.jsdelivr.net/gh/Feifly688/life-station@main/quotes.json"
        )

        /**
         * 自动收集用的网络语录源（一言）。
         *
         * 注意：多分类必须写成**重复参数** `c=d&c=i`；写成逗号形式 `c=d,i` 接口会返回
         * 400「没有分类有句子符合长度区间」（实测）。末尾 `_=` 拼时间戳用于绕过 CDN 缓存，
         * 否则连续请求会反复拿到同一句。
         */
        private const val HITOKOTO_URL =
            "https://v1.hitokoto.cn/?c=d&c=i&encode=json&charset=utf-8&_="
    }
}
