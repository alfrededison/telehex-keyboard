package com.telehex.telex

import com.telehex.telex.predict.*
import com.telehex.platform.ByteArrayModelBuffer
import com.telehex.platform.ModelBuffer
import com.telehex.platform.Normalizer
import com.telehex.telex.predict.EnglishPredictor
import com.telehex.telex.predict.Phrases
import com.telehex.telex.predict.PredictionContext
import com.telehex.telex.predict.Predictor
import com.telehex.telex.predict.VietnameseVocabulary

/** String-based Swift boundary: invalid input never throws across Objective-C interop. */
class KeyboardEngine {
    private val engine = TelexEngine()
    fun press(letter: String): String {
        val key = letter.singleOrNull() ?: return engine.text()
        if (key.lowercaseChar() in 'a'..'z') engine.press(key.lowercaseChar(), key.isUpperCase())
        return engine.text()
    }
    fun backspace(): String { engine.backspace(); return engine.text() }
    fun text(): String = engine.text()
    fun rawKeys(): String = engine.rawText()
    /** Chốt từ như UniKey: âm tiết có dấu mà sai chính tả thì trả về phím đã gõ ("ủe" → user), ngược lại null. */
    fun restoredKeys(): String? = engine.restoredKeys()
    fun rawCaps(): List<Boolean> = engine.rawCaps()
    fun candidates(): List<String> = engine.candidates().map { it.text }
    /** Âm tiết có nguyên âm mà chưa hợp lệ: thanh gợi ý chỉ hiện phương án sửa. */
    fun invalidSyllable(): Boolean = !engine.isEmpty && engine.hasVowel() && !engine.isValid()
    fun reset() = engine.reset()
    /** Nạp lại từ ngay trước con trỏ để gõ tiếp dấu ("hiêu" + s → "hiếu"); false thì engine rỗng. */
    fun resume(word: String): Boolean = engine.resume(word)
    /** Vùng chạm động: gõ thêm `letter` có giữ âm tiết đang gõ là tiếng Việt (hoặc còn có thể thành tiếng Việt) không. */
    fun keepsSyllableValid(letter: String): Boolean {
        val key = letter.singleOrNull()?.lowercaseChar()?.takeIf { it in 'a'..'z' } ?: return true
        return !engine.copy().apply { press(key) }.isSuspicious()
    }
    /** `englishFallback`: chuỗi phím không thể là tiếng Việt thì giữ đúng các phím đã gõ (windows không thành ưindows). */
    fun configure(newToneStyle: Boolean, lAsGrave: Boolean, englishFallback: Boolean) {
        engine.reset()
        engine.config = TelexConfig(newToneStyle = newToneStyle, lAsGrave = lAsGrave, englishFallback = englishFallback)
    }
}

/** Construct once off the UI thread, then confine calls to the keyboard's main thread. */
class KeyboardVocabulary(englishText: String, vietnameseText: String, phrasesText: String) {
    private val englishWords = englishText.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
    private var english = EnglishPredictor(englishWords)
    private var config = TelexConfig()
    private var neighbors: Map<Char, List<Char>> = emptyMap()
    private val vietnamese = Predictor(
        vocabulary = VietnameseVocabulary(vietnameseText.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()),
        phrases = Phrases.parse(phrasesText),
    )

    /**
     * [neighbors]: chữ → các chữ kề nhau trên bố cục đang dùng (sửa gõ sai), do app tính từ hình học lưới thật
     * (iOS: `LayoutBridge.neighbors`, Android: `LayoutModel.letterNeighbors`).
     */
    fun configure(newToneStyle: Boolean, lAsGrave: Boolean, neighbors: Map<String, String>) {
        config = TelexConfig(newToneStyle = newToneStyle, lAsGrave = lAsGrave)
        this.neighbors = neighbors.entries
            .mapNotNull { (k, v) -> k.singleOrNull()?.let { it to v.filter { c -> c in 'a'..'z' }.toList() } }
            .toMap()
    }

    fun corrections(raw: String, before: String): List<String> = Corrector(config, neighbors).suggest(
        raw, raw.map { it.isUpperCase() }, 24, vietnamese.correctionScorer(PredictionContext.before(before)))

    fun wordCorrections(word: String, before: String): List<String> {
        val keys = TelexDecomposer.keysOf(Normalizer.normalize(word, Normalizer.Form.NFC), config) ?: return emptyList()
        return Corrector(config, neighbors).suggest(keys.first, keys.second, 24, vietnamese.correctionScorer(PredictionContext.before(before))).filter { it != word }
    }

    fun nextWords(before: String, englishMode: Boolean): List<String> =
        if (englishMode) english.next(EnglishPredictor.contextBefore(before))
        else vietnamese.next(PredictionContext.before(before), max = 24)

    fun learn(before: String, word: String) {
        val normalized = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFC)
        if (normalized.length !in 1..16 || normalized.any { !it.isLetter() }) return
        val e = TelexDecomposer.keysOf(normalized, config) ?: return
        if (e.first.isEmpty()) return
        vietnamese.personal.learn(PredictionContext.before(before), word)
        vietnamese.personal.decayIfLargerThan(5000)
    }
    /** Từ đã học thường viết hoa (tên riêng): gợi ý viết hoa chữ đầu. */
    fun usuallyCapitalized(word: String): Boolean = vietnamese.personal.usuallyCapitalized(word)
    fun personalData(): String = vietnamese.personal.serialize()
    fun restorePersonal(text: String): Boolean = runCatching {
        require(text.length <= 2_000_000 && (text.isEmpty() || text.lineSequence().first() == "TELEHEX-PERSONAL-1"))
        vietnamese.personal = PersonalModel.deserialize(text)
        vietnamese.personal.decayIfLargerThan(5000)
        true
    }.getOrDefault(false)
    fun forget(word: String) = vietnamese.personal.forget(word)
    fun clearPersonal() = vietnamese.personal.clear()

    fun setLanguagePack(pack: KeyboardLanguagePack?, englishMode: Boolean) {
        if (englishMode) english = EnglishPredictor(englishWords, pack?.model)
        else vietnamese.base = pack?.model
    }

    /** Gõ vuốt: VI theo từ điển + n-gram tiếng Việt; EN (`englishMode`) theo từ điển EN, bỏ dấu nháy khi so đường vuốt. */
    fun glide(path: String, centers: String, before: String, englishMode: Boolean): List<String> {
        fun point(value: String): GlideDecoder.Point? {
            val pair = value.split(',')
            val x = pair.getOrNull(0)?.toFloatOrNull() ?: return null
            val y = pair.getOrNull(1)?.toFloatOrNull() ?: return null
            return if (x.isFinite() && y.isFinite()) GlideDecoder.Point(x, y) else null
        }
        val trace = path.split(';').take(256).mapNotNull(::point)
        val keys = centers.lines().mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon != 1 || line[0] !in 'a'..'z') null else point(line.substring(2))?.let { line[0] to it }
        }.toMap()
        if (englishMode) return GlideDecoder().suggest(trace, keys, english.glideWords(EnglishPredictor.contextBefore(before)), max = 24, english = true)
        return GlideDecoder().suggest(trace, keys, vietnamese.glideWords(PredictionContext.before(before)), config, 24)
    }

    /**
     * EN như Android: gợi ý cho từ quanh con trỏ (phần trước [head] và phần sau [tail]), kể cả khi con trỏ nằm trong một
     * từ đã gõ; chưa có chữ nào thì gợi ý từ kế tiếp. Null khi con trỏ không ở chỗ gợi ý được.
     */
    fun englishAtCursor(before: String, after: String, capsLock: Boolean, capitalize: Boolean): EnglishCursorSuggestions? {
        val query = EnglishPredictor.queryAtCursor(before, after) ?: return null
        val head = query.token.head
        val words = if (head.isEmpty()) english.next(query.context, capsLock = capsLock, capitalize = capitalize)
        else english.complete(head, capsLock = capsLock, context = query.context)
        return EnglishCursorSuggestions(head, query.token.tail, words)
    }

    fun suggestions(prefix: String, before: String, englishMode: Boolean): List<String> {
        if (englishMode) {
            val query = EnglishPredictor.queryAtCursor(before, "") ?: return emptyList()
            if (query.token.head != prefix) return emptyList()
            return english.complete(query.token.head, context = query.context)
        }
        val context = PredictionContext.before(if (before.endsWith(prefix)) before.dropLast(prefix.length) else before)
        return vietnamese.next(context, prefix, 24).map { word ->
            if (prefix.firstOrNull()?.isUpperCase() == true) word.replaceFirstChar { it.uppercaseChar() } else word
        }
    }
}

/** Gợi ý EN cho từ quanh con trỏ: chọn thì thay [head] + [tail]. */
class EnglishCursorSuggestions(val head: String, val tail: String, val words: List<String>)

/** A validated pack; malformed user files return an error instead of escaping into Swift. */
class KeyboardLanguagePack private constructor(internal val model: BinaryNgramModel?, val error: String) {
    val valid: Boolean get() = model != null
    companion object {
        /** Largest accepted pack: the default 3/4 build of the Vietnamese pack is about 29 MB. */
        const val MAX_BYTES = 32 * 1024 * 1024
        fun load(bytes: ByteArray, englishMode: Boolean): KeyboardLanguagePack = load(ByteArrayModelBuffer(bytes), englishMode)
        /** Reads the pack through [buffer] without copying it, so Apple can pass a memory-mapped file. */
        fun load(buffer: ModelBuffer, englishMode: Boolean): KeyboardLanguagePack = try {
            require(buffer.limit() in 17..MAX_BYTES) { "Gói phải nhỏ hơn ${MAX_BYTES / 1024 / 1024} MiB" }
            require(buffer.getInt(5) in 1..100_000) { "Số từ vựng vượt giới hạn" }
            val model = BinaryNgramModel(buffer)
            val englishWord = Regex("[a-zA-Z]+(?:['’][a-zA-Z]+)*")
            if (englishMode) require(model.vocabulary.all { it.length <= 32 && englishWord.matches(it) }) { "Gói EN chứa từ không hợp lệ" }
            KeyboardLanguagePack(model, "")
        } catch (error: Exception) {
            KeyboardLanguagePack(null, error.message ?: "Gói ngôn ngữ không hợp lệ")
        }
    }
}
