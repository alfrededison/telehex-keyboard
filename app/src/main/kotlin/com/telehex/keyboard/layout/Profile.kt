package com.telehex.keyboard.layout

/**
 * Bộ bố cục đủ ba lớp: chữ, vuốt xuống, 123. Bàn phím luôn dùng đúng một profile; profile có sẵn
 * ([BUILT_IN]) không sửa được, sửa thì lưu thành profile mới.
 */
class Profile(val name: String, val letters: LayoutModel, val swipe: CellGrid, val symbols: List<CellGrid>) {

    val isBuiltIn: Boolean get() = BUILT_IN.any { it.name == name }

    fun sameLayers(o: Profile): Boolean = letters.rows == o.letters.rows && swipe == o.swipe && symbols == o.symbols

    fun named(n: String) = Profile(n, letters, swipe, symbols)

    /** Giá trị vuốt xuống / 123 nằm dưới phím chức năng thì dời sang ô trống ([KeyLayers.reconcile]). */
    fun reconciled(): Profile = Profile(
        name, letters,
        KeyLayers.reconcile(letters, listOf(swipe), KeyLayers::swipeUsable).single(),
        KeyLayers.reconcile(letters, symbols, KeyLayers::symbolUsable),
    )

    companion object {
        const val MINE = "Của tôi"

        val DEFAULT = Profile("Mặc định", LayoutModel.DEFAULT, KeyLayers.DEFAULT_SWIPE, SymbolPages.DEFAULT)

        /** Profile có sẵn, hiện trước profile người dùng tạo. */
        val BUILT_IN: List<Profile> = listOf(
            DEFAULT,
            Profile("Cân bằng hai ngón", LayoutModel.BALANCED, KeyLayers.DEFAULT_SWIPE, SymbolPages.DEFAULT),
        )

        fun nameTaken(name: String, taken: Collection<String>) = taken.any { it.equals(name, ignoreCase = true) }

        /** [base] nếu chưa ai dùng (không phân biệt hoa thường), không thì "[base] 2", "[base] 3"… */
        fun uniqueName(base: String, taken: Collection<String>): String {
            if (!nameTaken(base, taken)) return base
            var i = 2
            while (nameTaken("$base $i", taken)) i++
            return "$base $i"
        }

        /**
         * Chưa có profile đang dùng (bản cài từ trước khi có profile) hay profile đó không còn: bố cục đang dùng
         * ([current]) trùng profile nào thì dùng profile đó, không thì thành profile "Của tôi".
         * @return profile người dùng và tên profile đang dùng.
         */
        fun adopt(current: Profile, existing: List<Profile>): Pair<List<Profile>, String> {
            (BUILT_IN + existing).firstOrNull { it.sameLayers(current) }?.let { return existing to it.name }
            val name = uniqueName(MINE, (BUILT_IN + existing).map { it.name })
            return existing + current.named(name) to name
        }
    }
}
