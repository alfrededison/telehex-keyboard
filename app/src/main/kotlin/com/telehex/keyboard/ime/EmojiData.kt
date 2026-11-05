package com.telehex.keyboard.ime

/** Danh mục emoji cho bảng emoji. Tab "Gần đây" lấy từ Prefs. */
object EmojiData {
    class Category(val id: String, val name: String, val icon: String, val emoji: List<String>)

    private fun l(s: String) = s.split(' ').filter { it.isNotEmpty() }

    val CATEGORIES: List<Category> = listOf(
        Category(
            "face", "Mặt cười", "😀",
            l("😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😋 😛 😜 🤪 😝 🤗 🤭 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🥵 🥶 😵 🤯 🥳 😎 🤓 😕 😟 🙁 😮 😲 😳 🥺 😢 😭 😱 😖 😣 😞 😓 😩 😫 😤 😡 😠 🤬 🤡 👻 💀 👽 🤖 💩"),
        ),
        Category(
            "hand", "Cử chỉ", "👍",
            l("👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 👏 🙌 👐 🤲 🙏 🤝 💪 ✍️ 💅 🤳 👀 👁️ 👅 👄 🧠"),
        ),
        Category(
            "heart", "Trái tim", "❤️",
            l("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 😻 💋 🌹 💐 🌸 🌺 🌻 🌷"),
        ),
        Category(
            "animal", "Động vật & đồ ăn", "🐶",
            l("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐢 🐍 🐙 🐠 🐟 🐬 🐳 🦈 🐊 🐘 🦒 🐓 🍎 🍊 🍋 🍉 🍇 🍓 🍒 🥭 🍍 🥥 🥑 🍅 🌽 🥕 🍞 🥐 🧀 🍗 🍔 🍟 🍕 🌮 🍜 🍚 🍣 🍦 🍰 🎂 🍫 🍬 ☕ 🍵 🧋 🍺 🍻 🥂 🍷"),
        ),
        Category(
            "obj", "Đồ vật", "🎉",
            l("🎉 🎊 🎁 🎂 🔥 ✨ ⭐ 🌟 💯 ✅ ❌ ⚡ ☀️ 🌙 🌈 ☔ ⛅ ❄️ ⚽ 🏀 🏸 🎾 🎵 🎶 🎤 🎧 📷 📱 💻 ⌚ 🚗 🏍️ 🚲 ✈️ 🚀 🏠 🏫 🏥 💡 📌 📎 ✏️ 📚 📅 ⏰ 💰 💵 🛒 🎈 🎀 🧧 🏆 🥇 🎯 🔑 🔒 🔔 💊 🩺 🧸"),
        ),
    )
}
