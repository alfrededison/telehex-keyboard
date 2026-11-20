package com.telehex.typing

/** Xoá theo từ bằng cách kéo phím cách sang trái, dùng chung cho hai app. */
object WordStep {
    /**
     * Chỉ số bắt đầu của [words] bước từ cuối [text] (bỏ khoảng trắng cuối). Mỗi dấu xuống dòng là một bước riêng, nên kéo
     * tiếp sẽ lên dòng trên mà không phải nhả tay: "abc\n  " một bước xoá hai khoảng trắng cùng dấu xuống dòng.
     */
    fun wordStart(text: CharSequence, words: Int): Int {
        var i = text.length
        repeat(words) {
            while (i > 0 && text[i - 1].isWhitespace() && text[i - 1] != '\n') i--
            if (i > 0 && text[i - 1] == '\n') i--
            else while (i > 0 && !text[i - 1].isWhitespace()) i--
        }
        return i
    }

    /** Đích của "kéo quá mép trái": đầu dòng chứa con trỏ (cuối [text]); con trỏ đã ở đầu dòng thì lấy đầu dòng trên. */
    fun lineStart(text: CharSequence): Int {
        var i = text.length
        while (i > 0 && text[i - 1] != '\n') i--
        if (i == text.length && i > 0) {
            i--
            while (i > 0 && text[i - 1] != '\n') i--
        }
        return i
    }

    /** Đoạn cuối [textBefore] sẽ bị xoá: [words] bước từ; kéo quá mép trái ([toLineStart]) thì ít nhất tới đầu dòng. */
    fun deletion(textBefore: String, words: Int, toLineStart: Boolean): String {
        val start = wordStart(textBefore, words).let { if (toLineStart) minOf(it, lineStart(textBefore)) else it }
        return textBefore.substring(start)
    }
}
