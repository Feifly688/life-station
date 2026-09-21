package com.feiqi.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.feiqi.data.model.AccountRecord
import com.feiqi.data.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.feiqi.utils.AppLogger

/**
 * 把记账记录导出为真正可打开的 Excel (.xlsx) 文件。
 * 不依赖 Apache POI 等外部库，使用原生 ZIP + SpreadsheetML 手写最小合法 xlsx。
 */
class ExcelExporter(private val context: Context) {

    private val folderName = AppConfig.BACKUP_FOLDER
    private val tag = "ExcelExporter"

    private fun generateFileName(): String {
        val time = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.CHINA).format(Date())
        return "翡栖记账_$time.xlsx"
    }

    suspend fun exportAccounts(records: List<AccountRecord>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = generateFileName()
            AppLogger.d(tag, "开始导出 Excel：$fileName，共 ${records.size} 条")
            val bytes = buildXlsx(records)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/$folderName")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("无法创建 Excel 文件")
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                } ?: throw IllegalStateException("无法写入 Excel 文件")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(downloads, folderName)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                file.writeBytes(bytes)
                file.absolutePath
            }
        }.onFailure { AppLogger.e(tag, "导出 Excel 失败", it) }
    }

    private fun buildXlsx(records: List<AccountRecord>): ByteArray {
        val header = listOf("日期", "时间", "类型", "分类", "金额", "备注")
        val rows: List<List<String>> = listOf(header) + records.map {
            listOf(
                DateUtils.iso(it.dateTime.toLocalDate()),
                DateUtils.hms(it.dateTime.toLocalTime()),
                if (it.type == AccountType.INCOME) "收入" else "支出",
                it.category,
                String.format(Locale.US, "%.2f", it.amount),
                it.note
            )
        }

        val entries = mutableListOf<Pair<String, String>>()
        entries += "[Content_Types].xml" to contentTypesXml()
        entries += "_rels/.rels" to relsXml()
        entries += "xl/_rels/workbook.xml.rels" to workbookRelsXml()
        entries += "xl/workbook.xml" to workbookXml()
        entries += "xl/styles.xml" to stylesXml()
        entries += "xl/worksheets/sheet1.xml" to worksheetXml(rows)

        return zip(entries)
    }

    private fun zip(entries: List<Pair<String, String>>): ByteArray {
        val baos = java.io.ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (path, content) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun contentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".trimIndent()
    }

    private fun relsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trimIndent()
    }

    private fun workbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trimIndent()
    }

    private fun workbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="记账记录" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".trimIndent()
    }

    private fun stylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/></cellXfs>
</styleSheet>""".trimIndent()
    }

    private fun worksheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>""")
        rows.forEachIndexed { rIndex, cols ->
            val rowNum = rIndex + 1
            sb.append("    <row r=\"$rowNum\">")
            cols.forEachIndexed { cIndex, value ->
                val ref = cellRef(rIndex, cIndex)
                val isNumber = value.isNotEmpty() && value.toDoubleOrNull() != null && cIndex == 4
                if (isNumber) {
                    sb.append("<c r=\"$ref\" t=\"n\"><v>$value</v></c>")
                } else {
                    sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(value)}</t></is></c>")
                }
            }
            sb.appendLine("</row>")
        }
        sb.appendLine("  </sheetData>")
        sb.appendLine("</worksheet>")
        return sb.toString()
    }

    private fun cellRef(row: Int, col: Int): String {
        return colToLetters(col) + (row + 1)
    }

    private fun colToLetters(col: Int): String {
        var n = col
        val sb = StringBuilder()
        while (n >= 0) {
            sb.append('A' + (n % 26))
            n = n / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
