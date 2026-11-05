#!/usr/bin/env python3
"""Sinh app/src/main/assets/emoji.tsv từ dữ liệu Unicode/CLDR.

  python3 tools/build_emoji.py emoji-test.txt emoji-data.txt annotations/vi.xml annotationsDerived/vi.xml

Nguồn (license Unicode-3.0, ghi công trong NOTICE):
  https://unicode.org/Public/emoji/16.0/emoji-test.txt
  https://unicode.org/Public/16.0.0/ucd/emoji/emoji-data.txt
  https://github.com/unicode-org/cldr/blob/main/common/annotations/vi.xml (+ annotationsDerived)

Mỗi dòng đầu ra: emoji<TAB>nhóm<TAB>tên tiếng Việt<TAB>từ khoá (phân cách |)<TAB>1 nếu nhận tông da.
Bỏ các biến thể tông da (áp tông da lúc chèn), giữ emoji fully-qualified.
"""
import re
import sys
import xml.etree.ElementTree as ET

GROUP_VI = {
    "Smileys & Emotion": "Mặt cười",
    "People & Body": "Người",
    "Animals & Nature": "Động vật & thiên nhiên",
    "Food & Drink": "Đồ ăn",
    "Travel & Places": "Du lịch",
    "Activities": "Hoạt động",
    "Objects": "Đồ vật",
    "Symbols": "Ký hiệu",
    "Flags": "Cờ",
}
MODIFIERS = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF}


def load_annotations(paths):
    names, keywords = {}, {}
    for p in paths:
        root = ET.parse(p).getroot()
        for a in root.iter("annotation"):
            cp = a.get("cp")
            if a.get("type") == "tts":
                names.setdefault(cp, a.text.strip())
            else:
                keywords.setdefault(cp, [k.strip() for k in a.text.split("|")])
    return names, keywords


def main():
    test, data, *ann = sys.argv[1:]
    bases = set()
    for line in open(data, encoding="utf-8"):
        if "Emoji_Modifier_Base" in line and not line.startswith("#"):
            rng = line.split(";")[0].strip()
            if ".." in rng:
                a, b = rng.split("..")
                bases.update(range(int(a, 16), int(b, 16) + 1))
            else:
                bases.add(int(rng, 16))
    names, keywords = load_annotations(ann)
    # Chỉ coi là "nhận tông da" khi chính emoji-test.txt liệt kê biến thể <cp đầu><tông da><phần còn lại>
    # (loại các emoji nhiều người như gia đình, vì chèn một modifier vào đó không phải chuỗi hợp lệ).
    fully = set()
    for line in open(test, encoding="utf-8"):
        if not line.startswith("#") and "fully-qualified" in line:
            fully.add(tuple(int(c, 16) for c in line.split(";")[0].split()))
    out = []
    group = None
    seen = set()
    for line in open(test, encoding="utf-8"):
        if line.startswith("# group:"):
            group = line.split(":", 1)[1].strip()
            continue
        if line.startswith("#") or ";" not in line or "fully-qualified" not in line:
            continue
        cps = [int(c, 16) for c in line.split(";")[0].split()]
        if any(c in MODIFIERS for c in cps):
            continue
        if group == "Component" or group not in GROUP_VI:
            continue
        emoji = "".join(chr(c) for c in cps)
        key = emoji.replace("️", "")
        if key in seen:
            continue
        seen.add(key)
        name = names.get(key, "")
        kws = keywords.get(key, [])
        mod = 1 if cps[0] in bases and (cps[0], 0x1F3FD, *cps[1:]) in fully else 0
        out.append(f"{emoji}\t{GROUP_VI[group]}\t{name}\t{'|'.join(kws)}\t{mod}")
    sys.stdout.write("\n".join(out) + "\n")
    print(f"{len(out)} emoji", file=sys.stderr)


if __name__ == "__main__":
    main()
