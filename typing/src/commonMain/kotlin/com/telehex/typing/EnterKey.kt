package com.telehex.typing

/** Nhãn phím ↵ theo hành động của ô nhập, dùng chung cho hai app (Android: IME_ACTION_*, iOS: UIReturnKeyType). */
object EnterKey {
    enum class Action { NONE, SEARCH, SEND, GO, NEXT, DONE }

    fun label(action: Action): String = when (action) {
        Action.SEARCH -> "Tìm"
        Action.SEND -> "Gửi"
        Action.GO -> "Đi"
        Action.NEXT -> "Tiếp"
        Action.DONE -> "Xong"
        Action.NONE -> "↵"
    }
}
