package com.feiqi.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.feiqi.utils.AppLogger

class BackupManager(private val context: Context) {

    private val folderName = AppConfig.BACKUP_FOLDER
    private val tag = "BackupManager"

    fun generateFileName(): String {
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.CHINA).format(Date())
        return "翡栖备份_$time.json"
    }

    suspend fun exportJson(json: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = generateFileName()
            AppLogger.d(tag, "开始导出备份：$fileName")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/$folderName")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("无法创建导出文件")
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入导出文件")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(downloads, folderName)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeText(json, Charsets.UTF_8)
                file.absolutePath
            }
        }.onFailure { AppLogger.e(tag, "导出备份失败", it) }
    }

    suspend fun importJson(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            AppLogger.d(tag, "开始导入备份：$uri")
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: throw IllegalStateException("无法读取导入文件")
        }.onFailure { AppLogger.e(tag, "导入备份失败", it) }
    }
}
