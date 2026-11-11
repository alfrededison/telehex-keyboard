# TeleHex cho iOS

Bản iOS chính thức được phát triển trong cùng repo Android. **Đang thử công khai qua [TestFlight](https://testflight.apple.com/join/gvyHeek6), chưa lên App Store.** Chuẩn tính năng là Android 0.8.1, chỉ phát hành khi các mục iOS hỗ trợ trong [bảng đối chiếu](docs/feature-parity.md) đã được kiểm thử và các khác biệt nền tảng đã được chốt.

## Cấu trúc

- `../telex/src/commonMain`: cùng một engine Telex, sửa lỗi, dự đoán VI/EN, giải mã gõ vuốt và mô hình cá nhân cho Android/iOS.
- `../telex/src/jvmMain`: Unicode/reader JVM và adapter ByteBuffer, giữ API đang dùng bởi Android và mmap gói ngôn ngữ.
- `../telex/src/appleMain`: Unicode Foundation và reader Apple.
- `Sources/App`: màn chào (các bước bật bàn phím, chọn Tổ ong/Truyền thống), toàn bộ Cài đặt như Android (bàn phím, Telex, gõ tắt, gợi ý và gói ngôn ngữ, chống bấm nhầm, cử chỉ, emoji, sao lưu cài đặt) và trình sửa lớp phím; ô thử dùng extension thật, thông tin giấy phép.
- `Sources/Keyboard`: keyboard extension native UIKit.
- `Sources/Shared`: hình học phím, adapter văn bản, trạng thái gõ và cài đặt trong sandbox của keyboard.
- Từ điển VI/EN và thông báo giấy phép lấy trực tiếp từ `../app/src/main/assets`; không có bản sao riêng cho iOS.

## Build

Hiện hỗ trợ development trên Mac Apple Silicon, iOS/iPadOS 16+, Xcode 26.x, JDK 17+ và XcodeGen. Kotlin pin 2.3.21 để hỗ trợ toolchain Apple, đồng thời giữ target JVM 17 của Android.

```sh
# Từ root repo
ios/scripts/build.sh
open ios/TeleHex.xcodeproj
```

Chọn scheme `TeleHex`, chọn iPhone Simulator rồi Run. Xcode tự gọi Gradle `:telex:embedAndSignAppleFrameworkForXcode` trước khi compile, nên sửa engine Kotlin rồi Run sẽ dùng đúng engine mới. Debug/Release lấy framework theo configuration của Xcode; không ghim framework debug vào bản release. Project Xcode được sinh từ `project.json`.

Để build ARM64 cho iPhone mà chưa ký:

```sh
SDK=iphoneos DESTINATION='generic/platform=iOS' CONFIGURATION=Release ios/scripts/build.sh
```

## Ký và phát hành

App và keyboard extension dùng chung App Group `group.com.telehex.keyboard.shared` (entitlements sinh từ `project.json`). Không cần Truy cập đầy đủ: app là nơi chỉnh cài đặt và ghi cấu hình, gói ngôn ngữ, lệnh xoá vào group; bàn phím chỉ đọc khi mở ra (`SharedSettings.swift`). Cả hai target có `PrivacyInfo.xcprivacy` (không thu thập dữ liệu; khai báo UserDefaults lý do `CA92.1` và `1C8F.1`) và `ITSAppUsesNonExemptEncryption = NO`.

1. Tạo `ios/Config/Signing.local.xcconfig` (không commit) với `DEVELOPMENT_TEAM = <Team ID>`, rồi `xcodegen generate --spec ios/project.json`.
2. Chủ tài khoản tạo App Group `group.com.telehex.keyboard.shared` và gán cho App ID `com.telehex.keyboard`, `com.telehex.keyboard.extension` trên developer.apple.com (xem [docs/app-store.md](../docs/app-store.md)). Tài khoản có quyền Certificates, Identifiers & Profiles đăng nhập Xcode thì ký tự động làm việc này.
3. Tạo app trên App Store Connect với bundle ID `com.telehex.keyboard`, rồi chạy `ios/scripts/release.sh` (archive Release, ký, tải lên TestFlight; số build tự tăng). `DESTINATION=export` chỉ xuất `.ipa`.
4. `python3 ios/scripts/asc_metadata.py` đẩy tên, mô tả, ảnh chụp, xếp hạng tuổi, giá và gắn build vào phiên bản App Store; chi tiết ở [docs/app-store.md](../docs/app-store.md).
5. `python3 ios/scripts/asc_testflight.py` đưa build mới nhất ra TestFlight công khai (Test Information, What to Test, nhóm Công khai có public link, gửi Beta App Review).

Không đăng nhập Xcode được thì thêm khoá API App Store Connect (vai trò Admin) vào `Signing.local.xcconfig`:

```
ASC_KEY_PATH = /Users/<bạn>/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8
ASC_KEY_ID = <KEY_ID>
ASC_ISSUER_ID = <ISSUER_ID>
```

Khoá API không quản lý được App Group nên ký tự động bằng khoá lỗi 401. Khi có khoá, `release.sh` gọi `scripts/asc_signing.py` để ký thủ công: tái dùng hoặc tạo chứng chỉ Apple Distribution (khoá riêng vào login keychain, bản lưu ở `~/.appstoreconnect/signing`), tạo profile App Store cho app và extension, kiểm tra profile có đủ App Group rồi mới archive. Thiếu group thì script dừng và nói cần gán gì. Lần đầu codesign dùng khoá mới nhập, macOS có thể hỏi quyền truy cập Keychain: chọn **Always Allow** (chạy không có người bấm thì lỗi `errSecInternalComponent`).

Bản không ký (Simulator, CI) không có App Group: cài đặt chỉnh trong app không tới được bàn phím (trang chính có ghi chú), mọi thứ khác chạy bình thường. Để thử App Group trên Simulator, build ký ad-hoc:

```sh
xcodebuild -project ios/TeleHex.xcodeproj -scheme TeleHex -destination 'platform=iOS Simulator,id=SIMULATOR_UUID' \
  -derivedDataPath out/ios-signed build CODE_SIGN_STYLE=Manual CODE_SIGN_IDENTITY=- DEVELOPMENT_TEAM=
```

## Dùng thử

1. Cài app trên Simulator.
2. Settings → General → Keyboard → Keyboards → Add New Keyboard → TeleHex.
3. Mở TeleHex, chạm ô thử gõ và dùng nút 🌐 của iOS để chọn TeleHex.
4. Gõ `tieengs vieetj` → `tiếng việt`. Chạm VI/EN hoặc vuốt xuống phím cách để đổi ngôn ngữ. Trong EN, thử `hel` rồi chạm `help`.

Gõ và lưu dữ liệu trong keyboard không cần Full Access. Chỉ lịch sử clipboard, giữ kéo đổi chỗ phím trên bàn phím, âm phím và rung cần quyền này; không có chức năng gửi nội dung gõ lên mạng. Cài đặt chỉnh trong app TeleHex và tới bàn phím ở lần mở tiếp theo. Nếu không thấy keyboard trên Simulator, mở Simulator.app và kiểm tra I/O → Keyboard → Connect Hardware Keyboard / Toggle Software Keyboard.

## Tính năng và dữ liệu

Cài đặt nằm trong app, cùng nhóm với Android. ⚙ trên bàn phím chỉ còn thao tác nhanh: đổi profile, ẩn danh, emoji, sao lưu/khôi phục đầy đủ và xoá dữ liệu học, vì từ đã học chỉ bàn phím giữ (ghi sang app cần Truy cập đầy đủ). Đổi profile, ẩn danh, kéo đổi chỗ phím ngay trên bàn phím được giữ khi app gửi cài đặt cũ hơn; phần nào sửa sau thì thắng.

Gói `.ngram` dựng bằng pipeline sẵn có của repo, nạp trong app, tối đa 32 MiB. Bàn phím ánh xạ gói từ tệp (mmap) thay vì đọc hết vào RAM, để gói lớn không vượt giới hạn bộ nhớ của keyboard extension. Tệp tải bằng Safari nằm ở **iCloud Drive › Tải về**: trong bộ chọn tệp, chạm ‹ về màn Duyệt rồi vào iCloud Drive (xem [docs/language-pack.md](../docs/language-pack.md#6-nạp-vào-bàn-phím)). Nạp/xoá VI và EN riêng. Không học EN; ẩn danh tắt ghi dữ liệu học. Xoá dữ liệu học không đụng các gói. Sao lưu iOS dùng JSON có phiên bản, không dùng ZIP Android: app xuất cài đặt (cấu hình, profile, gõ tắt, gói), ⚙ trên bàn phím xuất bản đầy đủ có cả từ đã học nhưng không kèm gói, để vừa bộ nhớ của bàn phím. Khôi phục kiểm tra dữ liệu và yêu cầu xác nhận trước khi thay thế.

## Kiểm thử

```sh
./gradlew :telex:test :app:testDebugUnitTest :app:lintDebug
./gradlew :telex:macosArm64Test
xcodebuild -project ios/TeleHex.xcodeproj -scheme TeleHex \
  -destination 'platform=iOS Simulator,id=SIMULATOR_UUID' \
  -derivedDataPath out/ios -parallel-testing-enabled NO \
  test -only-testing:TeleHexTests CODE_SIGNING_ALLOWED=NO
```

Corpus `telex/src/test/resources/golden.tsv` là nguồn duy nhất cho các ca gõ: Gradle tạo fixture cho common tests; Swift cũng đọc chính corpus đó. XCTest kiểm tra thêm Native Unicode, dữ liệu VI/EN, thay thế văn bản an toàn và hit testing vùng giao nhau giữa các hàng.

UI tests cần video. Dùng helper của môi trường khi có:

```sh
python3 ~/.claude/tools/ios_record_test.py --device SIMULATOR_UUID \
  --out test-recordings/YYYY-MM-DD-ios-foundation-typing.mp4 \
  --require 'Executed 1 test, with 0 failures' -- \
  xcodebuild -project ios/TeleHex.xcodeproj -scheme TeleHex \
  -destination 'platform=iOS Simulator,id=SIMULATOR_UUID' \
  -derivedDataPath out/ios -parallel-testing-enabled NO test \
  -only-testing:TeleHexUITests/KeyboardUITests/testVietnameseEnglishAndSettings CODE_SIGNING_ALLOWED=NO
```

Kịch bản `testSystemExtension` yêu cầu đã bật extension trong Settings. Preview nhúng chỉ bật ở build Debug qua launch argument `--embedded-preview` (thêm `--focus-field` để bàn phím hiện ngay, tiện chụp màn hình); app thông thường dùng bàn phím hệ thống. Taps trong app dùng `--record-touches`; test helpers ghi `TOUCH_TAP`/`TOUCH_SWIPE` để renderer vẽ điểm chạm bên ngoài app. Clip nằm ở root `test-recordings/`, được loại khỏi Git qua `git rev-parse --git-path info/exclude`.

## Ghi chú kỹ thuật

- Dùng Kotlin 2.3.21 trở lên: ma trận tương thích Xcode của Kotlin 2.2.x cũ hơn Xcode hiện dùng để build framework iOS. [Kotlin compatibility](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- Kịch bản dùng extension thật cần Simulator hiện bàn phím phần mềm: tắt I/O › Keyboard › Connect Hardware Keyboard rồi chạm lại ô nhập. Bật TeleHex qua app Cài đặt của Simulator; giá trị ô nhập được kiểm tra trong containing app vì extension chạy process riêng.
