package com.telehex.bridge

import com.telehex.layout.CellGrid
import com.telehex.layout.GridSpec
import com.telehex.layout.HexGeometry
import com.telehex.layout.KeyCode
import com.telehex.layout.KeyLayers
import com.telehex.layout.LayoutModel
import com.telehex.layout.Profile

/**
 * Số đo một kiểu lưới theo bề rộng ô (iOS tự nhân với bề rộng ô thật). [hexRowTops] / [hexHeight] chỉ có nghĩa với lưới
 * tổ ong; lưới chữ nhật dùng bước hàng của app.
 */
class IosGrid(
    val name: String,
    val title: String,
    val lefts: List<List<Double>>,
    val widths: List<Double>,
    val stretches: List<Double>,
    val hexRowTops: List<Double>,
    val hexHeight: Double,
    val span: Double,
    val isHex: Boolean,
    val spaceCells: Int,
)

/** Profile theo định dạng iOS lưu: mỗi lớp là mảng phẳng theo thứ tự đọc, mã phím kiểu iOS ([LayoutBridge.iosCode]). */
class IosProfile(val id: String, val name: String, val letters: List<String>, val swipe: List<String>, val symbols: List<List<String>>)

/** Lớp vuốt xuống và hai trang 123 sau khi dời giá trị bị phím chức năng che. */
class IosLayers(val swipe: List<String>, val symbols: List<List<String>>)

/**
 * Bố cục dùng chung cho iOS: profile có sẵn, số đo lưới, kiểm tra hợp lệ, dời giá trị bị che và ô kề cho sửa gõ sai đều
 * lấy từ module `layout` như Android. iOS lưu profile dạng mảng phẳng với mã phím riêng (`#symbols`, `#settings`,
 * `#backspace`, `"\n"`, phím cách `" "` rồi các ô `""`), nên mọi hàm ở đây nhận và trả đúng định dạng đó.
 */
object LayoutBridge {
    private val toIos = mapOf(
        KeyCode.NUMBERS to "#symbols", KeyCode.SETTINGS to "#settings", KeyCode.ENTER to "\n", KeyCode.BACKSPACE to "#backspace",
    )
    private val fromIos = toIos.entries.associate { (k, v) -> v to k }

    /** Id iOS của profile có sẵn theo kiểu lưới (giữ nguyên id đã lưu trong App Group). */
    private val presetIds = mapOf(
        GridSpec.HEX7 to "default", GridSpec.QWERTY to "qwerty", GridSpec.QWERTY_NUM to "qwertyNum",
        GridSpec.HEX7_NUM to "hexNum", GridSpec.HEX8 to "hex8", GridSpec.HEX8_NUM to "hex8Num",
    )

    /** Mã phím Android → iOS. */
    fun iosCode(code: String): String = toIos[code] ?: code

    /** Mã phím iOS → Android; ô phím cách (`" "`, `""`) là null. */
    fun layoutCode(code: String): String? = if (code == " " || code.isEmpty()) null else fromIos[code] ?: code

    fun grids(): List<IosGrid> = GridSpec.entries.map { spec ->
        // Lưới rộng đúng span, không lề, không khe: bề rộng ô = 1
        val g = HexGeometry(spec.span, 0f, 0f, 0f, spec = spec)
        IosGrid(
            name = spec.name,
            title = spec.title,
            lefts = spec.lefts.map { row -> row.map { it.toDouble() } },
            widths = spec.widths.map { it.toDouble() },
            stretches = spec.stretches.map { it.toDouble() },
            hexRowTops = (0 until spec.rows).map { (g.centerY(it) - g.cellH(it) / 2).toDouble() },
            hexHeight = g.gridHeight.toDouble(),
            span = spec.span.toDouble(),
            isHex = spec.shape == GridSpec.Shape.HEX,
            spaceCells = spec.spaceCells,
        )
    }

    fun presets(): List<IosProfile> = Profile.BUILT_IN.map { p ->
        IosProfile(
            id = "builtin." + presetIds.getValue(p.spec), name = p.name,
            letters = flatLetters(p.letters), swipe = p.swipe.rows.flatten(), symbols = p.symbols.map { it.rows.flatten() },
        )
    }

    /** Lớp chữ iOS hợp lệ: đúng một kiểu lưới, qua [LayoutModel.validate] và có đúng bộ phím của profile có sẵn cùng kiểu. */
    fun validLetters(letters: List<String>): Boolean {
        val model = model(letters) ?: return false
        if (model.validate() != null) return false
        // iOS vẽ phím cách từ ô " " sang phải: dải phím cách phải bắt đầu bằng " ", các ô sau là ""
        val first = letters.indexOfFirst { it == " " || it.isEmpty() }
        if (first < 0 || letters[first] != " " || letters.count { it == " " } != 1) return false
        // So mã iOS thô (không qua ánh xạ): mã kiểu Android như "#123" iOS không xử lý nên không hợp lệ
        return letters.toSet() == flatLetters(Profile.builtInFor(model.spec).letters).toSet()
    }

    /** Dời giá trị vuốt xuống / 123 nằm dưới ô không dùng được sang ô trống, như [Profile.reconciled]. */
    fun reconcile(letters: List<String>, swipe: List<String>, symbols: List<List<String>>): IosLayers? {
        val model = model(letters) ?: return null
        val sizes = model.spec.rowSizes
        if (swipe.size != letters.size || symbols.any { it.size != letters.size }) return null
        val p = Profile("", model, grid(swipe, sizes), symbols.map { grid(it, sizes) }).reconciled()
        return IosLayers(p.swipe.rows.flatten(), p.symbols.map { it.rows.flatten() })
    }

    /** Chữ → các chữ kề trên lưới thật (sửa gõ sai), theo [LayoutModel.letterNeighbors]. */
    fun neighbors(letters: List<String>): Map<String, String> =
        model(letters)?.letterNeighbors()?.entries?.associate { (k, v) -> "$k" to v.joinToString("") } ?: emptyMap()

    fun swipeUsable(code: String): Boolean = layoutCode(code)?.let(KeyLayers::swipeUsable) ?: false
    fun symbolUsable(code: String): Boolean = layoutCode(code)?.let(KeyLayers::symbolUsable) ?: false
    fun isPunct(code: String): Boolean = KeyCode.isPunct(code)

    private fun flatLetters(model: LayoutModel): List<String> {
        val out = ArrayList<String>()
        var inSpace = false
        for (code in model.rows.flatten()) {
            out += if (code == null) (if (inSpace) "" else " ") else iosCode(code)
            inSpace = code == null
        }
        return out
    }

    private fun model(letters: List<String>): LayoutModel? {
        val spec = GridSpec.entries.firstOrNull { it.rowSizes.sum() == letters.size } ?: return null
        return runCatching { LayoutModel(split(letters.map(::layoutCode), spec.rowSizes)) }.getOrNull()
    }

    private fun grid(values: List<String>, sizes: List<Int>) = CellGrid(split(values, sizes))

    private fun <T> split(values: List<T>, sizes: List<Int>): List<List<T>> {
        var start = 0
        return sizes.map { n -> values.subList(start, start + n).also { start += n } }
    }
}
