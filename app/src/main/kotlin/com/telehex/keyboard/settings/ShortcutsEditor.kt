package com.telehex.keyboard.settings

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.HexKeyboardService

/** Hai cột nhập độc lập; Tiếp đi từ viết tắt sang nội dung rồi đến dòng sau. */
internal class ShortcutsEditor(context: Context, private val prefs: Prefs, state: Bundle?) : LinearLayout(context) {
    private data class Row(val view: LinearLayout, val key: EditText, val value: EditText)
    private val rows = mutableListOf<Row>()
    private val table = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        add(LinearLayout(context).apply {
            addView(TextView(context).apply { text = "Viết tắt" }, cellParams(1f))
            addView(TextView(context).apply { text = "Nội dung" }, cellParams(2f))
        }, context.dp(12))
        add(table)
        val draft = state?.getStringArrayList(STATE_ROWS)
        if (draft != null) {
            draft.chunked(2).forEach { addRow(it[0], it.getOrElse(1) { "" }) }
        } else {
            prefs.shortcuts.entries.sortedBy { it.key }.forEach { addRow(it.key, it.value) }
        }
        if (rows.isEmpty() || rows.last().key.text.isNotEmpty() || rows.last().value.text.isNotEmpty()) addRow()
        addButton("Thêm dòng") {
            val last = rows.last()
            val row = if (last.key.text.isEmpty() && last.value.text.isEmpty()) last else addRow()
            focus(row.key)
        }
    }

    private fun cellParams(weight: Float) = LayoutParams(0, LayoutParams.WRAP_CONTENT, weight)

    private fun field(label: String, initial: String) = EditText(context).apply {
        id = View.generateViewId()
        // Nháp được lưu theo dòng, tránh id sinh động khôi phục nhầm ô sau khi xoá dòng.
        isSaveEnabled = false
        hint = label
        contentDescription = label
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_NEXT
        privateImeOptions = HexKeyboardService.NO_AUTO_CAP
        setText(initial)
        textSize = 15f
        minHeight = context.dp(48)
    }

    private fun addRow(keyText: String = "", valueText: String = ""): Row {
        val key = field("Viết tắt", keyText)
        val value = field("Nội dung", valueText)
        val view = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val row = Row(view, key, value)
        rows.add(row)
        view.addView(key, cellParams(1f))
        view.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(value, cellParams(1f))
            addView(settingsButton(context, "×") {
                val index = rows.indexOf(row)
                val hadFocus = key.hasFocus() || value.hasFocus()
                rows.remove(row)
                table.removeView(view)
                if (rows.isEmpty()) addRow()
                if (hadFocus) focus(rows[index.coerceAtMost(rows.lastIndex)].key)
                save()
            }.apply {
                contentDescription = "Xoá dòng gõ tắt"
                minWidth = 0
                minimumWidth = 0
                setPadding(0, 0, 0, 0)
            }, LayoutParams(context.dp(48), context.dp(48)))
        }, cellParams(2f))
        table.add(view)
        key.doAfterTextChanged { save() }
        value.doAfterTextChanged { save() }
        onNext(key) { focus(value) }
        onNext(value) {
            val next = rows.getOrNull(rows.indexOf(row) + 1) ?: addRow()
            focus(next.key)
        }
        return row
    }

    private fun onNext(field: EditText, action: () -> Unit) {
        field.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event == null || event.action == KeyEvent.ACTION_UP) action()
                true
            } else false
        }
    }

    private fun focus(field: EditText) {
        field.requestFocus()
        field.setSelection(field.text.length)
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun save() {
        prefs.shortcuts = rows.map { it.key.text.toString().trim() to it.value.text.toString().trim() }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toMap()
    }

    fun saveState(out: Bundle) {
        out.putStringArrayList(STATE_ROWS, ArrayList(rows.flatMap { listOf(it.key.text.toString(), it.value.text.toString()) }))
    }

    private companion object {
        const val STATE_ROWS = "shortcut_rows"
    }
}
