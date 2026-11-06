package com.telehex.keyboard.data

import android.content.Context
import android.content.SharedPreferences
import com.telehex.keyboard.layout.CellGrid
import com.telehex.keyboard.layout.KeyLayers
import com.telehex.keyboard.layout.LayoutModel
import com.telehex.keyboard.layout.SymbolPages
import com.telehex.telex.TelexConfig
import org.json.JSONArray
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

    /** Màu nhấn, xem [com.telehex.keyboard.ime.Accent]. */
    var accent: String
        get() = sp.getString(KEY_ACCENT, "blue") ?: "blue"
        set(v) = sp.edit().putString(KEY_ACCENT, v).apply()

    /** Padding thêm phía dưới (dp), vì lục giác đều nên chiều cao chỉ đổi qua padding. */
    var extraBottomDp: Int
        get() = sp.getInt(KEY_EXTRA_BOTTOM, 0)
        set(v) = sp.edit().putInt(KEY_EXTRA_BOTTOM, v).apply()

    /** Tăng mỗi khi gói ngôn ngữ / dữ liệu học đổi từ Cài đặt, để IME nạp lại. */
    var modelsVersion: Long
        get() = sp.getLong(KEY_MODELS_VERSION, 0)
        set(v) = sp.edit().putLong(KEY_MODELS_VERSION, v).apply()

    /** Chế độ một tay: 0 tắt, 1 trái, 2 phải. */
    var oneHanded: Int
        get() = sp.getInt(KEY_ONE_HANDED, 0)
        set(v) = sp.edit().putInt(KEY_ONE_HANDED, v).apply()

    /** Toàn bộ tuỳ chọn dạng JSON để sao lưu (không gồm dữ liệu học, lưu riêng). */
    fun exportJson(): String = JSONObject(sp.all.filterKeys { it != KEY_TOUCH_OFFSETS && it != KEY_OFFSETS_LAYOUT }.mapValues { (_, v) ->
        if (v is Set<*>) org.json.JSONArray(v.toList()) else v
    }).toString()

    /** Khôi phục tuỳ chọn từ JSON đã xuất; chỉ nhận các khoá đã biết với đúng kiểu. */
    fun importJson(json: String) {
        val o = JSONObject(json)
        val e = sp.edit()
        for (k in o.keys()) {
            val expected = KNOWN_TYPES[k] ?: continue // khoá lạ: bỏ qua
            when (val v = o.get(k)) {
                is Boolean -> if (expected == Boolean::class) e.putBoolean(k, v)
                is Int -> if (expected == Int::class) e.putInt(k, v) else if (expected == Long::class) e.putLong(k, v.toLong())
                is Long -> if (expected == Long::class) e.putLong(k, v) else if (expected == Int::class) e.putInt(k, v.toInt())
                is String -> if (expected == String::class) e.putString(k, v)
                is org.json.JSONArray -> if (expected == Set::class) e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
            }
        }
        // Bản sao lưu cũ (lớp vuốt xuống / 123 đi theo chữ): bỏ lưới hiện tại để dựng lại từ bố cục vừa khôi phục
        if ((o.has(KEY_LAYOUT) || o.has(KEY_CUSTOM)) && !o.has(KEY_SWIPE_GRID)) e.remove(KEY_SWIPE_GRID)
        if (o.has(KEY_LAYOUT) && !o.has(KEY_SYMBOL_PAGES)) e.remove(KEY_SYMBOL_PAGES)
        e.apply()
    }

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

    /** Tự viết hoa chữ đầu câu. */
    var autoCapitalize: Boolean
        get() = sp.getBoolean(KEY_AUTO_CAP, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_CAP, v).apply()

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

    /**
     * Độ lệch chạm đã học (dp): mã phím → [dx, dy]. Học theo chữ nhưng đúng cho ô mà chữ đang đứng,
     * nên lưu kèm bố cục lúc học; bố cục đổi (sắp xếp lại, khôi phục, đổi mặc định khi nâng cấp) thì bỏ.
     */
    var touchOffsets: Map<String, FloatArray>
        get() = sp.getString(KEY_TOUCH_OFFSETS, null)?.takeIf { offsetsMatchLayout() }?.let { json ->
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
            sp.edit().putString(KEY_TOUCH_OFFSETS, o.toString()).putString(KEY_OFFSETS_LAYOUT, layout.toJson()).apply()
        }

    private fun offsetsMatchLayout(): Boolean {
        val learnedOn = sp.getString(KEY_OFFSETS_LAYOUT, null)
            // Bản cũ không lưu bố cục: chưa từng lưu bố cục riêng nghĩa là học trên bố cục mặc định cũ
            ?: return sp.contains(KEY_LAYOUT)
        return learnedOn == layout.toJson()
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

    /** Dữ liệu cũ: ký tự vuốt xuống gán theo phím (phím → chuỗi). Chỉ đọc để chuyển sang [swipeGrid]. */
    private val legacyCustomKeys: Map<String, String>
        get() = sp.getString(KEY_CUSTOM, null)?.let { json ->
            runCatching {
                val o = JSONObject(json)
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrNull()
        } ?: emptyMap()

    /**
     * Ký tự vuốt xuống theo ô, đã khớp với lớp chữ hiện tại. Chưa có (bản cũ lưu theo phím) thì dựng từ
     * dữ liệu cũ trên bố cục hiện tại, đưa số về chỗ mặc định mới (Q W R Y F H U O P L), rồi lưu lại.
     */
    var swipeGrid: CellGrid
        get() {
            val l = layout
            val raw = sp.getString(KEY_SWIPE_GRID, null)?.let { gridsFromJson(it, 1) }?.single() ?: run {
                val custom = legacyCustomKeys
                var g = CellGrid.fromCodes(l) { custom[it] ?: KeyLayers.DEFAULT_SWIPE_BY_CODE[it] }
                g = KeyLayers.pinDigits(g)
                sp.edit().putString(KEY_SWIPE_GRID, gridsToJson(listOf(g))).apply()
                g
            }
            return KeyLayers.reconcile(l, listOf(raw), KeyLayers::swipeUsable).single()
        }
        set(v) = sp.edit().putString(KEY_SWIPE_GRID, gridsToJson(listOf(v))).apply()

    /** Hai trang lớp 123 theo ô, đã khớp với lớp chữ. Chưa có thì dựng như cách cũ (theo chữ), đưa số về chỗ mặc định mới rồi lưu lại. */
    var symbolPages: List<CellGrid>
        get() {
            val l = layout
            val raw = sp.getString(KEY_SYMBOL_PAGES, null)?.let { gridsFromJson(it, 2) } ?: run {
                var p = SymbolPages.fromLetters(l)
                p = listOf(KeyLayers.pinDigits(p[0]), p[1])
                sp.edit().putString(KEY_SYMBOL_PAGES, gridsToJson(p)).apply()
                p
            }
            return KeyLayers.reconcile(l, raw, KeyLayers::symbolUsable)
        }
        set(v) = sp.edit().putString(KEY_SYMBOL_PAGES, gridsToJson(v)).apply()

    private fun gridsToJson(grids: List<CellGrid>): String =
        JSONArray(grids.map { g -> JSONArray(g.rows.map { JSONArray(it) }) }).toString()

    private fun gridsFromJson(json: String, count: Int): List<CellGrid>? = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).map { i ->
            val g = a.getJSONArray(i)
            CellGrid((0 until g.length()).map { r -> val row = g.getJSONArray(r); (0 until row.length()).map { row.getString(it) } })
        }.takeIf { it.size == count }
    }.getOrNull()

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

    companion object {
        const val KEY_NEW_TONE_STYLE = "new_tone_style"
        const val KEY_L_GRAVE = "l_as_grave"
        const val KEY_EN_FALLBACK = "english_fallback"
        const val KEY_HAPTIC = "haptic"
        const val KEY_SOUND = "sound"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT = "accent"
        const val KEY_EXTRA_BOTTOM = "extra_bottom_dp"
        const val KEY_FALLBACK_PKGS = "fallback_packages"
        const val KEY_LAYOUT = "layout_json"
        const val KEY_CUSTOM = "custom_keys"
        const val KEY_SWIPE_GRID = "swipe_grid"
        const val KEY_SYMBOL_PAGES = "symbol_pages"
        const val KEY_RECENT_EMOJI = "recent_emoji"
        const val KEY_PRESETS = "layout_presets"
        const val KEY_MODELS_VERSION = "models_version"
        const val KEY_ONE_HANDED = "one_handed"
        const val KEY_SKIN_TONE = "skin_tone"
        const val KEY_CLIPBOARD = "clipboard_history"
        const val KEY_SHORTCUTS = "shortcuts"
        const val KEY_PREDICTIONS = "predictions"
        const val KEY_INCOGNITO = "incognito"
        const val KEY_AUTO_CAP = "auto_capitalize"
        const val KEY_DYNAMIC_ZONES = "dynamic_hit_zones"
        const val KEY_LEARN_OFFSETS = "learn_touch_offsets"
        const val KEY_TOUCH_OFFSETS = "touch_offsets"
        const val KEY_OFFSETS_LAYOUT = "touch_offsets_layout"
        const val KEY_SWIPE_DP = "swipe_threshold_dp"
        const val KEY_HOLD_MS = "hold_ms"
        const val KEY_LONG_PRESS_MS = "long_press_ms"
        const val KEY_STEP_DP = "step_dp"
        const val KEY_DOUBLE_TAP_MS = "double_tap_ms"

        val DEFAULT_FALLBACK_PKGS: Set<String> = setOf("com.zing.zalo", "com.facebook.orca")

        /** Khoá → kiểu được phép khi khôi phục (sai kiểu sẽ làm getter ném ClassCastException). */
        val KNOWN_TYPES: Map<String, kotlin.reflect.KClass<*>> = mapOf(
            KEY_NEW_TONE_STYLE to Boolean::class, KEY_L_GRAVE to Boolean::class, KEY_EN_FALLBACK to Boolean::class,
            KEY_HAPTIC to Boolean::class, KEY_SOUND to Boolean::class, KEY_THEME to String::class, KEY_ACCENT to String::class,
            KEY_EXTRA_BOTTOM to Int::class, KEY_FALLBACK_PKGS to Set::class, KEY_LAYOUT to String::class,
            KEY_CUSTOM to String::class, KEY_RECENT_EMOJI to String::class, KEY_PRESETS to String::class,
            KEY_SWIPE_DP to Int::class, KEY_HOLD_MS to Int::class, KEY_LONG_PRESS_MS to Int::class,
            KEY_STEP_DP to Int::class, KEY_DOUBLE_TAP_MS to Int::class,
            KEY_PREDICTIONS to Boolean::class, KEY_INCOGNITO to Boolean::class,
            KEY_DYNAMIC_ZONES to Boolean::class, KEY_LEARN_OFFSETS to Boolean::class,
            KEY_ONE_HANDED to Int::class, KEY_SKIN_TONE to Int::class, KEY_CLIPBOARD to Boolean::class,
            KEY_SHORTCUTS to String::class, KEY_AUTO_CAP to Boolean::class,
            KEY_SWIPE_GRID to String::class, KEY_SYMBOL_PAGES to String::class,
        )
    }
}
