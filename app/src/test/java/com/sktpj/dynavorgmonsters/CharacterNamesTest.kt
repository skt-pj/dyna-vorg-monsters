package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterNamesTest {
    @Test
    fun rosterHasThreeDisplayNames() {
        assertEquals(3, CharacterRoster.all.map { it.displayName }.distinct().size)
    }
}
