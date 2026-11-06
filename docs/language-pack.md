# Dựng gói ngôn ngữ (`.ngram`)

Gợi ý âm tiết tiếp theo trên thanh gợi ý trộn ba nguồn: mô hình cá nhân học từ chính bạn trên máy, một bảng cụm từ nhỏ có sẵn trong app, và một mô hình n-gram âm tiết dựng từ văn bản tiếng Việt (gói ngôn ngữ). APK không kèm gói ngôn ngữ, và app không có quyền INTERNET nên không tự tải. Bạn dựng gói trên máy tính theo hướng dẫn này, rồi chép file `.ngram` sang điện thoại và nạp trong Cài đặt.

Không có gói ngôn ngữ thì bàn phím vẫn gợi ý được, nhưng chỉ từ những gì bạn đã gõ và bảng cụm từ.

## Cần có

- Python 3.9 trở lên. Bước dựng không cần thư viện ngoài; riêng Wikipedia cần cài `wikiextractor`.
- Khoảng 550 MB đĩa cho mỗi corpus Leipzig 1 triệu câu (file nén, file câu và file đã làm sạch).
- RAM: xem bảng số liệu ở cuối. Bộ đếm viết bằng Python thuần nên tốn bộ nhớ theo số câu.

## 1. Chọn corpus

Corpus là văn bản tiếng Việt thuần (UTF-8). File `.ngram` dựng từ corpus là sản phẩm phái sinh của corpus đó: dùng cho riêng bạn thì không sao, còn nếu chia sẻ hay phát hành file `.ngram` thì phải theo giấy phép của nguồn.

| Nguồn | Nội dung | Giấy phép | Nhận xét |
|---|---|---|---|
| [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download) | Câu tin tức, web, Wikipedia tiếng Việt, đã tách sẵn mỗi dòng một câu | Leipzig ghi CC BY cho bộ dữ liệu theo [điều khoản chung](https://wortschatz.uni-leipzig.de/en/usage), nhưng câu gốc vẫn thuộc bản quyền nơi đăng. Kiểm tra lại trên trang của từng file trước khi phát hành | Sạch và dễ dùng nhất. Nên bắt đầu từ đây |
| Wikipedia tiếng Việt (bản dump) | Toàn bộ bài viết | CC BY-SA 4.0: mô hình phát hành phải ghi công và giữ CC BY-SA | Rất nhiều bài sơ khai do bot tạo (loài sinh vật, địa danh) làm lệch thống kê; nên trộn với nguồn khác |
| Văn bản của chính bạn | Tin nhắn, ghi chú bạn tự xuất ra | Của bạn | Sát với cách gõ nhất. Chỉ dùng cho bản riêng, đừng phát hành |

Hiện chưa tìm thấy corpus hội thoại hay tin nhắn tiếng Việt nào có giấy phép mở rõ ràng. Phần văn phong chat do mô hình cá nhân trên máy tự học dần.

Các file tiếng Việt 1 triệu câu trên máy chủ Leipzig (kiểm tra ngày 2026-11-05):

| File | Nội dung | Dung lượng tải |
|---|---|---|
| `vie_news_2022_1M` | Tin tức 2022 | 234 MB |
| `vie_news_2020_1M` | Tin tức 2020 | 231 MB |
| `vie-vn_web_2015_1M` | Web tên miền .vn, gồm cả diễn đàn | 240 MB |
| `vie_mixed_2014_1M` | Trộn nhiều nguồn | 306 MB |
| `vie_wikipedia_2021_1M` | Wikipedia | 217 MB |

Mỗi bộ còn có bản nhỏ hơn (`_300K`, `_100K`, `_30K`, `_10K`) nếu máy ít RAM. Để gợi ý gần với văn gõ hằng ngày, nên trộn một bộ tin tức với bộ web.

## 2. Tải và làm sạch

Mọi file corpus và file kết quả đều nằm trong một thư mục riêng ngoài repo, ở đây là `~/telehex-corpus`, để không lỡ commit chúng.

**Leipzig.** Mỗi file nén chứa `*-sentences.txt`, mỗi dòng có dạng `số<TAB>câu`. Chỉ giữ cột câu:

```
mkdir -p ~/telehex-corpus && cd ~/telehex-corpus
for n in vie_news_2022_1M vie-vn_web_2015_1M; do
  curl -fLO "https://downloads.wortschatz-leipzig.de/corpora/$n.tar.gz" || break
  tar -xzf "$n.tar.gz" "$n/$n-sentences.txt"
  cut -f2 "$n/$n-sentences.txt" > "$n.txt"
done
```

**Wikipedia** (tuỳ chọn). Bản dump nặng vài GB. Cài `wikiextractor` trong một môi trường ảo, vì Homebrew Python và Debian/Ubuntu đời mới chặn `pip install` vào Python hệ thống:

```
cd ~/telehex-corpus
curl -fLO https://dumps.wikimedia.org/viwiki/latest/viwiki-latest-pages-articles.xml.bz2
python3 -m venv .venv && . .venv/bin/activate
pip install wikiextractor
python -m wikiextractor.WikiExtractor viwiki-latest-pages-articles.xml.bz2 -o wiki -q
cat wiki/*/wiki_* | grep -v '^<' > wiki.txt
deactivate
```

Bước `grep -v '^<'` bỏ các dòng thẻ `<doc id=… url=… title=…>` và `</doc>` mà WikiExtractor sinh ra. Nếu không bỏ, những chữ như "doc", "url", "https" sẽ lọt vào từ vựng.

## 3. Tách tập kiểm tra

Giữ riêng khoảng 1% số câu không dùng để dựng, để đo chất lượng cho công bằng. File của Leipzig sắp xếp câu theo thứ tự chữ cái, nên đừng lấy các dòng cuối. Hãy chia ngẫu nhiên với hạt giống cố định, để lần sau chia lại vẫn ra đúng như cũ:

```
cd ~/telehex-corpus
python3 - vie_news_2022_1M.txt news-train.txt test.txt <<'PY'
import random, sys
src, train, test = sys.argv[1:]
random.seed(20261105)
with open(src, encoding="utf-8") as f, \
     open(train, "w", encoding="utf-8") as tr, open(test, "w", encoding="utf-8") as te:
    for line in f:
        (te if random.random() < 0.01 else tr).write(line)
PY
```

## 4. Dựng

Chạy từ thư mục gốc của repo TeleHex. Liệt kê mọi file corpus muốn dùng; thêm `~/telehex-corpus/wiki.txt` nếu đã làm bước Wikipedia:

```
python3 tools/build_ngram.py ~/telehex-corpus/news-train.txt ~/telehex-corpus/vie-vn_web_2015_1M.txt \
  --min-bigram 6 --min-trigram 8 -o ~/telehex-corpus/vi.ngram
```

Với hai corpus 1 triệu câu, ngưỡng mặc định cho ra file khoảng 29 MB. Ngưỡng 6 và 8 như trên giảm còn một nửa mà gần như không mất chất lượng; xem bảng số liệu ở cuối.

Các tuỳ chọn:

| Tuỳ chọn | Mặc định | Ý nghĩa |
|---|---|---|
| `--max-vocab` | 12000 | Số âm tiết giữ trong từ vựng, phổ biến nhất trước |
| `--min-bigram` | 3 | Bỏ cặp âm tiết xuất hiện ít hơn số lần này |
| `--min-trigram` | 4 | Bỏ bộ ba âm tiết xuất hiện ít hơn số lần này |
| `--discount` | 0.75 | Hệ số làm trơn Kneser–Ney |

File quá lớn thì tăng `--min-bigram` và `--min-trigram`. Nên giữ file dưới 20 MB.

Cách script đọc văn bản:

- Chuẩn hoá Unicode (NFC) rồi chuyển về chữ thường.
- Mỗi chuỗi chữ cái Latin hoặc tiếng Việt liền nhau là một âm tiết. Số và ký hiệu bị bỏ, nên hai chữ đứng hai bên một con số sẽ thành liền nhau ("năm 2022 có" thành "năm có").
- Câu kết thúc ở `.`, `!`, `?` hoặc xuống dòng, kể cả dấu chấm trong số thập phân.
- URL không được lọc: `https://vi.wikipedia.org` thành các âm tiết "https", "vi", "wikipedia", "org". Hãy bỏ URL khỏi corpus trước nếu nguồn có nhiều.

## 5. Đo chất lượng

```
python3 tools/eval_ngram.py ~/telehex-corpus/vi.ngram ~/telehex-corpus/test.txt --max-sentences 100000
```

Mặc định script chỉ chấm 2.000 câu đầu của tập kiểm tra (tính theo câu sau khi tách, không phải theo dòng). Tham số `--max-sentences 100000` ở trên đủ lớn để chấm hết tập kiểm tra 1%.

Kết quả gồm tỉ lệ âm tiết đúng ở gợi ý đầu (top-1), nằm trong 3 gợi ý (top-3), và keystroke savings: phần trăm số phím tiết kiệm được nếu người gõ luôn chạm gợi ý khi âm tiết đúng nằm trong 3 gợi ý. Dùng cùng tập kiểm tra và cùng `--max-sentences` khi so sánh hai mô hình.

## 6. Nạp vào bàn phím

1. Chép `vi.ngram` sang điện thoại, ví dụ vào thư mục Download.
2. Mở app TeleHex → Cài đặt → "Nạp gói ngôn ngữ (.ngram)…" → chọn file.
3. App kiểm tra cấu trúc file trước khi dùng. File sai định dạng, bị cắt ngắn hoặc trỏ tới từ không có trong từ vựng sẽ bị từ chối mà không làm hỏng bàn phím. App không có checksum, nên không phát hiện được phần xác suất bị hỏng lặng lẽ.

Muốn bỏ thì chọn "Xoá gói ngôn ngữ đã nạp". Để đóng gói sẵn vào APK khi tự build, đặt file vào `app/src/main/assets/vi.ngram`. Repo bỏ qua mọi file `*.ngram` để không lỡ commit; chỉ commit (bằng `git add -f`) khi giấy phép của corpus cho phép phát hành.

## Số liệu tham khảo

Đo ngày 2026-11-05 trên MacBook 16 GB RAM, Python 3.12.

- **Corpus dựng:** `vie_news_2022_1M` trừ phần kiểm tra (990.045 câu) cộng `vie-vn_web_2015_1M` (1.000.000 câu). Tổng 49,6 triệu âm tiết, 153.391 loại khác nhau, từ vựng giữ 12.000.
- **Tập kiểm tra:** 9.955 dòng tin tức chia ngẫu nhiên như bước 3, tách thành 10.723 câu và 248.079 âm tiết. Đo bằng lệnh ở bước 5, chấm toàn bộ tập.
- **Tài nguyên:** dựng mất khoảng 1 phút 45 giây, RAM cao nhất khoảng 4,6 GB. Đo chất lượng mất khoảng 1 phút.

| `--min-bigram` / `--min-trigram` | Dung lượng | Top-1 | Top-3 | Keystroke savings |
|---|---|---|---|---|
| 3 / 4 (mặc định) | 29,2 MB | 27,0% | 38,7% | 31,6% |
| 6 / 8 (khuyên dùng) | 14,6 MB | 26,4% | 37,9% | 30,9% |
| 10 / 12 | 9,6 MB | 26,0% | 37,3% | 30,4% |

Các con số trên chỉ đo mô hình gốc. Trên điện thoại, bàn phím còn trộn thêm mô hình cá nhân học từ chính bạn.
