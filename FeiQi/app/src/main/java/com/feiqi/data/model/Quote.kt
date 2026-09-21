package com.feiqi.data.model

/**
 * 单条语录。
 *
 * [author] 可为空——内置语料全部没有作者，远程语录集可以提供。
 *
 * 远程语录集（仓库根目录 `quotes.json`）的结构与解析见
 * [com.feiqi.data.repository.QuoteRepository]。
 */
data class Quote(
    val text: String,
    val author: String? = null
)
