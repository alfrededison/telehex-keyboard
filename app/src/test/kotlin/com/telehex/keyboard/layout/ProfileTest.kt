package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    private val custom = Profile("", LayoutModel.DEFAULT.withSwapped(1 to 0, 1 to 1), KeyLayers.DEFAULT_SWIPE, SymbolPages.DEFAULT)

    @Test
    fun uniqueNameIgnoresCaseAndCounts() {
        assertEquals("A", Profile.uniqueName("A", listOf("B")))
        assertEquals("A 2", Profile.uniqueName("A", listOf("a")))
        assertEquals("A 3", Profile.uniqueName("A", listOf("A", "a 2")))
    }

    @Test
    fun adoptPicksMatchingBuiltIn() {
        val (user, active) = Profile.adopt(Profile.BUILT_IN[1].named(""), emptyList())
        assertTrue(user.isEmpty())
        assertEquals(Profile.BUILT_IN[1].name, active)
    }

    @Test
    fun adoptKeepsCustomLayoutAsMine() {
        val (user, active) = Profile.adopt(custom, emptyList())
        assertEquals(Profile.MINE, active)
        assertTrue(user.single().sameLayers(custom))
    }

    @Test
    fun adoptReusesMatchingUserProfileAndAvoidsNameClash() {
        val mine = custom.named(Profile.MINE)
        assertEquals(Profile.MINE, Profile.adopt(custom, listOf(mine)).second)
        val other = Profile(Profile.MINE, LayoutModel.BALANCED, KeyLayers.DEFAULT_SWIPE, SymbolPages.DEFAULT)
        val (user, active) = Profile.adopt(custom, listOf(other))
        assertEquals("${Profile.MINE} 2", active)
        assertEquals(2, user.size)
    }

    @Test
    fun saveEditOnBuiltInCreatesMine() {
        val edited = Profile.DEFAULT.swapLetters(1 to 0, 1 to 1)
        val (user, active) = Profile.saveEdit(edited, Profile.DEFAULT, emptyList())
        assertEquals(Profile.MINE, active.name)
        assertTrue(user.single().sameLayers(edited))
        assertEquals("e", active.letters[1, 0])
        val (user2, active2) = Profile.saveEdit(edited, Profile.DEFAULT, user)
        assertEquals("${Profile.MINE} 2", active2.name)
        assertEquals(2, user2.size)
    }

    @Test
    fun saveEditOnUserProfileReplacesInPlace() {
        val mine = custom.named("A")
        val other = Profile.DEFAULT.named("B")
        val edited = mine.swapLetters(0 to 0, 0 to 1)
        val (user, active) = Profile.saveEdit(edited, mine, listOf(mine, other))
        assertEquals(listOf("A", "B"), user.map { it.name })
        assertTrue(user[0].sameLayers(edited))
        assertEquals("A", active.name)
    }

    @Test
    fun swapLettersMovesHiddenSwipeValue() {
        // ⚙ sang ô của Q: ký tự vuốt xuống của ô Q (số 1) không mất mà dời sang ô trống
        val q = LayoutModel.DEFAULT.find("q")!!
        val set = LayoutModel.DEFAULT.find(KeyCode.SETTINGS)!!
        val p = Profile.DEFAULT.swapLetters(q, set)
        assertEquals(KeyCode.SETTINGS, p.letters[q.first, q.second])
        assertEquals("", p.swipe[q.first, q.second])
        assertTrue(p.swipe.find("1") != null)
    }

    @Test
    fun swapSymbolsSwapsWithinPage() {
        val a = 1 to 0; val b = 1 to 1
        val p = Profile.DEFAULT.swapSymbols(1, a, b)
        assertEquals(SymbolPages.DEFAULT[1][b.first, b.second], p.symbols[1][a.first, a.second])
        assertEquals(SymbolPages.DEFAULT[0], p.symbols[0])
    }
}
