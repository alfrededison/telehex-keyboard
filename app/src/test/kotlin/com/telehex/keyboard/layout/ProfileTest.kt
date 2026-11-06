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
}
