package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteAnimationControllerTest {
    @Test
    fun spriteMotionRowsMatchSheetContract() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            listOf(
                SpriteMotion.FORWARD.row,
                SpriteMotion.BACKWARD.row,
                SpriteMotion.JUMP.row,
                SpriteMotion.PUNCH.row,
                SpriteMotion.KICK.row,
                SpriteMotion.GUARD.row,
                SpriteMotion.SPECIAL_1.row,
                SpriteMotion.SPECIAL_2.row,
            ),
        )
    }

    @Test
    fun loopMotionAdvancesAtFifteenFpsAndWrapsAfterEightFrames() {
        val controller = SpriteAnimationController()
        repeat(7) { controller.update(0.067f, SpriteMotion.FORWARD) }
        assertEquals(SpriteMotion.FORWARD, controller.currentMotion)
        assertEquals(7, controller.frameIndex)

        controller.update(0.067f, SpriteMotion.FORWARD)
        assertEquals(0, controller.frameIndex)
    }

    @Test
    fun oneShotRunsEightFramesThenReturnsToIdle() {
        val controller = SpriteAnimationController()
        controller.startOneShot(SpriteMotion.PUNCH)

        repeat(7) { controller.update(0.067f, null) }
        assertEquals(SpriteMotion.PUNCH, controller.currentMotion)
        assertEquals(7, controller.frameIndex)

        controller.update(0.067f, null)
        assertEquals(SpriteMotion.FORWARD, controller.currentMotion)
        assertEquals(0, controller.frameIndex)
    }

    @Test
    fun oneShotReturnsToRequestedLoopMotion() {
        val controller = SpriteAnimationController()
        controller.startOneShot(SpriteMotion.SPECIAL_2)

        repeat(8) { controller.update(0.067f, SpriteMotion.GUARD) }
        assertEquals(SpriteMotion.GUARD, controller.currentMotion)
        assertEquals(0, controller.frameIndex)
    }
}
