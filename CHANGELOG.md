# Changelog

## 0.6.1 — unreleased

- Tách nhóm xoá dữ liệu đã học bằng đường phân cách, ghi rõ chỉ xoá dữ liệu cá nhân tiếng Việt và giữ các gói ngôn ngữ đã nạp.
- Thêm asset từ vựng/tần suất tiếng Việt: bổ sung hoàn thành âm tiết khi chưa nạp gói VI và vốn từ cho gõ vuốt; chuyển bảng cụm từ cũ sang asset, giữ thứ tự ưu tiên. Gói vi.ngram vẫn tự dựng và nạp riêng.
- Gợi ý tiếng Anh offline: từ điển 50.000 từ theo độ phổ biến, kết hợp mô hình n-gram 1–3 từ để xếp hạng hoàn thành theo ngữ cảnh và dự đoán từ tiếp theo. Từ điển có sẵn trong APK; gói en.ngram tự dựng/nạp và xoá riêng với VI. Giữ hoa/thường, hỗ trợ … và chạm để chèn; không tự sửa hay học chung với tiếng Việt. Tắt ở ô mật khẩu/email/URL/mã và khi tắt Gợi ý.

## 0.6.0 — 2026-11-08

- Hướng dẫn trong Cài đặt › Cử chỉ chia thành các gạch đầu dòng, nhóm theo phím chữ (vuốt lên đảo hoa/thường, vuốt xuống nhập ký tự phụ), phím cách, vùng chọn và hai ngón để dễ đọc.
- Vuốt xuống phím ⚙ để mở bảng điều hướng và clipboard history, có nhãn phụ 📋. Chạm hoặc giữ rồi nhả ⚙ đều mở Cài đặt; bỏ chức năng riêng của thao tác giữ phím này.
- Gợi ý sửa lỗi xét hai âm tiết trước từ bằng mô hình cá nhân/gói ngôn ngữ, áp dụng khi đang gõ và khi đặt con trỏ vào từ. Giới hạn ảnh hưởng ngữ cảnh để giữ ưu tiên lỗi gõ gần; thiếu dữ liệu thì giữ xếp hạng cũ.
- Giới tính emoji mặc định Nguyên bản / Nữ / Nam, kết hợp tông da, dùng trong bảng emoji, tìm và gợi ý. Giữ emoji đã chọn rõ giới tính; thiếu biến thể hoặc font không hỗ trợ thì giữ nguyên.
- Nút … mở thêm ứng viên sửa lỗi, hoàn thành, dự đoán, gõ vuốt và tìm emoji trong lưới cuộn, giữ nguyên chiều cao bàn phím. Chọn từ hoặc quay lại để đóng; giữ gợi ý đã học vẫn xoá được.

## 0.5.1 — 2026-11-08

- Gõ tắt dùng bảng hai cột Viết tắt / Nội dung, tự lưu và bỏ khoảng trắng thừa ở hai đầu. Phím Tiếp chuyển ô và thêm dòng; có nút xoá từng dòng, không cần gõ dấu `=`.
- Cài đặt mặc định mở tất cả section và ghi nhớ trạng thái đóng/mở của từng section khi mở lại ứng dụng.
- Section Cài đặt có icon nét, mô tả ngắn và khối bo góc với khoảng cách thoáng hơn, theo giao diện sáng/tối.

## 0.5.0 — 2026-11-07

- Slide to type: lướt qua ít nhất 3 phím chữ khác nhau (chữ không dấu hoặc Telex), nhả tay để hiện gợi ý rồi chạm để chèn từ. Cùng ngưỡng cho mọi hướng; qua 1–2 phím vẫn là cử chỉ thường. Vẽ nét vuốt, theo bố cục/profile và chế độ một tay; nhận diện hoàn toàn offline từ âm tiết phổ biến, từ đã học và gói ngôn ngữ nếu có. Tắt được trong Cài đặt › Gợi ý; giữ-kéo đổi chỗ phím vẫn dùng như trước.
- Profile có sẵn "Công thái học" (L V B ⚙ Q P 123 / M W R X Y U E / F N S J A I K / Z D H C T O G): nhanh hơn "Cân bằng hai ngón" ~5% theo mô hình Fitts hai ngón, tối ưu trên văn bản tiểu thuyết (~250 nghìn âm tiết), gõ luân phiên ~71%. Sắp xếp lại toàn bộ chữ nên cần học lại; lớp vuốt xuống và 123 giữ như mặc định.

## 0.4.0 — 2026-11-07

- Đổi chỗ phím ngay trên bàn phím: giữ một phím ký tự ~0,8 giây đến khi nó nổi lên, kéo thả lên phím khác. Áp cho lớp chữ hoặc trang 123 đang hiện. Phím chức năng không tự nhấc nhưng đổi chỗ được khi phím ký tự thả lên nó; phím cách và ↵ cố định; ký tự vuốt xuống đứng yên theo ô như ở màn chỉnh sửa. Tự lưu vào profile đang dùng (profile có sẵn thì thành profile mới "Của tôi") và giữ độ lệch chạm đã học. Nhả tại chỗ không kéo đi thì vẫn gõ ký tự đó. Tắt được trong Cài đặt › Bàn phím.
- Chế độ EN (tắt Telex): vuốt xuống phím cách để bật/tắt, phím cách ghi "cách · EN" khi đang bật. Lựa chọn giữ cho các ô của cùng app. Ô email và các app chọn trong Cài đặt › Telex › "Chọn app tự bật EN…" mở sẵn ở EN. Ở EN vẫn tự viết hoa đầu câu và lưu clipboard history như bình thường.

## 0.3.1 — 2026-11-07

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
