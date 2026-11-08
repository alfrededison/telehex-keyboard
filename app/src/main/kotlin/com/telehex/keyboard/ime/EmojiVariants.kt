package com.telehex.keyboard.ime

/** Chỉ chọn biến thể có trong danh mục Unicode và font của máy vẽ được. */
class EmojiVariants(
    private val available: Set<String>,
    private val modifierBases: Set<String>,
    private val canDraw: (String) -> Boolean,
) {
    /** 0 giữ nguyên, 1 nữ, 2 nam. Emoji đã chọn rõ giới tính được giữ nguyên. */
    fun apply(emoji: String, gender: Int, tone: Int): String {
        val gendered = when (gender) {
            1, 2 -> {
                val female = gender == 1
                val replacement = when {
                    emoji.startsWith("🧑") -> (if (female) "👩" else "👨") + emoji.removePrefix("🧑")
                    emoji == "🧒" -> if (female) "👧" else "👦"
                    emoji == "🧓" -> if (female) "👵" else "👴"
                    emoji == "🫅" -> if (female) "👸" else "🤴"
                    else -> {
                        val direction = "‍➡️".takeIf { emoji.endsWith(it) }.orEmpty()
                        emoji.removeSuffix(direction) + (if (female) "‍♀️" else "‍♂️") + direction
                    }
                }
                replacement.takeIf { it in available && canDraw(it) } ?: emoji
            }
            else -> emoji
        }
        if (tone !in 1..5 || gendered !in modifierBases) return gendered
        val first = gendered.offsetByCodePoints(0, 1)
        val rest = gendered.substring(first).removePrefix("️")
        val toned = gendered.substring(0, first) + String(Character.toChars(0x1F3FA + tone)) + rest
        return if (canDraw(toned)) toned else gendered
    }
}
