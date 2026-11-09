"""Checks the Google Play "What's new" text built from a CHANGELOG section."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from play_whatsnew import LIMIT, MORE, whatsnew  # noqa: E402


class WhatsNewTest(unittest.TestCase):
    def test_short_section_is_kept_whole_as_plain_text(self):
        body = "- Sửa **lỗi** gõ `ee` → ê.\n- Xem [tài liệu](docs/a.md).\n"
        self.assertEqual("• Sửa lỗi gõ ee → ê.\n• Xem tài liệu.", whatsnew(body))

    def test_wrapped_items_and_subheadings(self):
        body = "### Thêm\n\n- Dòng một\n  tiếp tục.\n- Hai.\n"
        self.assertEqual("• Dòng một tiếp tục.\n• Hai.", whatsnew(body))

    def test_long_section_keeps_whole_items_within_the_limit(self):
        body = "\n".join("- Mục %d " % i + "x" * 80 for i in range(10))
        text = whatsnew(body)
        self.assertLessEqual(len(text), LIMIT)
        self.assertTrue(text.endswith(MORE))
        self.assertIn("• Mục 0 ", text)
        for line in text.splitlines()[:-1]:
            self.assertTrue(line.endswith("x" * 80))

    def test_single_huge_item_is_shortened(self):
        text = whatsnew("- " + "từ " * 400)
        self.assertLessEqual(len(text), LIMIT)
        self.assertIn("…\n", text)
        self.assertTrue(text.endswith(MORE))

    def test_real_changelog_sections_fit(self):
        log = (Path(__file__).resolve().parent.parent / "CHANGELOG.md").read_text(encoding="utf-8")
        for section in log.split("\n## ")[1:]:
            body = section.split("\n", 1)[1] if "\n" in section else ""
            if body.strip():
                self.assertLessEqual(len(whatsnew(body)), LIMIT, section.split("\n", 1)[0])


if __name__ == "__main__":
    unittest.main()
