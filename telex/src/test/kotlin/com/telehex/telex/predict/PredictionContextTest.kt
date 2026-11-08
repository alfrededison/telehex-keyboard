package com.telehex.telex.predict

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer

class PredictionContextTest {
    @Test fun takesTwoSyllablesAndHandlesPunctuationAndVietnameseUnicode() {
        assertEquals(listOf("tôi", "tìm"), PredictionContext.before("hôm nay tôi tìm "))
        assertEquals(listOf("bạn", "tìm"), PredictionContext.before("bạn, tìm "))
        assertEquals(listOf("tôi", "tìm"), PredictionContext.before(Normalizer.normalize("tôi tìm ", Normalizer.Form.NFD)))
    }

    @Test fun stopsAtSentenceBoundariesAndIgnoresEmptyContext() {
        for (boundary in listOf(".", "!", "?", "\n")) {
            assertEquals(listOf("tìm"), PredictionContext.before("lạch cạch${boundary} tìm "))
            assertEquals(emptyList<String>(), PredictionContext.before("tôi tìm$boundary "))
        }
        assertEquals(emptyList<String>(), PredictionContext.before(""))
    }
}
