package com.telehex.typing

/**
 * Khoảng trắng quanh chữ chèn từ thanh gợi ý và dấu câu, dùng chung cho hai app để gõ ra cùng một kết quả.
 */
object Spacing {
    /** Dấu câu đứng sát chữ phía trước: gõ ngay sau khoảng trắng tự thêm thì xoá khoảng trắng đó ("không" + "," → "không,"). */
    const val NO_SPACE_BEFORE: String = ".,!?:;…)]}"

    /** Dấu mở ngoặc/nháy: từ gõ vuốt EN chèn ngay sau, không thêm khoảng trắng đằng trước. */
    const val OPENERS: String = "([{\"'“‘"

    /** Dấu mở mà gõ tắt vẫn bung ngay sau: "(dc" → "(được". */
    const val SHORTCUT_OPENERS: String = "([{\"'“‘«<"

    /**
     * Chọn một từ trên thanh gợi ý thì thêm khoảng trắng sau nó, trừ khi ngay sau con trỏ đã là khoảng trắng hay dấu câu
     * đứng sát: "khong|," chọn "không" ra "không,", không phải "không ,". [next]: ký tự ngay sau con trỏ (null = hết văn bản).
     */
    fun spaceAfterPick(next: Char?): Boolean = next == null || (!next.isWhitespace() && next !in NO_SPACE_BEFORE)

    /** Gõ [typed] ngay sau khoảng trắng tự thêm thì xoá khoảng trắng đó. */
    fun removesAutoSpace(typed: String): Boolean = typed.length == 1 && typed[0] in NO_SPACE_BEFORE

    /** Từ gõ vuốt EN chèn sau [previous] (ký tự trước con trỏ) cần khoảng trắng đằng trước không ("hello,|" → "hello, world"). */
    fun spaceBeforeGlide(previous: Char?): Boolean = previous != null && !previous.isWhitespace() && previous !in OPENERS

    /** Gõ tắt tách được sau [c]: dấu mở ngoặc/nháy. */
    fun opensShortcut(c: Char): Boolean = c in SHORTCUT_OPENERS

    // Bản nhận chuỗi cho Swift (Char của Kotlin sang Swift là số UTF-16)

    /** [spaceAfterPick] theo văn bản sau con trỏ. */
    fun pickAddsSpace(textAfterCursor: String?): Boolean = spaceAfterPick(textAfterCursor?.firstOrNull())

    /** [spaceBeforeGlide] theo văn bản trước con trỏ. */
    fun glideNeedsLeadingSpace(textBeforeCursor: String?): Boolean = spaceBeforeGlide(textBeforeCursor?.lastOrNull())

    /** Âm tiết đứng ngay sau [textBeforeSyllable] có bung gõ tắt được không: đầu văn bản, sau khoảng trắng hay dấu mở. */
    fun shortcutCanFollow(textBeforeSyllable: String): Boolean =
        textBeforeSyllable.lastOrNull()?.let { it.isWhitespace() || opensShortcut(it) } ?: true
}
