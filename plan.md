# Plan v2: Bàn phím Telex tổ ong (Android)

> v1 (layout, engine Telex, tuỳ biến, phát hành) được giữ nguyên ở các mục 1–8 và lưu riêng trong `plan-v1.md`. v2 thêm bàn phím số dạng dialpad (mục 1.5) và mục 9: tính năng nâng cao. Gõ vuốt (glide) tạm hoãn; không dùng autocorrect.

Bàn phím tiếng Việt gõ **Telex** trên lưới **lục giác đều 7 cột**, giữ tinh thần QWERTY nhưng phím to hơn ~45% so với QWERTY 10 cột. Thao tác phụ (xoá, chữ hoa, ký tự phụ) chuyển sang cử chỉ vuốt.

Prototype tương tác (nguồn tham chiếu cho hành vi và hình học): https://claude.ai/artifact/J75nPGKMTPv1F9DcoT3Suc — file `prototype/Main.dc.html`, layout `qwerty-7`. Logic Telex trong prototype (các hàm `vIdx`, `toneIndex`, `spell`, `press`) là bản tham chiếu để port sang Kotlin.

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

### 1.5 Dialpad (bàn phím số kiểu điện thoại)

Dùng phím chữ nhật bo góc như dialpad của các bàn phím khác (không dùng lục giác), vì người dùng đã quen bố cục này.

```
 [ 1 ] [ 2 ] [ 3 ] [ - ]
 [ 4 ] [ 5 ] [ 6 ] [cách]
 [ 7 ] [ 8 ] [ 9 ] [ ⌫ ]
 [ABC] [ 0 ] [ . ] [ ↵ ]
```

- **Tự mở** khi ô nhập có `inputType` là `phone`, `number` hoặc `datetime`. Từ bàn phím chữ, **giữ phím 123** để mở dialpad.
- Ba cột số mỗi phím rộng ~90dp, cột chức năng ~86dp, cao ~66dp.
- Vuốt xuống trên số ra ký tự phụ (hiện nhỏ phía trên số):
  - 1→`(`, 2→`)`, 3→`/`, 4→`*`, 5→`#`
  - 6→`-`, 7→`;`, 8→`:`, 9→`=`, 0→`+`, `.`→`,`
- Có phím ⌫ riêng. Nhập số điện thoại hay sửa từng chữ số nên cần nút xoá rõ ràng; phím cách ở đây vẫn hỗ trợ các cử chỉ xoá ở mục 9.2.
- Với `inputType` number thuần (không phải phone), cân nhắc ẩn các phím không hợp lệ.

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
| Vuốt **trái** trên phím cách | Xoá ký tự / từ / dòng theo **khoảng cách** vuốt (chi tiết ở 9.2) |
| `,` `.` `↵` | Chốt âm tiết rồi chèn ký tự hoặc xuống dòng |
| 123 / ☺ / ⚙ / 1/2 | Đổi lớp / mở emoji / mở cài đặt / đổi trang |

Không có phím backspace trên lưới chữ (chỉ có trong bảng emoji và dialpad). Các cử chỉ chức năng khác (con trỏ, xoá từ/dòng, undo/redo, cut/copy/paste) ở mục 9.2. Cần haptic nhẹ khi chạm và một nhịp riêng cho mỗi ký tự bị xoá khi vuốt.

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
- [x] Tự khôi phục từ tiếng Anh khi âm tiết không hợp lệ (`windows` không thành `ưindows`).
- [x] Bộ kiểm tra âm tiết hợp lệ (phụ âm đầu + vần + dấu; p/t/c/ch chỉ đi với sắc hoặc nặng).
- [x] Bộ test vàng ≥ 300 ca: từ thường gặp, `ươ`, `qu`, `gi`, `uy`, chữ hoa, gõ hoàn tác, kiểu dấu cũ/mới.

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
- [x] Repo GitHub + workflow chạy test trên PR (mục 8).
- [x] Module `telex` + bộ test vàng (bắt đầu bằng các ca trong prototype).
- [x] `HexGeometry` + `KeyboardView` vẽ đúng layout 1.1, kể cả phím cách vát góc.
- [x] `InputMethodService`: tap, phím cách, `,` `.` `↵`, composing + chế độ dự phòng.
- [x] Cử chỉ: vuốt lên (hoa), vuốt trái trên phím cách (xoá).

**Phase 2: Đủ dùng hằng ngày**
- [x] Lớp ký tự 2 trang, phím 1/2.
- [x] Vuốt xuống với bảng ký tự mặc định.
- [x] Candidate bar đổi dấu.
- [x] Bảng emoji + tab Gần đây.
- [x] Haptic, giao diện sáng/tối.

**Phase 3: Tuỳ biến**
- [x] Màn hình Cài đặt + chọn ký tự vuốt xuống cho từng phím.
- [x] Layout editor kéo-thả, kiểm tra ràng buộc, preset, khôi phục mặc định.
- [x] Tự sinh trang số theo layout.
- [x] Toggle "L cũng là dấu huyền" và kiểu đặt dấu cũ/mới.
- [x] Workflow release: tag `v*` → APK đã ký trên GitHub Releases.

**Phase 4: Chống bấm nhầm**
- [x] Bộ kiểm tra âm tiết: gạch chân âm tiết không hợp lệ và đưa gợi ý sửa lên thanh gợi ý (không tự sửa, xem 9.4).
- [x] Vùng chạm động: nới vùng của các phím hợp lệ tiếp theo theo ngữ pháp âm tiết. Đây cũng là một dạng tự sửa ngầm, nên là **tuỳ chọn, mặc định tắt**.
- [x] Học độ lệch chạm của người dùng theo từng phím.
- [x] Gợi ý từ cơ bản (chi tiết ở 9.4), chạy hoàn toàn offline.

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

## 9. Tính năng nâng cao (v2)

Mục này bổ sung các tính năng của các bộ gõ khác, sau khi khảo sát Typewise, Gboard, SwiftKey, Laban Key, HeliBoard, FlorisBoard và FUTO Keyboard. Mọi thứ vẫn phải **chạy offline, không xin quyền INTERNET** (mục 8).

### 9.1 Khảo sát nhanh

| Bộ gõ | Đáng học hỏi | Ghi chú cho dự án |
|---|---|---|
| **Typewise** | Lưới tổ ong; vuốt lên = hoa, vuốt trái = xoá, **vuốt phải = khôi phục**; autocorrect có nút hoàn tác và học từ lần bị hoàn tác; tự nhận diện ngôn ngữ đang gõ; cá nhân hoá hoàn toàn trên máy | Gần nhất với dự án. Không hỗ trợ voice, emoji search yếu |
| **Gboard** | Glide typing; **giữ-kéo phím cách để di con trỏ**; xoá bằng cử chỉ; clipboard history có ghim (mục thường tự hết hạn sau 1 giờ); chế độ sửa văn bản (mũi tên, chọn, copy/paste); tìm emoji theo từ khoá; bàn phím nổi / một tay; gợi ý từ tiếp theo | Bộ tính năng "chuẩn" người dùng sẽ so sánh |
| **SwiftKey** | Flow: vuốt cả câu không nhấc tay; sau khi vuốt hiện **3 ứng viên** thay vì tự chèn; **vuốt từ phím chấm để ra dấu câu**; chế độ ẩn danh (không học); dự đoán emoji; giữ một gợi ý để xoá nó khỏi từ điển | Ý tưởng 3 ứng viên rất hợp với gõ vuốt tiếng Việt (9.3) |
| **Laban Key** | **Telex nâng cao**: cc=ch, gg=gi, kk=kh, nn=ng, qq=qu, pp=ph, tt=th, uu=ươ; gợi ý từ tiếp theo; vuốt từ phím G để di con trỏ; vuốt ngắn/dài từ phím xoá để xoá 1 từ / 1 dòng; tự ngừng bỏ dấu khi gõ từ không phải tiếng Việt; **vẽ dấu** | Đối thủ trực tiếp ở mảng tiếng Việt; được đánh giá sửa lỗi tiếng Việt tốt hơn Gboard |
| **HeliBoard** (GPL-3.0) | 100% offline; clipboard history; một tay; chia đôi; numpad; sao lưu/khôi phục cả dữ liệu đã học; người dùng tự nạp từ điển | **Không có thư viện glide typing mã nguồn mở**: phải nạp thư viện đóng của Google. Dự án ta cần tự viết |
| **FlorisBoard** (Apache-2.0, Kotlin) | Smartbar; clipboard manager; glide typing riêng (chỉ tiếng Anh) | Tham khảo kiến trúc Kotlin được, license thoải mái |
| **FUTO Keyboard** | Dự đoán bằng transformer qua llama.cpp; voice offline bằng whisper.cpp; gõ vuốt có sẵn; trình sửa layout | License "Source First", **không phải mã nguồn mở theo OSI**: chỉ học ý tưởng, không chép code |

### 9.2 Cử chỉ chức năng

**Nguyên tắc:** cử chỉ vuốt chỉ dùng cho **chức năng chỉnh sửa**, không dùng để nhập chữ (không gõ vuốt). Các flick trên từng phím của v1 vẫn giữ: vuốt lên ra chữ hoa, vuốt xuống ra ký tự phụ.

Hầu hết cử chỉ bắt đầu từ **phím cách**, vì đây là phím lớn nhất và ngón cái luôn ở gần. Phân biệt các cử chỉ bằng thời gian giữ, tốc độ và khoảng cách.

| Chức năng | Cử chỉ | Ghi chú |
|---|---|---|
| **Di chuyển con trỏ** | Giữ phím cách ≥ 300ms rồi kéo | Kéo ngang: theo ký tự. Kéo dọc: theo dòng. Có haptic mỗi bước. Phím mờ đi để báo đang ở chế độ con trỏ |
| **Chọn văn bản** | Chạm đúp phím cách, giữ lần chạm thứ hai, rồi kéo | Mở rộng vùng chọn từ vị trí con trỏ |
| **Xoá 1 ký tự** | Vuốt trái **ngắn** trên phím cách (≤ ~1 phím) rồi nhả | Thao tác hay dùng nhất, nên làm đơn giản nhất |
| Xoá liên tục | Vuốt trái ngắn rồi **giữ yên** ngón tay | Như giữ phím backspace: xoá từng ký tự, sau ~1 giây tăng tốc thành xoá từng từ |
| **Xoá theo từ** | Vuốt trái **dài** (> ~1 phím) | Mỗi ~1 phím thêm = thêm 1 từ, có xem trước (xem dưới) |
| **Xoá cả dòng** | Vuốt trái từ phím cách ra quá mép trái bàn phím | Xoá tới đầu dòng hiện tại |
| **Undo** | Hai ngón vuốt sang trái | Lịch sử thao tác do bàn phím tự lưu |
| **Redo** | Hai ngón vuốt sang phải | |
| **Copy** | Khi đang có vùng chọn: flick phím cách lên | |
| **Cut** | Khi đang có vùng chọn: flick phím cách sang trái | Nhất quán với "trái = xoá" |
| **Paste** | Vuốt phím cách sang **phải** | Dán mục mới nhất trong clipboard |
| Chọn tất cả | Hai ngón giữ lên phím cách | Hoặc nút trên thanh chỉnh sửa |

**Thanh chỉnh sửa** giúp người dùng biết các cử chỉ trên. Khi có vùng chọn, hoặc khi đang ở chế độ con trỏ, thanh gợi ý tạm đổi thành các nút: ↶ Undo, ↷ Redo, Cắt, Chép, Dán, Chọn hết. Mỗi nút ghi kèm cử chỉ tương ứng ở dòng chữ nhỏ.

**Undo/redo tự quản lý:**
- `InputConnection` không có lệnh undo chuẩn. Bàn phím tự lưu ngăn xếp các thao tác của chính nó: commit, xoá, dán, cắt.
- Undo thực hiện thao tác ngược lại.
- Lịch sử bị xoá khi con trỏ nhảy do người dùng chạm vào ô nhập, hoặc khi sang ô nhập khác.
- Riêng cut/copy/paste dùng `performContextMenuAction` (`android.R.id.cut`/`copy`/`paste`); nếu app không hỗ trợ thì tự làm qua `ClipboardManager`.

**Phân biệt xoá ký tự và xoá từ: dùng khoảng cách, không dùng tốc độ.** Tốc độ vuốt khác nhau theo từng người và từng lúc, rất dễ nhầm; khoảng cách thì người dùng tự kiểm soát được.
- Vuốt ngắn (trong phạm vi ~1 phím) rồi nhả: xoá đúng 1 ký tự.
- Vuốt dài hơn: chuyển sang **chế độ chọn-để-xoá**:
  - Bàn phím đặt vùng chọn trong ô nhập (`setSelection`), bôi đen từ con trỏ ngược về trước, **bám theo ranh giới từ**. Mỗi ~1 phím kéo thêm = thêm 1 từ, có haptic mỗi bước.
  - **Nhả tay** thì xoá phần đang bôi đen.
  - **Kéo ngược lại** để bớt từ; kéo về gần điểm bắt đầu thì **huỷ**, không xoá gì.
  - Kéo quá mép trái thì bôi đen tới đầu dòng.
- Nhờ bôi đen xem trước, người dùng luôn thấy chính xác sẽ xoá gì trước khi nhả tay, nên không cần nhớ ngưỡng.

**Tránh xung đột:**
- Giữ-kéo (con trỏ) và vuốt-trái (xoá) phân biệt bằng thời gian giữ trước khi di chuyển (≥ 300ms là con trỏ).
- Khi đang có vùng chọn, vuốt trái là **cut** (xoá vùng chọn và đưa vào clipboard) thay vì xoá thêm.
- Thao tác hai ngón luôn được ưu tiên hơn một ngón.
- Mọi ngưỡng (thời gian, vận tốc, khoảng cách) đặt trong Cài đặt để chỉnh theo tay từng người.

### 9.3 Gõ vuốt: tạm hoãn

Để sau v2. Nếu làm lại, ý tưởng đã khảo sát: mỗi lần vuốt là một âm tiết không dấu, dùng mô hình ngôn ngữ để thêm dấu, và hiện 3 ứng viên. Không có thư viện mã nguồn mở dùng được (HeliBoard phải nạp thư viện đóng của Google), nên sẽ phải tự viết.

### 9.4 Gợi ý từ và gợi ý sửa lỗi (không autocorrect)

**Nguyên tắc: bàn phím không bao giờ tự đổi chữ người dùng đã gõ.** Mọi thay đổi chỉ xảy ra khi người dùng chạm vào gợi ý.

**Thanh gợi ý** gồm 3 ô, thay đổi theo trạng thái:

| Trạng thái | Nội dung |
|---|---|
| Đang gõ dở một âm tiết | Ô giữa giữ đúng chữ đang gõ. Hai ô bên là biến thể dấu (như v1) hoặc **gợi ý sửa**. Âm tiết không hợp lệ thì gạch chân đỏ chữ đang gõ |
| Vừa chạm cách xong | **Gợi ý âm tiết tiếp theo** (3 ứng viên) |
| Giữ một ô | Xem thêm (mở rộng thành hàng cuộn ngang); với gợi ý học được thì có nút xoá khỏi từ điển cá nhân |

Chi tiết từng phần:

- **Gợi ý sửa lỗi**: sinh ứng viên từ ba nguồn rồi xếp hạng:
  - Phím lân cận trên lưới lục giác: thay 1 ký tự bằng các ô kề, tối đa 6 ô.
  - Thiếu hoặc thừa 1 ký tự, đảo 2 ký tự liền nhau.
  - Lỗi Telex thường gặp: quên `w`, đặt dấu nhầm vị trí, gõ dấu trước nguyên âm.
  
  Chỉ giữ âm tiết hợp lệ. Xếp hạng theo khoảng cách bàn phím × xác suất n-gram trong ngữ cảnh. Chạm vào gợi ý thì thay âm tiết đang gõ.

- **Gợi ý sửa cho âm tiết đã gõ xong**: chạm lại vào một từ đã gõ (con trỏ nằm trong từ) thì thanh gợi ý hiện các phương án sửa cho từ đó, kiểu Gboard.

- **Dự đoán âm tiết tiếp theo** là phần cần đầu tư nhất:
  - **Mô hình gốc**: n-gram theo âm tiết (3-gram, có thể lên 4-gram), lọc bằng Kneser-Ney, cắt tỉa và lượng tử hoá, đóng gói trie nhị phân đọc bằng mmap; mục tiêu < 20 MB. Dữ liệu từ Wikipedia tiếng Việt (CC-BY-SA-4.0) và corpus tin tức mở.
  - **Cụm từ cố định**: thêm bảng các từ ghép 2–3 âm tiết hay gặp ("điện thoại", "cảm ơn", "không sao") để ưu tiên hoàn thành từ, kể cả khi mới gõ dở âm tiết đầu.
  - **Mô hình cá nhân**: đếm n-gram từ chính người dùng, trộn với mô hình gốc theo trọng số tăng dần khi dữ liệu nhiều lên. Ưu tiên các cụm người dùng lặp lại (tên người, địa chỉ, câu chào). Học cả từ những gợi ý người dùng đã chọn.
  - **Ngữ cảnh ô nhập**: đọc vài chục ký tự trước con trỏ bằng `getTextBeforeCursor` khi mở bàn phím, để gợi ý đúng ngay từ đầu chứ không chờ người dùng gõ.
  - **Viết hoa thông minh**: gợi ý ở đầu câu thì viết hoa; tên riêng người dùng đã gõ hoa nhiều lần thì giữ hoa.
  - **Đo chất lượng**: script trong `tools/` tính keystroke savings và tỉ lệ đúng top-1/top-3 trên tập test tách riêng, để so mỗi lần đổi mô hình.
  - Về sau: cho phép người dùng tự nạp mô hình transformer nhỏ (GGUF, llama.cpp) qua trình chọn file để có gợi ý tốt hơn. App không có quyền INTERNET nên không tải model từ mạng.

- **Chế độ ẩn danh**: không học khi người dùng bật thủ công; tự bật trong ô mật khẩu hoặc khi app đặt cờ `IME_FLAG_NO_PERSONALIZED_LEARNING`.

- **Tự nhận diện tiếng Anh**: khi chuỗi phím không thể là âm tiết tiếng Việt thì ngừng áp dụng Telex cho từ đó và gợi ý theo từ điển tiếng Anh nhỏ. Không tự đổi chữ.

- **Gợi ý emoji** xuất hiện như một ô gợi ý khi gõ từ khoá ("vui", "cười", "tim").

### 9.5 Gõ tắt

- **Gõ tắt do người dùng định nghĩa** (text expansion): `ko` → không, `dc` → được, `@@` → email của tôi. Kích hoạt khi chạm cách.

### 9.6 Clipboard và chỉnh sửa văn bản

- **Clipboard history**:
  - Mục thường tự hết hạn (mặc định 1 giờ); mục được ghim thì giữ lâu dài.
  - Bỏ qua nội dung được đánh dấu nhạy cảm (`ClipDescription.EXTRA_IS_SENSITIVE`, Android 13+), ví dụ mật khẩu copy từ trình quản lý mật khẩu.
  - Dữ liệu chỉ lưu trong bộ nhớ app.
- **Bảng sửa văn bản** (cho ai không muốn dùng cử chỉ): mũi tên, chọn từ/chọn hết, cut/copy/paste, undo/redo, Home/End. Mở bằng cách giữ phím ⚙, chiếm chỗ lưới phím như bảng emoji.

### 9.7 Emoji

- **Tìm emoji bằng tiếng Việt** dùng chú thích CLDR (có bản tiếng Việt), kèm tông da / giới tính mặc định được ghi nhớ.

### 9.8 Bố cục và tiện ích khác

- **Chế độ một tay**: thu lưới về ~80% và dồn sang trái/phải. Lục giác vẫn đều nên chỉ cần đổi `Wu`.
- **Split keyboard**: cho màn hình ngang.
- **Bàn phím nổi** (để sau).
- **Nhập giọng nói qua app khác**: gọi IME giọng nói của hệ thống bằng implicit intent. Ví dụ FUTO Voice Input chạy offline bằng Whisper; hiện nhiều bàn phím mã nguồn mở đã tích hợp kiểu này. Không tự nhúng model vào APK ở giai đoạn này.
- **Sao lưu / khôi phục**: một file zip gồm layout, ký tự vuốt xuống, gõ tắt và dữ liệu đã học. Khi khôi phục phải kiểm tra đường dẫn trong zip để tránh lỗi bảo mật kiểu zip-slip.
- Cân nhắc **vẽ dấu** kiểu Laban Key: hiện thanh gợi ý 6 biến thể dấu đã giải quyết cùng nhu cầu, nên để ở mức thử nghiệm.

### 9.9 Lộ trình v2 (sau Phase 1–4)

**Phase 5: Cử chỉ chức năng và dialpad**
- [x] Dialpad + tự mở theo `inputType`; giữ 123 để mở dialpad.
- [x] Giữ-kéo phím cách di con trỏ; chạm đúp-giữ-kéo để chọn văn bản.
- [x] Xoá từ (flick nhanh) và xoá dòng (vuốt quá mép).
- [x] Ngăn xếp undo/redo + cử chỉ hai ngón.
- [x] Cut/copy/paste bằng flick khi có vùng chọn + thanh chỉnh sửa.
- [x] Màn hình chỉnh ngưỡng cử chỉ.

**Phase 6: Gợi ý (không autocorrect)**
- [x] Pipeline dựng n-gram âm tiết (script Python trong `tools/`, output vào `assets/`) + bộ đo chất lượng.
- [x] Gợi ý âm tiết tiếp theo, dùng cả ngữ cảnh từ `getTextBeforeCursor`.
- [x] Gợi ý sửa lỗi (phím lân cận lục giác + lỗi Telex), gạch chân âm tiết không hợp lệ.
- [x] Gợi ý sửa cho từ đã gõ khi đặt con trỏ vào từ.
- [x] Mô hình cá nhân + chế độ ẩn danh + xuất/nhập dữ liệu học.

**Phase 7: Tiện ích**
- [x] Clipboard history (ghim, hết hạn, bỏ qua nội dung nhạy cảm).
- [x] Gõ tắt người dùng.
- [x] Gợi ý emoji + tìm emoji bằng tiếng Việt.
- [x] Bảng sửa văn bản.

**Phase 8: Tuỳ chọn**
- [x] Một tay (bàn phím nổi: chưa).
- [x] Voice qua IME ngoài.
- [ ] Nạp mô hình transformer do người dùng cung cấp.
- [x] Sao lưu / khôi phục.
- [ ] Gõ vuốt (xem 9.3).

### 9.10 License khi tham khảo mã

- HeliBoard: GPL-3.0. Tham khảo/chép code thì repo phải dùng GPL-3.0.
- FlorisBoard: Apache-2.0, tương thích với cả MIT lẫn GPL-3.0.
- FUTO Keyboard / FUTO Voice Input: Source First License. Không chép code; chỉ tham khảo ý tưởng.
- Dữ liệu Wikipedia: CC-BY-SA-4.0. Mô hình n-gram dựng từ đó nên ghi công và giữ cùng điều khoản.

---

## 10. Câu hỏi còn mở

- Đoạn văn bản ngắn (snippet) cho vuốt xuống: có cho phép không, và giới hạn bao nhiêu ký tự?
- Có cần bản dọc cho máy tính bảng / màn hình gập không? Lưới 7 cột sẽ quá to khi màn hình rộng.
- Ngân sách dung lượng APK cho mô hình ngôn ngữ: đóng gói sẵn, hay tách thành "gói ngôn ngữ" tải từ GitHub Releases rồi nạp bằng tay?
