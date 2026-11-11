# Gợi ý tiếng Anh offline

EN tách từ điển có sẵn và mô hình do người dùng nạp. Dữ liệu được đọc trên luồng nền:

- `app/src/main/assets/en-frequency.txt`: 50.000 từ và contraction theo độ phổ biến.
- `en.ngram`: mô hình từ 1–3 gram do người dùng tự dựng/nạp, cùng định dạng THNG và
  `BinaryNgramModel` với tiếng Việt. Bản mẫu dựng bên dưới có 36.906 từ, 49.441 bigram,
  29.248 trigram, 1.182.741 byte. File được lưu riêng trong bộ nhớ app sau khi nạp.

Khi đang gõ, bộ tìm tiền tố lấy đúng các từ khớp trong toàn bộ từ điển, giữ thứ tự phổ biến
và cộng phần xác suất tăng nhờ hai từ trước con trỏ. Ngữ cảnh không biết giữ xếp hạng từ điển.
Khi có gói EN, sau khoảng trắng mô hình dự đoán từ tiếp theo; đầu ô/câu lùi về unigram. Không đi qua ranh giới
câu và giữ contraction (`don't`, `I'm`, cả dấu nháy cong khi đọc ngữ cảnh).

Chỉ chèn khi chạm gợi ý, giữ phần chữ hoa/thường đã gõ, hỗ trợ khoá hoa và bảng `…`.
Đọc lại văn bản quanh con trỏ trước khi thay để tránh chọn nhầm khi editor cập nhật chậm.
Chưa nạp gói thì chỉ hoàn thành từ theo độ phổ biến. Không tự sửa, không học dữ liệu gõ, không ghi vào từ điển cá nhân tiếng Việt. Tắt ở ô
mật khẩu/email/URL hoặc editor cấm gợi ý; cùng tuỳ chọn Gợi ý trong Cài đặt điều khiển VI và EN.

## Nguồn và giấy phép

Danh sách từ phái sinh từ **wordfreq 3.1.1**, Robyn Speer, theo **CC BY-SA 4.0**:
[dự án](https://github.com/rspeer/wordfreq), [bản 3.1.1](https://pypi.org/project/wordfreq/3.1.1/),
[thông tin ghi công](https://github.com/rspeer/wordfreq/blob/master/NOTICE.md).
Giữ 50.000 token ASCII đầu tiên (chữ và contraction, tối đa 32 ký tự), chuẩn hoá nháy cong,
loại trùng. Đây là danh sách tần suất, có cả tên riêng và một số cách viết thông dụng trên mạng;
không phải từ điển kiểm tra chính tả.

N-gram chỉ dựng từ export **English sentences CC0** của cộng đồng **Tatoeba**, không dùng
export câu thông thường CC BY: [mô tả và giấy phép](https://tatoeba.org/en/downloads),
[file nguồn](https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences_CC0.tsv.bz2),
[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
Bản tải ngày 2026-11-08 có 41.515 dòng; sau tách và loại câu trùng còn 49.557 câu.
Chia theo SHA-256 của câu chuẩn hoá: 44.596 câu train, 4.961 câu test (không trùng câu).
File export được Tatoeba cập nhật hằng tuần; muốn tái lập đúng bản này phải giữ file tải gốc
và kiểm tra checksum bên dưới.

Ghi công từ điển nằm trong [NOTICE](../NOTICE) và `assets/en-NOTICE.txt` trong APK.
Danh sách từ phái sinh giữ CC BY-SA 4.0; bản mẫu n-gram Tatoeba giữ CC0, được dựng riêng và không kèm trong APK. Mã TeleHex giữ giấy phép dự án.

## Dựng lại

Chạy từ thư mục gốc repo. Build APK chỉ cần từ điển đã commit; mô hình không nằm trong Git.

```sh
python3 -m venv /tmp/telehex-en-venv
/tmp/telehex-en-venv/bin/pip install wordfreq==3.1.1
/tmp/telehex-en-venv/bin/python tools/build_english_dictionary.py

curl -fL https://downloads.tatoeba.org/exports/per_language/eng/eng_sentences_CC0.tsv.bz2 \
  -o /tmp/eng_sentences_CC0.tsv.bz2
python3 tools/prepare_english_corpus.py /tmp/eng_sentences_CC0.tsv.bz2 --out-dir /tmp/telehex-en
python3 tools/build_ngram.py /tmp/telehex-en/en-train.txt --language en \
  --max-vocab 50000 --min-bigram 2 --min-trigram 2 \
  -o /tmp/telehex-en/en.ngram
python3 tools/eval_ngram.py /tmp/telehex-en/en.ngram /tmp/telehex-en/en-test.txt \
  --language en --max-sentences 100000
python3 tools/eval_ngram.py /tmp/telehex-en/en.ngram /tmp/telehex-en/en-test.txt \
  --language en --max-sentences 100000 --unigram-only
python3 tools/test_ngram.py
```

Không đưa test trở lại train. Với corpus tiếng Anh khác, truyền text UTF-8 cho
`build_ngram.py --language en` và bảo đảm quyền phát hành mô hình phái sinh.
Tuỳ chọn mặc định vẫn là `--language vi` để giữ pipeline tiếng Việt.
Chép `en.ngram` sang điện thoại, mở **Cài đặt → Gợi ý → Nạp gói tiếng Anh (.ngram)…**
để chọn file (trên iPhone: **Nạp gói EN .ngram…**; tệp Safari tải về nằm ở iCloud Drive › Tải về, xem
[language-pack.md](language-pack.md#6-nạp-vào-bàn-phím)). **Xoá gói tiếng Anh đã nạp** đưa EN về hoàn thành từ theo độ phổ biến.
Gói VI và EN nạp/xoá riêng; gói sai định dạng hoặc gói EN chứa từ không đúng bảng chữ
sẽ bị từ chối trước khi thay file đang dùng. Gói không nằm trong bản sao lưu tuỳ chọn.
Repo bỏ qua mọi `*.ngram`, chỉ giữ fixture test nhỏ cho VI và EN; không commit corpus hay
mô hình người dùng. Fixture EN được dựng từ `telex/src/test/resources/tiny-en.txt` bằng
`tools/build_ngram.py --language en`. Module `telex` chỉ chứa thuật toán; từ điển ứng dụng
nằm trong `app/assets`, được đưa vào classpath **test** để kiểm thử.

SHA-256 của từ điển và bản mẫu đã dựng:

| File | SHA-256 |
| --- | --- |
| Export CC0 nguồn | `c2e2edd063a31cbbc8e416a4858225e934bbb0afff485031821e59f230ccf1ca` |
| `en-frequency.txt` | `184fd2d1d3db31d841dc3a62d71d51ffd8f369036a2f4f0f8c68f2dd55083e12` |
| `en.ngram` | `9b69c369f68f61d45f1a24df47f5fa5aa99bc253336425452817ab8e95324a43` |
| `en-train.txt` | `e61998b0461977e08457175fc48456f6a439cca9b3677ba964dc6b61ffc190a1` |
| `en-test.txt` | `eef372b66911972467ce1d2d60ab1571d139d2e84f9c12bab7ddad7d62218a8e` |

## Chất lượng và giới hạn

Trên 56.848 token của tập test riêng, mô hình đạt khoảng **14,2% top-1, 22,9% top-3**;
unigram cùng mô hình đạt **2,2% / 6,8%**. Đây là phép đo dự đoán từ tiếp theo bằng mô hình
mẫu Tatoeba, không đo hoàn thành tiền tố hay khả năng tiết kiệm phím thực tế của một người dùng.
Câu Tatoeba có các mẫu gần nhau; kết quả này không đại diện cho mọi cuộc trò chuyện.

Corpus CC0 còn nhỏ, chủ yếu câu ví dụ; có thể gợi ý thiếu tự nhiên ở ngữ cảnh lạ, thiếu thuật ngữ
hoặc tên riêng. Từ điển lớn bổ sung vốn từ khi hoàn thành nhưng không tự tạo thêm ngữ cảnh.
Muốn nâng chất lượng tiếp, bổ sung corpus được phép phát hành và so sánh trên tập test cố định.
