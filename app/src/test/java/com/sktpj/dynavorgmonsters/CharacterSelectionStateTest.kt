package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterSelectionStateTest {
    @Test
    fun battleBecomesReadyOnlyAfterBothSidesAreSelected() {
        val state = CharacterSelectionState(rosterSize = 3)

        assertFalse(state.ready)
        assertNull(state.selectedPair())

        state.selectPlayer(0)
        assertFalse(state.ready)
        assertNull(state.selectedPair())

        state.selectEnemy(2)
        assertTrue(state.ready)
        assertEquals(0 to 2, state.selectedPair())
    }

    @Test
    fun playerAndEnemyCanUseTheSameCharacter() {
        val state = CharacterSelectionState(rosterSize = 3)

        state.selectPlayer(0)
        state.selectEnemy(0)

        assertEquals(0 to 0, state.selectedPair())
    }
}
