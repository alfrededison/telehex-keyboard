package com.telehex.keyboard.data

import android.content.Context
import android.net.Uri
import com.telehex.telex.predict.BinaryNgramModel
import com.telehex.telex.predict.NgramModel
import com.telehex.telex.predict.PersonalModel
import com.telehex.telex.predict.Predictor
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel

/**
 * Lưu/nạp mô hình cá nhân (file text trong bộ nhớ app) và mô hình gốc (gói ngôn ngữ `.ngram`
 * do người dùng nạp bằng tay, hoặc `assets/vi.ngram` nếu đóng gói sẵn). Mọi thứ nằm trên máy.
 */
class PredictionStore(private val context: Context) {
    private val personalFile = File(context.filesDir, "personal.tsv")
    private val packFile = File(context.filesDir, "vi.ngram")

    val predictor: Predictor = Predictor(personal = loadPersonal())

    init { predictor.base = loadBase() }

    private fun loadPersonal(): PersonalModel =
        runCatching { if (personalFile.exists()) PersonalModel.deserialize(personalFile.readText()) else null }.getOrNull() ?: PersonalModel()

    fun savePersonal() {
        runCatching {
            val tmp = File(context.filesDir, "personal.tsv.tmp")
            tmp.writeText(predictor.personal.serialize())
            tmp.renameTo(personalFile)
        }
    }

    fun clearPersonal() {
        predictor.personal.clear()
        personalFile.delete()
        bump()
    }

    private fun bump() { Prefs(context).modelsVersion = System.currentTimeMillis() }

    /** Mô hình gốc: file người dùng nạp (mmap) trước, rồi asset đóng gói. */
    private fun loadBase(): NgramModel? {
        if (packFile.exists()) {
            runCatching {
                FileInputStream(packFile).use { fis ->
                    val ch = fis.channel
                    return BinaryNgramModel(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
                }
            }
        }
        return runCatching {
            context.assets.openFd("vi.ngram").use { afd ->
                FileInputStream(afd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
            }
        }.map { BinaryNgramModel(it) }.getOrNull()
    }

    /** Nạp gói ngôn ngữ từ file người dùng chọn; kiểm tra định dạng trước khi thay. */
    fun importPack(uri: Uri): Result<Unit> = runCatching {
        val tmp = File(context.filesDir, "vi.ngram.tmp")
        context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        FileInputStream(tmp).use { fis ->
            val ch = fis.channel
            BinaryNgramModel(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())) // ném lỗi nếu sai định dạng
        }
        check(tmp.renameTo(packFile)) { "Không ghi được file" }
        predictor.base = loadBase()
        bump()
    }

    fun removePack() {
        packFile.delete()
        predictor.base = loadBase()
        bump()
    }

    fun hasBase(): Boolean = predictor.base != null
    fun hasPack(): Boolean = packFile.exists()
    fun personalFileForBackup(): File = personalFile
}
