# TeleHex – Bàn phím Telex tổ ong cho Android

Bàn phím tiếng Việt gõ **Telex** trên lưới **lục giác đều 7 cột**. Giữ tinh thần QWERTY nhưng phím to hơn ~45% so với bàn phím 10 cột. Thao tác phụ (xoá, chữ hoa, ký tự phụ) chuyển sang cử chỉ vuốt.

## Quyền riêng tư

**Ứng dụng không xin quyền INTERNET.** Không có dữ liệu gõ nào được gửi đi đâu; mọi xử lý nằm trên máy.

## Bật bàn phím

1. Cài APK từ [Releases](../../releases).
2. Cài đặt → Hệ thống → Ngôn ngữ và nhập liệu → Bàn phím ảo → Quản lý bàn phím → bật **TeleHex**.
3. Khi gõ, chạm biểu tượng bàn phím ở thanh điều hướng và chọn **TeleHex**.

## Cử chỉ

| Cử chỉ | Hành vi |
|---|---|
| Chạm phím chữ | Gõ (Telex) |
| Vuốt **lên** phím chữ | Chữ hoa |
| Vuốt **xuống** phím chữ | Ký tự phụ (số, emoji, ký hiệu; tuỳ biến được) |
| Vuốt **trái** trên phím cách | Xoá: ngắn = 1 ký tự, dài = theo từ (bôi đen xem trước) |
| Giữ phím cách rồi kéo | Di chuyển con trỏ |
| Giữ phím 123 | Mở dialpad |

## Build

```
./gradlew :telex:test        # unit test engine Telex
./gradlew :app:assembleDebug # APK debug
```

Xem `plan.md` để biết thiết kế chi tiết.

## License

MIT.
