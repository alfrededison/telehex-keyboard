# Kế hoạch TeleHex

Tài liệu này mô tả trạng thái hiện tại và công việc còn lại. README là hướng dẫn sử dụng;
CHANGELOG ghi thay đổi theo phiên bản. Prototype trong `prototype/` chỉ là tham chiếu lịch sử.

## 1. Nguyên tắc

- Bàn phím Telex tiếng Việt trên lưới lục giác đều 7 cột; hỗ trợ profile tuỳ biến.
- Chạy hoàn toàn offline, không xin quyền INTERNET, dữ liệu gõ ở bộ nhớ riêng của app.
- Không autocorrect: chỉ thay chữ khi người dùng chạm một gợi ý.
- Phím cách và Enter cố định; từng lớp Chữ / Vuốt xuống / 123 sắp xếp riêng theo ô.
- Giữ dependency tối thiểu; không chép mã có giấy phép không tương thích với MIT.

## 2. Kiến trúc và hình học

- `telex/`: Kotlin thuần, engine Telex, kiểm tra âm tiết, sửa lỗi và dự đoán; unit test độc lập Android.
- `app/ime/`: InputMethodService và custom Views vẽ Canvas; settings/editor dùng Android Views.
- `app/data/`: SharedPreferences cho tuỳ chọn/profile; file riêng cho mô hình và clipboard.
- `tools/`: dựng/đo n-gram, dựng dữ liệu emoji, version, ký APK và release.

Với chiều rộng khả dụng `Wu` (trừ padding 8dp mỗi bên):

- `W = Wu / 7.5`, `H = W · 2/√3`, bước hàng `P = 0.75 · H`.
- Độ lệch hàng `[0.5, 0, 0.5, 0, 0.5] · W`; chiều cao lưới `4P + H`.
- Phím cách ghép 3 ô giữa hàng cuối; thu hình vẽ 1.5dp mỗi cạnh để có khe.
- Hit-test chọn tâm gần nhất, có tuỳ chọn học độ lệch chạm và vùng chạm động.
- Một tay thu lưới về 80%, dồn trái/phải; lục giác luôn đều.

Mặc định dùng composing text. App trong danh sách dự phòng dùng
`deleteSurroundingText` + `commitText`. Theo dõi thay đổi selection để giữ chữ và kết thúc
âm tiết khi con trỏ di chuyển. Ô mật khẩu tắt Telex, gợi ý và học.

## 3. Đã triển khai

### Gõ và bố cục

- [x] Telex đầy đủ, kiểu dấu cũ/mới, L tuỳ chọn làm dấu huyền, bộ test vàng.
- [x] Gõ lại phím biến đổi để huỷ: `ass → as`, `aaa → aa`; kết quả huỷ giữ dạng literal.
- [x] Chuỗi không thể là tiếng Việt giữ đúng phím đã gõ: `windows`, `pornt`.
- [x] Lưới chữ, lớp ký tự 2 trang, dialpad tự mở theo inputType.
- [x] Vuốt lên đảo hoa/thường, vuốt xuống ký tự phụ; tự viết hoa và khoá hoa.
- [x] EN bật/tắt bằng vuốt xuống phím cách, mặc định theo app và ô email.
- [x] Gõ vuốt offline: một âm tiết mỗi lượt, ít nhất 3 phím chữ khác nhau; chạm gợi ý để chèn.
- [x] Profile Mặc định / Cân bằng hai ngón / Công thái học và profile cá nhân.
- [x] Editor ba lớp, đổi chỗ phím bằng giữ-kéo ngay trên bàn phím.
- [x] Snippet vuốt xuống tối đa 8 đơn vị UTF-16; phím chức năng không làm mất đường vào Cài đặt.
- [x] Một tay, sáng/tối, màu nhấn, rung/âm thanh, padding đáy, học độ lệch chạm.

### Chỉnh sửa

- [x] Vuốt trái ngắn trên phím cách xoá ký tự; vuốt dài chọn-để-xoá theo từ, kéo qua dòng.
- [x] Giữ-kéo phím cách di con trỏ, chạm đúp-kéo chọn văn bản.
- [x] Cut/copy/paste bằng cử chỉ, undo/redo hai ngón, chọn hết.
- [x] Thanh chỉnh sửa khi chọn/con trỏ; bảng chỉnh sửa riêng khi giữ ⚙.
- [x] Giữ nút ⌫ để xoá liên tục; xoá emoji theo grapheme.
- [x] Clipboard history, ghim, hết hạn 1 giờ, bỏ qua nội dung nhạy cảm.

### Gợi ý và tiện ích

- [x] Biến thể dấu hợp lệ; chạm để chốt kèm khoảng trắng, dấu câu bỏ khoảng trắng tự thêm.
- [x] Gợi ý sửa lỗi phím lân cận/Telex và khôi phục dấu; sửa từ ở con trỏ.
- [x] Dự đoán âm tiết tiếp theo và hoàn thành tiền tố từ mô hình cá nhân/gói n-gram.
- [x] Ngữ cảnh trước con trỏ, học tên riêng, xoá từ đã học, chế độ ẩn danh.
- [x] Pipeline n-gram 3 bậc, Kneser–Ney, lượng tử hoá, mmap và script đo chất lượng.
- [x] APK không kèm `.ngram`; người dùng tự dựng và nạp qua trình chọn file.
- [x] Tìm/gợi ý emoji tiếng Việt từ CLDR, lọc theo font máy, tông da mặc định.
- [x] Gõ tắt hai cột, sao lưu/khôi phục zip, voice qua IME ngoài.
- [x] CI test/lint, dev APK và release có ký, ghim chứng chỉ và version tái lập từ tag.

## 4. Đợt triển khai tiếp theo

Mỗi tính năng có commit riêng, kiểm thử trước khi commit; cập nhật trạng thái khi hoàn tất.

1. [ ] **Giới tính emoji mặc định**: nguyên bản / nữ / nam, kết hợp tông da; chỉ đổi sang
   biến thể Unicode hợp lệ và font vẽ được. Áp dụng nhất quán khi chọn từ bảng/tìm/gợi ý.
2. [ ] **Nút xem thêm gợi ý**: `…` hiện khi có thêm ứng viên, mở rộng ngay trong vùng bàn phím;
   chọn từ giữ hành vi chèn hiện có. Giữ gợi ý vẫn xoá khỏi từ điển cá nhân.
3. [ ] **Gợi ý EN**: từ điển tiếng Anh offline nhỏ, hoàn thành từ đang gõ, giữ hoa/thường;
   chạm để chèn. Không học chung với tiếng Việt, không hiện ở ô mật khẩu/email/mã.
4. [ ] **Sửa lỗi xét ngữ cảnh**: kết hợp chi phí sửa, độ phổ biến và xác suất n-gram cá nhân/gốc;
   áp dụng cả khi đang gõ và khi đặt con trỏ vào từ. Không có dữ liệu thì giữ thứ tự dự phòng.
   Test cùng lỗi với hai ngữ cảnh và kiểm tra độ trễ.

## 5. Để sau

- [ ] Bàn phím chia đôi cho màn hình ngang.
- [ ] Bàn phím nổi.
- [ ] Nạp mô hình transformer nhỏ do người dùng cung cấp (GGUF/llama.cpp).

## 6. Ý tưởng chưa chốt

- Bố cục riêng cho tablet/màn hình gập; cân nhắc giới hạn chiều rộng và căn giữa trước khi chia đôi.
- Vẽ dấu: thử nghiệm nếu có lợi hơn việc chọn 6 biến thể dấu.
- Phát hành lên F-Droid: kiểm tra yêu cầu build và metadata trước khi lên lịch.

Không bổ sung cử chỉ vuốt trái rồi giữ để xoá liên tục: thao tác này đã có trên nút ⌫.
Enter tuân theo imeOptions hoặc chèn xuống dòng; không có tuỳ chọn ép gửi tin cho app chat.

## 7. Kiểm tra và phát hành

- `./gradlew :telex:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Kiểm thử UI trên emulator, quay một video cho mỗi kịch bản đạt; lưu `test-recordings/`,
  bỏ qua bằng `.git/info/exclude`. Chỉ thử trên điện thoại thật khi được yêu cầu.
- Với feature lớn, review độc lập diff chưa commit; một commit cho mỗi feature hoàn tất.
- Version chỉ sửa ở `appVersion`; quy trình release đầy đủ nằm trong README và `tools/release.sh`.
- Dữ liệu Unicode/CLDR theo Unicode-3.0, ghi công trong NOTICE. Corpus n-gram phải có giấy phép
  phù hợp trước khi chia sẻ mô hình; không đưa corpus vào repo.
