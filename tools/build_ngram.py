#!/usr/bin/env python3
"""Dựng mô hình n-gram âm tiết (1–3) cho TeleHex từ corpus text tiếng Việt.

Cách dùng:
  python3 tools/build_ngram.py corpus1.txt corpus2.txt ... -o app/src/main/assets/vi.ngram

Đầu vào: file text thuần (UTF-8), ví dụ dump Wikipedia tiếng Việt đã bóc markup
(CC-BY-SA-4.0: mô hình sinh ra phải ghi công và giữ cùng điều khoản) hoặc corpus tin tức mở.

Thuật toán: đếm n-gram theo âm tiết (chữ thường, chỉ ký tự Latin/Việt), cắt tỉa theo ngưỡng,
làm trơn Kneser–Ney nội suy (discount mặc định 0.75, đổi bằng --discount), lượng tử hoá xác suất thành 8 bit
(q = round(-log2 p × 8), clamp 255), ghi theo định dạng `BinaryNgramModel` (xem mô tả trong
telex/.../predict/BinaryNgramModel.kt). Mục tiêu < 20 MB: chỉnh --min-bigram, --min-trigram và --max-vocab.
Hướng dẫn đầy đủ: docs/language-pack.md.
"""
import argparse
import math
import unicodedata
import re
import struct
import sys
from collections import Counter, defaultdict

VI = "a-zA-Zàáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ" \
     "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ"
TOKEN = re.compile(f"[{VI}]+|[.!?\n]")
BOS = "<s>"


def sentences(paths):
    for path in paths:
        with open(path, encoding="utf-8", errors="ignore") as f:
            buf = []
            for line in f:
                for tok in TOKEN.findall(unicodedata.normalize("NFC", line)):
                    if tok in ".!?\n":
                        if buf:
                            yield buf
                            buf = []
                    else:
                        buf.append(tok.lower())
            if buf:
                yield buf


def q8(p):
    if p <= 0:
        return 255
    return max(0, min(255, int(round(-math.log2(p) * 8))))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("corpus", nargs="+")
    ap.add_argument("-o", "--out", required=True)
    ap.add_argument("--max-vocab", type=int, default=12000)
    ap.add_argument("--min-bigram", type=int, default=3)
    ap.add_argument("--min-trigram", type=int, default=4)
    ap.add_argument("--discount", type=float, default=0.75)
    args = ap.parse_args()

    uni, bi, tri = Counter(), Counter(), Counter()
    for sent in sentences(args.corpus):
        words = [BOS, BOS] + sent
        for i in range(2, len(words)):
            w = words[i]
            uni[w] += 1
            bi[(words[i - 1], w)] += 1
            tri[(words[i - 2], words[i - 1], w)] += 1
    print(f"đọc xong: {sum(uni.values())} âm tiết, {len(uni)} loại", file=sys.stderr)

    vocab = sorted(w for w, _ in uni.most_common(args.max_vocab))
    vset = set(vocab)
    vocab.sort(key=lambda s: s.encode("utf-8"))
    index = {w: i for i, w in enumerate(vocab)}
    D = args.discount

    # Kneser–Ney nội suy
    bi = Counter({k: v for k, v in bi.items() if k[1] in vset and (k[0] in vset or k[0] == BOS)})
    tri = Counter({k: v for k, v in tri.items() if k[2] in vset and (k[1] in vset or k[1] == BOS) and (k[0] in vset or k[0] == BOS)})
    cont = Counter()            # số ngữ cảnh khác nhau đứng trước w (cho unigram KN)
    for (a, b) in bi:
        cont[b] += 1
    total_cont = sum(cont.values()) or 1
    p_uni = {w: cont[w] / total_cont for w in vocab}

    bi_ctx_total = defaultdict(int)
    bi_ctx_types = defaultdict(int)
    for (a, b), c in bi.items():
        bi_ctx_total[a] += c
        bi_ctx_types[a] += 1

    def p_bi(a, b):
        c = bi.get((a, b), 0)
        tot = bi_ctx_total.get(a, 0)
        if tot == 0:
            return p_uni.get(b, 0.0)
        lam = D * bi_ctx_types[a] / tot
        return max(c - D, 0) / tot + lam * p_uni.get(b, 0.0)

    tri_ctx_total = defaultdict(int)
    tri_ctx_types = defaultdict(int)
    for (a, b, c3), c in tri.items():
        tri_ctx_total[(a, b)] += c
        tri_ctx_types[(a, b)] += 1

    def p_tri(a, b, c3):
        c = tri.get((a, b, c3), 0)
        tot = tri_ctx_total.get((a, b), 0)
        if tot == 0:
            return p_bi(b, c3)
        lam = D * tri_ctx_types[(a, b)] / tot
        return max(c - D, 0) / tot + lam * p_bi(b, c3)

    bi_rows = sorted(
        (index[a], index[b], q8(p_bi(a, b)))
        for (a, b), c in bi.items() if c >= args.min_bigram and a != BOS
    )
    tri_rows = sorted(
        (index[a], index[b], index[c3], q8(p_tri(a, b, c3)))
        for (a, b, c3), c in tri.items() if c >= args.min_trigram and a != BOS and b != BOS
    )

    with open(args.out, "wb") as f:
        f.write(b"THNG" + bytes([1]))
        f.write(struct.pack("<I", len(vocab)))
        for w in vocab:
            b = w.encode("utf-8")
            f.write(bytes([len(b)]) + b)
        f.write(bytes(q8(p_uni[w]) for w in vocab))
        f.write(struct.pack("<I", len(bi_rows)))
        f.write(struct.pack(f"<{len(bi_rows)}I", *[r[0] for r in bi_rows]))
        f.write(struct.pack(f"<{len(bi_rows)}I", *[r[1] for r in bi_rows]))
        f.write(bytes(r[2] for r in bi_rows))
        f.write(struct.pack("<I", len(tri_rows)))
        f.write(struct.pack(f"<{len(tri_rows)}I", *[r[0] for r in tri_rows]))
        f.write(struct.pack(f"<{len(tri_rows)}I", *[r[1] for r in tri_rows]))
        f.write(struct.pack(f"<{len(tri_rows)}I", *[r[2] for r in tri_rows]))
        f.write(bytes(r[3] for r in tri_rows))
    print(f"ghi {args.out}: vocab={len(vocab)} bigram={len(bi_rows)} trigram={len(tri_rows)}", file=sys.stderr)


if __name__ == "__main__":
    main()
