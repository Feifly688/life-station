package com.feiqi.utils

/**
 * 应用级集中配置常量。
 * 把散落在多个工具类里的同名硬编码值收敛到单一来源，避免改一处漏一处。
 */
object AppConfig {
    /** 备份 / 导出文件统一存放的下载子目录名（对应 MediaStore Downloads 的 RELATIVE_PATH）。 */
    const val BACKUP_FOLDER = "FeiQiFile"
}
