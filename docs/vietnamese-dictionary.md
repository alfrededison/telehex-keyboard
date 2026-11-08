# Từ điển tần suất tiếng Việt

`app/src/main/assets/vi-frequency.txt` là danh sách 10.052 token theo thứ tự phổ biến,
60.184 byte, phái sinh từ **wordfreq 3.1.1** của **Robyn Speer**, theo **CC BY-SA 4.0**.
Nguồn: [wordfreq](https://github.com/rspeer/wordfreq),
[bản 3.1.1](https://pypi.org/project/wordfreq/3.1.1/),
[ghi công và giấy phép](https://github.com/rspeer/wordfreq/blob/master/NOTICE.md).
Ghi công cũng có trong [NOTICE](../NOTICE) và `assets/vi-NOTICE.txt` trong APK.

Script giữ token chỉ có chữ Latin/Việt, dài tối đa 8 ký tự, chuẩn hoá Unicode NFC và
loại trùng. Khi nạp trên luồng nền, `VietnameseVocabulary` chỉ nhận các âm tiết hợp lệ
theo bộ kiểm tra của Telex (4.793 âm tiết ở bản này), loại token ngoại ngữ không phù hợp
và token mang nhiều dấu thanh.
Dữ liệu nguồn là tần suất trong corpus, không phải một từ điển chính tả đầy đủ.

Khi chưa có gói n-gram VI, danh sách bổ sung hoàn thành âm tiết và gợi ý phổ biến với trọng số
thấp; dữ liệu cá nhân và bảng cụm từ theo ngữ cảnh được ưu tiên. Khi có gói VI, giữ cách xếp
hạng hoàn thành bằng mô hình đó. Danh sách còn bổ sung vốn âm tiết cho gõ vuốt.
Không thay `vi.ngram`, không chứa bigram/trigram và không ghi vào dữ liệu học cá nhân.
Module `telex` chỉ nhận từ vựng qua tham số; asset nằm ở module ứng dụng.

`app/src/main/assets/vi-phrases.tsv` chứa 35 mục cụm từ biên soạn trong dự án,
chuyển nguyên nội dung và thứ tự từ bảng hardcode cũ. Mỗi dòng là âm tiết đầu, tab,
rồi danh sách âm tiết theo sau phân cách dấu phẩy. File theo giấy phép dự án;
được nạp và truyền vào `Predictor`, giữ mức ưu tiên cũ. `Phrases` chỉ còn bộ đọc dữ liệu.

Tái tạo từ thư mục gốc repo (cùng môi trường Python của từ điển EN):

```sh
python3 -m venv /tmp/telehex-dictionaries
/tmp/telehex-dictionaries/bin/pip install wordfreq==3.1.1
/tmp/telehex-dictionaries/bin/python tools/build_vietnamese_dictionary.py
```

SHA-256: `28a056633867d1b147157c9b7883b6f4cc808bc11932e1b2f209353ae18cd3ca`.
Từ điển phái sinh giữ **CC BY-SA 4.0**; mã TeleHex giữ giấy phép của dự án.
Gói n-gram vẫn do người dùng tự dựng và nạp riêng: [language-pack.md](language-pack.md).
