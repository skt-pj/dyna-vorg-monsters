package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSelectionContractTest {
    @Test
    fun allCharactersProvideEightMotionRowsAndPositiveFrameRate() {
        CharacterRoster.all.forEach { definition ->
            assertEquals(8, definition.rows)
            assertTrue(definition.columns > 0)
            assertTrue(definition.fps > 0f)
        }
    }
}
