package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterRosterTest {
    @Test
    fun rosterKeepsMinotaurAndAddsTwoCharacters() {
        assertEquals(listOf("minotaur", "kaiju", "dragon"), CharacterRoster.all.map { it.id })
    }

    @Test
    fun spriteGridMetadataMatchesBundledSheets() {
        assertEquals(listOf(8, 7, 7), CharacterRoster.all.map { it.columns })
        assertEquals(listOf(8, 8, 8), CharacterRoster.all.map { it.rows })
    }
}
