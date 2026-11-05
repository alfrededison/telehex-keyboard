package com.telehex.keyboard.ime

import android.content.Context
import com.telehex.telex.ToneRules

/**
 * Danh mục emoji và chỉ mục tìm kiếm tiếng Việt, nạp từ `assets/emoji.tsv`
 * (sinh bởi `tools/build_emoji.py` từ Unicode emoji-test + chú thích CLDR tiếng Việt).
 */
class EmojiData private constructor(
    val categories: List<Category>,
    private val entries: List<Entry>,
    private val modifierBases: Set<String>,
) {
    class Category(val id: String, val name: String, val icon: String, val emoji: List<String>)
    class Entry(val emoji: String, val name: String, val keywords: List<String>, val searchText: String)

    /** Emoji nhận tông da (Emoji_Modifier_Base). */
    fun acceptsSkinTone(emoji: String): Boolean = emoji in modifierBases

    /** Áp tông da (1..5) vào emoji nếu nhận; 0 = giữ nguyên. */
    fun withSkinTone(emoji: String, tone: Int): String {
        if (tone !in 1..5 || !acceptsSkinTone(emoji)) return emoji
        val modifier = String(Character.toChars(0x1F3FA + tone))
        // Chèn modifier ngay sau code point đầu (bỏ FE0F nếu có), giữ phần ZWJ phía sau
        val first = emoji.offsetByCodePoints(0, 1)
        var rest = emoji.substring(first)
        if (rest.startsWith("️")) rest = rest.substring(1)
        return emoji.substring(0, first) + modifier + rest
    }

    /** Tìm theo tên / từ khoá tiếng Việt; bỏ dấu thanh khi so để gõ dở vẫn ra. */
    fun search(query: String, max: Int = 40): List<String> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<String>()
        val contains = ArrayList<String>()
        for (e in entries) {
            if (e.searchText.startsWith(q) || e.searchText.contains("|$q")) starts.add(e.emoji)
            else if (e.searchText.contains(q)) contains.add(e.emoji)
            if (starts.size >= max) break
        }
        return (starts + contains).take(max)
    }

    /** Emoji có từ khoá đúng bằng [word] (cho gợi ý trên thanh). */
    fun exactKeyword(word: String, max: Int = 2): List<String> {
        val w = normalize(word)
        if (w.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        for (e in entries) {
            if (e.keywords.any { normalize(it) == w }) { out.add(e.emoji); if (out.size >= max) break }
        }
        return out
    }

    companion object {
        @Volatile private var instance: EmojiData? = null

        fun get(context: Context): EmojiData = instance ?: synchronized(this) {
            instance ?: load(context).also { instance = it }
        }

        private val ORDER = listOf("Mặt cười", "Người", "Động vật & thiên nhiên", "Đồ ăn", "Du lịch", "Hoạt động", "Đồ vật", "Ký hiệu", "Cờ")
        private val ICON = mapOf(
            "Mặt cười" to "😀", "Người" to "👋", "Động vật & thiên nhiên" to "🐶", "Đồ ăn" to "🍜", "Du lịch" to "✈️",
            "Hoạt động" to "⚽", "Đồ vật" to "💡", "Ký hiệu" to "❤️", "Cờ" to "🏁",
        )

        private fun load(context: Context): EmojiData {
            val byGroup = LinkedHashMap<String, ArrayList<String>>()
            val entries = ArrayList<Entry>(2000)
            val bases = HashSet<String>()
            context.assets.open("emoji.tsv").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val p = line.split('\t')
                    if (p.size < 5) continue
                    val emoji = p[0]
                    byGroup.getOrPut(p[1]) { ArrayList() }.add(emoji)
                    val kws = p[3].split('|').filter { it.isNotEmpty() }
                    val search = (listOf(p[2]) + kws).joinToString("|") { normalize(it) }
                    entries.add(Entry(emoji, p[2], kws, search))
                    if (p[4] == "1") bases.add(emoji)
                }
            }
            val cats = ORDER.mapNotNull { name -> byGroup[name]?.let { Category(name, name, ICON[name] ?: "•", it) } }
            return EmojiData(cats, entries, bases)
        }

        fun normalize(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s.lowercase()) sb.append(ToneRules.stripTone(c))
            return sb.toString().trim()
        }
    }
}
