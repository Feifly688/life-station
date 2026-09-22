package com.feiqi.data.repository

import android.content.Context
import com.feiqi.data.model.Quote
import com.feiqi.utils.AppLogger
import com.feiqi.utils.Quotes
import com.google.gson.Gson
import android.os.NetworkOnMainThreadException
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
import java.time.LocalDate
import javax.net.ssl.SSLException

/** 当前语录集的来源。 */
enum class QuoteSource { BUILT_IN, CACHE, REMOTE }

/** 语录集状态（供 UI 展示与取当天语录）。 */
data class QuoteState(
    val quotes: List<Quote> = Quotes.builtIn,
    val source: QuoteSource = QuoteSource.BUILT_IN,
    /** 最近一次**联网成功**更新的时间戳；0 表示从未成功更新过。 */
    val updatedAt: Long = 0L,
    val lastError: String? = null,
    val refreshing: Boolean = false
) {
    /** 取当天语录：同一天稳定同一条，跨零点自然切换（与改造前规则一致）。 */
    fun daily(today: LocalDate): Quote {
        if (quotes.isEmpty()) return Quote("今天，慢慢来。")
        return quotes[today.toEpochDay().mod(quotes.size.toLong()).toInt()]
    }
}

/** 一次刷新的结果，供 UI 提示。 */
data class QuoteRefreshResult(
    val success: Boolean,
    val message: String,
    /** 是否真的从网络取到了新数据（未到更新周期时为 false）。 */
    val updated: Boolean = false
)

/**
 * 语录集仓库：**远程更新 + 本地缓存 + 内置兜底**。
 *
 * ## 数据源
 * 远端是仓库根目录的 `quotes.json`（`main` 分支），结构：
 * ```json
 * {
 *   "version": 1,
 *   "updatedAt": "2026-09-21",
 *   "quotes": [
 *     { "text": "慢慢来，比较快。" },
 *     { "text": "行百里者半九十。", "author": "《战国策》" }
 *   ]
 * }
 * ```
 * 依次尝试 [SOURCE_URLS]（GitHub raw → jsDelivr 镜像），任一成功即结束。
 *
 * ## 降级顺序
 * 远程成功 → 写入缓存（`filesDir/quotes_cache.json`，先写临时文件再改名，避免半截文件）；
 * 远程失败 → 继续用上次缓存；缓存也没有或损坏 → 用 [Quotes.builtIn]。
 * **任何失败都不会影响展示**，只记录 [QuoteState.lastError]。
 *
 * ## 更新时机
 * 由调用方决定：App 启动时 `refresh(force = false)`（距上次成功 ≥ [REFRESH_INTERVAL_MS] 才真正联网），
 * 设置页手动触发用 `refresh(force = true)`。
 */
class QuoteRepository(private val appContext: Context) {

    private val gson = Gson()
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(QuoteState())
    val state: StateFlow<QuoteState> = _state.asStateFlow()

    /** 上次发起联网更新的时间（进程内），用于失败后的快速重试节流。 */
    private var lastAttemptAt = 0L

    /**
     * 启动时载入本地缓存。缓存不存在/损坏/条数过少时**保持内置语录**，不抛异常。
     */
    suspend fun loadCache() = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext
        runCatching {
            gson.fromJson(cacheFile.readText(Charsets.UTF_8), CacheDto::class.java)
        }.onSuccess { cached ->
            val quotes = sanitize(cached?.quotes)
            if (quotes.size >= MIN_QUOTES) {
                _state.value = _state.value.copy(
                    quotes = quotes,
                    source = QuoteSource.CACHE,
                    updatedAt = cached?.fetchedAt ?: 0L
                )
                AppLogger.i(TAG, "语录缓存载入成功：${quotes.size} 条")
            }
        }.onFailure {
            AppLogger.e(TAG, "语录缓存解析失败，改用内置语录", it)
        }
    }

    /**
     * 联网更新语录集。
     *
     * **线程契约**：网络与文件 IO 全部在 [Dispatchers.IO] 内完成，本方法可以安全地从任何
     * 上下文调用（主线程也可以）——不要把线程切换的责任推给调用方：
     * `viewModelScope` 是 `Dispatchers.Main`，一旦漏了 `withContext` 就会抛
     * `NetworkOnMainThreadException`（v1.4.0 的实际缺陷）。
     *
     * **重试策略**：每个源最多尝试 [ATTEMPTS_PER_SOURCE] 次（间隔 [RETRY_DELAY_MS] 毫秒），
     * 再换下一个源；全部失败时记录 [QuoteState.lastError]，并让下次自动更新提前到
     * [FAIL_RETRY_INTERVAL_MS] 之后（而不是傻等 7 天）。
     *
     * @param force 为 false 时，仅当「该更新了」才真正发起请求（见 [isDue]）。
     */
    suspend fun refresh(force: Boolean): QuoteRefreshResult = mutex.withLock {
        val current = _state.value
        if (current.refreshing) {
            return@withLock QuoteRefreshResult(false, "正在更新中，请稍候")
        }
        if (!force && !isDue(current)) {
            return@withLock QuoteRefreshResult(true, "语录已是最新（${current.quotes.size} 条）")
        }

        _state.value = current.copy(refreshing = true)
        lastAttemptAt = System.currentTimeMillis()
        try {
            // 关键：所有阻塞式网络/文件操作都切到 IO 线程。
            val outcome = withContext(Dispatchers.IO) {
                var failure: String? = null
                for (url in SOURCE_URLS) {
                    for (attempt in 1..ATTEMPTS_PER_SOURCE) {
                        val result = runCatching { fetchAndParse(url) }
                        val quotes = result.getOrNull()
                        if (quotes != null) return@withContext FetchOutcome.Success(quotes)
                        failure = describe(result.exceptionOrNull())
                        if (attempt < ATTEMPTS_PER_SOURCE) delay(RETRY_DELAY_MS)
                    }
                    AppLogger.e(TAG, "语录源不可用（$url）：$failure")
                }
                FetchOutcome.Failure(failure ?: "网络不可用")
            }

            return@withLock when (outcome) {
                is FetchOutcome.Success -> {
                    persist(outcome.quotes)
                    _state.value = QuoteState(
                        quotes = outcome.quotes,
                        source = QuoteSource.REMOTE,
                        updatedAt = System.currentTimeMillis(),
                        lastError = null,
                        refreshing = false
                    )
                    AppLogger.i(TAG, "语录集更新成功：${outcome.quotes.size} 条")
                    QuoteRefreshResult(true, "已更新 ${outcome.quotes.size} 条语录", updated = true)
                }

                is FetchOutcome.Failure -> {
                    // 失败：保留现有语录（缓存或内置），只记录原因，供设置页展示与手动重试。
                    _state.value = _state.value.copy(refreshing = false, lastError = outcome.message)
                    AppLogger.e(TAG, "语录集更新失败：${outcome.message}")
                    QuoteRefreshResult(false, outcome.message)
                }
            }
        } finally {
            if (_state.value.refreshing) {
                _state.value = _state.value.copy(refreshing = false)
            }
        }
    }

    /** 当天语录（按当前生效的语录集取模）。 */
    fun daily(today: LocalDate = LocalDate.now()): Quote = _state.value.daily(today)

    /**
     * 是否到了该联网更新的时间点。
     * - 从未成功过 → 距上次**尝试**（含失败）超过 [FAIL_RETRY_INTERVAL_MS] 才再试，避免每次启动都硬撞；
     * - 成功过 → 距上次成功超过 [REFRESH_INTERVAL_MS]（7 天）。
     */
    fun isDue(state: QuoteState = _state.value): Boolean {
        val now = System.currentTimeMillis()
        if (state.updatedAt <= 0L) {
            return now - lastAttemptAt >= FAIL_RETRY_INTERVAL_MS
        }
        return now - state.updatedAt >= REFRESH_INTERVAL_MS
    }

    /** 本次刷新尝试的结果（域内类型，避免用异常表达失败）。 */
    private sealed interface FetchOutcome {
        data class Success(val quotes: List<Quote>) : FetchOutcome
        data class Failure(val message: String) : FetchOutcome
    }

    // ---------------- 内部实现 ----------------

    /** 取回并解析一个源；解析后条数不足视为失败（避免半截/错误内容覆盖好数据）。 */
    private fun fetchAndParse(url: String): List<Quote> {
        val body = httpGet(url)
        val dto = runCatching { gson.fromJson(body, RemoteDto::class.java) }
            .getOrElse { throw IOException("响应不是合法 JSON") }
            ?: throw IOException("响应为空")
        val quotes = sanitize(dto.quotes)
        if (quotes.size < MIN_QUOTES) {
            throw IOException("语录条数过少（${quotes.size} < $MIN_QUOTES）")
        }
        return quotes
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

    /** 清洗：去空白、丢弃空/超长条目、按文本去重、限制总量。 */
    private fun sanitize(raw: List<RawQuote>?): List<Quote> {
        return raw.orEmpty().asSequence()
            .mapNotNull { item ->
                val text = item.text?.trim().orEmpty()
                if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) {
                    null
                } else {
                    Quote(text = text, author = item.author?.trim()?.ifBlank { null })
                }
            }
            .distinctBy { it.text }
            .take(MAX_QUOTES)
            .toList()
    }

    private fun persist(quotes: List<Quote>) {
        runCatching {
            val tmp = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
            tmp.writeText(
                gson.toJson(CacheDto(fetchedAt = System.currentTimeMillis(), quotes = quotes.map {
                    RawQuote(text = it.text, author = it.author)
                })),
                Charsets.UTF_8
            )
            // 先写临时文件再改名：避免写一半被中断留下半截 JSON。
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(tmp.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmp.delete()
            }
        }.onFailure { AppLogger.e(TAG, "语录缓存写入失败", it) }
    }

    /** 把异常翻译成用户能看懂的中文提示（设置页会直接展示）。 */
    private fun describe(t: Throwable?): String = when (t) {
        null -> "未知错误"
        is UnknownHostException -> "网络不可用，请检查网络连接后重试"
        is SocketTimeoutException -> "连接超时，请稍后重试"
        is ConnectException -> "无法连接到服务器，请稍后重试"
        is SSLException -> "安全连接建立失败，请稍后重试"
        is NetworkOnMainThreadException -> "网络请求误在主线程执行（内部错误）"
        else -> t.message?.takeIf { it.isNotBlank() } ?: t::class.simpleName ?: "未知错误"
    }

    /** 远端 `quotes.json` 的解析模型（字段全部可空，容忍不完整/脏数据）。 */
    private data class RemoteDto(val version: Int? = null, val quotes: List<RawQuote>? = null)

    /** 本地缓存的解析模型。 */
    private data class CacheDto(val fetchedAt: Long? = null, val quotes: List<RawQuote>? = null)

    private data class RawQuote(val text: String? = null, val author: String? = null)

    companion object {
        private const val TAG = "QuoteRepository"
        private const val CACHE_FILE_NAME = "quotes_cache.json"
        /** 更新周期：7 天。 */
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        /** 上次失败后，多久才再自动重试一次（10 分钟）。 */
        private const val FAIL_RETRY_INTERVAL_MS = 10L * 60 * 1000
        /** 单个源的最大尝试次数与重试间隔。 */
        private const val ATTEMPTS_PER_SOURCE = 2
        private const val RETRY_DELAY_MS = 600L
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        /** 少于这个条数就认为不是一份有效语录集（防止坏数据把好数据顶掉）。 */
        private const val MIN_QUOTES = 10
        private const val MAX_QUOTES = 2_000
        private const val MAX_TEXT_LENGTH = 160
        private const val USER_AGENT = "FeiQi-Android"

        /**
         * 语录集地址，按顺序尝试。指向本仓库 `main` 分支根目录的 `quotes.json`，
         * 改语录只需在 GitHub 上编辑该文件（手机端也能改），无需服务器。
         */
        private val SOURCE_URLS = listOf(
            "https://raw.githubusercontent.com/Feifly688/life-station/main/quotes.json",
            "https://cdn.jsdelivr.net/gh/Feifly688/life-station@main/quotes.json"
        )
    }
}
