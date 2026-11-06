package com.telehex.telex.predict

/** Mô hình dự đoán âm tiết tiếp theo. Ngữ cảnh là các âm tiết trước đó (chữ thường), gần nhất ở cuối. */
interface NgramModel {
    /** Các ứng viên (chữ thường) kèm điểm (xác suất ước lượng, càng cao càng tốt). */
    fun predict(context: List<String>, max: Int): List<Scored>

    /** Xác suất của [next] sau [context], để trộn mô hình. 0 nếu không biết. */
    fun probability(context: List<String>, next: String): Double

    /**
     * Bộ chấm điểm cho một ngữ cảnh cố định: phần việc chung (tra ngữ cảnh) làm một lần, rồi chấm
     * nhiều ứng viên rẻ. Predictor gọi nó ở mỗi lần gõ phím.
     */
    fun scorer(context: List<String>): Scorer = object : Scorer {
        override fun probability(next: String): Double = this@NgramModel.probability(context, next)
        override fun candidates(max: Int): List<String> = predict(context, max).map { it.text }
    }

    interface Scorer {
        /** Xác suất của [next] (chữ thường) sau ngữ cảnh; 0 nếu không biết. */
        fun probability(next: String): Double
        /** Tối đa [max] ứng viên tốt nhất, tốt nhất trước. */
        fun candidates(max: Int): List<String>
    }

    data class Scored(val text: String, val score: Double)
}
