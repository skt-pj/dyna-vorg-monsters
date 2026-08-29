package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterRowsTest {
    @Test
    fun everyCharacterSupportsAllEightMotionRows() {
        assertTrue(CharacterRoster.all.all { it.rows == 8 })
    }
}
