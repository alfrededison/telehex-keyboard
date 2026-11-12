# Điều kiện phát hành iOS theo Android 0.8.1

Quyết định: phát hành khi đủ tính năng Android 0.8.1 mà iOS hỗ trợ. Bản iOS dùng UIKit và engine KMP trong cùng repo. **Bản 0.1.0 đang thử công khai trên TestFlight; chuẩn bị gửi App Review.**

## Phần đã triển khai

| Nhóm | Hành vi iOS |
|---|---|
| Engine | Một nguồn Telex/corrector/predictor/glide/parser cho JVM và Apple; corpus 413 ca gõ dùng chung |
| Gõ | VI/EN, dấu cũ/mới, L là huyền, hoa/thường, tự viết hoa theo traits và tuỳ chọn, khoá hoa, newline, xoá grapheme, bỏ khoảng trắng tự thêm trước dấu câu |
| Dự đoán | Tone candidates, hoàn thành VI/EN offline, sửa lỗi xét ngữ cảnh khi gõ và ở con trỏ, từ tiếp theo; thanh ngang và lưới …; chạm mới thay/chèn |
| Học | Mô hình VI riêng từng keyboard, lưu atomic có debounce; không học EN/ô hạn chế/ẩn danh; giữ để quên từ, xác nhận xoá dữ liệu học, giữ nguyên gói VI/EN |
| Gói ngôn ngữ | Nạp/xoá VI và EN riêng bằng Files; kiểm tra cấu trúc Native, giới hạn 32 MiB và 100.000 từ; bàn phím ánh xạ gói từ tệp (mmap); kiểm tra từ EN; không tải mạng hoặc bundle mô hình |
| Gõ vuốt | VI và EN (EN theo từ điển EN, lướt bỏ dấu nháy ra từ rút gọn, cách sau chữ/dấu câu đứng trước, "I" luôn hoa; không bật ở email/URL/ô số/khi tắt gợi ý); nét vuốt theo tâm phím của profile/một tay; ≥3 phím khác nhau; hiện ứng viên và đợi chạm để chèn; không tự học trước lúc chọn |
| Cử chỉ | Vuốt chữ lên đảo hoa/thường, xuống ký tự phụ; space trái xoá/xoá từ, lên khoá hoa, xuống VI/EN; giữ space kéo con trỏ; hai ngón undo/redo; giữ ⌫ lặp |
| Màn chào và Cài đặt | Như Android 0.8.0: các bước bật bàn phím (bước thêm TeleHex hiện ✓ khi đã thêm), chọn Tổ ong/Truyền thống có hình xem trước, ô gõ thử; toàn bộ Cài đặt trong app cùng nhóm với Android, trình sửa lớp phím trên bàn phím dựng trong app. Cài đặt tới bàn phím qua App Group, áp khi bàn phím mở ra; ⚙ trên bàn phím chỉ còn thao tác nhanh |
| Chạm | Như Android 0.7.1: huỷ chạm đang dở khi đổi lớp/bố cục hay ẩn bàn phím; chạm mép phím chức năng rồi nhấc trên chữ thì gõ chữ; độ lệch học được chỉ dời vùng giữa các phím chữ, chạm giữa phím luôn trúng; kéo đổi chỗ thả theo ô đang hiển thị |
| Profile | Theo Android 0.8.0: Mặc định (tổ ong, ⚙ sát mép trái, zigzag ⚙ Q W E R T G Y H U I O P 123) và Truyền thống (QWERTY 10 cột, phím chữ nhật, ⇧ chạm đúp khoá hoa, ⌫ giữ để xoá liên tục); tạo/nhân bản/đổi tên/xoá; sửa chữ/vuốt/2 trang ký tự, đổi chỗ bằng hai lần chạm hoặc giữ kéo, chuyển giữa trang trong editor; reset từng lớp; sửa builtin tự tạo bản riêng |
| Ký tự | Hai trang 123 gắn theo ô như Android (phím 1/2 ở ô ⚙ hoặc ⇧, trang 2 có `<` `>` ở ô `,` `.`), dialpad, tự chọn EN/numeric theo traits của ô; ký tự phụ gắn theo ô |
| Emoji | Cùng catalog CLDR với Android, tìm tiếng Việt không dấu, nhóm/phân trang, gợi ý từ khoá, giới tính/tông da, lọc glyph và chuỗi ZWJ không vẽ được |
| Gõ tắt | Editor hai ô dùng chính keyboard; lưu/chỉnh/xoá; mở rộng khi chạm space, giữ hoa đầu câu; nội dung editor không vào ứng dụng chủ |
| Giao diện | Theo bàn phím hệ thống iOS (`KeyboardTheme.swift`): phím trắng có bóng, phím chức năng xám, nguyên âm tô màu nhấn nhạt và phím dấu cam nhạt như Android, ↵ màu nhấn, bong bóng khi chạm, lục giác giữ khe 3pt và phím cách ghép ô; thanh gợi ý chữ trơn ba ô, lối tắt khi chưa có gợi ý; bảng cài đặt dạng danh sách nhóm có điều hướng ‹/Xong; toast tối, dialpad 4×4; hệ thống/sáng/tối, màu nhấn, một tay, chiều cao, font theo Dynamic Type, nhãn và action accessibility, độ lệch chạm có giới hạn và reset; rung/âm phím tuỳ chọn; 🌐 chỉ hiện khi iOS yêu cầu (`needsInputModeSwitchKey`) |
| Sao lưu | JSON có phiên bản của iOS. App: cấu hình/profile/gõ tắt/gói VI/EN; ⚙ trên bàn phím: bản đầy đủ có cả dữ liệu học. Bỏ qua lịch sử clipboard trong bản sao lưu cũ; whitelist và giới hạn dữ liệu, kiểm tra trước xác nhận restore, staging và rollback file |

## Bằng chứng kiểm thử

- 80 JVM tests, 7 macOS Native tests, 34 Android app tests đều đạt; corpus golden và Unicode NFC/NFD.
- 62 XCTest đạt: bridge Swift → Native, safe replacement khi đổi field/selection/context, Unicode cursor, keyboard-owned undo/redo, snippets/ẩn danh/EN không học, cấu hình/profile và bố cục 0.8.0, thanh gợi ý/điều hướng bảng, pending write/clear, backup/restore và emoji ghép.
- UI Simulator có video riêng từng kịch bản trong `test-recordings/`: gõ VI/EN, profile/symbols/emoji, snippets/privacy, bố cục truyền thống, editor lớp phím, glide/space, appearance/dialpad và extension thật. Chỉ giữ video khi test đạt.
- Android phải tiếp tục qua `:telex:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Build Simulator và Release ARM64 không ký đều đạt tại máy phát triển. Workflow iOS (build + XCTest) và Android đều đạt trên `main` tới commit gói ngôn ngữ 32 MiB.

Danh sách hành vi đã có code không thay thế kiểm thử thực tế trên thiết bị và các ứng dụng chủ.

## Review độc lập và hồi quy

Review độc lập đã hoàn tất một lần cho diff mở rộng. Người dùng đã duyệt sửa cả 6 finding; các bản sửa có test hồi quy. Kết quả này hoàn tất nhóm finding đã báo, còn các gate thiết bị/CI/phát hành bên dưới.

| Mức | Nhánh lỗi đã sửa | Kiểm chứng |
|---|---|---|
| P1 | Chọn ô Chữ rồi chuyển 123 và chọn ô đích gây truy cập `symbols[-2]` | Bỏ selection giữa lớp không tương thích và khi thoát editor; kiểm tra chỉ số; giữ selection giữa hai trang symbol. XCTest và UI `testLayoutEditorLayerTransitions` đạt |
| P1 | Restore backup chưa áp incognito/gõ tắt/cấu hình Telex vào session đang chạy | Áp cấu hình và profile ngay sau restore; nạp từ điển không reset âm tiết đang gõ. Test restore ẩn danh/gõ tắt/dấu/L và callback bị trì hoãn đạt |
| P1 | VocabularyLoader đang chạy có thể trả mô hình đã học cũ sau khi xoá | Vô hiệu hoá generation cũ và nạp lại từ storage đã xoá. Test đảo thứ tự completion và snapshot pending write đạt |
| P1 | Chọn emoji khi đang sửa gõ tắt/profile có thể chèn vào host và mất draft | Tách session tìm emoji, giữ field/draft/composition/callback của editor. XCTest và UI `testEmojiSearchReturnsToSnippetDraftWithoutEditingHost` đạt |
| P2 | Vuốt lên hoặc accessibility uppercase ở phím chức năng gõ `#SETTINGS`/`#SYMBOLS`/`#EMOJI` | Chỉ áp hoa/thường và bắt đầu glide từ phím chữ. XCTest accessibility/gesture và UI extension `testSystemFunctionSwipesCannotInsertInternalKeyCodes` đạt |
| P2 | Sửa từ trước dấu cách có sẵn có thể thêm khoảng trắng kép | Giữ dấu cách/newline/dấu câu có sẵn; chỉ đánh dấu khoảng trắng do keyboard thêm. Test sửa từ, undo/redo và dấu câu đạt |

## Khác biệt nền tảng

- Secure fields, phone pad và app chủ có thể loại keyboard bên thứ ba; không vượt qua bằng private API. [Apple keyboard interface](https://developer.apple.com/documentation/uikit/configuring-a-custom-keyboard-interface)
- Proxy cung cấp context giới hạn, insertion point và selection của host. Chọn vùng, chọn hết và undo của toàn bộ ứng dụng không có API tương đương Android. Người dùng chọn bằng công cụ của host; TeleHex hỗ trợ kéo con trỏ bằng phím cách, xoá vùng chọn và undo/redo các thay đổi của chính keyboard khi anchor còn khớp. Bàn phím không chọn được văn bản thay ứng dụng nên không có bảng điều hướng; nhưng đọc được vùng người dùng đã chọn (`selectedText`), nên cắt/chép/dán theo cử chỉ phím cách như Android: vuốt phải dán, có vùng chọn thì vuốt lên chép, vuốt trái cắt. Cả ba cần Truy cập đầy đủ (clipboard); không có quyền thì vuốt trái vẫn xoá vùng chọn và báo cần quyền. Vuốt xuống ⚙ mở lịch sử clipboard khi có Truy cập đầy đủ. Xoá từ hiện số từ trước khi thả; không giả bôi đen selection trong host. [Apple text interactions](https://developer.apple.com/documentation/uikit/handling-text-interactions-in-custom-keyboards)
- Âm tiết đang gõ được cập nhật bằng xoá/chèn qua proxy (không dùng marked text); độ ổn định ở ô nhập web và các app chat cần xác nhận trên thiết bị (xem điều kiện phát hành).
- Keyboard extension có giới hạn bộ nhớ riêng, khác nhau theo thiết bị, và bị hệ thống kết thúc khi vượt; số đo trên Simulator không thay được đo bản release trên iPhone thật. [Apple: limit memory usage](https://developer.apple.com/documentation/uikit/creating-a-custom-keyboard)
- Tự EN dựa vào traits email/URL/ASCII. Không đọc danh tính app chủ hoặc port danh sách app Android; lựa chọn VI/EN giữ trong phiên keyboard.
- Gõ, mô hình, profile và gõ tắt không cần Full Access; những gì cần quyền này liệt kê ở dòng dưới. `RequestsOpenAccess` chỉ cho phép người dùng bật quyền; không có code gửi nội dung gõ lên mạng. [Apple open access](https://developer.apple.com/documentation/uikit/configuring-open-access-for-a-custom-keyboard)
- Truy cập đầy đủ là tuỳ chọn. Có quyền này thì thêm: cắt/chép/dán bằng vuốt phím cách, lịch sử clipboard (iOS hỏi quyền dán mỗi lần đọc trừ khi cho phép dán từ ứng dụng khác, nên chỉ đọc khi mở bảng clipboard thay vì tự ghi mỗi lần chép như Android), giữ kéo đổi chỗ phím trên bàn phím (profile tạo ra báo về app), rung và âm phím. Clipboard và kéo đổi chỗ mặc định bật; iOS không cho app biết bàn phím có quyền hay không (không có quyền thì bàn phím không ghi được App Group, tín hiệu từ bàn phím chỉ tới khi bàn phím hiện lên), nên app không hiện trạng thái quyền mà chỉ ghi chú cần quyền và có nút mở Cài đặt; bàn phím tự kiểm tra `hasFullAccess` khi chạy.
- Không xin Truy cập đầy đủ: bàn phím chỉ đọc App Group, nên cài đặt đi một chiều từ app. Từ đã học, độ lệch chạm chỉ nằm trong sandbox extension; app gửi lệnh xoá, bàn phím làm ở lần mở sau. Đổi nhanh trên bàn phím (profile, ẩn danh) không hiện trong app trừ khi có Truy cập đầy đủ. Backup iOS JSON không tương thích ZIP Android.
- Giọng nói dùng cơ chế dictation/chuyển keyboard của iOS nếu hệ thống cung cấp; không chuyển sang IME Android hoặc thu microphone trong extension.
- Files picker và các quyền phụ cần kiểm tra thêm trên máy thật; import/export dùng API public, không truy cập đường dẫn host.

## Điều kiện còn lại trước phát hành

- [x] Review độc lập diff và xử lý cả 6 finding được duyệt, có test hồi quy.
- [ ] Kiểm thử đủ các nhánh cử chỉ: held cursor, xoá vùng chọn, hai ngón, chuyển trang khi drag; kiểm thử âm/rung trên thiết bị thật.
- [x] iPhone thật, bản TestFlight: gõ đầy đủ khi tắt Truy cập đầy đủ; dialpad ở ô số; cài đặt chỉnh trong app tới bàn phím qua App Group.
- [ ] Gõ dài trên iPhone thật (bản TestFlight, đã nạp gói VI khuyên dùng 14,6 MB): trong một ghi chú, gõ liên tục khoảng 15 phút (cỡ 3.000 ký tự) xen gõ vuốt, chọn gợi ý, emoji, đổi VI/EN; sau đó chuyển qua lại 5–10 app có ô nhập rồi quay lại gõ tiếp. Đạt khi bàn phím không lần nào tự biến mất hay rơi về bàn phím hệ thống, không chậm dần, và Cài đặt › Quyền riêng tư & Bảo mật › Phân tích & Cải tiến › Dữ liệu phân tích không có `JetsamEvent` mới nhắc tới `TeleHexKeyboard` (cần bật Chia sẻ phân tích iPhone).
- [ ] Đo kỹ trên iPhone thật (cần bản ký development để Xcode attach): RAM/footprint, cold open, memory warning, extension bị terminate/recreate; gói ngôn ngữ sát giới hạn 32 MiB.
- [ ] UIKit, SwiftUI, Safari/WKWebView và app chat mục tiêu; iPhone/iPad/rotation, Dynamic Type/VoiceOver.
- [ ] Files import/export/restore thực tế và rung/âm phím với Full Access bật/tắt trên thiết bị.
- [x] Cấu hình ký: entitlements App Group, `Signing.local.xcconfig` cho Team ID, privacy manifest, khai báo mã hoá.
- [x] Đặt Team ID, đăng ký App ID `com.telehex.keyboard`/`com.telehex.keyboard.extension` và App Group `group.com.telehex.keyboard.shared`, archive bản ký (`ios/scripts/release.sh`), build 0.1.0 lên TestFlight; build qua Beta App Review, có link công khai.
- [x] Icon, versioning, metadata và ảnh chụp App Store (`fastlane/metadata/ios`, `fastlane/screenshots/ios`, đẩy bằng `ios/scripts/asc_metadata.py`).
- [x] Người liên hệ App Review (đẩy bằng `asc_metadata.py`).
- [ ] Trên web App Store Connect: App Privacy (Data Not Collected), Content Rights (nguồn dữ liệu/giấy phép).
- [ ] Gắn build mới nhất vào phiên bản 0.1.0: chạy lại `asc_metadata.py`.
- [ ] Android CI và iOS CI đều qua trên `main` sau khi push các commit iOS mới nhất.
