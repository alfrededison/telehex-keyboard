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

    /** @return true nếu có thao tác để hoàn tác. */
    fun undo(ic: InputConnection): Boolean {
        val op = undoStack.removeLastOrNull() ?: return false
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
        val op = redoStack.removeLastOrNull() ?: return false
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
