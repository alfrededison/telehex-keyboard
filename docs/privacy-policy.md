# Chính sách quyền riêng tư – TeleHex

*Cập nhật: 2026-11-10 · English below*

TeleHex là bàn phím tiếng Việt mã nguồn mở, do Hieu Nguyen Duc phát triển. Chính sách này áp dụng cho ứng dụng Android TeleHex (`com.telehex.keyboard`).

## Tóm tắt

TeleHex **không thu thập, không gửi và không chia sẻ** dữ liệu của bạn. Ứng dụng không xin quyền Internet, nên về mặt kỹ thuật không thể gửi dữ liệu ra khỏi máy. Không có quảng cáo, không có công cụ phân tích hay báo lỗi tự động, không cần tài khoản.

## Dữ liệu được xử lý và lưu trên máy

Là một bàn phím, TeleHex nhận các phím bạn gõ để chuyển Telex thành chữ có dấu và đưa ra gợi ý. Việc này diễn ra hoàn toàn trên thiết bị. Những dữ liệu sau được lưu trong bộ nhớ riêng của ứng dụng, các ứng dụng khác không đọc được:

- **Cài đặt**: bố cục phím, profile, ký tự vuốt xuống, gõ tắt, giao diện và các tuỳ chọn khác.
- **Từ đã học**: âm tiết và từ bạn hay dùng, để gợi ý tốt hơn. Không học từ ở ô mật khẩu, ở ô mà ứng dụng yêu cầu không cá nhân hoá, và khi bật Chế độ ẩn danh. Bạn có thể xoá dữ liệu đã học trong Cài đặt.
- **Độ lệch chạm đã học**: vị trí bạn hay chạm trên từng phím, để nhận phím chính xác hơn.
- **Clipboard history** (nếu bật): nội dung bạn sao chép ở bất kỳ ứng dụng nào trong lúc TeleHex là bàn phím đang dùng, kể cả khi bàn phím đang ẩn. Bỏ qua khi ô vừa nhập gần nhất là ô mật khẩu và khi Android đánh dấu nội dung là nhạy cảm. Mục thường bị xoá sau 1 giờ (tối đa 30 mục); mục ghim giữ đến khi bạn xoá.
- **Gói ngôn ngữ** (`.ngram`) mà bạn tự nạp.

Gỡ ứng dụng hoặc xoá dữ liệu ứng dụng sẽ xoá toàn bộ những dữ liệu trên.

## Sao lưu

- **Sao lưu thủ công**: khi bạn chọn Sao lưu trong Cài đặt, TeleHex ghi một file zip (cài đặt, từ đã học, các mục clipboard đã ghim) vào nơi **bạn chọn**. Bạn quyết định file đó đi đâu.
- **Sao lưu của Android**: nếu bạn bật sao lưu thiết bị, Android có thể tự sao lưu dữ liệu ứng dụng (trừ clipboard history) vào tài khoản Google của bạn, theo [chính sách của Google](https://policies.google.com/privacy). TeleHex không đọc được bản sao lưu này. Bạn có thể tắt trong Cài đặt Android › Hệ thống › Sao lưu.

## Quyền ứng dụng

- **Không có quyền Internet.**
- Danh sách ứng dụng có biểu tượng trên màn hình chính chỉ được đọc khi bạn chọn ứng dụng trong Cài đặt › Telex (tự bật chế độ tiếng Anh) hoặc Cài đặt › Tương thích (chế độ dự phòng). Danh sách này không được lưu hay gửi đi; chỉ tên gói của ứng dụng bạn chọn được lưu lại.

## Trẻ em

TeleHex không nhắm tới trẻ em dưới 13 tuổi và không thu thập dữ liệu của bất kỳ ai.

## Thay đổi

Mọi thay đổi chính sách sẽ được cập nhật tại trang này, kèm ngày cập nhật. Lịch sử thay đổi xem được trong kho mã nguồn.

## Liên hệ

Gửi câu hỏi qua [GitHub Issues](https://github.com/alfrededison/telehex-keyboard/issues).

---

# Privacy Policy – TeleHex

*Updated: 2026-11-10*

TeleHex is an open-source Vietnamese keyboard developed by Hieu Nguyen Duc. This policy covers the TeleHex Android app (`com.telehex.keyboard`).

## Summary

TeleHex **does not collect, send or share** your data. The app does not request the Internet permission, so it technically cannot send data off your device. There are no ads, no analytics or crash-reporting tools and no account.

## Data processed and stored on your device

As a keyboard, TeleHex receives the keys you type to turn Telex into accented text and to make suggestions. This happens entirely on the device. The following is kept in the app's private storage, which other apps cannot read:

- **Settings**: key layouts, profiles, swipe-down characters, text shortcuts, theme and other options.
- **Learned words**: syllables and words you use often, for better suggestions. No words are learned in password fields, in fields whose app asks for no personalised learning, or while Incognito mode is on. You can clear learned data in Settings.
- **Learned touch offsets**: where you tend to touch each key, for more accurate key detection.
- **Clipboard history** (when enabled): text you copy in any app while TeleHex is your active keyboard, even when the keyboard is hidden. Skipped when the last field you typed in is a password field and for content Android marks as sensitive. Regular items are deleted after 1 hour (30 items at most); pinned items stay until you remove them.
- **Language packs** (`.ngram`) you import yourself.

Uninstalling the app or clearing its data deletes all of the above.

## Backups

- **Manual backup**: when you choose Back up in Settings, TeleHex writes a zip file (settings, learned words, pinned clipboard items) to a location **you choose**. You decide where that file goes.
- **Android backup**: if you turn on device backup, Android may back up app data (except clipboard history) to your Google account, under [Google's policy](https://policies.google.com/privacy). TeleHex cannot read that backup. You can turn it off in Android Settings › System › Backup.

## Permissions

- **No Internet permission.**
- The list of apps with a home-screen icon is read only when you pick apps in Settings › Telex (start in English) or Settings › Compatibility (fallback mode). The list is not stored or sent; only the package names of the apps you pick are saved.

## Children

TeleHex is not directed at children under 13 and collects no one's data.

## Changes

Any change to this policy is published on this page with its update date. Its history is visible in the source repository.

## Contact

Ask questions through [GitHub Issues](https://github.com/alfrededison/telehex-keyboard/issues).
