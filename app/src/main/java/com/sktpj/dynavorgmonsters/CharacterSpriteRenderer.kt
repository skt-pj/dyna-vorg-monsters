package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

data class CharacterSpriteDefinition(
    val drawableResId: Int,
    val columns: Int = 8,
    val rows: Int = 8,
    val framesPerMotion: Int = 8,
    val fps: Float = 15f,
    val drawWidth: Float = 430f,
    val drawHeight: Float = 430f,
    val bottomOffset: Float = 45f,
)

object PlayerCharacterSprites {
    val spikemanMinotaur = CharacterSpriteDefinition(
        drawableResId = R.drawable.spikeman_minotaur_sprite_sheet,
    )
}

class CharacterSpriteRenderer(
    context: Context,
    val definition: CharacterSpriteDefinition,
) {
    private val bitmap: Bitmap = requireNotNull(
        BitmapFactory.decodeResource(context.resources, definition.drawableResId),
    ) { "Unable to decode character sprite sheet resource ${definition.drawableResId}" }
    private val sourceRect = Rect()
    private val destinationRect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val animation = SpriteAnimationController(
        framesPerMotion = definition.framesPerMotion,
        fps = definition.fps,
    )

    init {
        require(definition.columns == 8)
        require(definition.rows == 8)
        require(definition.framesPerMotion == 8)
        require(bitmap.width % definition.columns == 0)
        require(bitmap.height % definition.rows == 0)
    }

    val currentMotion: SpriteMotion
        get() = animation.currentMotion

    val currentFrame: Int
        get() = animation.frameIndex

    fun reset() = animation.reset()

    fun startOneShot(motion: SpriteMotion) = animation.startOneShot(motion)

    fun update(deltaSeconds: Float, loopMotion: SpriteMotion?) =
        animation.update(deltaSeconds, loopMotion)

    fun draw(canvas: Canvas) {
        val cellWidth = bitmap.width / definition.columns
        val cellHeight = bitmap.height / definition.rows
        val column = currentFrame.coerceIn(0, definition.framesPerMotion - 1)
        val row = currentMotion.row.coerceIn(0, definition.rows - 1)
        val left = column * cellWidth
        val top = row * cellHeight
        sourceRect.set(left, top, left + cellWidth, top + cellHeight)

        val halfWidth = definition.drawWidth / 2f
        destinationRect.set(
            -halfWidth,
            definition.bottomOffset - definition.drawHeight,
            halfWidth,
            definition.bottomOffset,
        )
        canvas.drawBitmap(bitmap, sourceRect, destinationRect, paint)
    }
}
