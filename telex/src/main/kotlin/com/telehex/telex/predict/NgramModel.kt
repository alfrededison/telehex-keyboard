package com.telehex.telex.predict

/** Mô hình dự đoán âm tiết tiếp theo. Ngữ cảnh là các âm tiết trước đó (chữ thường), gần nhất ở cuối. */
interface NgramModel {
    /** Các ứng viên (chữ thường) kèm điểm (xác suất ước lượng, càng cao càng tốt). */
    fun predict(context: List<String>, max: Int): List<Scored>

    /** Xác suất của [next] sau [context], để trộn mô hình. 0 nếu không biết. */
    fun probability(context: List<String>, next: String): Double

    /** Số liệu để tính trọng số trộn (số âm tiết đã học). */
    val size: Long

    data class Scored(val text: String, val score: Double)
}
