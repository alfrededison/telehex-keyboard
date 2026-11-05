package com.telehex.keyboard.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Sao lưu / khôi phục: một file zip gồm tuỳ chọn (layout, ký tự vuốt xuống, gõ tắt…),
 * dữ liệu đã học và clipboard đã ghim. Khi khôi phục chỉ nhận đúng các tên entry đã biết
 * (tránh zip-slip) và giới hạn kích thước.
 */
object Backup {
    private const val PREFS = "prefs.json"
    private const val PERSONAL = "personal.tsv"
    private const val CLIPBOARD = "clipboard.json"
    private const val MAX_ENTRY = 20L * 1024 * 1024

    fun export(context: Context, uri: Uri): Result<Unit> = runCatching {
        val prefs = Prefs(context)
        context.contentResolver.openOutputStream(uri)!!.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(PREFS)); zip.write(prefs.exportJson().toByteArray()); zip.closeEntry()
                for (name in listOf(PERSONAL, CLIPBOARD)) {
                    val f = File(context.filesDir, name)
                    if (f.exists()) { zip.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
                }
            }
        }
    }

    fun import(context: Context, uri: Uri): Result<Unit> = runCatching {
        val allowed = setOf(PREFS, PERSONAL, CLIPBOARD)
        var found = false
        context.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name !in allowed) { zip.closeEntry(); continue } // bỏ qua mọi đường dẫn lạ
                    val bytes = readLimited(zip)
                    when (name) {
                        PREFS -> Prefs(context).importJson(String(bytes))
                        else -> File(context.filesDir, name).writeBytes(bytes)
                    }
                    found = true
                    zip.closeEntry()
                }
            }
        }
        check(found) { "File không phải bản sao lưu TeleHex" }
        Prefs(context).modelsVersion = System.currentTimeMillis()
    }

    private fun readLimited(zip: ZipInputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = zip.read(buf); if (n < 0) break
            total += n
            check(total <= MAX_ENTRY) { "Entry quá lớn" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
