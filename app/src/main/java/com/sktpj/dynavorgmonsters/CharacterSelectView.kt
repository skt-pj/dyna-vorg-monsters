package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class CharacterSelectView(
    context: Context,
    private val onStartBattle: (CharacterDefinition, CharacterDefinition) -> Unit,
) : View(context) {
    private companion object {
        const val WORLD_WIDTH = 1920f
        const val WORLD_HEIGHT = 1080f
        const val CARD_WIDTH = 420f
        const val CARD_HEIGHT = 270f
        val CARD_X = floatArrayOf(170f, 750f, 1330f)
        const val PLAYER_CARD_Y = 230f
        const val ENEMY_CARD_Y = 590f
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val roster = CharacterRoster.all
    private val bitmaps: List<Bitmap?> = roster.map { CharacterRoster.loadBitmap(context, it) }
    private var playerIndex: Int? = null
    private var enemyIndex: Int? = null
    private val startButton = RectF(735f, 930f, 1185f, 1035f)

    init {
        isClickable = true
        keepScreenOn = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        canvas.save()
        canvas.scale(width / WORLD_WIDTH, height / WORLD_HEIGHT)

        paint.color = Color.rgb(18, 21, 32)
        canvas.drawRect(0f, 0f, WORLD_WIDTH, WORLD_HEIGHT, paint)
        paint.color = Color.rgb(42, 34, 42)
        canvas.drawRect(0f, 155f, WORLD_WIDTH, WORLD_HEIGHT, paint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 64f
        canvas.drawText("キャラクター選択", WORLD_WIDTH / 2f, 105f, textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 38f
        canvas.drawText("自分", 80f, 205f, textPaint)
        canvas.drawText("敵", 80f, 565f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER

        roster.indices.forEach { index ->
            drawCard(canvas, index, PLAYER_CARD_Y, playerIndex == index)
            drawCard(canvas, index, ENEMY_CARD_Y, enemyIndex == index)
        }

        val ready = playerIndex != null && enemyIndex != null
        paint.color = if (ready) Color.rgb(194, 73, 57) else Color.rgb(88, 88, 96)
        canvas.drawRoundRect(startButton, 24f, 24f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawRoundRect(startButton, 24f, 24f, paint)
        paint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        textPaint.textSize = 42f
        canvas.drawText("バトル開始", startButton.centerX(), startButton.centerY() + 15f, textPaint)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP || width <= 0 || height <= 0) return true
        val x = event.x * WORLD_WIDTH / width
        val y = event.y * WORLD_HEIGHT / height

        val index = CARD_X.indices.firstOrNull { cardIndex ->
            x in CARD_X[cardIndex]..(CARD_X[cardIndex] + CARD_WIDTH)
        }
        if (index != null) {
            when {
                y in PLAYER_CARD_Y..(PLAYER_CARD_Y + CARD_HEIGHT) -> playerIndex = index
                y in ENEMY_CARD_Y..(ENEMY_CARD_Y + CARD_HEIGHT) -> enemyIndex = index
            }
            invalidate()
            return true
        }

        if (startButton.contains(x, y)) {
            val player = playerIndex
            val enemy = enemyIndex
            if (player != null && enemy != null) {
                onStartBattle(roster[player], roster[enemy])
            }
        }
        return true
    }

    private fun drawCard(canvas: Canvas, index: Int, y: Float, selected: Boolean) {
        val x = CARD_X[index]
        val card = RectF(x, y, x + CARD_WIDTH, y + CARD_HEIGHT)
        paint.color = if (selected) Color.rgb(105, 78, 58) else Color.rgb(35, 39, 50)
        canvas.drawRoundRect(card, 22f, 22f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (selected) 8f else 3f
        paint.color = if (selected) Color.rgb(255, 211, 116) else Color.rgb(120, 126, 140)
        canvas.drawRoundRect(card, 22f, 22f, paint)
        paint.style = Paint.Style.FILL

        val bitmap = bitmaps[index]
        if (bitmap != null) {
            val definition = roster[index]
            val src = Rect(
                0,
                0,
                bitmap.width / definition.columns,
                bitmap.height / definition.rows,
            )
            val dst = RectF(x + 85f, y + 20f, x + CARD_WIDTH - 85f, y + 205f)
            canvas.drawBitmap(bitmap, src, dst, paint)
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 31f
        canvas.drawText(roster[index].displayName, x + CARD_WIDTH / 2f, y + 248f, textPaint)
    }
}
