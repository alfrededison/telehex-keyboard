#!/usr/bin/env python3
"""Chuyển export eng_sentences_CC0.tsv.bz2 của Tatoeba thành train/test text.

Chỉ dùng export CC0 (id, lang, text, date); không dùng export CC BY thông thường.
Chuẩn hoá, loại câu trùng và chia theo hash câu: 90% train, 10% test.
"""
import argparse
import bz2
import hashlib
from pathlib import Path

from build_ngram import text_sentences


def prepare(source, out_dir):
    out_dir.mkdir(parents=True, exist_ok=True)
    seen = set()
    train, test = [], []
    with bz2.open(source, "rt", encoding="utf-8") as f:
        for line in f:
            fields = line.rstrip("\n").split("\t")
            if len(fields) != 4 or fields[1] != "eng":
                raise ValueError("Cần export CC0 tiếng Anh gồm 4 cột")
            for tokens in text_sentences(fields[2], "en"):
                if not 2 <= len(tokens) <= 80:
                    continue
                sentence = " ".join(tokens)
                if sentence in seen:
                    continue
                seen.add(sentence)
                digest = hashlib.sha256(sentence.encode()).digest()
                (test if int.from_bytes(digest[:4], "big") % 10 == 0 else train).append(sentence)
    for name, rows in (("train", train), ("test", test)):
        (out_dir / f"en-{name}.txt").write_text("".join(row + "\n" for row in rows), encoding="utf-8")
    print(f"source sha256: {hashlib.sha256(source.read_bytes()).hexdigest()}")
    print(f"unique sentences: {len(seen)}; train: {len(train)}; test: {len(test)}")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("source", type=Path)
    ap.add_argument("--out-dir", type=Path, required=True)
    args = ap.parse_args()
    prepare(args.source, args.out_dir)


if __name__ == "__main__":
    main()
