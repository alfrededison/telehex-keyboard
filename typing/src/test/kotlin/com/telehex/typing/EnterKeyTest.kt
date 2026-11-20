package com.telehex.typing

import org.junit.Assert.assertEquals
import org.junit.Test

class EnterKeyTest {
    @Test
    fun labelsFollowTheFieldAction() {
        assertEquals("↵", EnterKey.label(EnterKey.Action.NONE))
        assertEquals("Tìm", EnterKey.label(EnterKey.Action.SEARCH))
        assertEquals("Xong", EnterKey.label(EnterKey.Action.DONE))
    }
}
