package com.sktpj.dynavorgmonsters

import kotlin.math.min

data class SpriteDrawSize(
    val width: Float,
    val height: Float,
)

object SpriteGeometry {
    fun fitInside(
        sourceWidth: Int,
        sourceHeight: Int,
        maxWidth: Float,
        maxHeight: Float,
    ): SpriteDrawSize {
        require(sourceWidth > 0)
        require(sourceHeight > 0)
        require(maxWidth > 0f)
        require(maxHeight > 0f)

        val scale = min(maxWidth / sourceWidth, maxHeight / sourceHeight)
        return SpriteDrawSize(
            width = sourceWidth * scale,
            height = sourceHeight * scale,
        )
    }
}
