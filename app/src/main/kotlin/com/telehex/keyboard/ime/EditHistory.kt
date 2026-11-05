package com.telehex.keyboard.ime

import android.view.inputmethod.InputConnection

/**
 * Ngăn xếp undo/redo do bàn phím tự quản lý, vì `InputConnection` không có lệnh undo chuẩn.
 * Chỉ ghi các thao tác của chính bàn phím: chèn (commit/dán), xoá (xoá/cắt).
 * Lịch sử bị xoá khi con trỏ nhảy hoặc sang ô nhập khác.
 */
class EditHistory(private val limit: Int = 50) {
    sealed class Op {
        /** Đã chèn [text] tại con trỏ (con trỏ nằm sau text). */
        data class Insert(val text: String) : Op()
        /** Đã xoá [text] ngay trước con trỏ. */
        data class Delete(val text: String) : Op()
    }

    private val undoStack = ArrayDeque<Op>()
    private val redoStack = ArrayDeque<Op>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(op: Op) {
        if ((op is Op.Insert && op.text.isEmpty()) || (op is Op.Delete && op.text.isEmpty())) return
        undoStack.addLast(op)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    fun clear() { undoStack.clear(); redoStack.clear() }

    /** Rút bớt [n] ký tự cuối của thao tác chèn gần nhất (khi đã xoá lại khoảng trắng vừa chèn). */
    fun trimLastInsert(n: Int) {
        val last = undoStack.lastOrNull() as? Op.Insert ?: return
        undoStack.removeLast()
        if (last.text.length > n) undoStack.addLast(Op.Insert(last.text.dropLast(n)))
    }

    /**
     * Chỉ hoàn tác khi văn bản ngay trước con trỏ đúng là thứ sẽ bị xoá; nếu con trỏ đã nhảy đi
     * nơi khác thì xoá lịch sử thay vì xoá nhầm chữ không phải do bàn phím gõ.
     */
    private fun safeToDelete(ic: InputConnection, text: String): Boolean {
        if (!ic.getSelectedText(0).isNullOrEmpty()) return false
        val before = ic.getTextBeforeCursor(text.length, 0) ?: return false
        return before.toString() == text
    }

    /** @return true nếu đã hoàn tác được một thao tác. */
    fun undo(ic: InputConnection): Boolean {
        val op = undoStack.lastOrNull() ?: return false
        if (op is Op.Insert && !safeToDelete(ic, op.text)) { clear(); return false }
        undoStack.removeLast()
        ic.beginBatchEdit()
        when (op) {
            is Op.Insert -> ic.deleteSurroundingText(op.text.length, 0)
            is Op.Delete -> ic.commitText(op.text, 1)
        }
        ic.endBatchEdit()
        redoStack.addLast(op)
        return true
    }

    fun redo(ic: InputConnection): Boolean {
        val op = redoStack.lastOrNull() ?: return false
        if (op is Op.Delete && !safeToDelete(ic, op.text)) { clear(); return false }
        redoStack.removeLast()
        ic.beginBatchEdit()
        when (op) {
            is Op.Insert -> ic.commitText(op.text, 1)
            is Op.Delete -> ic.deleteSurroundingText(op.text.length, 0)
        }
        ic.endBatchEdit()
        undoStack.addLast(op)
        return true
    }
}
