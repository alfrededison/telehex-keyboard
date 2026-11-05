# TeleHex – Bàn phím Telex tổ ong cho Android

Bàn phím tiếng Việt gõ **Telex** trên lưới **lục giác đều 7 cột**. Giữ tinh thần QWERTY (Q-W-E-R-T-Y đọc zigzag giữa hai hàng trên) nhưng phím to hơn ~45% so với bàn phím 10 cột. Thao tác phụ (xoá, chữ hoa, ký tự phụ, con trỏ, chọn, dán…) chuyển sang cử chỉ vuốt, nên lưới chữ không có phím backspace hay shift.

```
  W   R   Y   ☺  123  O   P
Q   E   T   ⚙   U   I   H
  A   S   D   ,   F   G   J
Z   C   B   .   N   M   K
  X   V  \___cách___/  L   ↵
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
| Vuốt **lên** phím chữ | Chữ hoa (giữ hoa khi biến đổi: vuốt lên D rồi gõ d → Đ) |
| Vuốt **xuống** phím chữ / `,` / `.` | Ký tự phụ hiện nhỏ phía trên (số, emoji, ký hiệu; tuỳ biến từng phím trong Cài đặt) |
| Chạm phím cách | Chốt âm tiết + khoảng trắng; áp dụng gõ tắt nếu khớp |
| Vuốt **trái ngắn** trên phím cách | Xoá 1 ký tự (xoá theo grapheme, emoji ghép xoá một lần) |
| Vuốt **trái dài** trên phím cách | Chọn-để-xoá theo từ: bôi đen xem trước, mỗi ~1 phím thêm 1 từ, kéo ngược để bớt, nhả để xoá, kéo quá mép trái = tới đầu dòng |
| **Giữ** phím cách ≥ 300 ms rồi kéo | Di con trỏ (ngang theo ký tự, dọc theo dòng); bàn phím mờ đi |
| Chạm đúp phím cách, giữ lần hai rồi kéo | Chọn văn bản |
| Vuốt **phải** trên phím cách | Dán |
| Có vùng chọn: vuốt lên / vuốt trái trên phím cách | Chép / Cắt |
| Hai ngón vuốt trái / phải | Undo / Redo (bàn phím tự quản lý lịch sử) |
| Hai ngón giữ | Chọn hết |
| Giữ phím 123 | Dialpad (tự mở ở ô số điện thoại / số / ngày giờ) |
| Giữ phím ⚙ | Bảng sửa văn bản + clipboard history |
| Chạm ⚙ | Cài đặt |

Mọi ngưỡng (vuốt, giữ, bước xoá từ, chạm đúp) chỉnh được trong Cài đặt → Cử chỉ.

## Thanh gợi ý (không autocorrect)

Bàn phím **không bao giờ tự đổi chữ** đã gõ. Thanh trên bàn phím hiện:

- 6 biến thể dấu của âm tiết đang soạn (mới / mởi / mời / mỡi / mợi / mơi), chạm để đổi dấu;
- gợi ý sửa khi âm tiết chưa hợp lệ (phím lân cận trên lưới lục giác, thiếu/thừa/đảo phím, quên phím Telex), chữ đang gõ luôn giữ nguyên ở ô đầu;
- gợi ý âm tiết tiếp theo sau khi chạm cách (học từ chính bạn; giữ một gợi ý để xoá khỏi từ điển cá nhân);
- phương án sửa cho từ đã gõ khi đặt con trỏ vào từ;
- emoji theo từ khoá (gõ "cười" → 😂).

Chuỗi phím không thể là tiếng Việt thì hiện đúng phím đã gõ (`windows` không thành `ưindows`) và được tô nhạt.

### Gói ngôn ngữ (tuỳ chọn)

Gợi ý âm tiết tiếp theo dùng mô hình n-gram âm tiết nếu có. APK không đóng gói sẵn mô hình; bạn tự dựng và nạp bằng tay (app không có quyền INTERNET nên không tải từ mạng):

```
python3 tools/build_ngram.py corpus1.txt corpus2.txt -o vi.ngram   # Kneser–Ney, lượng tử hoá 8 bit
python3 tools/eval_ngram.py vi.ngram test.txt                       # top-1/top-3, keystroke savings
```

rồi Cài đặt → "Nạp gói ngôn ngữ (.ngram)…". Dữ liệu Wikipedia tiếng Việt là CC-BY-SA-4.0: mô hình dựng từ đó phải ghi công và giữ cùng điều khoản. Đặt file vào `app/src/main/assets/vi.ngram` nếu muốn đóng gói sẵn.

## Tuỳ biến

- **Sắp xếp phím**: kéo một phím thả lên phím khác để đổi chỗ; phím cách và ↵ cố định; lưu nhiều preset. Số ở lớp ký tự luôn đi theo chữ (Q→1 … P→0).
- **Ký tự vuốt xuống** cho từng phím: số, ký hiệu, emoji hoặc đoạn văn bản ngắn (≤ 8 ký tự).
- Kiểu đặt dấu cũ/mới (hòa/hoà), "L cũng là dấu huyền" (`lal` → là), rung, âm thanh, giao diện sáng/tối, chế độ một tay, tông da emoji, gõ tắt, clipboard history, chế độ ẩn danh, vùng chạm động (tắt mặc định), học độ lệch chạm.
- **Sao lưu / khôi phục** mọi thứ ra một file zip.
- **Giọng nói**: nút 🎤 trong bảng sửa văn bản chuyển sang IME giọng nói đã bật (ví dụ FUTO Voice Input chạy offline).

## Build

```
./gradlew :telex:test        # engine Telex thuần Kotlin + bộ test vàng ~400 ca
./gradlew :app:assembleDebug # APK debug (có activity "TeleHex test" để thử nhanh)
```

Yêu cầu JDK 17+, Android SDK 36. Tag `v*` sẽ build APK release đã ký qua GitHub Actions (keystore base64 trong Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).

Thiết kế chi tiết: [plan.md](plan.md). Prototype HTML tham chiếu: [prototype/](prototype/).

## License

MIT. Dữ liệu emoji/CLDR của Unicode, Inc. theo Unicode-3.0 (xem [NOTICE](NOTICE)).
