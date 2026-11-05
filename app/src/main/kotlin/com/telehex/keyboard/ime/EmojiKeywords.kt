package com.telehex.keyboard.ime

/** Từ khoá tiếng Việt → emoji, dùng cho gợi ý emoji trên thanh khi gõ từ khoá. */
object EmojiKeywords {
    private const val RAW = """
vui:😄,😊
cười:😂,🤣
buồn:😢,😔
khóc:😭
yêu:❤️,🥰
tim:❤️,💕
thích:👍,😍
giận:😡,😠
sợ:😱
ngủ:😴
ốm:🤒
mệt:😩
ok:👌,👍
cảm ơn:🙏
chào:👋
hôn:😘,💋
tiệc:🎉,🥳
sinh nhật:🎂,🎉
chúc mừng:🎉,🎊
lửa:🔥
sao:⭐
nắng:☀️
mưa:🌧️,☔
hoa:🌸,🌹
ăn:🍜,🍚
cà phê:☕
bia:🍺
bóng:⚽
xe:🚗
máy bay:✈️
nhà:🏠
tiền:💰
đúng:✅
sai:❌
suy nghĩ:🤔
chó:🐶
mèo:🐱
"""
    val MAP: Map<String, List<String>> by lazy {
        RAW.lineSequence().filter { ':' in it }.associate { line ->
            val (k, v) = line.split(':', limit = 2)
            k.trim() to v.split(',').map { it.trim() }
        }
    }

    /** Emoji gợi ý cho một từ (hoặc cụm "từ trước + từ này"). */
    fun forWord(word: String, previous: String?): List<String> {
        val w = word.lowercase()
        val out = ArrayList<String>()
        if (previous != null) MAP["${previous.lowercase()} $w"]?.let { out.addAll(it) }
        MAP[w]?.let { out.addAll(it) }
        return out.distinct().take(2)
    }
}
