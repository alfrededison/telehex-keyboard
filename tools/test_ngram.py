#!/usr/bin/env python3
"""Kiểm tra tokenize VI/EN, chia corpus và round-trip mô hình EN."""
import bz2
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from build_ngram import text_sentences
from eval_ngram import Model
from prepare_english_corpus import prepare


class NgramTests(unittest.TestCase):
    def test_english_contractions_and_foreign_or_structured_boundaries(self):
        self.assertEqual([["i'm", "here"], ["we", "don't", "know"]],
                         list(text_sentences("I’m here. We don't know!", "en")))
        self.assertEqual([["see"], ["again"], ["now"]],
                         list(text_sentences("see café again foo123 now", "en")))

    def test_vietnamese_default_preserves_syllables_and_sentence_boundaries(self):
        self.assertEqual([["tôi", "đang", "học"], ["bạn", "khỏe", "không"]],
                         list(text_sentences("Tôi đang học. Bạn khỏe không?")))

    def test_export_deduplicates_before_splitting_and_rejects_wrong_format(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / "cc0.tsv.bz2"
            rows = ["1\teng\tI'm here.\t2026-11-07", "2\teng\tI’m here!\t2026-11-07",
                    "3\teng\tThank you.\t2026-11-07"]
            source.write_bytes(bz2.compress("\n".join(rows).encode()))
            prepare(source, root)
            train = set((root / "en-train.txt").read_text().splitlines())
            test = set((root / "en-test.txt").read_text().splitlines())
            self.assertFalse(train & test)
            self.assertEqual({"i'm here", "thank you"}, train | test)
            source.write_bytes(bz2.compress(b"1\teng\tNormal export lacks CC0 date field"))
            with self.assertRaises(ValueError):
                prepare(source, root)

    def test_builder_round_trip_retains_contractions_and_learns_english_context(self):
        with tempfile.TemporaryDirectory() as tmp:
            corpus = Path(tmp) / "train.txt"
            model = Path(tmp) / "en.ngram"
            corpus.write_text("Thank you. I'm here. We don't know.\n" * 10)
            subprocess.run([sys.executable, str(Path(__file__).with_name("build_ngram.py")),
                            str(corpus), "--language", "en", "-o", str(model)],
                           check=True, capture_output=True)
            loaded = Model(model)
            self.assertIn("i'm", loaded.vocab)
            self.assertIn("don't", loaded.vocab)
            self.assertNotIn("don", loaded.vocab)
            self.assertEqual("you", loaded.predict(["thank"])[0])


if __name__ == "__main__":
    unittest.main()
