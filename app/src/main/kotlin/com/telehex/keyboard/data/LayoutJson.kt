package com.telehex.keyboard.data

import com.telehex.layout.LayoutModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON lưu lớp chữ trong SharedPreferences: mảng các hàng, ô phím cách là `null`. Giữ đúng định dạng của org.json vì
 * độ lệch chạm đã học được đóng dấu bằng chuỗi này ([Prefs.offsetsStampedOnLayout]).
 */
fun LayoutModel.toJson(): String {
    val arr = JSONArray()
    for (row in rows) {
        val jr = JSONArray()
        for (c in row) jr.put(c ?: JSONObject.NULL)
        arr.put(jr)
    }
    return arr.toString()
}

fun LayoutModel.Companion.fromJson(json: String): LayoutModel? = runCatching {
    val arr = JSONArray(json)
    val rows = (0 until arr.length()).map { r ->
        val jr = arr.getJSONArray(r)
        (0 until jr.length()).map { c -> if (jr.isNull(c)) null else jr.getString(c) }
    }
    LayoutModel(rows)
}.getOrNull()
