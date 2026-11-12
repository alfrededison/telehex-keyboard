# Phát hành lên App Store

Bản iOS/iPadOS dùng bundle ID `com.telehex.keyboard` (trùng `applicationId` Android), keyboard extension `com.telehex.keyboard.extension`, App Group `group.com.telehex.keyboard.shared`. Cách build và ký xem [ios/README.md](../ios/README.md).

## Repo đã có gì

| Thành phần | Nơi |
|---|---|
| Tên, phụ đề, văn bản quảng bá, mô tả, từ khoá, URL (vi, en-US) | `fastlane/metadata/ios/<ngôn ngữ>/` theo định dạng `fastlane deliver` |
| Bản quyền, danh mục | `fastlane/metadata/ios/copyright.txt`, `primary_category.txt`, `secondary_category.txt` |
| Ghi chú cho App Review | `fastlane/metadata/ios/review_information/notes.txt` |
| Ảnh chụp iPhone 6.9" và iPad 13" | `fastlane/screenshots/ios/vi/iphone-<n>.png`, `ipad-<n>.png`, dựng từ `tools/store/ios-screens.html` bằng `python3 tools/store_ios_screens.py` (ảnh gốc chụp từ Simulator ở `tools/store/ios-raw/`) |
| Đẩy toàn bộ thông tin và ảnh lên App Store Connect | `python3 ios/scripts/asc_metadata.py` |
| Archive, ký, tải lên TestFlight | `ios/scripts/release.sh` |
| Chính sách quyền riêng tư (Android và iOS) | [privacy-policy.md](privacy-policy.md) |

Metadata iOS viết riêng, không chép từ `fastlane/metadata/android/`: Apple từ chối metadata nhắc tới nền tảng khác (Guideline 2.3.10), và một số tính năng Android không có trên iOS (quyền Internet, tự bật tiếng Anh theo ứng dụng, cảnh báo bàn phím của Android). Sửa mô tả bên này thì xem lại bên kia.

Nếu dùng `fastlane deliver`, trỏ `metadata_path` vào `fastlane/metadata/ios`.

## 1. Đăng ký App ID và App Group

Làm một lần trên developer.apple.com › Certificates, Identifiers & Profiles. Tài khoản Individual chỉ chủ tài khoản vào được trang này.

1. Identifiers › **+** › App IDs: `com.telehex.keyboard` và `com.telehex.keyboard.extension`, bật **App Groups** cho cả hai.
2. Identifiers › **+** › App Groups: `group.com.telehex.keyboard.shared`.
3. Mở từng App ID ở bước 1 › App Groups › **Configure** › chọn `group.com.telehex.keyboard.shared` › Save.

Khoá API App Store Connect tạo được App ID và bật capability (bước 1), nhưng không tạo hay gán được App Group (bước 2–3): API không có resource này, và `xcodebuild -allowProvisioningUpdates` dùng khoá API sẽ lỗi 401 ở `appGroups`.

## 2. Tạo app trong App Store Connect

Apps › **+** › New App (đã tạo):

| Ô | Giá trị |
|---|---|
| Platforms | iOS (bản iPad dùng chung, project build cho iPhone và iPad) |
| Name | `vi/name.txt` |
| Primary Language | Vietnamese |
| Bundle ID | `com.telehex.keyboard` |
| SKU | `TELEHEX-KEYBOARD` (nội bộ, không đổi được) |
| User Access | Limited Access |

Ngôn ngữ chính phải là tiếng Việt: chỉ bộ ảnh `vi` được tải lên, bản en-US dùng lại ảnh của ngôn ngữ chính.

## 3. Điền thông tin app

Sửa file trong `fastlane/metadata/ios/` hoặc ảnh, rồi chạy:

```sh
python3 ios/scripts/asc_metadata.py                   # thêm --no-screenshots để bỏ qua ảnh
```

Script dùng khoá API trong `ios/Config/Signing.local.xcconfig` và chạy lại bao nhiêu lần cũng được (chỉ gửi phần khác, ảnh trùng checksum thì giữ). Nó điền:

| Mục trên App Store Connect | Nguồn / giá trị |
|---|---|
| App Information › Name, Subtitle, Privacy Policy URL (vi, en-US) | `<ngôn ngữ>/name.txt`, `subtitle.txt`, `privacy_url.txt` |
| Category | Utilities, phụ Productivity |
| Age Rating | mọi câu None/No, ra 4+ |
| Phiên bản › số phiên bản | `MARKETING_VERSION` trong `ios/project.json` (phải khớp build) |
| Promotional Text, Description, Keywords, Support URL, Marketing URL | `promotional_text.txt` (≤ 170, đổi không cần duyệt lại), `description.txt` (≤ 4000, không emoji), `keywords.txt` (≤ 100 byte, không lặp chữ trong tên), `support_url.txt`, `marketing_url.txt` |
| What's New | `<ngôn ngữ>/release_notes.txt`, từ phiên bản thứ hai |
| Copyright | `copyright.txt` |
| Screenshots | `fastlane/screenshots/ios/<ngôn ngữ>/` |
| App Review Information | Notes: `review_information/notes.txt`, không cần tài khoản demo; người liên hệ lấy từ `REVIEW_FIRST_NAME`, `REVIEW_LAST_NAME`, `REVIEW_EMAIL`, `REVIEW_PHONE` trong `Signing.local.xcconfig` (không commit thông tin cá nhân) |
| Pricing and Availability | miễn phí, mọi quốc gia; chỉ đặt khi app chưa có giá |
| Build | build mới nhất cùng số phiên bản đã xử lý xong |

API không làm được, phải làm trên web:

- **App Privacy** › Data Collection › **No, we do not collect data from this app**. Dữ liệu chỉ nằm trên máy, không có SDK bên thứ ba, không có code mạng.
- **App Information › Content Rights**: app có dữ liệu bên thứ ba (từ điển, danh sách emoji CLDR) dùng theo giấy phép ghi trong app.
- Encryption không cần: `ITSAppUsesNonExemptEncryption = NO` đã khai trong Info.plist.

## 4. Quy định riêng cho bàn phím

Guideline 4.4.1 (keyboard extension). Đã đáp ứng, nêu trong ghi chú App Review:

- Gõ chạy đầy đủ khi không có Full Access; Full Access chỉ dùng cho âm phím, rung, clipboard history, kéo đổi chỗ phím trên bàn phím và báo cho app biết quyền đã bật.
- Có nhập số và thập phân (trang 123, dialpad cho ô số).
- Không có code mạng, không gửi nội dung gõ đi đâu.
- Mô tả nói rõ tính năng nào cần Full Access; ảnh chụp chỉ dùng tính năng không cần quyền này.

Sửa chỗ dùng Full Access thì cập nhật cả mô tả, ghi chú App Review và chính sách quyền riêng tư.

## 5. Tải bản lên TestFlight

```sh
ios/scripts/release.sh
```

Đặt Team ID và khoá API trong `ios/Config/Signing.local.xcconfig` như [ios/README.md](../ios/README.md#ký-và-phát-hành). Ký tự động bằng khoá API vẫn lỗi 401 ở `appGroups` dù group đã gán, nên script ký thủ công bằng chứng chỉ và profile App Store tạo qua API (`ios/scripts/asc_signing.py`).

Nhóm **Nội bộ** (thành viên App Store Connect của team) nhận mọi build ngay, không cần duyệt.

### TestFlight công khai

```sh
python3 ios/scripts/asc_testflight.py
```

Script đưa build tải lên gần nhất vào nhóm External **Công khai** có public link (`https://testflight.apple.com/join/gvyHeek6`), và chạy lại bao nhiêu lần cũng được:

| Mục | Nguồn |
|---|---|
| Test Information › Beta App Description | `<ngôn ngữ>/testflight_description.txt` |
| Feedback Email | `TESTFLIGHT_FEEDBACK_EMAIL` trong `Signing.local.xcconfig`, mặc định là email liên hệ App Review (người thử thấy email này) |
| Privacy Policy URL, Marketing URL | `privacy_url.txt`, `marketing_url.txt` |
| Beta App Review Information | người liên hệ lấy từ phần App Review của phiên bản App Store; ghi chú `review_information/notes.txt` |
| What to Test (mỗi build) | `<ngôn ngữ>/testflight_what_to_test.txt` |
| Giới hạn số người qua link | `TESTFLIGHT_PUBLIC_LIMIT` (bỏ trống là không giới hạn, tối đa 10.000) |

Nhóm **Nội bộ** tự nhận mọi build, còn nhóm **Công khai** chỉ nhận build qua script này: mỗi lần `release.sh` xong phải chạy lại. Build vừa tải lên còn đang xử lý thì script đợi tới khi xong (tối đa 30 phút). Ngay sau khi tải lên, build có thể vài phút sau mới hiện trong API; lúc đó script vẫn thấy build trước, nên đợi vài phút rồi hẵng chạy, và kiểm tra dòng "thêm build N" có đúng số build mới.

Build đầu của mỗi phiên bản phải qua Beta App Review (thường dưới 1 ngày); public link chỉ mở khi đã duyệt. Build sau của cùng phiên bản thường được duyệt tự động. Không dùng emoji trong các file này: App Store Connect từ chối emoji ở mô tả.

## 6. Gửi App Review

1. Chạy lại `python3 ios/scripts/asc_metadata.py --no-screenshots` để gắn build mới nhất đã xử lý xong vào phiên bản. Build TestFlight hết hạn sau 90 ngày hoặc khi bị expire thủ công; phiên bản gắn build hết hạn thì không gửi được.
2. Làm xong hai mục chỉ có trên web ở bước 3 (App Privacy, Content Rights).
3. Trang phiên bản › **Add for Review** › **Submit to App Review**.
4. Phiên bản đặt phát hành thủ công: duyệt xong thì bấm **Release This Version**.
