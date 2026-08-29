package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameLogicTest {
    private lateinit var logic: GameLogic

    @Before
    fun setUp() {
        logic = GameLogic()
    }

    @Test
    fun hpNeverDropsBelowZero() {
        logic.damagePlayer(500f)
        assertEquals(0f, logic.player.hp, 0.001f)
    }

    @Test
    fun doubleJumpAllowsTwoJumpsAndRejectsThird() {
        assertTrue(logic.jump())
        assertTrue(logic.jump())
        assertFalse(logic.jump())
        assertEquals(2, logic.player.jumpsUsed)
    }

    @Test
    fun aerialThrustIsLimitedToFiveSeconds() {
        assertTrue(logic.jump())
        repeat(120) {
            logic.update(0.05f, 0f, true)
        }
        assertTrue(logic.player.flightUsedSeconds <= GameLogic.MAX_FLIGHT_SECONDS + 0.001f)
    }

    @Test
    fun guardReducesFrontDamage() {
        logic.player.facing = 1f
        logic.player.guarding = true
        logic.damagePlayer(20f, logic.player.x + 100f)
        assertEquals(94f, logic.player.hp, 0.001f)
    }

    @Test
    fun specialOneProjectileDamagesEnemy() {
        logic.enemy.x = logic.player.x + 600f
        val before = logic.enemy.hp
        assertTrue(logic.special1())
        repeat(30) {
            logic.update(0.05f, 0f, false)
        }
        assertTrue(logic.enemy.hp < before)
    }

    @Test
    fun specialTwoDamagesNearbyEnemy() {
        logic.enemy.x = logic.player.x + 180f
        val before = logic.enemy.hp
        assertTrue(logic.special2())
        assertTrue(logic.enemy.hp < before)
    }

    @Test
    fun groundedPunchUsesBaseDamageOutsideHeadWeakPoint() {
        logic.enemy.x = logic.player.x + 150f
        logic.player.facing = 1f

        assertTrue(logic.punch())

        assertEquals(91f, logic.enemy.hp, 0.001f)
        val event = logic.consumeCombatEvents().single()
        assertFalse(event.weakPoint)
        assertEquals(9f, event.damage, 0.001f)
    }

    @Test
    fun aerialPunchOnHeadUsesWeakPointMultiplier() {
        logic.enemy.x = logic.player.x + 150f
        logic.player.facing = 1f
        logic.player.y = GameLogic.GROUND_Y - 40f

        assertTrue(logic.punch())

        assertEquals(100f - 9f * GameLogic.WEAK_POINT_MULTIPLIER, logic.enemy.hp, 0.001f)
    }

    @Test
    fun weakPointEventCarriesBoostedDamage() {
        logic.enemy.x = logic.player.x + 150f
        logic.player.facing = 1f
        logic.player.y = GameLogic.GROUND_Y - 40f

        assertTrue(logic.punch())

        val event = logic.consumeCombatEvents().single()
        assertTrue(event.weakPoint)
        assertEquals(CombatTarget.ENEMY, event.target)
        assertEquals(9f * GameLogic.WEAK_POINT_MULTIPLIER, event.damage, 0.001f)
    }

    @Test
    fun specialTwoNeverUsesWeakPointMultiplier() {
        logic.enemy.x = logic.player.x + 180f
        logic.player.y = GameLogic.GROUND_Y - 80f

        assertTrue(logic.special2())

        assertEquals(82f, logic.enemy.hp, 0.001f)
        val event = logic.consumeCombatEvents().single()
        assertFalse(event.weakPoint)
        assertEquals(18f, event.damage, 0.001f)
    }
}
