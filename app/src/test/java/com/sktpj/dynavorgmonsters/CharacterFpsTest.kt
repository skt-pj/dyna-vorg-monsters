package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterFpsTest {
    @Test
    fun allCharactersUseFifteenFps() {
        assertEquals(listOf(15f, 15f, 15f), CharacterRoster.all.map { it.fps })
    }
}
