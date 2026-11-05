# Changelog

## 0.1.0 — unreleased

Bản đầu tiên.

### Gõ

- Lưới lục giác đều 7 cột giữ tinh thần QWERTY, phím cách vát góc chiếm 3 ô giữa hàng cuối.
- Telex đầy đủ: dấu thanh (s f r x j, z bỏ dấu), â ê ô ă ơ ư đ, đặt dấu kiểu cũ hoặc mới, tuỳ chọn "L cũng là dấu huyền".
- Gõ lại cùng phím để huỷ (ass → as, aaa → aa); chuỗi không thể là tiếng Việt thì giữ đúng phím đã gõ (pornt, windows).
- Vuốt lên ra chữ hoa, vuốt xuống ra ký tự phụ (số, emoji, ký hiệu; tuỳ biến từng phím).
- Lớp ký tự 2 trang, số luôn đi theo chữ (Q→1 … P→0); dialpad tự mở ở ô số.

### Cử chỉ

- Phím cách: vuốt trái xoá ký tự hoặc xoá theo từ có bôi đen xem trước; giữ rồi kéo để di con trỏ; chạm đúp rồi kéo để chọn; vuốt phải để dán; chép và cắt khi có vùng chọn.
- Hai ngón vuốt trái/phải để undo/redo, hai ngón giữ để chọn hết. Mọi ngưỡng chỉnh được trong Cài đặt.

### Gợi ý (không tự sửa)

- 6 biến thể dấu của âm tiết đang gõ, gợi ý sửa từ phím lân cận và lỗi Telex, sửa từ đã gõ khi đặt con trỏ vào từ.
- Gợi ý âm tiết tiếp theo học từ chính người dùng, chế độ ẩn danh, nạp gói ngôn ngữ n-gram dựng bằng `tools/build_ngram.py`.
- Gợi ý emoji theo từ khoá và tìm emoji bằng tiếng Việt (chú thích CLDR), tông da mặc định.

### Tiện ích

- Bảng emoji kiểu Gboard, clipboard history (ghim, tự hết hạn, bỏ qua nội dung nhạy cảm), bảng sửa văn bản, gõ tắt, nút giọng nói qua IME khác.
- Sắp xếp lại phím bằng kéo-thả với preset, chế độ một tay, giao diện sáng/tối, sao lưu/khôi phục ra file zip.
- Không xin quyền INTERNET: mọi xử lý nằm trên máy.
