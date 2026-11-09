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
        KeyLayers.reconcileSymbols(letters, symbols),
    )

    /** Đổi chỗ hai ô ở lớp chữ; giá trị vuốt xuống / 123 bị phím chức năng che thì dời sang ô trống. */
    fun swapLetters(a: Pair<Int, Int>, b: Pair<Int, Int>): Profile =
        Profile(name, letters.withSwapped(a, b), swipe, symbols).reconciled()

    /** Đổi chỗ hai ô trong trang [page] (từ 0) của lớp 123. */
    fun swapSymbols(page: Int, a: Pair<Int, Int>, b: Pair<Int, Int>): Profile =
        Profile(name, letters, swipe, KeyLayers.swap(symbols, GridCell(page, a.first, a.second), GridCell(page, b.first, b.second)))

    companion object {
        const val MINE = "Của tôi"

        /**
         * Ghi [edited] (bản sửa của profile đang dùng [active]) vào danh sách [user]: profile người dùng thì thay
         * tại chỗ; profile có sẵn không sửa được nên thành profile mới tên "Của tôi" (hoặc "Của tôi 2"…).
         * @return danh sách profile người dùng mới và profile đang dùng mới.
         */
        fun saveEdit(edited: Profile, active: Profile, user: List<Profile>): Pair<List<Profile>, Profile> {
            if (!active.isBuiltIn) return user.map { if (it.name == active.name) edited.named(active.name) else it } to edited.named(active.name)
            val p = edited.named(uniqueName(MINE, (BUILT_IN + user).map { it.name }))
            return user + p to p
        }

        val DEFAULT = Profile("Mặc định", LayoutModel.DEFAULT, KeyLayers.DEFAULT_SWIPE, SymbolPages.DEFAULT)

        /** Profile có sẵn, hiện trước profile người dùng tạo. */
        val BUILT_IN: List<Profile> = listOf(
            DEFAULT,
            builtIn("Cân bằng hai ngón", LayoutModel.BALANCED),
            builtIn("Công thái học", LayoutModel.ERGONOMIC),
        )

        /** Lớp 123 như mặc định; vuốt xuống theo phím trên [letters] (số dưới Q…P). */
        private fun builtIn(name: String, letters: LayoutModel) =
            Profile(name, letters, CellGrid.fromCodes(letters) { KeyLayers.DEFAULT_SWIPE_BY_CODE[it] }, SymbolPages.DEFAULT)

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
