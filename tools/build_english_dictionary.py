#!/usr/bin/env python3
"""Dựng từ điển EN offline theo thứ tự phổ biến từ wordfreq 3.1.1.

  python3 -m pip install wordfreq==3.1.1
  python3 tools/build_english_dictionary.py

Dữ liệu phái sinh CC BY-SA 4.0; xem NOTICE và docs/english-dictionary.md.
Không cần Python/wordfreq khi build APK hoặc chạy app.
"""
import argparse
from importlib.metadata import version
from pathlib import Path
import re

from wordfreq import iter_wordlist


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--limit", type=int, default=50000)
    ap.add_argument("-o", "--out", type=Path, default=Path(__file__).resolve().parents[1] /
                    "app/src/main/assets/en-frequency.txt")
    args = ap.parse_args()
    if version("wordfreq") != "3.1.1":
        ap.error("Cần wordfreq==3.1.1 để tái lập dữ liệu")
    if args.limit <= 0:
        ap.error("--limit phải lớn hơn 0")
    words = []
    seen = set()
    for raw in iter_wordlist("en", wordlist="large"):
        word = raw.replace("’", "'")
        # Cùng bảng chữ với EnglishPredictor; bỏ số, URL, dấu nối và token dài.
        if len(word) > 32 or not re.fullmatch(r"[a-z]+(?:'[a-z]+)*", word) or word in seen:
            continue
        words.append(word)
        seen.add(word)
        if len(words) == args.limit:
            break
    if len(words) != args.limit:
        ap.error(f"Nguồn chỉ có {len(words)} từ phù hợp")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(words) + "\n", encoding="utf-8")
    print(f"{len(words)} từ → {args.out} ({args.out.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
