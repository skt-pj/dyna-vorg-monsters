package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class CharacterSelectionPairTest {
    @Test
    fun rosterAllowsAllPlayerEnemyPairsIncludingSameCharacter() {
        val pairs = CharacterRoster.all.flatMap { player ->
            CharacterRoster.all.map { enemy -> player.id to enemy.id }
        }
        assertEquals(9, pairs.size)
        assertEquals(3, pairs.count { it.first == it.second })
    }
}
