package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterFrameCountTest {
    @Test
    fun totalFramesMatchEachGrid() {
        assertEquals(listOf(64, 56, 56), CharacterRoster.all.map { it.columns * it.rows })
    }
}
