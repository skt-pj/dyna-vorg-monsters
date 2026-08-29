package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

class SpriteGameContainer(
    context: Context,
    private val playerDefinition: CharacterDefinition,
    private val enemyDefinition: CharacterDefinition,
) : FrameLayout(context) {
    private companion object {
        const val TAG = "DVMCharacter"

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

    private data class MotionFrame(val row: Int, val frame: Int)

    private val gameView = GameView(context)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val arenaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arenaTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val playerSheet: Bitmap? = CharacterRoster.loadBitmap(context, playerDefinition)
    private val enemySheet: Bitmap? = CharacterRoster.loadBitmap(context, enemyDefinition)

    private val logicField = GameView::class.java.getDeclaredField("logic").apply { isAccessible = true }
    private val joystickHorizontalField = GameView::class.java.getDeclaredField("joystickHorizontal").apply { isAccessible = true }

    private var playerActionRow: Int? = null
    private var playerActionStartedNanos = 0L
    private var enemyActionRow: Int? = null
    private var enemyActionStartedNanos = 0L
    private var previousEnemyAttackFlashSeconds = 0f
    private val animationEpochNanos = System.nanoTime()

    init {
        setWillNotDraw(false)
        addView(
            gameView,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        Log.i(
            TAG,
            "battle_sprite_container player=${playerDefinition.id}:${playerSheet?.width}x${playerSheet?.height} " +
                "enemy=${enemyDefinition.id}:${enemySheet?.width}x${enemySheet?.height}",
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
                playerActionRow = row
                playerActionStartedNanos = System.nanoTime()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (width <= 0 || height <= 0) return

        val logic = runCatching { logicField.get(gameView) as GameLogic }.getOrNull() ?: return
        val horizontal = runCatching { joystickHorizontalField.getFloat(gameView) }.getOrDefault(0f)
        val now = System.nanoTime()

        expireAction(now, isPlayer = true)
        val enemyFlash = logic.enemyAttackFlashSeconds
        if (enemyFlash > 0f && previousEnemyAttackFlashSeconds <= 0f) {
            enemyActionRow = ROW_PUNCH
            enemyActionStartedNanos = now
        }
        previousEnemyAttackFlashSeconds = enemyFlash
        expireAction(now, isPlayer = false)

        val playerMotion = resolveMotion(
            definition = playerDefinition,
            fighter = logic.player,
            actionRow = playerActionRow,
            actionStartedNanos = playerActionStartedNanos,
            horizontalInput = horizontal,
            now = now,
            guarding = logic.player.guarding,
        )
        val enemyMotion = resolveMotion(
            definition = enemyDefinition,
            fighter = logic.enemy,
            actionRow = enemyActionRow,
            actionStartedNanos = enemyActionStartedNanos,
            horizontalInput = 0f,
            now = now,
            guarding = false,
        )

        canvas.save()
        canvas.scale(width / WORLD_WIDTH, height / WORLD_HEIGHT)

        redrawArenaBehindFighter(
            canvas,
            RectF(logic.player.x - 245f, logic.player.y - 460f, logic.player.x + 245f, logic.player.y + 24f),
        )
        redrawArenaBehindFighter(
            canvas,
            RectF(logic.enemy.x - 275f, logic.enemy.y - 460f, logic.enemy.x + 275f, logic.enemy.y + 24f),
        )

        playerSheet?.let { bitmap ->
            drawSprite(canvas, bitmap, playerDefinition, logic.player, playerMotion)
        }
        enemySheet?.let { bitmap ->
            drawSprite(canvas, bitmap, enemyDefinition, logic.enemy, enemyMotion)
        }
        canvas.restore()

        postInvalidateOnAnimation()
    }

    private fun expireAction(now: Long, isPlayer: Boolean) {
        val definition = if (isPlayer) playerDefinition else enemyDefinition
        val started = if (isPlayer) playerActionStartedNanos else enemyActionStartedNanos
        val active = if (isPlayer) playerActionRow else enemyActionRow
        if (active == null || started == 0L) return
        val elapsed = (now - started) / 1_000_000_000f
        val duration = definition.columns / definition.fps
        if (elapsed >= duration) {
            if (isPlayer) playerActionRow = null else enemyActionRow = null
        }
    }

    private fun resolveMotion(
        definition: CharacterDefinition,
        fighter: Fighter,
        actionRow: Int?,
        actionStartedNanos: Long,
        horizontalInput: Float,
        now: Long,
        guarding: Boolean,
    ): MotionFrame {
        val moving = abs(fighter.vx) > 8f || abs(horizontalInput) > 0.08f
        val row = when {
            actionRow != null -> actionRow
            guarding -> ROW_GUARD
            fighter.y < GameLogic.GROUND_Y - 0.5f || abs(fighter.vy) > 1f -> ROW_JUMP
            moving -> if (fighter.vx * fighter.facing >= 0f) ROW_FORWARD else ROW_BACKWARD
            else -> ROW_FORWARD
        }

        val frame = when {
            actionRow != null && actionStartedNanos != 0L -> {
                val elapsed = (now - actionStartedNanos) / 1_000_000_000f
                (elapsed * definition.fps).toInt().coerceIn(0, definition.columns - 1)
            }
            row == ROW_FORWARD && !moving && !guarding && fighter.y >= GameLogic.GROUND_Y - 0.5f -> 0
            else -> (((now - animationEpochNanos) / 1_000_000_000f) * definition.fps).toInt() % definition.columns
        }
        return MotionFrame(row, frame)
    }

    private fun drawSprite(
        canvas: Canvas,
        sheet: Bitmap,
        definition: CharacterDefinition,
        fighter: Fighter,
        motion: MotionFrame,
    ) {
        val left = motion.frame * sheet.width / definition.columns
        val right = (motion.frame + 1) * sheet.width / definition.columns
        val top = motion.row * sheet.height / definition.rows
        val bottom = (motion.row + 1) * sheet.height / definition.rows
        val src = Rect(left, top, right, bottom)

        val fitted = SpriteGeometry.fitInside(
            sourceWidth = right - left,
            sourceHeight = bottom - top,
            maxWidth = definition.drawWidth,
            maxHeight = definition.drawHeight,
        )

        canvas.save()
        canvas.translate(fighter.x, fighter.y)
        canvas.scale(fighter.facing, 1f)
        val halfWidth = fitted.width / 2f
        val dst = RectF(-halfWidth, -fitted.height, halfWidth, 0f)
        canvas.drawBitmap(sheet, src, dst, spritePaint)
        canvas.restore()
    }

    private fun redrawArenaBehindFighter(canvas: Canvas, bounds: RectF) {
        canvas.save()
        canvas.clipRect(bounds)
        drawArena(canvas)
        canvas.restore()
    }

    private fun drawArena(canvas: Canvas) {
        arenaPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            700f,
            Color.rgb(22, 24, 42),
            Color.rgb(96, 55, 48),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, arenaPaint)
        arenaPaint.shader = null

        arenaPaint.color = Color.rgb(58, 45, 55)
        canvas.drawRect(0f, 235f, WORLD_WIDTH, 680f, arenaPaint)
        for (row in 0 until 4) {
            val y = 295f + row * 82f
            arenaPaint.color = if (row % 2 == 0) Color.rgb(104, 66, 62) else Color.rgb(79, 58, 67)
            canvas.drawRect(0f, y, WORLD_WIDTH, y + 50f, arenaPaint)
            arenaPaint.color = Color.rgb(218, 170, 112)
            for (seat in 0 until 24) {
                val x = 28f + seat * 82f + if (row % 2 == 0) 0f else 36f
                canvas.drawCircle(x, y + 20f, 10f, arenaPaint)
            }
        }
        arenaPaint.color = Color.rgb(130, 116, 96)
        for (x in 0..WORLD_WIDTH.toInt() step 240) {
            canvas.drawRect(x.toFloat(), 185f, x + 28f, 680f, arenaPaint)
        }
        arenaPaint.color = Color.rgb(48, 42, 38)
        canvas.drawRect(0f, 680f, WORLD_WIDTH, WORLD_HEIGHT, arenaPaint)
        arenaPaint.color = Color.rgb(156, 126, 82)
        canvas.drawOval(RectF(140f, 720f, 1780f, 900f), arenaPaint)
        arenaPaint.color = Color.rgb(78, 64, 52)
        canvas.drawOval(RectF(205f, 758f, 1715f, 890f), arenaPaint)
        arenaPaint.color = Color.rgb(190, 156, 104)
        canvas.drawRect(110f, GameLogic.GROUND_Y + 5f, 1810f, GameLogic.GROUND_Y + 40f, arenaPaint)
        arenaTextPaint.textSize = 42f
        arenaTextPaint.color = Color.argb(150, 255, 238, 198)
        canvas.drawText("DYNA VORG ARENA", 960f, 645f, arenaTextPaint)
    }

    private fun inside(x: Float, y: Float, cx: Float, cy: Float, radius: Float): Boolean =
        hypot((x - cx).toDouble(), (y - cy).toDouble()) <= radius
}
