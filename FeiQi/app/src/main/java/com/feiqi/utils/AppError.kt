package com.feiqi.utils

import com.google.gson.JsonSyntaxException
import java.text.ParseException

/**
 * 跨层统一错误类型。各 IO / 解析边界可把异常映射为 [AppError]，
 * 上层据此展示对应文案或做降级，而不是只拿到一个裸 Throwable。
 *
 * 目前 BackupManager / ExcelExporter 已返回 [kotlin.Result]，失败时可经
 * [from] 转换为 AppError 取结构化信息；后续阶段会把 Repository 失败路径
 * 也统一为 Result<AppError>。
 */
sealed class AppError(open val message: String) {
    /** 文件 / 流读写失败（备份导出、Excel 导出、导入）。 */
    data class Io(override val message: String) : AppError(message)

    /** JSON / XML 解析失败。 */
    data class Parse(override val message: String) : AppError(message)

    /** 持久化（DataStore / Room）读写失败。 */
    data class Storage(override val message: String) : AppError(message)

    /** 参数 / 业务校验不通过。 */
    data class Validation(override val message: String) : AppError(message)

    /** 未归类异常。 */
    data class Unknown(override val message: String) : AppError(message)

    companion object {
        fun from(throwable: Throwable?, fallback: String = "未知错误"): AppError =
            when (throwable) {
                is java.io.IOException ->
                    Io(throwable.message ?: fallback)
                is ParseException, is JsonSyntaxException ->
                    Parse(throwable.message ?: fallback)
                else -> Unknown(throwable?.message ?: fallback)
            }
    }
}
