package com.telehex.typing

/** Gõ tắt khớp: thay [prefix] (phần đã chốt trước âm tiết, có thể rỗng) cùng âm tiết đang soạn bằng [expansion]. */
class ShortcutMatch(val prefix: String, val expansion: String)

/** Bung gõ tắt khi chạm cách, dùng chung cho hai app. */
object Shortcuts {
    /** Đoạn trước âm tiết được xét: không quá chừng này ký tự, chỉ trong cùng một từ. */
    const val MAX_HEAD = 32

    /**
     * Gõ tắt khớp với token trước con trỏ: phần đã chốt [textBefore] (văn bản trước âm tiết đang soạn) nối với âm tiết.
     * Token lấy từ khoảng trắng gần nhất ("k2", "@@g" khớp được), hoặc chỉ âm tiết vừa gõ ngay sau một dấu mở ("(dc" khớp
     * "dc" → "(được"); "x.dc" hay link "https://x.co" thì không tách. Thử cả chữ đang hiện [typed] lẫn phím đã gõ [keys]:
     * khớp đúng trước, rồi khớp chữ thường (chữ đầu hoa thì chữ thay thế cũng hoa: "Ko" → "Không").
     */
    fun match(snippets: Map<String, String>, textBefore: String, typed: String, keys: String): ShortcutMatch? {
        if (snippets.isEmpty()) return null
        val head = textBefore.takeLast(MAX_HEAD).takeLastWhile { !it.isWhitespace() }
        for (start in 0..head.length) {
            if (start > 0 && (typed.isEmpty() || !Spacing.opensShortcut(head[start - 1]))) continue
            val part = head.substring(start)
            for (word in listOf(typed, keys).distinct()) {
                val token = part + word
                if (token.isEmpty()) continue
                val expansion = snippets[token]
                    ?: snippets[token.lowercase()]?.let { e -> if (token.first().isUpperCase()) e.replaceFirstChar { it.titlecase() } else e }
                if (expansion != null) return ShortcutMatch(part, expansion)
            }
        }
        return null
    }
}
