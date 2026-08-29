package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Hosts the existing game unchanged and renders the active player character from an 8x8 sprite sheet.
 *
 * Character replacement rule: replace drawable-nodpi/spikeman_minotaur_sprite_sheet.webp with another
 * 8 columns x 8 rows sheet that follows the row order below. No gameplay code change is required.
 */
class SpriteGameContainer(context: Context) : FrameLayout(context) {
    private companion object {
        const val COLUMNS = 8
        const val ROWS = 8
        const val FPS = 15f
        const val FRAME_COUNT_PER_MOTION = 8
        const val ACTION_SECONDS = FRAME_COUNT_PER_MOTION / FPS

        const val ROW_FORWARD = 0
        const val ROW_BACKWARD = 1
        const val ROW_JUMP = 2
        const val ROW_PUNCH = 3
        const val ROW_KICK = 4
        const val ROW_GUARD = 5
        const val ROW_SPECIAL_1 = 6
        const val ROW_SPECIAL_2 = 7

        const val WORLD_WIDTH = 1920f
        const val WORLD_HEIGHT = 1080f

        const val PUNCH_X = 1435f
        const val PUNCH_Y = 805f
        const val KICK_X = 1610f
        const val KICK_Y = 750f
        const val GUARD_X = 1780f
        const val GUARD_Y = 845f
        const val SPECIAL_1_X = 1475f
        const val SPECIAL_1_Y = 985f
        const val SPECIAL_2_X = 1680f
        const val SPECIAL_2_Y = 970f
    }

    private val gameView = GameView(context)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spriteSheet: Bitmap? = BitmapFactory.decodeResource(
        resources,
        R.drawable.spikeman_minotaur_sprite_sheet,
    )

    private val logicField = GameView::class.java.getDeclaredField("logic").apply { isAccessible = true }
    private val joystickHorizontalField = GameView::class.java.getDeclaredField("joystickHorizontal").apply { isAccessible = true }

    private var actionRow: Int? = null
    private var actionStartedNanos = 0L
    private val animationEpochNanos = System.nanoTime()

    init {
        setWillNotDraw(false)
        addView(
            gameView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (width > 0 && height > 0 &&
            (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN)
        ) {
            val index = event.actionIndex
            val worldX = event.getX(index) * WORLD_WIDTH / width
            val worldY = event.getY(index) * WORLD_HEIGHT / height
            val row = when {
                inside(worldX, worldY, PUNCH_X, PUNCH_Y, 95f) -> ROW_PUNCH
                inside(worldX, worldY, KICK_X, KICK_Y, 95f) -> ROW_KICK
                inside(worldX, worldY, SPECIAL_1_X, SPECIAL_1_Y, 100f) -> ROW_SPECIAL_1
                inside(worldX, worldY, SPECIAL_2_X, SPECIAL_2_Y, 100f) -> ROW_SPECIAL_2
                inside(worldX, worldY, GUARD_X, GUARD_Y, 100f) -> ROW_GUARD
                else -> null
            }
            if (row != null && row != ROW_GUARD) {
                actionRow = row
                actionStartedNanos = System.nanoTime()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val sheet = spriteSheet ?: return
        val logic = runCatching { logicField.get(gameView) as GameLogic }.getOrNull() ?: return
        val player = logic.player
        val horizontal = runCatching { joystickHorizontalField.getFloat(gameView) }.getOrDefault(0f)
        val now = System.nanoTime()

        val actionElapsed = if (actionStartedNanos == 0L) Float.MAX_VALUE else (now - actionStartedNanos) / 1_000_000_000f
        if (actionElapsed >= ACTION_SECONDS) actionRow = null

        val row = when {
            actionRow != null -> actionRow!!
            player.guarding -> ROW_GUARD
            player.y < GameLogic.GROUND_Y - 0.5f || abs(player.vy) > 1f -> ROW_JUMP
            abs(player.vx) > 8f || abs(horizontal) > 0.08f -> if (player.vx * player.facing >= 0f) ROW_FORWARD else ROW_BACKWARD
            else -> ROW_FORWARD
        }

        val moving = abs(player.vx) > 8f || abs(horizontal) > 0.08f
        val frame = when {
            actionRow != null -> (actionElapsed * FPS).toInt().coerceIn(0, FRAME_COUNT_PER_MOTION - 1)
            row == ROW_FORWARD && !moving && !player.guarding && player.y >= GameLogic.GROUND_Y - 0.5f -> 0
            else -> (((now - animationEpochNanos) / 1_000_000_000f) * FPS).toInt() % FRAME_COUNT_PER_MOTION
        }

        val cellWidth = sheet.width / COLUMNS
        val cellHeight = sheet.height / ROWS
        val src = Rect(
            frame * cellWidth,
            row * cellHeight,
            (frame + 1) * cellWidth,
            (row + 1) * cellHeight,
        )

        canvas.save()
        canvas.scale(width / WORLD_WIDTH, height / WORLD_HEIGHT)
        canvas.translate(player.x, player.y)
        canvas.scale(player.facing, 1f)

        maskPaint.color = Color.argb(165, 26, 25, 29)
        canvas.drawOval(RectF(-128f, -310f, 128f, 18f), maskPaint)

        val dst = RectF(-198f, -396f, 198f, 0f)
        canvas.drawBitmap(sheet, src, dst, spritePaint)
        canvas.restore()

        postInvalidateOnAnimation()
    }

    private fun inside(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Boolean =
        hypot((x - cx).toDouble(), (y - cy).toDouble()) <= radius
}
