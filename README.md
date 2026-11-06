# TeleHex – Bàn phím Telex tổ ong cho Android

Bàn phím tiếng Việt gõ **Telex** trên lưới **lục giác đều 7 cột**. Giữ tinh thần QWERTY (Q-W-E-R-T-Y đọc zigzag giữa hai hàng trên) nhưng phím to hơn ~45% so với bàn phím 10 cột. Thao tác phụ (xoá, chữ hoa, ký tự phụ, con trỏ, chọn, dán…) chuyển sang cử chỉ vuốt, nên lưới chữ không có phím backspace hay shift.

```
  W   R   Y   ⚙   U   O  123
Q   E   T   F   H   I   P
  A   S   D   G   J   K   L
Z   X   C   V   B   N   M
  ☺   ,  \___cách___/  .   ↵
```

## Quyền riêng tư

**Ứng dụng không xin quyền INTERNET.** Không có dữ liệu gõ nào được gửi đi đâu; mọi xử lý (Telex, gợi ý, học từ người dùng, clipboard) nằm trên máy, trong bộ nhớ riêng của app.

## Bật bàn phím

1. Cài APK từ [Releases](../../releases) (hoặc build như dưới).
2. Mở app TeleHex → "Bật TeleHex trong Cài đặt hệ thống" → bật TeleHex.
3. "Chọn TeleHex làm bàn phím hiện tại" (hoặc chạm biểu tượng bàn phím ở thanh điều hướng khi đang gõ).

## Cử chỉ

| Cử chỉ | Hành vi |
|---|---|
| Chạm phím chữ | Gõ Telex (`tieengs vieetj` → tiếng việt) |
| Vuốt **lên** phím chữ | Đảo hoa/thường như Gboard: nhãn phím đang thường thì ra chữ hoa (giữ hoa khi biến đổi: vuốt lên D rồi gõ d → Đ), đang hoa (đầu câu) thì ra chữ thường |
| Vuốt **xuống** phím chữ / `,` / `.` | Ký tự phụ hiện nhỏ phía dưới chữ (số, emoji, ký hiệu; tuỳ biến từng phím trong Cài đặt) |
| Chạm phím ↵ | Ô có hành động (Tìm, Gửi…) thì làm hành động đó, ngược lại xuống dòng |
| Chạm phím cách | Chốt âm tiết + khoảng trắng; áp dụng gõ tắt nếu khớp |
| Vuốt **trái ngắn** trên phím cách | Xoá 1 ký tự (xoá theo grapheme, emoji ghép xoá một lần) |
| Vuốt **trái dài** trên phím cách | Chọn-để-xoá theo từ: bôi đen xem trước, mỗi ~1 phím thêm 1 từ, kéo ngược để bớt, nhả để xoá; mỗi dấu xuống dòng là một bước nên kéo tiếp được lên dòng trên; kéo quá mép trái = tới đầu dòng |
| **Giữ** phím cách ≥ 300 ms rồi kéo | Di con trỏ (ngang theo ký tự, dọc theo dòng); bàn phím mờ đi |
| Chạm đúp phím cách, giữ lần hai rồi kéo | Chọn văn bản |
| Vuốt **phải** trên phím cách | Dán |
| Có vùng chọn: vuốt lên / vuốt trái (ngắn hay dài) trên phím cách | Chép / Cắt |
| Hai ngón vuốt trái / phải | Undo / Redo (bàn phím tự quản lý lịch sử) |
| Hai ngón giữ | Chọn hết |
| Phím 1234 ở lớp ký tự (hoặc giữ phím 123) | Dialpad (tự mở ở ô số điện thoại / số / ngày giờ) |
| Giữ phím ⚙ | Bảng sửa văn bản + clipboard history |
| Chạm ⚙ | Cài đặt |

Mọi ngưỡng (vuốt, giữ, bước xoá từ, chạm đúp) chỉnh được trong Cài đặt → Cử chỉ.

## Thanh gợi ý (không autocorrect)

Bàn phím **không bao giờ tự đổi chữ** đã gõ. Thanh trên bàn phím hiện:

- 6 biến thể dấu của âm tiết đang soạn (mới / mởi / mời / mỡi / mợi / mơi), chạm để chốt âm tiết với dấu đó kèm khoảng trắng;
- gợi ý sửa khi âm tiết chưa hợp lệ (phím lân cận trên lưới lục giác, thiếu/thừa/đảo phím, quên phím Telex), chữ đang gõ luôn giữ nguyên ở ô đầu;
- gợi ý âm tiết tiếp theo sau khi chạm cách (học từ chính bạn; giữ một gợi ý để xoá khỏi từ điển cá nhân);
- phương án sửa cho từ đã gõ khi đặt con trỏ vào từ;
- emoji theo từ khoá (gõ "cười" → 😂).

Chạm một gợi ý thì chèn luôn kèm khoảng trắng; gõ dấu câu (`. , ! ? : ; … ) ] }`) ngay sau đó thì khoảng trắng này bị xoá ("Tối" rồi `,` → "Tối,").

Chuỗi phím không thể là tiếng Việt thì hiện đúng phím đã gõ (`windows` không thành `ưindows`) và được tô nhạt.

### Gói ngôn ngữ (tuỳ chọn)

Gợi ý âm tiết tiếp theo dùng thêm một mô hình n-gram âm tiết nếu có. APK không đóng gói sẵn mô hình, và app không có quyền INTERNET nên không tự tải. Bạn tự dựng từ corpus tiếng Việt rồi nạp trong Cài đặt → "Nạp gói ngôn ngữ (.ngram)…". Hướng dẫn đầy đủ gồm nguồn corpus và giấy phép, cách tải, làm sạch, dựng, đo chất lượng và nạp: [docs/language-pack.md](docs/language-pack.md).

## Tuỳ biến

- **Chỉnh sửa phím**: kéo một phím thả lên phím khác để đổi chỗ; phím cách và ↵ cố định. Màn này có 3 chế độ: **Chữ**, **Vuốt xuống**, **123**; mỗi lớp đổi chỗ riêng, hai lớp còn lại đứng yên theo ô. Ở chế độ 123, đang kéo thì giữ ngón trên phím 1/2 để sang trang kia. Số 1…0 mặc định ở ô của Q W R Y F H U O P L (cả lớp 123 lẫn vuốt xuống) và kéo được như mọi ký tự khác.
- **Profile**: mỗi profile giữ đủ ba lớp Chữ, Vuốt xuống, 123. Màn chỉnh sửa luôn sửa profile đang dùng; Lưu là áp ngay cho bàn phím. Menu ⋮ có Tạo mới, Nhân bản (gồm cả thay đổi chưa lưu), Đổi tên, Xoá, Bỏ thay đổi. Có sẵn "Mặc định" và "Cân bằng hai ngón" (chia đều tải cho hai ngón cái, tăng gõ luân phiên); hai profile này khoá 🔒, sửa thì lưu thành profile mới. Đổi nhanh profile ngay trong Cài đặt › Bàn phím.
- **Ký tự vuốt xuống** cho từng ô phím: số, ký hiệu, emoji hoặc đoạn văn bản ngắn (≤ 8 ký tự). Ở chế độ **Vuốt xuống** của màn chỉnh sửa phím, chạm một phím để chọn ký tự, kéo để đổi chỗ.
- Kiểu đặt dấu cũ/mới (hòa/hoà), "L cũng là dấu huyền" (`lal` → là), tự viết hoa đầu câu, rung, âm thanh, giao diện sáng/tối, màu nhấn, chế độ một tay, tông da emoji, gõ tắt, clipboard history, chế độ ẩn danh, vùng chạm động (tắt mặc định), học độ lệch chạm.
- **Sao lưu / khôi phục** mọi thứ ra một file zip.
- **Giọng nói**: nút 🎤 trong bảng sửa văn bản chuyển sang IME giọng nói đã bật (ví dụ FUTO Voice Input chạy offline).

## Build

```
./gradlew :telex:test        # engine Telex thuần Kotlin + bộ test vàng ~400 ca
./gradlew :app:assembleDebug # APK debug (có activity "TeleHex test" để thử nhanh)
tools/release-build.sh       # APK release đã minify ở out/TeleHex.apk + .sha256 (X.Y.Z-dev.N)
```

Yêu cầu JDK 17+, Android SDK 36.

## Phát hành

- **Phiên bản chỉ viết ở một nơi**: dòng `appVersion` trong `app/build.gradle.kts`. `tools/version.sh` tính versionCode cố định từ X.Y.Z: bản release là `X·10^7 + Y·10^5 + Z·10^3 + 999`, bản dev `X.Y.Z-dev.N` là `… + N` với N đếm commit từ lần đổi `appVersion`. Build từ tag ở đâu cũng ra cùng số (cần cho F-Droid).
- **Bản dev**: mỗi push lên `main` có đổi mã thay prerelease `dev` trên GitHub bằng APK `TeleHex-X.Y.Z-dev.N.apk` + `.sha256`, ký bằng key dự án nên các bản dev cài đè được lên nhau. Bản dev chỉ cài đè được lên bản release khi `appVersion` đã được tăng sau release (versionCode của `X.Y.Z-dev.N` luôn thấp hơn của `X.Y.Z`); workflow dev dừng nếu tag `v<appVersion>` đã tồn tại để nhắc việc này. Push chỉ sửa tài liệu (`*.md`, `prototype/`, LICENSE, NOTICE) không chạy gì. Commit "Start … development" ngay sau tag release chỉ đổi số phiên bản nên không build, mà xoá prerelease `dev` cũ (bản đó thấp hơn bản release); commit mã tiếp theo sẽ phát hành lại `dev`. Workflow `ci` chỉ chạy cho pull request, vì trên `main` workflow dev đã chạy đủ test và lint.
- **Bản release**: ghi thay đổi vào mục `## <appVersion> — unreleased` của CHANGELOG.md, rồi chạy

  ```
  tools/release.sh            # không tham số: phát hành đúng appVersion hiện tại
  tools/release.sh minor      # có tham số: bump appVersion trước rồi mới phát hành
  git push --atomic origin main vX.Y.Z
  ```

  **Không tham số** thì phát hành đúng `appVersion` hiện tại. **Thêm tham số để bump** `appVersion` trước khi tag; với `appVersion` 0.1.1: `patch` → 0.1.2 (bỏ qua 0.1.1), `minor` → 0.2.0, `major` → 1.0.0. Khi bump, mục `unreleased` của CHANGELOG cũng đổi sang số mới. `tools/release.sh -h` in hướng dẫn này. Script đóng ngày cho mục CHANGELOG, commit "Release X.Y.Z", gắn tag `vX.Y.Z`, rồi tạo ngay commit "Start X.Y.(Z+1) development" tăng `appVersion` và mở mục CHANGELOG mới. Trước khi ghi gì, script fetch `origin` và dừng nếu `main` đang tụt sau GitHub, tag đã có trên GitHub, mục `unreleased` chưa ghi thay đổi nào (nên chạy lại ngay sau một release không tạo bản rỗng) hoặc CHANGELOG đã có mục mang số sắp phát hành; nếu lỗi giữa chừng (hook, GPG) thì tự quay về commit ban đầu và xoá tag để chạy lại được. Script không tự push; `--atomic` đảm bảo tag và `main` cùng lên hoặc cùng không. Push tag thì workflow chạy test, build, kiểm tra chứng chỉ, rồi tạo **release nháp** kèm APK + `.sha256` để bạn đọc lại và bấm Publish; push `main` thì prerelease `dev` cũ bị xoá, và commit mã tiếp theo phát hành `X.Y.(Z+1)-dev.N`, cao hơn bản release.
- **Ghim chứng chỉ**: `signing/release-cert.sha256` là SHA-256 của chứng chỉ ký. Bản release và bản dev bị chặn nếu APK không ký đúng key này, vì Android không cho cài đè một APK ký bằng key khác.
- **Secrets**: `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Keystore và mật khẩu nằm trong `.signing/` (không commit); `python3 .signing/upload-secrets.py` đẩy chúng lên GitHub Secrets bằng `gh`.

Kiểm tra một APK tải về:

```
shasum -a 256 -c TeleHex-v0.1.0.apk.sha256
apksigner verify --print-certs TeleHex-v0.1.0.apk   # SHA-256 phải trùng signing/release-cert.sha256
```

Thiết kế chi tiết: [plan.md](plan.md). Prototype HTML tham chiếu: [prototype/](prototype/). Lịch sử thay đổi: [CHANGELOG.md](CHANGELOG.md).

## License

MIT. Dữ liệu emoji/CLDR của Unicode, Inc. theo Unicode-3.0 (xem [NOTICE](NOTICE)).
