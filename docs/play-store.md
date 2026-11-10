# Phát hành lên Google Play

TeleHex lên Play bằng **chính key dự án** đang ký APK trên GitHub (chứng chỉ ghim ở `signing/release-cert.sha256`). Google giữ key này qua Play App Signing và dùng nó ký bản phát cho người dùng. Vì cùng chữ ký, bản cài từ Play và từ GitHub cập nhật đè lên nhau được, không phải gỡ app hay mất dữ liệu.

Key này cũng là upload key: file AAB tải lên Play được ký bằng đúng key đó, nên CI không cần thêm secret ký nào.

## Repo đã có gì

| Thành phần | Nơi |
|---|---|
| AAB ký bằng key dự án + `mapping.txt` (R8) | `tools/release-build.sh` tạo `out/TeleHex.aab`, `out/mapping.txt` mỗi khi ký bằng key dự án, và kiểm tra chứng chỉ của AAB theo ghim |
| Build AAB trên máy | `RELEASE=1 tools/signed-build.sh` (đọc key trong `.signing/`) |
| Tải AAB lên Play khi push tag | `.github/workflows/release.yml`, chỉ chạy khi có secret `PLAY_SERVICE_ACCOUNT_JSON`; luôn đính AAB vào artifact `TeleHex-release-play-bundle` |
| "Có gì mới" (≤ 500 ký tự) | `tools/play_whatsnew.py` rút từ mục CHANGELOG của tag |
| Tiêu đề, mô tả ngắn, mô tả đầy đủ (vi, en-US) | `fastlane/metadata/android/<ngôn ngữ>/` |
| Icon 512, feature graphic 1024×500, ảnh chụp 1080×1920 | `fastlane/metadata/android/vi/images/`; ảnh chụp dựng từ `tools/store/screens.html` bằng `python3 tools/store_screens.py` (cần Python Playwright, Google Chrome, ffmpeg), xuất thẳng `phoneScreenshots/1.png`, `2.png`… theo thứ tự trên trang |
| Chính sách quyền riêng tư | [privacy-policy.md](privacy-policy.md) |

## 1. Tạo app trong Play Console

Play Console › **Create app**:

- App name: `TeleHex – Bàn phím Telex`; ngôn ngữ mặc định: **Tiếng Việt – vi**.
- Package name: `com.telehex.keyboard` (đúng `applicationId` trong `app/build.gradle.kts`; không đổi được sau khi tạo).
- App; Free.

Ngôn ngữ mặc định phải là `vi` (hoặc có thêm bản dịch `vi`), vì CI gửi "Có gì mới" dưới mã `vi`.

## 2. Đưa key dự án cho Play App Signing

Làm **trước** khi tải AAB đầu tiên. Nếu tải AAB lên trước, Play sẽ tự tạo key mới.

Mở **Protected with Play › Play Store protection › Manage Play app signing** (trước đây là App integrity). Nếu không thấy, gõ "app signing" vào ô tìm kiếm của Play Console. Chọn **Change key / Change app signing key**, rồi chọn **Export and upload a key from Java keystore**. Lúc tạo bản phát hành đầu tiên, trang Create release cũng có liên kết đổi key ký.

1. Tải `pepk.jar` và file khoá công khai mã hoá (`encryption_public_key.pem`) từ trang đó vào `.signing/` (thư mục đã gitignore).
2. Xem alias và mật khẩu trong `.signing/credentials.json` (`alias`, `store_password`, `key_password`).
3. Chạy ở gốc repo:

   ```sh
   java -jar .signing/pepk.jar \
     --keystore=.signing/telehex-release.p12 \
     --alias=<alias> \
     --output=.signing/play-signing-key.zip \
     --include-cert \
     --rsa-aes-encryption \
     --encryption-key-path=.signing/encryption_public_key.pem
   ```

   PEPK hỏi mật khẩu keystore rồi mật khẩu key.
4. Tải `.signing/play-signing-key.zip` lên. **Không** đăng ký upload key riêng.
5. Kiểm tra: trên cùng trang Manage Play app signing, mục App signing key certificate › SHA-256 phải trùng `signing/release-cert.sha256` (Console in hoa và có dấu `:`, so phần chữ số).

Chỉ chọn được key ký **một lần, trước khi có bản phát hành đầu tiên**. Nếu lỡ để Google tạo key mới thì bản Play và bản GitHub sẽ khác chữ ký vĩnh viễn.

## 3. Tải AAB đầu tiên bằng tay

Google Play API chưa nhận upload cho app chưa từng có AAB nào, nên lần đầu phải tải trong Console:

- Phát hành bản tiếp theo như thường (`tools/release.sh`, push tag). Chưa có secret Play thì workflow bỏ qua bước tải lên, nhưng vẫn đính `TeleHex.aab` và `mapping.txt` vào artifact `TeleHex-release-play-bundle` của lần chạy đó. Tag cũ hơn 0.7.2 chưa build AAB.
- Đừng tải lên một AAB tự build trên máy bằng `RELEASE=1` từ `main` chưa tag: nó mang versionCode của bản release sắp tới, và Play không nhận lại cùng versionCode khi tag đó được build.
- **Test and release › Testing › Internal testing › Create new release**, tải `TeleHex.aab` lên (và `mapping.txt` ở App bundle explorer › Downloads nếu muốn).

Mỗi lần tải lên phải có versionCode lớn hơn lần trước. Số versionCode của bản release (`X·10^7 + Y·10^5 + Z·10^3 + 999`) luôn tăng theo phiên bản, nên chỉ cần tải các tag theo thứ tự.

## 4. Điền thông tin app

Play Console đổi menu thường xuyên. Cách chắc chắn nhất là vào **Dashboard** của app, mục **Set up your app**: mỗi việc dưới đây có một dòng dẫn thẳng tới form. Hoặc gõ tên form vào ô tìm kiếm của Console.

**Main store listing** (thường ở Grow users › Store presence; chép từ `fastlane/metadata/android/`):

| Ô | File |
|---|---|
| App name | `vi/title.txt` (≤ 30 ký tự) |
| Short description | `vi/short_description.txt` (≤ 80) |
| Full description | `vi/full_description.txt` (≤ 4000) |
| App icon | `vi/images/icon.png` (512×512) |
| Feature graphic | `vi/images/featureGraphic.png` (1024×500) |
| Phone screenshots | `vi/images/phoneScreenshots/1.png`, `2.png`… (1080×1920, tải đủ và đúng thứ tự; đổi ảnh thì tải lại cả bộ) |
| Video | URL YouTube của video giới thiệu (Public hoặc Unlisted, không bật quảng cáo, không giới hạn độ tuổi) |

Thêm bản dịch **English (United States) – en-US** từ `en-US/` (ảnh dùng chung). Category: **Tools**. Contact: email nhà phát triển; website: `https://github.com/alfrededison/telehex-keyboard`.

**App content** (thường ở Monitor and improve › Policy and programs; cũng chính là các việc trong Set up your app):

| Mục | Trả lời |
|---|---|
| Privacy policy | `https://github.com/alfrededison/telehex-keyboard/blob/main/docs/privacy-policy.md` |
| App access | All functionality is available without special access |
| Ads | No, my app does not contain ads |
| Content rating | Bảng hỏi IARC, loại "All other app types": trả lời **No** cho mọi câu (bạo lực, nội dung người lớn, cờ bạc, người dùng tương tác/chia sẻ nội dung, chia sẻ vị trí, mua hàng trong app…). Kết quả thường là 3+ / Everyone |
| Target audience | Chỉ chọn nhóm từ **13 tuổi trở lên** (chọn dưới 13 sẽ phải theo chính sách Families) |
| Data safety | Collect or share any required user data types: **No**. Dữ liệu chỉ nằm trên máy, app không có quyền Internet, không có SDK bên thứ ba |
| Government apps, Financial features, Health, News | Không phải |

Không cần khai báo quyền nhạy cảm: app không dùng `QUERY_ALL_PACKAGES` (chỉ `<queries>` cho ứng dụng có biểu tượng launcher), không dùng Accessibility.

## 5. Closed testing, rồi Production

Tài khoản cá nhân tạo sau 13/11/2023 phải chạy **closed testing với ít nhất 12 người thử đã opt-in, liên tục 14 ngày**, rồi mới được xin quyền Production (Dashboard › Apply for production). Kiểm tra lại con số trên Dashboard vì Google có thể đổi.

1. **Testing › Closed testing** › tạo track (mặc định tên `alpha` trong API) › thêm danh sách email người thử (Google Group hoặc email list).
2. Đẩy bản lên track này (promote từ Internal testing, hoặc đặt `PLAY_TRACK=alpha` ở mục 6).
3. Gửi link opt-in cho người thử; họ phải bấm tham gia và cài từ Play.
4. Đủ 14 ngày thì trả lời bảng hỏi xin Production. Lần duyệt đầu thường mất vài ngày.

## 6. Tự động tải lên khi push tag

1. Google Cloud Console: tạo (hoặc chọn) project, bật **Google Play Android Developer API**.
2. IAM › Service accounts › tạo service account, không cần role Cloud nào; Keys › Add key › JSON, tải về.
3. Play Console › **Users and permissions** › Invite new users › email của service account. Quyền cho app TeleHex: **Release to testing tracks** (thêm **Release to production…** nếu muốn CI đẩy thẳng Production), **View app information**.
4. Lưu khoá vào GitHub rồi xoá file JSON trên máy:

   ```sh
   gh secret set PLAY_SERVICE_ACCOUNT_JSON < ~/Downloads/<service-account>.json
   rm ~/Downloads/<service-account>.json
   ```

5. Tuỳ chọn, biến của repo (không phải secret):

   ```sh
   gh variable set PLAY_TRACK --body alpha              # internal (mặc định) | alpha | beta | production
   gh variable set PLAY_RELEASE_STATUS --body completed  # draft (mặc định) | completed
   ```

   Khi app còn ở trạng thái nháp (chưa từng phát hành), Play chỉ nhận `draft`; giữ mặc định đến khi bản đầu tiên được duyệt. Muốn phát hành dần theo tỉ lệ người dùng thì để `draft` rồi chọn tỉ lệ trong Play Console.

   Nếu bước tải lên Play lỗi, sửa nguyên nhân rồi bấm **Re-run jobs**: bước tạo release nháp trên GitHub tự bỏ qua khi release của tag đã có.

Sau đó `tools/release.sh` và `git push --atomic origin main vX.Y.Z` như cũ: workflow `release` vẫn tạo release nháp trên GitHub, rồi tải AAB, `mapping.txt` và "Có gì mới" lên track đã chọn. Bản dev (`X.Y.Z-dev.N`) không lên Play.

Xem trước "Có gì mới" của một tag:

```sh
python3 tools/play_whatsnew.py v0.7.1 out/whatsnew && cat out/whatsnew/whatsnew-vi
```
