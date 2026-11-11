package com.telehex.portable

import com.telehex.telex.*
import kotlin.test.*

class KeyboardVocabularyTest {
    private fun vocabulary() = KeyboardVocabulary("help\nhello", "xin\nchào\nbạn\nngười", "")
    @Test fun learnedContextRoundtripAndForget() {
        val source = vocabulary()
        repeat(5) { source.learn("xin ", "chào") }
        assertEquals("chào", source.nextWords("xin ", false).first())
        val copy = vocabulary()
        assertTrue(copy.restorePersonal(source.personalData()))
        assertEquals(source.personalData(), copy.personalData())
        copy.forget("chào")
        assertFalse(copy.personalData().contains("\tchào\t"))
        assertFalse(copy.restorePersonal("not a personal model"))
        assertTrue(copy.restorePersonal(""))
    }
    @Test fun invalidPacksDoNotEscapeNativeBoundary() {
        for (data in listOf(byteArrayOf(), ByteArray(17), ByteArray(18) { -1 })) {
            assertFalse(KeyboardLanguagePack.load(data, false).valid)
            assertFalse(KeyboardLanguagePack.load(data, true).valid)
        }
    }
    private fun pack(word: String): ByteArray {
        val bytes = word.encodeToByteArray()
        return "THNG".encodeToByteArray() + byteArrayOf(1,1,0,0,0,bytes.size.toByte()) + bytes + byteArrayOf(8,0,0,0,0,0,0,0,0)
    }
    @Test fun packsAreIndependentAndCanBeRemoved() {
        val vocab = vocabulary()
        val en = KeyboardLanguagePack.load(pack("keyboard"),true)
        assertTrue(en.valid)
        assertFalse(KeyboardLanguagePack.load(pack("người"),true).valid)
        assertTrue(KeyboardLanguagePack.load(pack("người"),false).valid)
        vocab.setLanguagePack(en,true)
        assertEquals(listOf("keyboard"),vocab.suggestions("key","key",true))
        vocab.learn("xin ","chào")
        vocab.clearPersonal()
        assertEquals(listOf("keyboard"),vocab.suggestions("key","key",true))
        vocab.setLanguagePack(null,true)
        assertTrue(vocab.suggestions("key","key",true).isEmpty())
    }
    @Test fun glideRequiresRealTraceAndNeverLearnsUntilChosen() {
        val vocab = vocabulary()
        val initial = vocab.personalData()
        val centers = "x:0,0\ni:1,0\nn:2,0\nc:0,1\nh:1,1\na:2,1\no:3,1"
        assertTrue(vocab.glide("0,0;1,0;2,0",centers,"",false).contains("xin"))
        assertTrue(vocab.glide("NaN,0;inf,1",centers,"",false).isEmpty())
        assertEquals(initial,vocab.personalData())
    }
    @Test fun englishGlideUsesTheEnglishDictionaryAndContractions() {
        val vocab = KeyboardVocabulary("help\nhello\ndon't", "xin\nchào", "")
        val centers = "h:0,0\ne:1,0\nl:2,0\np:3,0\no:4,0\nd:0,1\nn:1,1\nt:2,1\nx:3,1\ni:4,1"
        assertEquals("help", vocab.glide("0,0;1,0;2,0;3,0",centers,"",true).first())
        assertTrue(vocab.glide("0,0;1,0;2,0;3,0",centers,"",false).none { it == "help" })
        // Lướt "dont" (không có dấu nháy) ra don't.
        assertTrue(vocab.glide("0,1;4,0;1,1;2,1",centers,"",true).contains("don't"))
        assertEquals(listOf("don't"), vocab.suggestions("dont","dont",true))
    }
}
