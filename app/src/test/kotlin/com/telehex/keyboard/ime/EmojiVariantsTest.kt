package com.telehex.keyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiVariantsTest {
    private val available = setOf("🧑", "👩", "👨", "🧑‍💻", "👩‍💻", "👨‍💻", "🤷", "🤷‍♀️", "🤷‍♂️", "👍", "❤️")
    private val variants = EmojiVariants(available, available - "❤️") { true }

    @Test fun changesNeutralPeopleAndRolesAndCombinesSkinTone() {
        assertEquals("👩‍💻", variants.apply("🧑‍💻", 1, 0))
        assertEquals("👨‍💻", variants.apply("🧑‍💻", 2, 0))
        assertEquals("🤷🏽‍♀️", variants.apply("🤷", 1, 3))
        assertEquals("👨🏽‍💻", variants.apply("🧑‍💻", 2, 3))
    }

    @Test fun preservesExplicitGenderAndUngenderedEmoji() {
        assertEquals("👩‍💻", variants.apply("👩‍💻", 2, 0))
        assertEquals("👍", variants.apply("👍", 1, 0))
        assertEquals("❤️", variants.apply("❤️", 2, 4))
        assertEquals("🧑‍💻", variants.apply("🧑‍💻", 0, 0))
        assertEquals("👩🏽‍💻", variants.apply("👩🏽‍💻", 1, 3))
    }

    @Test fun fallsBackWhenVariantIsMissingOrFontCannotDrawIt() {
        assertEquals("🧓", variants.apply("🧓", 1, 0))
        val limited = EmojiVariants(available, available) { it != "👩‍💻" && "🏽" !in it }
        assertEquals("🧑‍💻", limited.apply("🧑‍💻", 1, 3))
        assertEquals("🤷‍♀️", limited.apply("🤷", 1, 3))
    }

    @Test fun placesGenderBeforeDirectionForWalkingRunningAndKneeling() {
        for (person in listOf("🚶", "🏃", "🧎")) {
            val neutral = "$person‍➡️"
            val female = "$person‍♀️‍➡️"
            val male = "$person‍♂️‍➡️"
            val directional = EmojiVariants(setOf(neutral, female, male), setOf(female, male)) { true }
            assertEquals(female, directional.apply(neutral, 1, 0))
            assertEquals(male, directional.apply(neutral, 2, 0))
            assertEquals(female, directional.apply(female, 2, 0))
            assertEquals(person + "🏽‍♀️‍➡️", directional.apply(neutral, 1, 3))
        }
    }
}
