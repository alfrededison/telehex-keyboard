#!/usr/bin/env python3
"""Đo chất lượng mô hình n-gram trên tập test tách riêng: top-1/top-3 và keystroke savings.

  python3 tools/eval_ngram.py model.ngram test.txt [--max-sentences 2000]

Keystroke savings: giả định mỗi âm tiết gõ bằng số phím Telex của nó + 1 phím cách; nếu âm tiết
nằm trong top-3 gợi ý thì chỉ tốn 1 chạm. Dùng cùng cách tách câu/tokenize với build_ngram.py.
"""
import argparse
import math
import struct
import sys
from bisect import bisect_left, bisect_right

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from build_ngram import sentences  # noqa: E402


class Model:
    def __init__(self, path):
        data = open(path, "rb").read()
        assert data[:4] == b"THNG" and data[4] == 1
        pos = 5
        n = struct.unpack_from("<I", data, pos)[0]; pos += 4
        self.vocab = []
        for _ in range(n):
            ln = data[pos]; pos += 1
            self.vocab.append(data[pos:pos + ln].decode("utf-8")); pos += ln
        self.index = {w: i for i, w in enumerate(self.vocab)}
        self.uni = list(data[pos:pos + n]); pos += n
        m = struct.unpack_from("<I", data, pos)[0]; pos += 4
        self.bw1 = list(struct.unpack_from(f"<{m}I", data, pos)); pos += 4 * m
        self.bw2 = list(struct.unpack_from(f"<{m}I", data, pos)); pos += 4 * m
        self.bq = list(data[pos:pos + m]); pos += m
        k = struct.unpack_from("<I", data, pos)[0]; pos += 4
        self.tw1 = list(struct.unpack_from(f"<{k}I", data, pos)); pos += 4 * k
        self.tw2 = list(struct.unpack_from(f"<{k}I", data, pos)); pos += 4 * k
        self.tw3 = list(struct.unpack_from(f"<{k}I", data, pos)); pos += 4 * k
        self.tq = list(data[pos:pos + k]); pos += k
        self.tkey = list(zip(self.tw1, self.tw2))
        self.top = sorted(range(n), key=lambda i: self.uni[i])[:50]

    @staticmethod
    def p(q):
        return 2 ** (-q / 8)

    def predict(self, ctx, k=3):
        ids = [self.index.get(w) for w in ctx[-2:]]
        c1 = ids[-1] if ids else None
        c2 = ids[-2] if len(ids) >= 2 else None
        cands = set()
        bi = {}
        if c1 is not None:
            lo, hi = bisect_left(self.bw1, c1), bisect_right(self.bw1, c1)
            # Mọi hàng để chấm điểm; ứng viên là 50 hàng xác suất cao nhất (giống BinaryNgramModel.kt),
            # không phải N hàng đầu theo thứ tự chữ cái.
            for i in range(lo, hi):
                bi[self.bw2[i]] = self.p(self.bq[i])
            for i in sorted(range(lo, hi), key=lambda i: self.bq[i])[:50]:
                cands.add(self.bw2[i])
        tri = {}
        if c1 is not None and c2 is not None:
            lo, hi = bisect_left(self.tkey, (c2, c1)), bisect_right(self.tkey, (c2, c1))
            for i in range(lo, hi):
                tri[self.tw3[i]] = self.p(self.tq[i])
            for i in sorted(range(lo, hi), key=lambda i: self.tq[i])[:50]:
                cands.add(self.tw3[i])
        # Như BinaryNgramModel.kt: unigram phổ biến chỉ bù khi ứng viên theo ngữ cảnh còn ít. Điểm ở đó
        # còn được chia theo các bậc có dữ liệu, nhưng phép chia đó không đổi thứ hạng trong một ngữ cảnh.
        if len(cands) < 2 * k:
            cands.update(self.top)
        scored = [(0.1 * self.p(self.uni[w]) + 0.3 * bi.get(w, 0) + 0.6 * tri.get(w, 0), w) for w in cands]
        scored.sort(reverse=True)
        return [self.vocab[w] for _, w in scored[:k]]


def keystrokes(word):
    # xấp xỉ số phím Telex: mỗi chữ 1 phím, mỗi mũ/móc/đ thêm 1, dấu thanh thêm 1
    extra = sum(ch in "ăâêôơưđ" for ch in word)
    toned = any(ch in "áàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ" for ch in word)
    return len(word) + extra + (1 if toned else 0)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model"); ap.add_argument("test"); ap.add_argument("--max-sentences", type=int, default=2000)
    a = ap.parse_args()
    m = Model(a.model)
    n = top1 = top3 = 0
    keys_total = keys_saved = 0
    for si, sent in enumerate(sentences([a.test])):
        if si >= a.max_sentences:
            break
        for i, w in enumerate(sent):
            ctx = sent[max(0, i - 2):i]
            pred = m.predict(ctx)
            n += 1
            cost = keystrokes(w) + 1
            keys_total += cost
            if pred and pred[0] == w:
                top1 += 1
            if w in pred:
                top3 += 1
                keys_saved += cost - 1
    print(f"âm tiết: {n}  top-1: {top1 / n:.3f}  top-3: {top3 / n:.3f}  keystroke savings: {keys_saved / keys_total:.3f}")


if __name__ == "__main__":
    main()
