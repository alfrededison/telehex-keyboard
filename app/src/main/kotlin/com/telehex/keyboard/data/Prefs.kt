package com.telehex.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.telehex.keyboard.layout.LayoutModel
import com.telehex.telex.TelexConfig
import org.json.JSONObject

/**
 * Lưu trữ: layout JSON, bảng ký tự vuốt xuống, emoji gần đây, tuỳ chọn.
 * Dùng SharedPreferences để không thêm dependency.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("telehex", Context.MODE_PRIVATE)

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.registerOnSharedPreferenceChangeListener(l)
    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)

    var newToneStyle: Boolean
        get() = sp.getBoolean(KEY_NEW_TONE_STYLE, false)
        set(v) = sp.edit().putBoolean(KEY_NEW_TONE_STYLE, v).apply()

    var lAsGrave: Boolean
        get() = sp.getBoolean(KEY_L_GRAVE, false)
        set(v) = sp.edit().putBoolean(KEY_L_GRAVE, v).apply()

    var englishFallback: Boolean
        get() = sp.getBoolean(KEY_EN_FALLBACK, true)
        set(v) = sp.edit().putBoolean(KEY_EN_FALLBACK, v).apply()

    var haptic: Boolean
        get() = sp.getBoolean(KEY_HAPTIC, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTIC, v).apply()

    var sound: Boolean
        get() = sp.getBoolean(KEY_SOUND, false)
        set(v) = sp.edit().putBoolean(KEY_SOUND, v).apply()

    /** "system" | "light" | "dark" */
    var themeMode: String
        get() = sp.getString(KEY_THEME, "system") ?: "system"
        set(v) = sp.edit().putString(KEY_THEME, v).apply()

    /** Padding thêm phía dưới (dp), vì lục giác đều nên chiều cao chỉ đổi qua padding. */
    var extraBottomDp: Int
        get() = sp.getInt(KEY_EXTRA_BOTTOM, 0)
        set(v) = sp.edit().putInt(KEY_EXTRA_BOTTOM, v).apply()

    /** Tăng mỗi khi gói ngôn ngữ / dữ liệu học đổi từ Cài đặt, để IME nạp lại. */
    var modelsVersion: Long
        get() = sp.getLong(KEY_MODELS_VERSION, 0)
        set(v) = sp.edit().putLong(KEY_MODELS_VERSION, v).apply()

    /** Tông da mặc định cho emoji (0 = không, 1..5 = sáng → tối). */
    var skinTone: Int
        get() = sp.getInt(KEY_SKIN_TONE, 0)
        set(v) = sp.edit().putInt(KEY_SKIN_TONE, v).apply()

    /** Lưu clipboard history. */
    var clipboardHistory: Boolean
        get() = sp.getBoolean(KEY_CLIPBOARD, true)
        set(v) = sp.edit().putBoolean(KEY_CLIPBOARD, v).apply()

    /** Gõ tắt do người dùng định nghĩa: "ko" → "không". */
    var shortcuts: Map<String, String>
        get() = sp.getString(KEY_SHORTCUTS, null)?.let { json ->
            runCatching { val o = JSONObject(json); o.keys().asSequence().associateWith { o.getString(it) } }.getOrNull()
        } ?: emptyMap()
        set(v) = sp.edit().putString(KEY_SHORTCUTS, JSONObject(v).toString()).apply()

    /** Gợi ý âm tiết tiếp theo trên thanh gợi ý. */
    var predictions: Boolean
        get() = sp.getBoolean(KEY_PREDICTIONS, true)
        set(v) = sp.edit().putBoolean(KEY_PREDICTIONS, v).apply()

    /** Chế độ ẩn danh: không học từ người dùng (bật thủ công). */
    var incognito: Boolean
        get() = sp.getBoolean(KEY_INCOGNITO, false)
        set(v) = sp.edit().putBoolean(KEY_INCOGNITO, v).apply()

    /** Vùng chạm động: nới vùng phím hợp lệ tiếp theo theo ngữ pháp âm tiết (mặc định tắt). */
    var dynamicHitZones: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC_ZONES, false)
        set(v) = sp.edit().putBoolean(KEY_DYNAMIC_ZONES, v).apply()

    /** Học độ lệch chạm của người dùng theo từng phím. */
    var learnTouchOffsets: Boolean
        get() = sp.getBoolean(KEY_LEARN_OFFSETS, true)
        set(v) = sp.edit().putBoolean(KEY_LEARN_OFFSETS, v).apply()

    /** Độ lệch chạm đã học (dp): mã phím → [dx, dy]. */
    var touchOffsets: Map<String, FloatArray>
        get() = sp.getString(KEY_TOUCH_OFFSETS, null)?.let { json ->
            runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { k ->
                    val a = o.getJSONArray(k); floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat())
                }
            }.getOrNull()
        } ?: emptyMap()
        set(v) {
            val o = JSONObject()
            for ((k, a) in v) o.put(k, org.json.JSONArray().put(a[0].toDouble()).put(a[1].toDouble()))
            sp.edit().putString(KEY_TOUCH_OFFSETS, o.toString()).apply()
        }

    /** Ngưỡng cử chỉ, chỉnh theo tay từng người. */
    var swipeThresholdDp: Int
        get() = sp.getInt(KEY_SWIPE_DP, 24)
        set(v) = sp.edit().putInt(KEY_SWIPE_DP, v).apply()

    /** Thời gian giữ phím cách trước khi kéo để vào chế độ con trỏ (ms). */
    var holdMs: Int
        get() = sp.getInt(KEY_HOLD_MS, 300)
        set(v) = sp.edit().putInt(KEY_HOLD_MS, v).apply()

    /** Giữ phím lâu hơn ngưỡng này là long-press (ms), ví dụ giữ 123 để mở dialpad. */
    var longPressMs: Int
        get() = sp.getInt(KEY_LONG_PRESS_MS, 450)
        set(v) = sp.edit().putInt(KEY_LONG_PRESS_MS, v).apply()

    /** Khoảng cách kéo (dp) cho mỗi bước xoá từ / di con trỏ. */
    var stepDp: Int
        get() = sp.getInt(KEY_STEP_DP, 50)
        set(v) = sp.edit().putInt(KEY_STEP_DP, v).apply()

    /** Hai lần chạm phím cách cách nhau dưới ngưỡng này (ms) là chạm đúp. */
    var doubleTapMs: Int
        get() = sp.getInt(KEY_DOUBLE_TAP_MS, 300)
        set(v) = sp.edit().putInt(KEY_DOUBLE_TAP_MS, v).apply()

    /** Danh sách package dùng chế độ dự phòng commit thay vì composing. */
    var fallbackPackages: Set<String>
        get() = sp.getStringSet(KEY_FALLBACK_PKGS, DEFAULT_FALLBACK_PKGS) ?: DEFAULT_FALLBACK_PKGS
        set(v) = sp.edit().putStringSet(KEY_FALLBACK_PKGS, v).apply()

    var layout: LayoutModel
        get() = sp.getString(KEY_LAYOUT, null)?.let { LayoutModel.fromJson(it) } ?: LayoutModel.DEFAULT
        set(v) = sp.edit().putString(KEY_LAYOUT, v.toJson()).apply()

    /** Ký tự vuốt xuống do người dùng gán (phím → chuỗi). */
    var customKeys: Map<String, String>
        get() = sp.getString(KEY_CUSTOM, null)?.let { json ->
            runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrNull()
        } ?: emptyMap()
        set(v) = sp.edit().putString(KEY_CUSTOM, JSONObject(v).toString()).apply()

    /** Preset layout do người dùng lưu: tên → LayoutModel. */
    var presets: Map<String, LayoutModel>
        get() = sp.getString(KEY_PRESETS, null)?.let { json ->
            runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().mapNotNull { name -> LayoutModel.fromJson(o.getString(name))?.let { name to it } }.toMap()
            }.getOrNull()
        } ?: emptyMap()
        set(v) {
            val o = JSONObject()
            for ((name, layout) in v) o.put(name, layout.toJson())
            sp.edit().putString(KEY_PRESETS, o.toString()).apply()
        }

    var recentEmoji: List<String>
        get() = sp.getString(KEY_RECENT_EMOJI, "")!!.split('\u001F').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString(KEY_RECENT_EMOJI, v.take(32).joinToString("\u001F")).apply()

    fun telexConfig(): TelexConfig = TelexConfig(
        newToneStyle = newToneStyle,
        lAsGrave = lAsGrave,
        englishFallback = englishFallback,
    )

    /** Ký tự vuốt xuống của một phím: tuỳ biến trước, mặc định sau. */
    fun hintOf(code: String): String = customKeys[code] ?: DEFAULT_SWIPE_DOWN[code] ?: ""

    companion object {
        const val KEY_NEW_TONE_STYLE = "new_tone_style"
        const val KEY_L_GRAVE = "l_as_grave"
        const val KEY_EN_FALLBACK = "english_fallback"
        const val KEY_HAPTIC = "haptic"
        const val KEY_SOUND = "sound"
        const val KEY_THEME = "theme"
        const val KEY_EXTRA_BOTTOM = "extra_bottom_dp"
        const val KEY_FALLBACK_PKGS = "fallback_packages"
        const val KEY_LAYOUT = "layout_json"
        const val KEY_CUSTOM = "custom_keys"
        const val KEY_RECENT_EMOJI = "recent_emoji"
        const val KEY_PRESETS = "layout_presets"
        const val KEY_MODELS_VERSION = "models_version"
        const val KEY_SKIN_TONE = "skin_tone"
        const val KEY_CLIPBOARD = "clipboard_history"
        const val KEY_SHORTCUTS = "shortcuts"
        const val KEY_PREDICTIONS = "predictions"
        const val KEY_INCOGNITO = "incognito"
        const val KEY_DYNAMIC_ZONES = "dynamic_hit_zones"
        const val KEY_LEARN_OFFSETS = "learn_touch_offsets"
        const val KEY_TOUCH_OFFSETS = "touch_offsets"
        const val KEY_SWIPE_DP = "swipe_threshold_dp"
        const val KEY_HOLD_MS = "hold_ms"
        const val KEY_LONG_PRESS_MS = "long_press_ms"
        const val KEY_STEP_DP = "step_dp"
        const val KEY_DOUBLE_TAP_MS = "double_tap_ms"

        val DEFAULT_FALLBACK_PKGS: Set<String> = setOf("com.zing.zalo", "com.facebook.orca")

        val DEFAULT_SWIPE_DOWN: Map<String, String> = mapOf(
            "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5", "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
            "a" to "😄", "s" to "😂", "d" to "😍", "f" to "👍", "g" to "🙏", "h" to "❤️", "j" to "😭", "k" to "🔥", "l" to "🎉",
            "z" to "@", "x" to "#", "c" to "&", "v" to "-", "b" to "/", "n" to "(", "m" to ")",
            "," to "?", "." to "!",
        )
    }
}
