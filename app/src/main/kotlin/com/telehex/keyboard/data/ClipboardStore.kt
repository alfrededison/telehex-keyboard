package com.telehex.keyboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Clipboard history: mục thường tự hết hạn (mặc định 1 giờ), mục ghim giữ lâu dài.
 * Dữ liệu chỉ lưu trong bộ nhớ app (file JSON).
 */
class ClipboardStore(context: Context, private val ttlMs: Long = 60L * 60 * 1000) {
    data class Item(val text: String, val time: Long, val pinned: Boolean)

    private val file = File(context.filesDir, "clipboard.json")
    private val items = ArrayList<Item>()

    // Mục hết hạn bị xoá khỏi file ngay khi nạp, không đợi lần chép hay lần mở bảng clipboard kế tiếp.
    init { load(); expire() }

    fun all(): List<Item> {
        expire()
        return items.sortedWith(compareByDescending<Item> { it.pinned }.thenByDescending { it.time })
    }

    fun add(text: String) {
        if (text.isBlank() || text.length > 10_000) return
        items.removeAll { it.text == text && !it.pinned }
        if (items.any { it.text == text }) return
        items.add(Item(text, System.currentTimeMillis(), pinned = false))
        expire()
        while (items.count { !it.pinned } > MAX_UNPINNED) {
            val oldest = items.filter { !it.pinned }.minByOrNull { it.time } ?: break
            items.remove(oldest)
        }
        save()
    }

    fun togglePin(text: String) {
        val i = items.indexOfFirst { it.text == text }
        if (i < 0) return
        items[i] = items[i].copy(pinned = !items[i].pinned, time = System.currentTimeMillis())
        save()
    }

    fun remove(text: String) { items.removeAll { it.text == text }; save() }

    fun clearUnpinned() { items.removeAll { !it.pinned }; save() }

    /** Chỉ các mục đã ghim, dạng JSON như file lưu (để sao lưu; không mang theo mục tạm). */
    fun exportPinnedJson(): String = toJson(items.filter { it.pinned }).toString()

    private fun toJson(list: List<Item>): JSONArray {
        val arr = JSONArray()
        for (it in list) arr.put(JSONObject().put("text", it.text).put("time", it.time).put("pinned", it.pinned))
        return arr
    }

    /** Xoá các mục thường đã quá hạn (và ghi lại file nếu có mục bị xoá). */
    fun expire() {
        val now = System.currentTimeMillis()
        if (items.removeAll { !it.pinned && now - it.time > ttlMs }) save()
    }

    private fun load() {
        runCatching {
            if (!file.exists()) return
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(Item(o.getString("text"), o.getLong("time"), o.optBoolean("pinned")))
            }
        }
    }

    private fun save() {
        runCatching { file.writeText(toJson(items).toString()) }
    }

    companion object { const val MAX_UNPINNED = 30 }
}
