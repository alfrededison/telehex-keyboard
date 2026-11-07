# Changelog

## 0.3.1 — unreleased

- Vuốt lên phím cách (khi không có vùng chọn) đổi hoa/thường: đang thường thì khoá chữ hoa (phím cách ghi "⇪ HOA", vuốt lên lần nữa để tắt), đang tự viết hoa đầu câu thì chữ kế tiếp ra thường. Chữ gõ khi khoá hoa không bị học thành "hay viết hoa".
- Nút ⌫ cố định ở góc phải thanh gợi ý; giữ để xoá liên tục. Khi đang bôi đen, ⌫ xoá vùng chọn mà không cắt vào clipboard.

## 0.3.0 — 2026-11-06

- Preset thành **profile** gồm đủ ba lớp Chữ, Vuốt xuống, 123. Màn chỉnh sửa luôn sửa profile đang dùng: thanh trên cùng hiện tên profile (● khi có thay đổi chưa lưu, 🔒 ở profile có sẵn), chạm để chuyển profile và áp ngay cho bàn phím; Lưu ghi vào profile đang dùng; menu ⋮ có Tạo mới, Nhân bản, Đổi tên, Xoá, Bỏ thay đổi. Profile có sẵn ("Mặc định", "Cân bằng hai ngón") không sửa được, Lưu sẽ tạo profile mới. Bố cục đang dùng thành profile "Của tôi" nếu không trùng profile có sẵn; preset đã lưu ở bản cũ không được chuyển sang.
- Gộp "Sắp xếp phím" và "Ký tự vuốt xuống" thành nút "Chỉnh sửa phím"; Cài đặt › Bàn phím có thêm nút đổi nhanh profile. Lưu không còn đóng màn chỉnh sửa. Nút "Về mặc định (lớp này)" có ở cả lớp Chữ.

## 0.2.1 — 2026-11-06

- Bảng emoji, tìm emoji và gợi ý emoji chỉ hiện emoji mà font của máy vẽ được; emoji Unicode mới mà máy chưa hỗ trợ (hiện ô trống hoặc tách thành nhiều hình) được ẩn. Tông da không vẽ được thì chèn bản không tông.
- Ô gõ tắt trong Cài đặt không tự viết hoa đầu dòng (trước đây lưu thành "Ko = không", gõ "ko" không khớp).
- Thanh gợi ý chỉ hiện dấu mà âm tiết mang được: tận cùng p/t/c/ch chỉ có sắc, nặng ("cach" không còn ra "cảch"); gợi ý hoàn thành trùng biến thể dấu chỉ hiện một lần.
- Chạm biến thể dấu trên thanh gợi ý thì chốt luôn âm tiết kèm khoảng trắng (trước đây chỉ đổi dấu). Gõ dấu câu ngay sau khi chọn gợi ý thì xoá khoảng trắng tự thêm.
- Chọn ký tự vuốt xuống ngay trong chế độ Vuốt xuống của màn sắp xếp phím: chạm phím để chọn, kéo để đổi chỗ, bấm Lưu một lần cho cả hai; thoát khi còn thay đổi chưa lưu thì được hỏi Lưu / Bỏ thay đổi / Ở lại. Màn "Ký tự vuốt xuống" riêng bị bỏ; nút cùng tên trong Cài đặt mở thẳng chế độ này.
- Gợi ý âm tiết đầu câu chỉ viết hoa khi ô nhập có tự viết hoa (theo tuỳ chọn và cờ của ô), như phím chữ; trước đây luôn viết hoa, kể cả ô gõ tắt hay khi tắt "Tự viết hoa". Gợi ý hoàn thành âm tiết đang gõ theo hoa/thường của phần đã gõ như biến thể dấu ("Yêu" → "Yếu", trước đây ra "yếu").
- Màn Cài đặt chia thành các mục thu gọn (chạm tiêu đề để mở / đóng), mặc định đóng hết; "Bật bàn phím" tự mở khi TeleHex chưa là bàn phím hiện tại. Ô "Thử gõ ở đây" luôn ở trên cùng.

## 0.2.0 — 2026-11-06

- Tự viết hoa chữ đầu câu (tắt được trong Cài đặt). Nhãn phím hiện hoa/thường theo chữ sắp gõ; vuốt lên đảo hoa/thường như Gboard. Chữ hoa tự thêm không bị học thành "hay viết hoa"; gõ tắt ở đầu câu cũng viết hoa.
- ↵ chèn xuống dòng bằng văn bản (như Gboard) thay vì phát phím ENTER.
- Vuốt trái dài trên phím cách kéo tiếp được qua các dòng.
- `uaw` ra `ưa` (vuaw → vưa).
- Bỏ chữ dấu (á, ả…) dưới phím, chỉ dùng màu; ký tự vuốt xuống hiện dưới chữ cái.
- Bố cục mặc định mới: ⚙ và 123 lên hàng trên, ☺ `,` `.` xuống hàng phím cách, chữ dồn thành 4 hàng (Q E T F H I P / A S D G J K L / Z X C V B N M). Bố cục đã lưu không đổi.
- Trang ký tự 2 cũng đi theo chữ như trang 1, nên phím chức năng không nhảy chỗ khi chuyển trang ở bố cục tuỳ biến.
- Màn sắp xếp phím đặt lưới ở đáy màn hình, đúng chỗ bàn phím thật.
- Thông báo ngắn sau undo, redo, cắt, chép, dán, chọn hết.
- Có vùng chọn thì vuốt trái dài trên phím cách cũng cắt (trước đây bỏ vùng chọn và xoá theo từ).
- Lớp ký tự: phím chuyển trang 1/2 ra mép trái; phím ☺ thành phím 1234 mở dialpad (vẫn giữ được phím 123).
- Nút ở màn Cài đặt có khoảng cách, không còn dính nhau.
- Màn Cài đặt đổi sáng/tối theo tuỳ chọn Giao diện của bàn phím.
- Chọn màu nhấn cho phím nguyên âm (xanh dương, xanh lá, xanh ngọc, tím, hồng); "Không màu" bỏ cả màu nguyên âm lẫn phím dấu.
- Bảng sửa văn bản (giữ ⚙) dùng phím lục giác có icon như bàn phím chính; bốn mũi tên xếp hình thoi, nhóm chọn / cắt / chép / dán tô màu nhấn. Giữ mũi tên hoặc ⌫ để lặp. Clipboard history cập nhật ngay khi chép lúc bảng đang mở; tuỳ chọn trong Cài đặt ghi rõ giữ ⚙ để mở.
- Preset có sẵn "Cân bằng hai ngón" (W R Y ⚙ B K 123 / Q E G F J U P / D S H V I O L / Z X C A T N M): tải hai ngón cái ~50/50, gõ luân phiên ~62% → ~68% theo mô hình Fitts hai ngón trên tần suất phím Telex. Danh sách preset hiện luôn ở màn sắp xếp phím (✓ ở preset trùng lưới đang sửa, cập nhật cả khi kéo đổi phím; nút Xoá cho preset đã lưu; không lưu được preset trùng tên preset có sẵn), thay cho nút "Nạp preset…" và "Khôi phục mặc định". Nút Lưu mờ khi lưới chưa khác bố cục đang dùng. Hộp lưu preset mở bàn phím ngay vào ô tên (trước đây bàn phím không hiện, dễ bấm nhầm vào lưới phía sau).
- Màn sắp xếp phím có 3 chế độ: Chữ, Vuốt xuống, 123. Ký tự vuốt xuống và lớp 123 giờ gắn theo ô, không đi theo chữ nữa; mỗi lớp sắp xếp riêng. Lớp 123 kéo được sang trang kia bằng cách giữ ngón trên phím 1/2. Nút "Về mặc định (lớp này)" cho lớp vuốt xuống và 123.
- Số 1…0 (lớp 123 và vuốt xuống) mặc định ở ô của Q W R Y F H U O P L. Dữ liệu cũ (vuốt xuống tuỳ biến theo phím, bản sao lưu cũ) được chuyển sang theo ô của bố cục đang dùng, số dời về chỗ mới; ký tự bạn đã thay vào chỗ một số được giữ nguyên.
- Thoát được tìm emoji mà không cần chọn emoji: chạm ô tìm (✕), ⌫ khi ô tìm trống, ↵ hoặc ☺ đều về bảng emoji; trước đây ⌫ xoá chữ trong ô nhập và ↵ gửi tin.

## 0.1.0 — 2026-11-05

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
