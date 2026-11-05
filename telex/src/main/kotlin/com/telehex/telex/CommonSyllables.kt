package com.telehex.telex

/**
 * Âm tiết thường gặp, xếp theo tần suất giảm dần (ước lượng), dùng để xếp hạng gợi ý sửa
 * khi chưa có mô hình n-gram. Rank nhỏ = phổ biến hơn.
 */
object CommonSyllables {
    private const val LIST = """
và của có là không được trong một người cho những với các này để đã ở cũng khi đến từ về như nhiều
tôi anh em chị ông bà bạn mình họ ai gì đó đây kia nào sao đâu
thì mà nhưng nếu vì nên hay hoặc rồi còn đang sẽ vừa mới đều chỉ rất quá lại nữa cả hơn nhất
làm đi ra vào lên xuống về đến thấy biết nói nghĩ muốn cần phải nên thích yêu ghét sợ chờ tìm gặp
hai ba bốn năm sáu bảy tám chín mười trăm nghìn triệu tỷ
ngày tháng năm giờ phút giây hôm nay mai qua sáng trưa chiều tối đêm tuần
nhà cửa phòng đường phố thành thị xã huyện tỉnh nước việt nam hà nội sài gòn
công việc học sinh viên giáo thầy cô trường lớp bài thi điểm kết quả tiếng việt nói chuyện
được rằng bởi theo cùng sau trước ngoài trong giữa bên cạnh gần xa
ăn uống ngủ chơi xem nghe đọc viết gọi hỏi trả lời mua bán tiền đồng giá
tốt xấu đẹp lớn nhỏ cao thấp dài ngắn nhanh chậm nóng lạnh mới cũ đúng sai
cảm ơn xin chào vâng dạ ừ ờ ok được thôi nhé nha ạ à ơi hả hử
điện thoại máy tính mạng tin nhắn hình ảnh video nhạc phim sách báo
bố mẹ con cháu vợ chồng anh em cháu cô chú bác dì cậu mợ ông bà
trời đất nước lửa gió mưa nắng mây sông núi biển cây hoa lá quả
xe đạp máy ô tô tàu bay chuyến đường đi lại gần xa trước sau trên dưới trái phải giữa ngoài
lúc khi nào bao giờ lâu sớm muộn ngay luôn thường hay ít nhiều mỗi vài
cái con chiếc cuốn tờ bức căn ngôi quyển bộ đôi cặp
tình yêu thương nhớ buồn vui cười khóc giận lo mừng hạnh phúc
sức khỏe bệnh viện bác sĩ thuốc đau ốm khỏi
tên tuổi địa chỉ số điện thư email
chúng ta chúng tôi mọi người tất cả cùng nhau riêng chung
thế giới xã hội kinh tế chính trị văn hóa khoa học kỹ thuật
"""

    /** Âm tiết → thứ hạng (0 = phổ biến nhất). */
    val RANK: Map<String, Int> by lazy {
        val out = HashMap<String, Int>(1024)
        var i = 0
        for (w in LIST.split(Regex("\\s+"))) {
            if (w.isEmpty() || w in out) continue
            out[w] = i++
        }
        out
    }

    fun rankOf(syllable: String): Int = RANK[syllable.lowercase()] ?: Int.MAX_VALUE
}
