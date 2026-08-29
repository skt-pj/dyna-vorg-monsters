package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionSelectionSmokeTest {
    @Test
    fun minotaurRemainsFirstRosterEntry() {
        assertEquals("minotaur", CharacterRoster.all.first().id)
    }
}
