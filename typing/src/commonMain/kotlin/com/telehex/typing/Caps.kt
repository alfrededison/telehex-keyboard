package com.telehex.typing

/**
 * Tự viết hoa theo kiểu ô nhập, chép từ `TextUtils.getCapsMode` của Android để hai app viết hoa ở cùng chỗ. Android vẫn
 * hỏi ô nhập (`getCursorCapsMode`); iOS dùng hàm này.
 */
object AutoCap {
    enum class Mode { NONE, CHARACTERS, WORDS, SENTENCES }

    /**
     * Chữ gõ ngay sau [textBefore] có tự viết hoa không. Đầu câu là đầu đoạn hoặc sau `.` `?` `!` rồi **khoảng trắng**
     * ("google.com" và "U.S." không viết hoa); bỏ qua ngoặc/nháy mở trước con trỏ và ngoặc/nháy đóng sau dấu kết câu.
     */
    fun capitalizes(textBefore: String, mode: Mode): Boolean {
        if (mode == Mode.NONE) return false
        if (mode == Mode.CHARACTERS) return true
        val cs = textBefore
        var i = cs.length
        // Lùi qua ngoặc/nháy mở
        while (i > 0) {
            val c = cs[i - 1]
            if (c != '"' && c != '\'' && c.category != CharCategory.START_PUNCTUATION) break
            i--
        }
        // Đầu đoạn (có thể có khoảng trắng)
        var j = i
        while (j > 0 && (cs[j - 1] == ' ' || cs[j - 1] == '\t')) j--
        if (j == 0 || cs[j - 1] == '\n') return true
        if (mode == Mode.WORDS) return i != j
        // Không phải đầu đoạn thì phải có khoảng trắng
        if (i == j) return false
        // Lùi qua ngoặc/nháy đóng
        while (j > 0) {
            val c = cs[j - 1]
            if (c != '"' && c != '\'' && c.category != CharCategory.END_PUNCTUATION) break
            j--
        }
        if (j == 0) return false
        val c = cs[j - 1]
        if (c != '.' && c != '?' && c != '!') return false
        // Chữ viết tắt có dấu chấm bên trong ("U.S. "): không viết hoa
        if (c == '.') {
            var k = j - 2
            while (k >= 0) {
                val d = cs[k]
                if (d == '.') return false
                if (!d.isLetter()) break
                k--
            }
        }
        return true
    }
}

/**
 * Ghi đè tự viết hoa của bàn phím (phím ⇧, vuốt lên phím cách), dùng chung cho hai app: [Override.LOCK] đến khi tắt;
 * [Override.LOWER] / [Override.UPPER] chỉ cho chữ kế tiếp.
 */
class CapsState {
    enum class Override { LOCK, LOWER, UPPER }

    var override: Override? = null
        private set
    /** Lần chạm ⇧ trước (ms), để chạm đúp khoá chữ hoa; 0 = không có. */
    private var lastShift = 0L

    val capsLock: Boolean get() = override == Override.LOCK
    val oneShotUpper: Boolean get() = override == Override.UPPER

    /** Chữ sắp gõ là hoa không; [auto]: ô nhập tự viết hoa ở vị trí con trỏ. */
    fun upperNext(auto: Boolean): Boolean = when (override) {
        Override.LOCK, Override.UPPER -> true
        Override.LOWER -> false
        null -> auto
    }

    /**
     * Phím ⇧: chữ kế tiếp đang thường thì viết hoa, đang hoa (cả tự viết hoa đầu câu) thì viết thường; chạm đúp trong
     * [doubleTapMs] thì khoá chữ hoa; chạm khi đang khoá thì tắt khoá.
     */
    fun pressShift(nowMs: Long, doubleTapMs: Long, auto: Boolean) {
        val quick = lastShift != 0L && nowMs - lastShift < doubleTapMs
        lastShift = nowMs
        override = when {
            override == Override.LOCK -> { lastShift = 0L; if (auto) Override.LOWER else null }
            quick -> Override.LOCK
            upperNext(auto) -> Override.LOWER
            else -> Override.UPPER
        }
    }

    /**
     * Vuốt lên phím cách: đang khoá hoa thì tắt (tự viết hoa chạy lại); chữ sắp gõ đang tự viết hoa thì chữ kế tiếp ra
     * thường; còn lại thì khoá chữ hoa.
     * @return true khi vừa khoá chữ hoa, false khi vừa tắt khoá, null khi chỉ đổi chữ kế tiếp (để app báo cho người dùng).
     */
    fun toggleLock(auto: Boolean): Boolean? {
        override = when {
            override == Override.LOCK -> null
            override == null && auto -> Override.LOWER
            else -> Override.LOCK
        }
        return when (override) {
            Override.LOCK -> true
            null -> false
            else -> null
        }
    }

    /** Phím khác ⇧: lần chạm ⇧ sau không còn là chạm đúp. */
    fun otherKey() { lastShift = 0L }

    /** Đã gõ chữ, hay chạm phím không phải chữ/cách (dấu câu, ↵, ⌫): bỏ hoa/thường một lần, giữ khoá hoa. */
    fun clearOneShot() {
        if (override == Override.LOWER || override == Override.UPPER) override = null
    }

    /** Sang ô nhập khác. */
    fun reset() { override = null; lastShift = 0L }
}
