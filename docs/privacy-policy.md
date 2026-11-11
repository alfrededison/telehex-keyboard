# Chính sách quyền riêng tư – TeleHex

*Cập nhật: 2026-11-11 · English below*

TeleHex là bàn phím tiếng Việt mã nguồn mở, do Hieu Nguyen Duc phát triển. Chính sách này áp dụng cho TeleHex trên Android và trên iOS/iPadOS (cùng mã `com.telehex.keyboard`). Phần nào chỉ đúng với một nền tảng thì có ghi rõ.

## Tóm tắt

TeleHex **không thu thập, không gửi và không chia sẻ** dữ liệu của bạn. Trên Android, ứng dụng không xin quyền Internet, nên về mặt kỹ thuật không thể gửi dữ liệu ra khỏi máy. Trên iOS, ứng dụng và bàn phím không có chức năng mạng nào. Không có quảng cáo, không có công cụ phân tích hay báo lỗi tự động, không cần tài khoản.

## Dữ liệu được xử lý và lưu trên máy

Là một bàn phím, TeleHex nhận các phím bạn gõ để chuyển Telex thành chữ có dấu và đưa ra gợi ý. Việc này diễn ra hoàn toàn trên thiết bị. Những dữ liệu sau được lưu trong bộ nhớ riêng của ứng dụng, các ứng dụng khác không đọc được. Trên iOS, ứng dụng TeleHex và bàn phím TeleHex dùng chung một App Group riêng để chuyển cài đặt từ ứng dụng sang bàn phím.

- **Cài đặt**: bố cục phím, profile, ký tự vuốt xuống, gõ tắt, giao diện và các tuỳ chọn khác.
- **Từ đã học**: âm tiết và từ bạn hay dùng, để gợi ý tốt hơn. Không học từ khi bật Chế độ ẩn danh. Trên Android cũng không học ở ô mật khẩu và ở ô mà ứng dụng yêu cầu không cá nhân hoá; trên iOS không học ở ô email, địa chỉ web, số và ô chỉ nhận chữ Latinh (iOS không cho bàn phím bên thứ ba gõ vào ô mật khẩu). Bạn có thể xoá dữ liệu đã học trong Cài đặt.
- **Độ lệch chạm đã học**: vị trí bạn hay chạm trên từng phím, để nhận phím chính xác hơn.
- **Clipboard history** (nếu bật): tối đa 30 mục thường; mục ghim giữ đến khi bạn xoá.
  - Android: mục thường bị xoá sau 1 giờ. Nội dung bạn sao chép ở bất kỳ ứng dụng nào trong lúc TeleHex là bàn phím đang dùng, kể cả khi bàn phím đang ẩn. Bỏ qua khi ô vừa nhập gần nhất là ô mật khẩu và khi Android đánh dấu nội dung là nhạy cảm.
  - iOS: chỉ hoạt động khi bạn bật Truy cập đầy đủ cho bàn phím và bật tuỳ chọn này. Bàn phím chỉ đọc clipboard khi bạn mở bảng clipboard, và iOS có thể hỏi quyền dán trước mỗi lần đọc. Mục thường bị xoá sau 1 giờ khi bàn phím mở ra lúc còn Truy cập đầy đủ; nếu bạn thu hồi quyền này, các mục đã lưu nằm yên trong bộ nhớ riêng của bàn phím cho tới khi bạn tắt tuỳ chọn. Tắt tuỳ chọn sẽ xoá hết.
- **Gói ngôn ngữ** (`.ngram`) mà bạn tự nạp.

Gỡ ứng dụng hoặc xoá dữ liệu ứng dụng sẽ xoá toàn bộ những dữ liệu trên.

## Sao lưu

- **Sao lưu thủ công**: khi bạn chọn Sao lưu, TeleHex ghi một file vào nơi **bạn chọn**. Bạn quyết định file đó đi đâu. Trên Android là file zip (cài đặt, từ đã học, các mục clipboard đã ghim). Trên iOS là file JSON: sao lưu trong ứng dụng gồm cài đặt, profile, gõ tắt và gói ngôn ngữ; sao lưu từ ⚙ trên bàn phím có thêm từ đã học. Bản sao lưu iOS không chứa clipboard history.
- **Sao lưu của Android**: nếu bạn bật sao lưu thiết bị, Android có thể tự sao lưu dữ liệu ứng dụng (trừ clipboard history) vào tài khoản Google của bạn, theo [chính sách của Google](https://policies.google.com/privacy). TeleHex không đọc được bản sao lưu này. Bạn có thể tắt trong Cài đặt Android › Hệ thống › Sao lưu.
- **Sao lưu của iOS**: bản sao lưu iCloud hoặc máy tính của iPhone, iPad có thể chứa dữ liệu ứng dụng, theo [chính sách của Apple](https://www.apple.com/legal/privacy/). TeleHex không đọc được bản sao lưu này.

## Quyền ứng dụng

Android:

- **Không có quyền Internet.**
- Danh sách ứng dụng có biểu tượng trên màn hình chính chỉ được đọc khi bạn chọn ứng dụng trong Cài đặt › Telex (tự bật chế độ tiếng Anh) hoặc Cài đặt › Tương thích (chế độ dự phòng). Danh sách này không được lưu hay gửi đi; chỉ tên gói của ứng dụng bạn chọn được lưu lại.

iOS:

- **Truy cập đầy đủ** (Full Access) không bắt buộc: gõ, gợi ý, từ đã học, profile và gõ tắt chạy đầy đủ khi không có quyền này. Khi bạn bật, TeleHex chỉ dùng nó cho âm phím, rung, clipboard history (nếu bật), để báo cho ứng dụng biết quyền đã bật, và để bàn phím ghi profile bạn đổi trên bàn phím (kéo đổi chỗ phím, đổi profile) vào App Group cho ứng dụng TeleHex thấy. iOS cảnh báo rằng bàn phím có Truy cập đầy đủ có thể gửi dữ liệu đi; TeleHex không có chức năng mạng nào để làm việc đó.
- Bàn phím không biết bạn đang gõ trong ứng dụng nào. Chế độ tiếng Anh tự bật chỉ dựa vào loại ô nhập (email, địa chỉ web, ô chỉ nhận chữ Latinh).

## Trẻ em

TeleHex không nhắm tới trẻ em dưới 13 tuổi và không thu thập dữ liệu của bất kỳ ai.

## Thay đổi

Mọi thay đổi chính sách sẽ được cập nhật tại trang này, kèm ngày cập nhật. Lịch sử thay đổi xem được trong kho mã nguồn.

## Liên hệ

Gửi câu hỏi qua [GitHub Issues](https://github.com/alfrededison/telehex-keyboard/issues).

---

# Privacy Policy – TeleHex

*Updated: 2026-11-11*

TeleHex is an open-source Vietnamese keyboard developed by Hieu Nguyen Duc. This policy covers TeleHex on Android and on iOS/iPadOS (both `com.telehex.keyboard`). Anything that applies to only one platform says so.

## Summary

TeleHex **does not collect, send or share** your data. On Android, the app does not request the Internet permission, so it technically cannot send data off your device. On iOS, the app and the keyboard contain no networking features. There are no ads, no analytics or crash-reporting tools and no account.

## Data processed and stored on your device

As a keyboard, TeleHex receives the keys you type to turn Telex into accented text and to make suggestions. This happens entirely on the device. The following is kept in the app's private storage, which other apps cannot read. On iOS, the TeleHex app and the TeleHex keyboard share a private App Group so the app can pass settings to the keyboard.

- **Settings**: key layouts, profiles, swipe-down characters, text shortcuts, theme and other options.
- **Learned words**: syllables and words you use often, for better suggestions. No words are learned while Incognito mode is on. On Android, none are learned in password fields or in fields whose app asks for no personalised learning either; on iOS, none are learned in email, web address, number or ASCII-only fields (iOS does not let third-party keyboards type into password fields). You can clear learned data in Settings.
- **Learned touch offsets**: where you tend to touch each key, for more accurate key detection.
- **Clipboard history** (when enabled): 30 regular items at most; pinned items stay until you remove them.
  - Android: regular items are deleted after 1 hour. Text you copy in any app while TeleHex is your active keyboard, even when the keyboard is hidden. Skipped when the last field you typed in is a password field and for content Android marks as sensitive.
  - iOS: works only when you allow Full Access for the keyboard and turn the option on. The keyboard reads the clipboard only when you open the clipboard panel, and iOS may ask for paste permission before each read. Regular items are deleted after 1 hour when the keyboard opens while it still has Full Access; if you revoke Full Access, saved items stay in the keyboard's private storage until you turn the option off. Turning the option off deletes everything.
- **Language packs** (`.ngram`) you import yourself.

Uninstalling the app or clearing its data deletes all of the above.

## Backups

- **Manual backup**: when you choose Back up, TeleHex writes a file to a location **you choose**. You decide where that file goes. On Android it is a zip file (settings, learned words, pinned clipboard items). On iOS it is a JSON file: a backup from the app holds settings, profiles, text shortcuts and language packs; a backup from ⚙ on the keyboard also holds learned words. iOS backups never contain clipboard history.
- **Android backup**: if you turn on device backup, Android may back up app data (except clipboard history) to your Google account, under [Google's policy](https://policies.google.com/privacy). TeleHex cannot read that backup. You can turn it off in Android Settings › System › Backup.
- **iOS backup**: iCloud or computer backups of your iPhone or iPad may include app data, under [Apple's policy](https://www.apple.com/legal/privacy/). TeleHex cannot read that backup.

## Permissions

Android:

- **No Internet permission.**
- The list of apps with a home-screen icon is read only when you pick apps in Settings › Telex (start in English) or Settings › Compatibility (fallback mode). The list is not stored or sent; only the package names of the apps you pick are saved.

iOS:

- **Full Access** is optional: typing, suggestions, learned words, profiles and text shortcuts all work without it. When you allow it, TeleHex uses it only for key sounds, haptics, clipboard history (when enabled), to tell the app that Full Access is on, and to let the keyboard write profile changes made on the keyboard (dragging keys, switching profiles) to the App Group so the TeleHex app can show them. iOS warns that a keyboard with Full Access could send data; TeleHex has no networking feature to do so.
- The keyboard does not know which app you are typing in. English mode turns on by itself based only on the field type (email, web address, ASCII-only).

## Children

TeleHex is not directed at children under 13 and collects no one's data.

## Changes

Any change to this policy is published on this page with its update date. Its history is visible in the source repository.

## Contact

Ask questions through [GitHub Issues](https://github.com/alfrededison/telehex-keyboard/issues).
