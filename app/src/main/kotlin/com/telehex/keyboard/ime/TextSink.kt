package com.telehex.keyboard.ime

import android.view.inputmethod.InputConnection

/**
 * Cách đưa âm tiết đang soạn vào ô nhập.
 *
 * Mặc định dùng composing text. Nhiều app (Zalo, Messenger, WebView) xử lý composing không ổn,
 * nên có chế độ dự phòng dùng `deleteSurroundingText` + `commitText`.
 */
interface TextSink {
    /** Hiện âm tiết đang soạn (thay thế bản trước). [suspicious] = tô đỏ nhạt vì không thể là tiếng Việt. */
    fun setComposing(ic: InputConnection, text: String, suspicious: Boolean = false)

    /** Bỏ phần đang soạn khỏi ô nhập (khi engine về rỗng do xoá). */
    fun clearComposing(ic: InputConnection)

    /** Chốt: thay phần đang soạn bằng [text]. */
    fun commit(ic: InputConnection, text: String)

    /** Giữ nguyên chữ trong ô nhập nhưng không coi là đang soạn nữa (con trỏ nhảy, đổi ô nhập). */
    fun detach(ic: InputConnection)

    /** Phần đang soạn có khớp với văn bản trước con trỏ không (phát hiện con trỏ bị di chuyển). */
    fun matchesCursor(ic: InputConnection, composing: String, selStart: Int, selEnd: Int, candStart: Int, candEnd: Int): Boolean
}

class ComposingSink : TextSink {
    override fun setComposing(ic: InputConnection, text: String, suspicious: Boolean) {
        if (!suspicious) { ic.setComposingText(text, 1); return }
        val sp = android.text.SpannableString(text)
        sp.setSpan(android.text.style.BackgroundColorSpan(0x40FF3B30), 0, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ic.setComposingText(sp, 1)
    }

    override fun clearComposing(ic: InputConnection) {
        ic.setComposingText("", 1)
        ic.finishComposingText()
    }

    override fun commit(ic: InputConnection, text: String) { ic.commitText(text, 1) }

    override fun detach(ic: InputConnection) { ic.finishComposingText() }

    override fun matchesCursor(ic: InputConnection, composing: String, selStart: Int, selEnd: Int, candStart: Int, candEnd: Int): Boolean {
        if (selStart != selEnd) return false
        if (candStart < 0 || candEnd < 0) return false
        return selEnd == candEnd
    }
}

class CommitSink : TextSink {
    private var committed = 0

    override fun setComposing(ic: InputConnection, text: String, suspicious: Boolean) {
        ic.beginBatchEdit()
        if (committed > 0) ic.deleteSurroundingText(committed, 0)
        ic.commitText(text, 1)
        committed = text.length
        ic.endBatchEdit()
    }

    override fun clearComposing(ic: InputConnection) {
        if (committed > 0) ic.deleteSurroundingText(committed, 0)
        committed = 0
    }

    override fun commit(ic: InputConnection, text: String) {
        ic.beginBatchEdit()
        if (committed > 0) ic.deleteSurroundingText(committed, 0)
        ic.commitText(text, 1)
        committed = 0
        ic.endBatchEdit()
    }

    override fun detach(ic: InputConnection) { committed = 0 }

    override fun matchesCursor(ic: InputConnection, composing: String, selStart: Int, selEnd: Int, candStart: Int, candEnd: Int): Boolean {
        if (selStart != selEnd) return false
        val before = ic.getTextBeforeCursor(composing.length, 0) ?: return false
        return before.toString() == composing
    }
}
