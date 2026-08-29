package com.sktpj.dynavorgmonsters

import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteGeometryTest {
    @Test
    fun squareFrameIsNeverStretchedIntoRectangle() {
        val size = SpriteGeometry.fitInside(
            sourceWidth = 128,
            sourceHeight = 128,
            maxWidth = 520f,
            maxHeight = 430f,
        )

        assertEquals(430f, size.width, 0.001f)
        assertEquals(430f, size.height, 0.001f)
    }

    @Test
    fun sourceAspectRatioIsPreservedInsideBounds() {
        val size = SpriteGeometry.fitInside(
            sourceWidth = 200,
            sourceHeight = 100,
            maxWidth = 300f,
            maxHeight = 300f,
        )

        assertEquals(300f, size.width, 0.001f)
        assertEquals(150f, size.height, 0.001f)
    }
}
