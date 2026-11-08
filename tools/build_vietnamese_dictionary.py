#!/usr/bin/env python3
"""Sinh asset tần suất VI từ wordfreq 3.1.1 (CC BY-SA 4.0; xem NOTICE).

  python3 -m pip install wordfreq==3.1.1
  python3 tools/build_vietnamese_dictionary.py

Giữ token chữ Latin/Việt dài tối đa 8 ký tự theo thứ tự phổ biến.
VietnameseVocabulary lọc âm tiết hợp lệ khi nạp trên luồng nền.
"""
import argparse
from importlib.metadata import version
from pathlib import Path
import re
import unicodedata

from wordfreq import iter_wordlist
from build_ngram import VI


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("-o", "--out", type=Path, default=Path(__file__).resolve().parents[1] /
                    "app/src/main/assets/vi-frequency.txt")
    args = ap.parse_args()
    if version("wordfreq") != "3.1.1":
        ap.error("Cần wordfreq==3.1.1 để tái lập dữ liệu")
    words = []
    seen = set()
    for raw in iter_wordlist("vi", wordlist="best"):
        word = unicodedata.normalize("NFC", raw.lower())
        if not 1 <= len(word) <= 8 or not re.fullmatch(f"[{VI}]+", word) or word in seen:
            continue
        words.append(word)
        seen.add(word)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(words) + "\n", encoding="utf-8")
    print(f"{len(words)} token → {args.out} ({args.out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
