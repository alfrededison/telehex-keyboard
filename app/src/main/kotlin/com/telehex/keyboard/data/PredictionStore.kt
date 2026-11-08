package com.telehex.keyboard.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.telehex.telex.predict.BinaryNgramModel
import com.telehex.telex.predict.EnglishPredictor
import com.telehex.telex.predict.NgramModel
import com.telehex.telex.predict.PersonalModel
import com.telehex.telex.predict.Predictor
import com.telehex.telex.predict.Phrases
import com.telehex.telex.predict.VietnameseVocabulary
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Lưu/nạp mô hình cá nhân (file text trong bộ nhớ app) và mô hình gốc (gói ngôn ngữ `.ngram`
 * do người dùng nạp bằng tay, hoặc `assets/vi.ngram` nếu đóng gói sẵn). Mọi thứ nằm trên máy.
 *
 * Nạp và ghi chạy trên một luồng nền để bàn phím không khựng: mô hình cá nhân lớn mất hàng trăm ms để
 * đọc, hàng chục ms để ghi. Trong lúc đang nạp, [predictor] chạy với mô hình rỗng và các âm tiết học
 * được giữ lại, ghép vào khi nạp xong. Mọi hàm public gọi từ luồng chính.
 */
class PredictionStore(private val context: Context) {
    enum class Language(val code: String) { VI("vi"), EN("en") }
    private val personalFile = File(context.filesDir, "personal.tsv")
    private val packFile = File(context.filesDir, "vi.ngram")
    private val englishPackFile = File(context.filesDir, "en.ngram")
    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "telehex-predictions").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    val predictor: Predictor = Predictor()
    var englishPredictor = EnglishPredictor(emptyList())
        private set

    private var loaded = false
    @Volatile private var discarded = false

    /**
     * Có file mô hình cá nhân nhưng đọc lỗi (lỗi đĩa, thiếu bộ nhớ…). Khi đó không ghi gì trong phiên
     * này: ghi mô hình rỗng sẽ đè mất toàn bộ dữ liệu đã học. File cũ giữ nguyên để lần sau đọc lại.
     */
    private var readFailed = false
    private val pending = ArrayList<(PersonalModel) -> Unit>()

    /** Nạp gói ngôn ngữ và mô hình cá nhân trên luồng nền, gắn vào [predictor] khi xong. */
    fun loadAsync(onLoaded: () -> Unit = {}) {
        io.execute {
            val englishWords = context.assets.open("en-frequency.txt").bufferedReader().use { it.readLines() }
            val english = EnglishPredictor(englishWords, loadBase(englishPackFile, "en.ngram"))
            val vietnameseWords = context.assets.open("vi-frequency.txt").bufferedReader().use { it.readLines() }
            val vietnamese = VietnameseVocabulary(vietnameseWords)
            val phrases = context.assets.open("vi-phrases.tsv").bufferedReader().use { Phrases.parse(it.readText()) }
            val base = loadBase()
            // "Chưa có file" khác "có file mà đọc lỗi": chỉ trường hợp đầu mới được bắt đầu từ mô hình rỗng
            // rồi ghi ra. Nội dung hỏng không làm đọc lỗi (read bỏ qua dòng hỏng); lỗi ở đây thường tạm thời.
            var failed = false
            val personal = if (!personalFile.exists()) PersonalModel() else try {
                personalFile.bufferedReader().use { PersonalModel.read(it) }
            } catch (t: Throwable) {
                failed = true
                PersonalModel()
            }
            main.post {
                if (discarded) return@post
                englishPredictor = english
                readFailed = failed
                predictor.base = base
                predictor.vocabulary = vietnamese
                predictor.phrases = phrases
                predictor.personal = personal
                val learnedWhileLoading = pending.isNotEmpty()
                for (op in pending) op(personal)
                pending.clear()
                loaded = true
                // Những gì học/quên trong lúc nạp chưa được ghi lần nào
                if (learnedWhileLoading) saveAsync()
                if (!io.isShutdown) onLoaded()
            }
        }
    }

    /** Học một âm tiết; nếu đang nạp thì ghép vào sau. */
    fun learn(context: List<String>, syllable: String) = whenLoaded { it.learn(context, syllable) }

    fun forget(syllable: String) = whenLoaded { it.forget(syllable) }

    private fun whenLoaded(op: (PersonalModel) -> Unit) {
        if (loaded) op(predictor.personal) else { op(predictor.personal); pending += op }
    }

    /**
     * Ghi mô hình cá nhân ra file trên luồng nền. Trên luồng chính chỉ giảm kích thước nếu cần và chụp
     * một bản sao bằng cách chép thẳng các mảng (cỡ vài chục ms ở mức trần [MAX_ENTRIES]); phần ghi chậm
     * làm trên bản sao trong khi bàn phím vẫn học tiếp.
     *
     * @return true nếu đã lên lịch ghi; false khi đang nạp (sẽ tự ghi khi nạp xong), store đã đóng, hoặc
     *   file cũ đọc lỗi (không ghi đè trong phiên này, xem [readFailed]).
     */
    fun saveAsync(): Boolean {
        if (!loaded || discarded || readFailed || io.isShutdown) return false
        val personal = predictor.personal
        personal.decayIfLargerThan(MAX_ENTRIES)
        val snapshot = personal.copy()
        io.execute { if (!discarded) write(snapshot) }
        return true
    }

    private fun write(snapshot: PersonalModel) {
        runCatching {
            val tmp = File(context.filesDir, "personal.tsv.tmp")
            tmp.bufferedWriter(Charsets.UTF_8, 64 * 1024).use { snapshot.writeTo(it) }
            // Store bị bỏ trong lúc đang ghi (vừa khôi phục hoặc xoá dữ liệu): đừng đè lên file mới.
            if (discarded) tmp.delete() else tmp.renameTo(personalFile)
        }
    }

    /**
     * Khi bàn phím bị huỷ: ghi lần cuối nếu [save], rồi chờ tối đa 1 giây cho mọi lần ghi đang chờ hoặc
     * đang chạy. Không bỏ lần ghi nào (khác [discard]).
     */
    fun close(save: Boolean) {
        if (save) saveAsync()
        io.shutdown()
        runCatching { io.awaitTermination(1, TimeUnit.SECONDS) }
    }

    /**
     * Bỏ store này mà không ghi gì nữa (dữ liệu trên đĩa vừa được thay bằng bản khôi phục hoặc bị xoá):
     * một lần ghi đang chờ sẽ không đè lên file mới.
     */
    fun discard() {
        discarded = true
        io.shutdown()
    }

    fun clearPersonal() {
        predictor.personal.clear()
        personalFile.delete()
        bump()
    }

    private fun bump() { Prefs(context).modelsVersion = System.currentTimeMillis() }

    /** Mô hình gốc: file người dùng nạp (mmap) trước, rồi asset đóng gói. */
    private fun loadBase(file: File = packFile, assetName: String = "vi.ngram"): NgramModel? {
        if (file.exists()) {
            runCatching {
                FileInputStream(file).use { fis ->
                    val ch = fis.channel
                    return BinaryNgramModel(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
                }
            }
        }
        return runCatching {
            context.assets.openFd(assetName).use { afd ->
                FileInputStream(afd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
            }
        }.map { BinaryNgramModel(it) }.getOrNull()
    }

    /** Nạp gói ngôn ngữ từ file người dùng chọn; kiểm tra định dạng trước khi thay. */
    private fun fileFor(language: Language): File = if (language == Language.VI) packFile else englishPackFile

    fun importPack(uri: Uri, language: Language = Language.VI): Result<Unit> = runCatching {
        val destination = fileFor(language)
        val tmp = File(context.filesDir, "${destination.name}.tmp")
        try {
            context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            FileInputStream(tmp).use { fis ->
                val ch = fis.channel
                val model = BinaryNgramModel(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
                if (language == Language.EN) require(model.vocabulary.all { it.length <= 32 && EN_WORD.matches(it) }) {
                    "Gói EN chứa từ không hợp lệ; hãy dựng với --language en"
                }
            }
            check(tmp.renameTo(destination)) { "Không ghi được file" }
        } finally {
            tmp.delete()
        }
        bump()
    }

    fun removePack(language: Language = Language.VI) {
        fileFor(language).delete()
        bump()
    }

    /** Có gói ngôn ngữ để dùng không (file đã nạp hoặc asset đóng gói), không cần nạp nó. */
    fun hasBase(language: Language = Language.VI): Boolean = hasPack(language) ||
        runCatching { context.assets.openFd("${language.code}.ngram").close() }.isSuccess
    fun hasPack(language: Language = Language.VI): Boolean = fileFor(language).exists()

    companion object {
        private val EN_WORD = Regex("[a-z]+(?:'[a-z]+)*")
        /**
         * Trần số cặp + bộ ba trong mô hình cá nhân (khoảng 15 MB bộ nhớ, ứng với vài trăm nghìn âm
         * tiết đã gõ). Vượt trần thì mọi số đếm chia đôi: thói quen cũ nhạt dần, thói quen mới giữ ưu thế.
         */
        const val MAX_ENTRIES = 300_000
    }
}
