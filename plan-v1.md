# Plan v1: Bàn phím Telex tổ ong (Android)

Bàn phím tiếng Việt gõ **Telex** trên lưới **lục giác đều 7 cột**, giữ tinh thần QWERTY nhưng phím to hơn ~45% so với QWERTY 10 cột. Thao tác phụ (xoá, chữ hoa, ký tự phụ) chuyển sang cử chỉ vuốt.

Prototype tương tác (nguồn tham chiếu cho hành vi và hình học): https://claude.ai/artifact/J75nPGKMTPv1F9DcoT3Suc — file `project/Main.dc.html`, layout `qwerty-7`. Logic Telex trong prototype (các hàm `vIdx`, `toneIndex`, `spell`, `press`) là bản tham chiếu để port sang Kotlin.

Thiết bị thử chính: Samsung Galaxy Note 10+ (Android 12+).

---

## 1. Layout đã chốt

### 1.1 Lớp chữ (mặc định)

```
row 0 (lệch ½):   W   R   Y   ☺   123  O   P
row 1:          Q   E   T   ⚙   U   I   H
row 2 (lệch ½):   A   S   D   ,   F   G   J
row 3:          Z   C   B   .   N   M   K
row 4 (lệch ½):   X   V  \___cách___/  L   ↵
```

Dạng dữ liệu (`null` = ô thuộc phím cách):

```json
[
  ["w","r","y","#emoji","#123","o","p"],
  ["q","e","t","#set","u","i","h"],
  ["a","s","d",",","f","g","j"],
  ["z","c","b",".","n","m","k"],
  ["x","v",null,null,null,"l","#enter"]
]
```

Nguyên tắc thiết kế (giữ khi cho phép người dùng sắp xếp lại):
- Q-W-E-R-T-Y đọc zigzag trái→phải giữa hàng 0 và 1; số 1–0 đi theo đúng đường đó.
- **Cột giữa (cột 3)** dành cho phím ít dùng: ☺, 123, ⚙, dấu phẩy, dấu chấm. Vùng này xa cả hai ngón cái.
- Phím cách chiếm 3 ô giữa hàng cuối.

Màu theo loại phím: phụ âm trắng, nguyên âm + W xanh nhạt, phím dấu (S F R X J Z, và L khi bật tuỳ chọn ở 5.3) cam nhạt, phím chức năng xám, Enter tối.

### 1.2 Lớp ký tự: 2 trang

Phím `#page` ở góc dưới trái (vị trí của X) chuyển 1/2 ↔ 2/2. Phím 123 hiển thị "ABC" khi đang ở lớp ký tự. Mỗi lần mở lớp ký tự luôn vào trang 1.

```
Trang 1                         Trang 2
  2  4  6  ☺  ABC 9  0            ~  `  |  ☺  ABC ^  °
 1  3  5  ⚙  7  8  -            [  ]  {  ⚙  }  <  >
  @  #  $  ,  %  &  +            _  "  '  ,  ;  \  •
 *  (  )  .  =  /  !            €  £  ¥  .  ₫  ×  ÷
  1/2 ?  [ cách ]  :  ↵           2/2 …  [ cách ]  ©  ↵
```

Quy tắc: **số luôn nằm ở vị trí của chữ tương ứng** (Q→1, W→2 … P→0). Khi người dùng sắp xếp lại chữ, trang 1 phải tự sinh lại vị trí số theo quy tắc này.

### 1.3 Thanh trên bàn phím (candidate bar, ~52dp)

Hiện 6 biến thể dấu của âm tiết đang soạn (ví dụ mới / mởi / mời / mỡi / mợi / moi). Chạm để đổi dấu. Sau này dùng thêm cho gợi ý từ.

### 1.4 Bảng emoji (kiểu Gboard)

Chiếm toàn bộ vùng phím. Gồm lưới 8 cột, cuộn được; hàng dưới có `ABC`, các tab (Gần đây / Mặt cười / Cử chỉ / Trái tim / Đồ vật) và nút xoá. Tab "Gần đây" lưu tối đa 32 emoji.

---

## 2. Hình học

Gọi `Wu` là chiều rộng khả dụng (trừ padding 8dp mỗi bên).

- `W = Wu / 7.5` (bề rộng một lục giác, pointy-top)
- `H = W · 2/√3` (lục giác **đều**, không kéo giãn)
- `P = 0.75 · H` (khoảng cách giữa các hàng)
- Độ lệch mỗi hàng: `[0.5, 0, 0.5, 0, 0.5] · W`
- Tâm ô `(r, c)`: `x = pad + (offset[r] + c + 0.5)·W`, `y = top + r·P + H/2`
- Chiều cao lưới: `4P + H`
- Khe hở khi vẽ: thu mỗi lục giác về `iw = W − 3dp`, `ih = iw·2/√3`, giữ nguyên tâm

**Phím cách** là đa giác ghép từ 3 ô giữa hàng 4, đặt gốc tại góc trái-trên của ô đầu:
- Cạnh trên răng cưa: `(0,H/4) → (W/2,0) → (W,H/4) → (1.5W,0) → (2W,H/4) → (2.5W,0) → (3W,H/4)`
- Hai cạnh bên thẳng đứng xuống `3H/4`
- Hai góc dưới vát `\___/`, song song cạnh dưới lục giác: `(3W,3H/4) → (2.5W,H) → (W/2,H) → (0,3H/4)`
- Các đỉnh co vào 1.5dp để tạo khe

**Hit-testing**: chọn tâm ô gần nhất (Voronoi của lưới lục giác chính là các lục giác, nên không có vùng chết). Ba ô của phím cách cùng trỏ về phím cách. Cấu trúc này cũng là nền cho vùng chạm động ở Phase 4.

---

## 3. Cử chỉ

Ghi điểm `down` cho mỗi pointer; quyết định khi `up`. Ngưỡng ~24dp và phải theo hướng trội (|dy| > |dx| hoặc ngược lại).

| Cử chỉ | Hành vi |
|---|---|
| Chạm phím chữ | Đưa vào engine Telex |
| Vuốt **lên** phím chữ | Chữ hoa cho ký tự đó (giữ hoa khi engine biến đổi: vuốt lên D rồi gõ d ra Đ) |
| Vuốt **xuống** phím chữ / `,` / `.` | Chèn ký tự phụ của phím (xem 5.1) |
| Chạm phím cách | Chốt âm tiết + khoảng trắng |
| Vuốt **trái** trên phím cách | Xoá, khoảng 28dp một ký tự. Bản thật nên xoá *trực tiếp* khi kéo, kéo ngược lại thì hoàn tác |
| `,` `.` `↵` | Chốt âm tiết rồi chèn ký tự hoặc xuống dòng |
| 123 / ☺ / ⚙ / 1/2 | Đổi lớp / mở emoji / mở cài đặt / đổi trang |

Không có phím backspace trên lưới chữ (chỉ có trong bảng emoji). Cần haptic nhẹ khi chạm và một nhịp riêng cho mỗi ký tự bị xoá khi vuốt.

---

## 4. Engine Telex (Kotlin thuần, không phụ thuộc Android)

Trạng thái âm tiết: `chars: List<Char>` (đã biến đổi, chữ thường), `caps: List<Boolean>`, `tone: Int (0..5)`.

Quy tắc (port từ prototype):
- **Phím dấu** (chỉ có hiệu lực khi âm tiết đã có nguyên âm): `s`=sắc, `f`=huyền, `r`=hỏi, `x`=ngã, `j`=nặng, `z`=bỏ dấu.
  - Gõ lại cùng dấu thì huỷ dấu và in chữ: `ss` → s.
  - **Tuỳ chọn "L cũng là dấu huyền"** (mặc định tắt, bật trong Cài đặt). L dùng được làm phím dấu vì chỉ đứng đầu âm tiết, không bao giờ là phụ âm cuối: `lal` → là, `nguowil` → người, `ll` → l. Engine nhận danh sách phím dấu từ cấu hình, không hard-code.
- `aa`/`ee`/`oo` → â/ê/ô; gõ lần nữa trả lại và in thêm chữ.
- `dd` → đ (ở đầu âm tiết).
- `w`:
  - `uo` → ươ (trừ sau `q`);
  - nếu không có `uo`: a/o/u gần nhất → ă/ơ/ư; nếu đã là ă/ơ/ư thì trả lại và in `w`;
  - không có nguyên âm → `ư`.
- **Vị trí dấu**:
  - Ưu tiên nguyên âm có mũ/móc cuối cùng (ươ → đặt ở ơ).
  - 3 nguyên âm → đặt ở nguyên âm giữa.
  - 2 nguyên âm và có phụ âm cuối → nguyên âm thứ hai.
  - 2 nguyên âm, không có phụ âm cuối → nguyên âm đầu. Riêng `oa/oe/uy` theo tuỳ chọn kiểu cũ ("hòa") hoặc kiểu mới ("hoà").
  - `u` sau `q` và `i` trong `gi`+nguyên âm không tính là nguyên âm.
- Xoá theo **grapheme** để emoji nhiều code point (❤️) xoá trong một lần.

Việc cần làm thêm so với prototype:
- [ ] Tự khôi phục từ tiếng Anh khi âm tiết không hợp lệ (`windows` không thành `ưindows`).
- [ ] Bộ kiểm tra âm tiết hợp lệ (phụ âm đầu + vần + dấu; p/t/c/ch chỉ đi với sắc hoặc nặng).
- [ ] Bộ test vàng ≥ 300 ca: từ thường gặp, `ươ`, `qu`, `gi`, `uy`, chữ hoa, gõ hoàn tác, kiểu dấu cũ/mới.

---

## 5. Tuỳ biến

### 5.1 Ký tự vuốt xuống (custom key)

Mặc định:

| Phím | Vuốt xuống |
|---|---|
| Q W E R T Y U I O P | 1 2 3 4 5 6 7 8 9 0 |
| A S D F G H J K L | 😄 😂 😍 👍 🙏 ❤️ 😭 🔥 🎉 |
| Z X C V B N M | @ # & - / ( ) |
| `,` `.` | ? ! |

- Mỗi phím gán được một ký tự, ký hiệu, emoji, hoặc một đoạn văn bản ngắn (cân nhắc giới hạn ~8 ký tự).
- Phím dấu (S F R X J Z) cũng có ký tự vuốt xuống như mọi phím chữ khác. Vuốt xuống luôn chèn ký tự phụ, không bao giờ đặt dấu.
- Ký tự phụ hiển thị nhỏ (9sp) phía trên chữ chính.
- UI: Cài đặt → chạm phím → bảng chọn có các tab Số & ký hiệu / emoji, cộng ô nhập tự do.

### 5.2 Sắp xếp lại phím (reorder)

- Màn hình editor vẽ đúng lưới thật; kéo một phím thả lên phím khác để **đổi chỗ** hai phím.
- Ô cố định: phím cách (3 ô), Enter. Các phím chức năng (☺ 123 ⚙ , .) di chuyển được.
- Ràng buộc khi lưu: đủ 26 chữ, mỗi phím xuất hiện đúng một lần, có 123 và ⚙ (để không tự khoá mình khỏi cài đặt).
- Có nút "Khôi phục mặc định" và cho lưu nhiều preset.
- Sau khi sắp xếp: tự sinh lại trang 1 của lớp ký tự để số đi theo chữ (Q→1 …); trang 2 giữ nguyên.

### 5.3 Cài đặt khác

Kiểu đặt dấu (cũ/mới), "L cũng là dấu huyền" (mặc định tắt; khi bật, phím L đổi sang màu phím dấu và hiện nhãn "à"), rung, âm thanh, chiều cao bàn phím (giữ lục giác đều nên chỉ đổi padding), giao diện sáng/tối.

---

## 6. Kiến trúc

```
app/
  ime/
    HexKeyboardService.kt     // InputMethodService
    KeyboardView.kt           // custom View, vẽ bằng Canvas
    GestureDetector.kt        // tap / swipe up / down / left-on-space
    CandidateBar.kt           // 6 biến thể dấu
    EmojiPanel.kt             // RecyclerView grid + tabs
  layout/
    LayoutModel.kt            // grid 5x7, key types, JSON (de)serialize
    HexGeometry.kt            // tâm ô, path lục giác, path phím cách, hit-test
    SymbolPages.kt            // sinh trang 1 từ layout chữ, trang 2 cố định
  telex/                      // module Kotlin thuần, có unit test
    TelexEngine.kt
    ToneRules.kt
    SyllableValidator.kt
  settings/
    SettingsActivity.kt       // Compose
    LayoutEditorScreen.kt     // kéo-thả reorder
    CustomKeyPicker.kt
  data/
    Prefs.kt                  // DataStore: layout JSON, custom map, recent emoji, options
```

Ghi chú kỹ thuật:
- **Vẽ bằng Canvas với `Path` dựng sẵn** cho mỗi phím, cache lại khi kích thước đổi. Không dùng Compose cho phần bàn phím (độ trễ chạm và khởi tạo IME).
- **Composing text**:
  - Mặc định dùng `setComposingText` / `finishComposingText`.
  - Nhiều app (Zalo, Messenger, ô nhập WebView) xử lý composing không ổn. Cần chế độ dự phòng dùng `deleteSurroundingText` + `commitText` và danh sách app bật sẵn chế độ này.
  - Test sớm trên Zalo, Messenger, Chrome và Gmail.
- Theo dõi `onUpdateSelection` để huỷ âm tiết đang soạn khi người dùng di chuyển con trỏ.
- Hỗ trợ ô mật khẩu (tắt Telex và gợi ý), ô số (`inputType` number → mở thẳng lớp ký tự), và `imeOptions` cho Enter (Gửi / Tìm / Tiếp).
- Insets: phần đáy bàn phím tránh thanh điều hướng cử chỉ.

---

## 7. Lộ trình

**Phase 1: Lõi gõ được (MVP)**
- [ ] Repo GitHub + workflow chạy test trên PR (mục 8).
- [ ] Module `telex` + bộ test vàng (bắt đầu bằng các ca trong prototype).
- [ ] `HexGeometry` + `KeyboardView` vẽ đúng layout 1.1, kể cả phím cách vát góc.
- [ ] `InputMethodService`: tap, phím cách, `,` `.` `↵`, composing + chế độ dự phòng.
- [ ] Cử chỉ: vuốt lên (hoa), vuốt trái trên phím cách (xoá).

**Phase 2: Đủ dùng hằng ngày**
- [ ] Lớp ký tự 2 trang, phím 1/2.
- [ ] Vuốt xuống với bảng ký tự mặc định.
- [ ] Candidate bar đổi dấu.
- [ ] Bảng emoji + tab Gần đây.
- [ ] Haptic, giao diện sáng/tối.

**Phase 3: Tuỳ biến**
- [ ] Màn hình Cài đặt + chọn ký tự vuốt xuống cho từng phím.
- [ ] Layout editor kéo-thả, kiểm tra ràng buộc, preset, khôi phục mặc định.
- [ ] Tự sinh trang số theo layout.
- [ ] Toggle "L cũng là dấu huyền" và kiểu đặt dấu cũ/mới.
- [ ] Workflow release: tag `v*` → APK đã ký trên GitHub Releases.

**Phase 4: Chống bấm nhầm**
- [ ] Bộ kiểm tra âm tiết + tự sửa khi âm tiết không hợp lệ.
- [ ] Vùng chạm động: nới vùng của các phím hợp lệ tiếp theo theo ngữ pháp âm tiết. Giao diện giữ nguyên, chỉ đổi trọng số trong hit-test.
- [ ] Học độ lệch chạm của người dùng theo từng phím.
- [ ] Gợi ý từ (bigram từ corpus tiếng Việt), chạy hoàn toàn offline.

---

## 8. Phát hành trên GitHub

- Repo public. README có ảnh chụp layout, GIF minh hoạ cử chỉ và hướng dẫn bật bàn phím (Cài đặt → Bàn phím → bật → chọn làm mặc định).
- **GitHub Actions**:
  - Mỗi PR chạy unit test của module `telex` và lint.
  - Mỗi tag `v*` build APK release đã ký và đính kèm vào GitHub Release, kèm changelog tự sinh từ commit.
  - Keystore đặt trong GitHub Secrets (base64), không commit vào repo.
- Đặt `versionCode` tự tăng theo số lần chạy workflow, `versionName` lấy từ tag.
- Ghi rõ trong README và trong màn hình Cài đặt: **không xin quyền INTERNET**. Không gửi dữ liệu gõ đi đâu; mọi xử lý nằm trên máy. Đây là lý do chính để người dùng tin một bàn phím bên thứ ba.
- Chọn license (MIT hoặc GPL-3.0). Nếu tham khảo mã nguồn OpenKey/Unikey (GPL) thì bắt buộc dùng GPL-3.0.
- Có thể đăng lên F-Droid sau. F-Droid yêu cầu build tái lập được và không có thư viện độc quyền, nên giữ dependency tối thiểu ngay từ đầu.

---

## 9. Câu hỏi còn mở

- Đoạn văn bản ngắn (snippet) cho vuốt xuống: có cho phép không, và giới hạn bao nhiêu ký tự?
- Có cần bản dọc cho máy tính bảng / màn hình gập không? Lưới 7 cột sẽ quá to khi màn hình rộng.
