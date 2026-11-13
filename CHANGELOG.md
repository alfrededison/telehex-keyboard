# Changelog

## 0.8.4 — unreleased

## 0.8.3 — 2026-11-13

- Telex: khi chốt từ, từ có dấu mà sai chính tả được trả lại đúng phím đã gõ như UniKey ("user" thay vì "ủe"); gõ tắt vẫn được xét trước. Gõ lại phím dấu để huỷ lại giữ nguyên văn như trước 0.8.2 ("usser" → user, "ass" → as).
- Gõ tắt bung được ngay sau dấu mở ngoặc, nháy ("(dc" → "(được"); chữ như "x.dc" hay đường link dán vào thì không bị bung.

## 0.8.2 — 2026-11-13

- Telex: chữ tiếng Anh không còn bị bỏ dấu khi đã không thể là tiếng Việt ("developer", "class"); "ddc" vẫn ra "đc".
- Gõ tiếp vào từ vừa gõ để sửa dấu: xoá dấu cách hoặc chạm vào cuối từ rồi gõ dấu ("hiêu", ⌫, s → hiếu; chạm sau "ban", s → bán).
- iOS: bản native đầu tiên, trong cùng repo và dùng chung engine Kotlin Multiplatform với Android: gõ Telex VI/EN với từ điển offline, gợi ý và học từ (có ẩn danh), gõ vuốt, emoji tìm bằng tiếng Việt, gõ tắt và sao lưu JSON. Không có bảng chỉnh sửa (chọn văn bản) vì bàn phím iOS không chọn được văn bản thay ứng dụng.
- iOS: bố cục như Android 0.8.0 — Mặc định (⚙ sát mép trái, zigzag ⚙ Q W E R T G Y H U I O P 123, lớp 123 hai trang gắn theo ô, vuốt xuống Q…P ra 1…0) và "Truyền thống" (QWERTY 10 cột, ⇧ chạm đúp khoá hoa, ⌫ giữ để xoá liên tục), cùng trình sửa các lớp phím.
- iOS: giao diện theo bàn phím hệ thống iOS — nền xám, phím chữ và phím cách trắng có bóng 1pt, phím chức năng xám, chữ nét thường, ↵ mang màu nhấn, ⇧ sáng lên khi bật; bong bóng phóng to ký tự khi chạm; trên phím chữ nhật ký tự vuốt xuống nằm ở góc dưới bên phải; emoji vuốt xuống vẽ nhỏ để không lấn vào chữ. Lưới tổ ong giữ hình lục giác, nguyên âm tô màu nhấn nhạt và phím dấu tô cam nhạt như Android; màu nhấn còn tô ↵, công tắc và nét gõ vuốt. "Không" để nguyên âm và phím dấu trắng như phụ âm.
- iOS: thanh gợi ý là chữ trơn chia ba ô có vạch ngăn, gợi ý đầu đậm; khi chưa có gợi ý thanh hiện lối tắt emoji, gõ tắt, profile, giao diện và cài đặt. Bảng cài đặt chuyển sang danh sách nhóm kiểu app Cài đặt: icon màu, ›, ✓ cho profile đang dùng, "‹ Cài đặt" quay về và "Xong" về bàn phím; màu nhấn chọn bằng ô tròn; bảng emoji có ô tìm kiếm và hàng nhóm cuộn ngang.
- iOS: khi tìm emoji, thêm gõ tắt hay đặt tên profile, bàn phím cao thêm một hàng ô nhập rộng (tên ô nhỏ phía trên, chữ lớn; ô viết tắt hẹp, ô nội dung rộng) thay vì thu nhỏ phím; tiêu đề trình soạn hiện trên thanh trên. Các nút chọn lớp khi sửa phím nằm ngay trên thanh gợi ý.
- iOS: gõ tắt có nút 🗑 ở mỗi hàng, chạm lần nữa vào "Xoá" để xác nhận.
- iOS: gõ vuốt ở chế độ EN như Android 0.8.1: lướt ra từ tiếng Anh và từ rút gọn ("dont" → don't), "I" luôn viết hoa, tự cách sau chữ hay dấu câu đứng trước; không bật ở email, URL hay ô tắt gợi ý, không học từ EN.
- iOS: toàn bộ Cài đặt chuyển vào app như Android (bàn phím, Telex, gõ tắt, gợi ý và gói ngôn ngữ, chống bấm nhầm, cử chỉ, emoji, sao lưu cài đặt, chỉnh lớp phím) và tới bàn phím qua App Group, không cần Truy cập đầy đủ; ⚙ trên bàn phím còn đổi profile, ẩn danh, emoji, sao lưu đầy đủ và xoá dữ liệu học.
- iOS: sửa âm tiết đầu tiên trong ô trống không ghép dấu ("ddi" thay vì "đi").
- iOS: cắt/chép/dán bằng vuốt phím cách như Android: vuốt phải dán; chọn văn bản bằng công cụ của ứng dụng rồi vuốt lên để chép, vuốt trái để cắt. Cần Truy cập đầy đủ; không có quyền thì vuốt trái vẫn xoá vùng chọn. Hai ngón vuốt trái/phải hoàn tác/làm lại có thông báo như Android.
- iOS: lịch sử clipboard (mở từ ⚙ hoặc vuốt xuống phím ⚙, phím ⚙ có icon clipboard nhỏ như Android khi dùng được; mục thường tự xoá sau 1 giờ, ghim để giữ) và giữ kéo đổi chỗ phím ngay trên bàn phím, cả hai mặc định bật và chạy ngay khi cho phép Truy cập đầy đủ (app không đoán trạng thái quyền, chỉ ghi chú và có nút mở Cài đặt); app hiện profile tạo khi kéo đổi chỗ. iOS hỏi quyền dán mỗi lần bàn phím đọc clipboard trừ khi cho phép dán từ ứng dụng khác.
- iOS: thêm các tuỳ chọn như Android: "Tự khôi phục từ tiếng Anh", "Vùng chạm động" (chạm lưng chừng giữa hai phím chữ thì chọn phím giữ âm tiết hợp lệ) và thanh trượt "Mỗi bước xoá từ / di con trỏ".
- iOS: chạm như Android 0.7.1: chạm mép ⚙/123 rồi nhấc tay trên chữ thì gõ chữ đó; vùng chạm học được không lấn sang phím chức năng; chạm đang dở bị huỷ khi đổi lớp hay ẩn bàn phím.
- iOS: màn chào trong app như Android 0.8.0: ô gõ thử ngay đầu trang, các bước bật bàn phím (✓ khi đã thêm TeleHex) và chọn kiểu Tổ ong/Truyền thống có hình xem trước; bàn phím đổi theo lựa chọn khi mở lại (qua App Group).
- iOS: gói ngôn ngữ `.ngram` tới 32 MiB (đủ cho gói tiếng Việt khuyên dùng 14,6 MB): bàn phím đọc gói thẳng từ tệp thay vì nạp hết vào bộ nhớ. Sao lưu đầy đủ từ ⚙ trên bàn phím không còn kèm gói; sao lưu cài đặt trong app vẫn kèm.
- iOS: app có icon; 🌐 trên thanh gợi ý chỉ hiện ở máy iOS yêu cầu (có nút Home), máy Face ID dùng nút chuyển bàn phím của hệ thống.

## 0.8.1 — 2026-11-10

- Gõ vuốt dùng được ở chế độ EN (vuốt xuống phím cách): lướt qua các chữ để gợi ý từ tiếng Anh, viết hoa đầu câu và "I", tự thêm dấu cách sau dấu câu. Không bật ở ô mật khẩu, email, URL hay ô tắt gợi ý, và không học từ EN vào dữ liệu tiếng Việt.
- Gợi ý EN nhận từ rút gọn gõ thiếu dấu nháy: "ill" → I'll, "dont" → don't, "well" → có cả we'll. "I" trong I'm, I'll… luôn viết hoa.

## 0.8.0 — 2026-11-10

- Thêm profile có sẵn "Truyền thống" cho ai chưa quen lưới tổ ong: bố cục QWERTY 10 cột quen thuộc, phím chữ nhật, có phím ⇧ (chạm đúp khoá chữ hoa) và ⌫ (giữ để xoá liên tục), lớp 123 hai trang với hàng số 1…0 ở trên. Mọi cử chỉ, gợi ý và gõ vuốt dùng như trên lưới tổ ong. Chọn trong Cài đặt › Bàn phím.
- Bảng sửa văn bản (vuốt xuống ⚙): nút giọng nói được thay bằng nút Profile, tô màu phím dấu thanh; chạm để hiện danh sách profile, chọn một profile là dùng ngay. Bỏ hẳn chức năng chuyển sang bàn phím giọng nói.
- Phím emoji và icon clipboard dưới ⚙ vẽ bằng icon phẳng cùng màu với các phím chức năng khác, theo giao diện sáng/tối, thay cho emoji màu (mỗi máy một kiểu).
- Màn chào khi mở app lần đầu: hướng dẫn bật và chọn TeleHex (hiện ✓ từng bước), rồi chọn kiểu bàn phím Tổ ong hay Truyền thống với hình xem trước và ô gõ thử. Ai đã bật TeleHex từ bản trước thì không thấy màn này.

## 0.7.2 — 2026-11-09

- Clipboard history không còn nằm trong bản sao lưu tự động của Android hay khi chuyển sang máy mới; mục thường quá 1 giờ bị xoá ngay khi bàn phím khởi động hoặc hiện lên, không đợi lần chép kế tiếp.
- Chuẩn bị phát hành trên Google Play: build kèm file AAB ký cùng key với APK trên GitHub, nên hai nguồn cài cập nhật đè lên nhau được. Thêm chính sách quyền riêng tư.

## 0.7.1 — 2026-11-09

- Bố cục mặc định gần QWERTY hơn: ⚙ sát mép trái, hai hàng trên đọc zigzag ⚙ Q W E R T G Y H U I O P 123, hàng giữa A S D F … J K L. Lớp 123 theo hai trang ký hiệu của Gboard (phím 1/2 ở ô ⚙, trang 2 có `<` `>` thay `,` `.`); vuốt xuống Q…P ra 1…0. Đang dùng "Mặc định" thì tự chuyển sang bố cục mới.
- Bỏ hai profile có sẵn "Cân bằng hai ngón" và "Công thái học"; ai đang dùng thì giữ nguyên bố cục thành profile "Của tôi".
- Huỷ phím và cử chỉ còn chờ khi đổi lớp/bố cục hoặc đóng bàn phím, tránh thực thi lại nút chức năng ở lần gõ đầu tiên sau khi chỉnh sửa.
- Chạm sát ⚙/123 rồi nhấc tay trên phím chữ sẽ gõ chữ tại điểm nhấc tay, tránh mất chữ đầu và bật nhầm Cài đặt/lớp số khi gõ nhanh cạnh nút chức năng.
- Sửa nhận nhầm ⚙/123 khi chạm phím chữ sau khi kéo đổi chỗ: vùng chạm học chỉ điều chỉnh giữa các phím chữ, giữ đúng tâm phím; kéo thả dùng ô đang hiển thị và vị trí nhả tay cuối cùng.

## 0.7.0 — 2026-11-08

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
