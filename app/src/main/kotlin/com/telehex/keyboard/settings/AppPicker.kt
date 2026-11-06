package com.telehex.keyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Một app hiện trong bảng chọn; [installed] = false với package đã lưu nhưng không còn trên máy. */
class AppEntry(val pkg: String, val label: String, val installed: Boolean)

/**
 * Bảng chọn nhiều app (biểu tượng + tên + ô tích, có ô tìm), thay cho việc gõ tên package.
 * Liệt kê các app có biểu tượng trên launcher — cần khai báo <queries> trong manifest từ Android 11.
 *
 * Activity giữ một instance, gọi [saveState]/[restoreState] để lựa chọn chưa lưu sống qua xoay màn hình,
 * và [dismiss] trong onDestroy để không rò cửa sổ.
 */
class AppPicker(
    private val activity: Activity,
    private val title: String,
    private val onSave: (Set<String>) -> Unit,
) {
    private var dialog: AlertDialog? = null
    /** Lựa chọn đang sửa (khi đang nạp hoặc đang mở bảng); null = bảng không mở. */
    private var chosen: MutableSet<String>? = null
    private var query = ""

    fun show(selected: Set<String>, initialQuery: String = "") {
        if (chosen != null) return // đang nạp hoặc đã mở: bỏ qua chạm lặp
        chosen = selected.toMutableSet()
        query = initialQuery
        // Nạp nhãn app có thể mất vài trăm ms, làm ngoài luồng UI.
        Thread {
            val apps = loadApps(selected)
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed && chosen != null) open(apps)
            }
        }.start()
    }

    fun saveState(out: Bundle) {
        val c = chosen ?: return
        out.putStringArrayList(KEY_CHOSEN, ArrayList(c))
        out.putString(KEY_QUERY, query)
    }

    fun restoreState(state: Bundle?) {
        val c = state?.getStringArrayList(KEY_CHOSEN) ?: return
        show(c.toSet(), state.getString(KEY_QUERY) ?: "")
    }

    fun dismiss() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
    }

    @Suppress("DEPRECATION")
    private fun loadApps(selected: Set<String>): List<AppEntry> {
        val pm = activity.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .filter { it != activity.packageName }
            .distinct()
            .mapNotNull { pkg -> labelOf(pm, pkg)?.let { AppEntry(pkg, it, true) } }
        val known = installed.mapTo(HashSet()) { it.pkg }
        // Package đã lưu nhưng không thấy trên launcher (chưa cài, hoặc app không có biểu tượng): vẫn hiện để bỏ tích được.
        val extra = selected.filter { it !in known }.map { pkg ->
            labelOf(pm, pkg)?.let { AppEntry(pkg, it, true) } ?: AppEntry(pkg, KNOWN_NAMES[pkg] ?: pkg, false)
        }
        return (installed + extra).sortedWith(
            compareByDescending<AppEntry> { it.pkg in selected }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }

    private fun open(apps: List<AppEntry>) {
        val pm = activity.packageManager
        val chosen = chosen ?: return
        val icons = HashMap<String, Drawable?>()
        fun filter(q: String) = if (q.isBlank()) apps else apps.filter { it.label.contains(q.trim(), true) || it.pkg.contains(q.trim(), true) }
        var shown = filter(query)
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics).toInt()

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val row = (convertView as? LinearLayout) ?: LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(6), dp(4), dp(6))
                    addView(ImageView(activity), LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
                    addView(LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(TextView(activity).apply { textSize = 16f })
                        addView(TextView(activity).apply { textSize = 12f; alpha = 0.6f })
                    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(CheckBox(activity).apply { isClickable = false; isFocusable = false })
                }
                val app = shown[position]
                val texts = row.getChildAt(1) as LinearLayout
                (row.getChildAt(0) as ImageView).setImageDrawable(
                    icons.getOrPut(app.pkg) { runCatching { pm.getApplicationIcon(app.pkg) }.getOrNull() }
                )
                (texts.getChildAt(0) as TextView).text = app.label
                (texts.getChildAt(1) as TextView).text = if (app.installed) app.pkg else "${app.pkg} · chưa cài"
                (row.getChildAt(2) as CheckBox).isChecked = app.pkg in chosen
                return row
            }
        }

        val search = EditText(activity).apply {
            hint = "Tìm app"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(query)
        }
        val list = ListView(activity).apply {
            this.adapter = adapter
            setOnItemClickListener { _, _, pos, _ ->
                val pkg = shown[pos].pkg
                if (!chosen.remove(pkg)) chosen += pkg
                adapter.notifyDataSetChanged()
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s.toString()
                shown = filter(query)
                adapter.notifyDataSetChanged()
            }
        })
        // Màn ngang thấp: danh sách không quá ~45% chiều cao để còn chỗ cho tiêu đề, ô tìm và nút.
        val listHeight = minOf(dp(400), (activity.resources.displayMetrics.heightPixels * 0.45f).toInt())
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
            addView(search)
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight))
        }
        dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Lưu") { _, _ -> onSave(chosen.toSet()) }
            .setNegativeButton("Huỷ", null)
            .setOnDismissListener { this.chosen = null; dialog = null }
            .show()
    }

    companion object {
        private const val KEY_CHOSEN = "app_picker_chosen"
        private const val KEY_QUERY = "app_picker_query"

        /** Tên dễ đọc cho các package mặc định khi app chưa cài (không đọc được nhãn từ hệ thống). */
        private val KNOWN_NAMES = mapOf("com.zing.zalo" to "Zalo", "com.facebook.orca" to "Messenger")

        private fun labelOf(pm: PackageManager, pkg: String): String? =
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()

        /** Tên hiển thị của các package; package không còn cài thì dùng tên quen thuộc hoặc tên package. */
        fun labels(activity: Activity, pkgs: Collection<String>): List<String> {
            val pm = activity.packageManager
            return pkgs.map { pkg -> labelOf(pm, pkg) ?: KNOWN_NAMES[pkg] ?: pkg }.sortedWith(String.CASE_INSENSITIVE_ORDER)
        }
    }
}
