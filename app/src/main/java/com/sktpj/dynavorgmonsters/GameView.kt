package com.sktpj.dynavorgmonsters

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

class GameView(context: Context) : View(context) {
    private enum class ButtonType { PUNCH, KICK, GUARD, SPECIAL_1, SPECIAL_2 }

    private data class ControlButton(
        val type: ButtonType,
        val x: Float,
        val y: Float,
        val radius: Float,
        val label: String,
    )

    private data class ImpactFx(
        val x: Float,
        val y: Float,
        val damage: Float,
        val target: CombatTarget,
        val weakPoint: Boolean,
        val guarded: Boolean,
        val durationSeconds: Float,
        var ageSeconds: Float = 0f,
    )

    private val logic = GameLogic()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val joystickCenterX = 235f
    private val joystickCenterY = 885f
    private val joystickRadius = 155f
    private var joystickPointerId = -1
    private var joystickHorizontal = 0f
    private var joystickUpHeld = false
    private var joystickKnobX = joystickCenterX
    private var joystickKnobY = joystickCenterY

    private val buttons = listOf(
        ControlButton(ButtonType.PUNCH, 1435f, 805f, 78f, "パンチ"),
        ControlButton(ButtonType.KICK, 1610f, 750f, 78f, "キック"),
        ControlButton(ButtonType.GUARD, 1780f, 845f, 82f, "ガード"),
        ControlButton(ButtonType.SPECIAL_1, 1475f, 985f, 82f, "特殊1"),
        ControlButton(ButtonType.SPECIAL_2, 1680f, 970f, 82f, "特殊2"),
    )
    private val pointerButtons = mutableMapOf<Int, ButtonType>()
    private val impactEffects = mutableListOf<ImpactFx>()
    private var lastFrameNanos = 0L
    private var running = true
    private var hitStopRemainingSeconds = 0f
    private var cameraShakeRemainingSeconds = 0f
    private var cameraShakeDurationSeconds = 0f
    private var cameraShakeStrength = 0f
    private var toneGenerator: ToneGenerator? = null

    init {
        isFocusable = true
        isClickable = true
        keepScreenOn = true
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrameNanos = 0L
        if (toneGenerator == null) {
            toneGenerator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 55) }.getOrNull()
        }
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        toneGenerator?.release()
        toneGenerator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val now = System.nanoTime()
        val rawDt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
        lastFrameNanos = now

        logic.setGuarding(pointerButtons.values.any { it == ButtonType.GUARD })
        val gameplayDt = if (hitStopRemainingSeconds > 0f) {
            hitStopRemainingSeconds = (hitStopRemainingSeconds - rawDt).coerceAtLeast(0f)
            0f
        } else {
            rawDt
        }
        logic.update(gameplayDt, joystickHorizontal, joystickUpHeld)
        logic.consumeCombatEvents().forEach(::handleCombatEvent)
        updatePresentation(rawDt)

        canvas.save()
        canvas.scale(width / GameLogic.WORLD_WIDTH, height / GameLogic.WORLD_HEIGHT)

        canvas.save()
        applyCameraShake(canvas)
        drawArena(canvas)
        drawProjectiles(canvas)
        drawFighters(canvas)
        drawImpactEffects(canvas)
        canvas.restore()

        drawHud(canvas)
        drawControls(canvas)
        drawResult(canvas)
        canvas.restore()

        if (running) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                val pointerId = event.getPointerId(index)
                val x = toWorldX(event.getX(index))
                val y = toWorldY(event.getY(index))

                if (logic.winner != null) {
                    logic.reset()
                    clearControls()
                    clearImpactFeedback()
                    invalidate()
                    return true
                }

                if (joystickPointerId == -1 && distance(x, y, joystickCenterX, joystickCenterY) <= joystickRadius * 1.35f) {
                    joystickPointerId = pointerId
                    updateJoystick(x, y)
                } else {
                    val button = buttons.firstOrNull { distance(x, y, it.x, it.y) <= it.radius * 1.15f }
                    if (button != null) {
                        pointerButtons[pointerId] = button.type
                        when (button.type) {
                            ButtonType.PUNCH -> logic.punch()
                            ButtonType.KICK -> logic.kick()
                            ButtonType.GUARD -> logic.setGuarding(true)
                            ButtonType.SPECIAL_1 -> logic.special1()
                            ButtonType.SPECIAL_2 -> logic.special2()
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (joystickPointerId != -1) {
                    val index = event.findPointerIndex(joystickPointerId)
                    if (index >= 0) {
                        updateJoystick(toWorldX(event.getX(index)), toWorldY(event.getY(index)))
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                releasePointer(pointerId)
            }

            MotionEvent.ACTION_CANCEL -> clearControls()
        }
        return true
    }

    private fun releasePointer(pointerId: Int) {
        if (pointerId == joystickPointerId) {
            joystickPointerId = -1
            joystickHorizontal = 0f
            joystickUpHeld = false
            joystickKnobX = joystickCenterX
            joystickKnobY = joystickCenterY
        }
        pointerButtons.remove(pointerId)
        logic.setGuarding(pointerButtons.values.any { it == ButtonType.GUARD })
    }

    private fun clearControls() {
        joystickPointerId = -1
        joystickHorizontal = 0f
        joystickUpHeld = false
        joystickKnobX = joystickCenterX
        joystickKnobY = joystickCenterY
        pointerButtons.clear()
        logic.setGuarding(false)
    }

    private fun clearImpactFeedback() {
        impactEffects.clear()
        hitStopRemainingSeconds = 0f
        cameraShakeRemainingSeconds = 0f
        cameraShakeDurationSeconds = 0f
        cameraShakeStrength = 0f
    }

    private fun updateJoystick(x: Float, y: Float) {
        var dx = x - joystickCenterX
        var dy = y - joystickCenterY
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length > joystickRadius && length > 0f) {
            val scale = joystickRadius / length
            dx *= scale
            dy *= scale
        }
        joystickKnobX = joystickCenterX + dx
        joystickKnobY = joystickCenterY + dy
        joystickHorizontal = (dx / joystickRadius).coerceIn(-1f, 1f)

        val newUpHeld = dy / joystickRadius < -0.34f
        if (newUpHeld && !joystickUpHeld) {
            logic.jump()
        }
        joystickUpHeld = newUpHeld
    }

    private fun handleCombatEvent(event: CombatEvent) {
        val isPlayerHit = event.target == CombatTarget.PLAYER
        val hitStop = when {
            event.guarded -> 0.025f
            event.weakPoint -> 0.075f
            isPlayerHit -> 0.055f
            else -> 0.045f
        }
        hitStopRemainingSeconds = maxOf(hitStopRemainingSeconds, hitStop)

        val shakeDuration = when {
            event.guarded -> 0.07f
            event.weakPoint -> 0.18f
            isPlayerHit -> 0.14f
            else -> 0.12f
        }
        val shakeStrength = when {
            event.guarded -> 8f
            event.weakPoint -> 32f
            isPlayerHit -> 24f
            else -> 18f
        } * event.impactStrength.coerceIn(0.35f, 1f)
        triggerCameraShake(shakeDuration, shakeStrength)

        val duration = when {
            event.weakPoint -> 0.34f
            isPlayerHit -> 0.28f
            else -> 0.24f
        }
        impactEffects += ImpactFx(
            x = event.impactX,
            y = event.impactY,
            damage = event.damage,
            target = event.target,
            weakPoint = event.weakPoint,
            guarded = event.guarded,
            durationSeconds = duration,
        )
        while (impactEffects.size > 12) impactEffects.removeAt(0)

        val haptic = when {
            event.guarded -> HapticFeedbackConstants.KEYBOARD_TAP
            event.weakPoint -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            isPlayerHit -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        performHapticFeedback(haptic)

        val tone = when {
            event.guarded -> ToneGenerator.TONE_PROP_BEEP
            event.weakPoint -> ToneGenerator.TONE_PROP_ACK
            isPlayerHit -> ToneGenerator.TONE_PROP_NACK
            else -> ToneGenerator.TONE_PROP_BEEP
        }
        val toneMs = when {
            event.weakPoint -> 55
            isPlayerHit -> 50
            else -> 35
        }
        runCatching { toneGenerator?.startTone(tone, toneMs) }
    }

    private fun triggerCameraShake(durationSeconds: Float, strength: Float) {
        if (cameraShakeRemainingSeconds <= 0f || strength >= cameraShakeStrength) {
            cameraShakeDurationSeconds = durationSeconds
            cameraShakeRemainingSeconds = durationSeconds
            cameraShakeStrength = strength
        } else {
            cameraShakeRemainingSeconds = maxOf(cameraShakeRemainingSeconds, durationSeconds)
        }
    }

    private fun updatePresentation(dt: Float) {
        if (dt <= 0f) return
        impactEffects.forEach { it.ageSeconds += dt }
        impactEffects.removeAll { it.ageSeconds >= it.durationSeconds }
        cameraShakeRemainingSeconds = (cameraShakeRemainingSeconds - dt).coerceAtLeast(0f)
        if (cameraShakeRemainingSeconds <= 0f) cameraShakeStrength = 0f
    }

    private fun applyCameraShake(canvas: Canvas) {
        if (cameraShakeRemainingSeconds <= 0f || cameraShakeDurationSeconds <= 0f) return
        val ratio = (cameraShakeRemainingSeconds / cameraShakeDurationSeconds).coerceIn(0f, 1f)
        val t = System.nanoTime() / 1_000_000_000.0
        val x = (sin(t * 115.0) * cameraShakeStrength * ratio).toFloat()
        val y = (cos(t * 153.0) * cameraShakeStrength * 0.6f * ratio).toFloat()
        canvas.translate(x, y)
    }

    private fun drawArena(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            700f,
            Color.rgb(22, 24, 42),
            Color.rgb(96, 55, 48),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, GameLogic.WORLD_WIDTH, GameLogic.WORLD_HEIGHT, paint)
        paint.shader = null

        paint.color = Color.rgb(58, 45, 55)
        canvas.drawRect(0f, 235f, GameLogic.WORLD_WIDTH, 680f, paint)

        for (row in 0 until 4) {
            val y = 295f + row * 82f
            paint.color = if (row % 2 == 0) Color.rgb(104, 66, 62) else Color.rgb(79, 58, 67)
            canvas.drawRect(0f, y, GameLogic.WORLD_WIDTH, y + 50f, paint)
            paint.color = Color.rgb(218, 170, 112)
            for (seat in 0 until 24) {
                val x = 28f + seat * 82f + if (row % 2 == 0) 0f else 36f
                canvas.drawCircle(x, y + 20f, 10f, paint)
            }
        }

        paint.color = Color.rgb(130, 116, 96)
        for (x in 0..GameLogic.WORLD_WIDTH.toInt() step 240) {
            canvas.drawRect(x.toFloat(), 185f, x + 28f, 680f, paint)
        }

        paint.color = Color.rgb(48, 42, 38)
        canvas.drawRect(0f, 680f, GameLogic.WORLD_WIDTH, GameLogic.WORLD_HEIGHT, paint)
        paint.color = Color.rgb(156, 126, 82)
        canvas.drawOval(RectF(140f, 720f, 1780f, 900f), paint)
        paint.color = Color.rgb(78, 64, 52)
        canvas.drawOval(RectF(205f, 758f, 1715f, 890f), paint)
        paint.color = Color.rgb(190, 156, 104)
        canvas.drawRect(110f, GameLogic.GROUND_Y + 5f, 1810f, GameLogic.GROUND_Y + 40f, paint)

        textPaint.textSize = 42f
        textPaint.color = Color.argb(150, 255, 238, 198)
        canvas.drawText("DYNA VORG ARENA", 960f, 645f, textPaint)
    }

    private fun drawHud(canvas: Canvas) {
        drawHealthBar(canvas, 90f, 75f, 700f, logic.player.hp, "MONSTER")
        drawHealthBar(canvas, 1130f, 75f, 700f, logic.enemy.hp, "DINOSAUR")

        val remainingFlight = (GameLogic.MAX_FLIGHT_SECONDS - logic.player.flightUsedSeconds).coerceAtLeast(0f)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 30f
        textPaint.color = Color.WHITE
        canvas.drawText("FLY ${"%.1f".format(remainingFlight)}s", 92f, 155f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawHealthBar(canvas: Canvas, x: Float, y: Float, width: Float, hp: Float, label: String) {
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(210, 20, 20, 24)
        canvas.drawRoundRect(RectF(x, y, x + width, y + 52f), 18f, 18f, paint)
        val ratio = (hp / GameLogic.MAX_HP).coerceIn(0f, 1f)
        paint.color = when {
            ratio > 0.55f -> Color.rgb(66, 205, 98)
            ratio > 0.25f -> Color.rgb(242, 181, 58)
            else -> Color.rgb(230, 70, 63)
        }
        canvas.drawRoundRect(RectF(x + 6f, y + 6f, x + 6f + (width - 12f) * ratio, y + 46f), 14f, 14f, paint)
        textPaint.textSize = 30f
        textPaint.color = Color.WHITE
        canvas.drawText("$label  ${hp.toInt()}", x + width / 2f, y + 37f, textPaint)
    }

    private fun drawProjectiles(canvas: Canvas) {
        logic.projectiles.forEach { projectile ->
            paint.color = Color.rgb(90, 224, 255)
            canvas.drawCircle(projectile.x, projectile.y, 31f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            paint.color = Color.argb(170, 210, 250, 255)
            canvas.drawCircle(projectile.x, projectile.y, 45f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawFighters(canvas: Canvas) {
        drawPlayerMonster(canvas)
        drawDinosaur(canvas)
    }

    private fun drawPlayerMonster(canvas: Canvas) {
        val f = logic.player
        canvas.save()
        canvas.translate(f.x, f.y)
        canvas.scale(f.facing, 1f)

        if (f.guarding) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 14f
            paint.color = Color.argb(210, 90, 220, 255)
            canvas.drawArc(RectF(-115f, -245f, 135f, 20f), -74f, 148f, false, paint)
            paint.style = Paint.Style.FILL
        }

        paint.color = Color.rgb(54, 163, 102)
        canvas.drawOval(RectF(-72f, -190f, 72f, -18f), paint)
        paint.color = Color.rgb(74, 195, 126)
        canvas.drawCircle(20f, -200f, 74f, paint)

        paint.color = Color.rgb(228, 226, 190)
        path.reset()
        path.moveTo(-25f, -257f)
        path.lineTo(-58f, -322f)
        path.lineTo(0f, -279f)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(45f, -257f)
        path.lineTo(78f, -318f)
        path.lineTo(71f, -248f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.WHITE
        canvas.drawCircle(48f, -215f, 18f, paint)
        paint.color = Color.rgb(24, 28, 30)
        canvas.drawCircle(54f, -213f, 8f, paint)

        paint.color = Color.rgb(38, 124, 81)
        canvas.drawOval(RectF(-125f, -145f, -48f, -82f), paint)
        canvas.drawOval(RectF(48f, -145f, 125f, -82f), paint)
        canvas.drawRect(-58f, -35f, -15f, 12f, paint)
        canvas.drawRect(15f, -35f, 58f, 12f, paint)

        if (logic.playerAttackFlashSeconds > 0f) {
            paint.color = Color.argb(205, 255, 230, 96)
            canvas.drawCircle(150f, -115f, 58f + logic.playerAttackFlashSeconds * 80f, paint)
        }
        if (logic.playerSpecialFlashSeconds > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 18f
            paint.color = Color.argb(210, 92, 230, 255)
            canvas.drawCircle(0f, -120f, 155f + logic.playerSpecialFlashSeconds * 120f, paint)
            paint.style = Paint.Style.FILL
        }
        if (f.hitFlashSeconds > 0f) {
            val alpha = (150f * (f.hitFlashSeconds / 0.12f).coerceIn(0f, 1f)).roundToInt()
            paint.color = Color.argb(alpha, 255, 88, 75)
            canvas.drawOval(RectF(-105f, -285f, 135f, 18f), paint)
        }
        canvas.restore()
    }

    private fun drawDinosaur(canvas: Canvas) {
        val f = logic.enemy
        canvas.save()
        canvas.translate(f.x, f.y)
        canvas.scale(f.facing, 1f)

        paint.color = Color.rgb(166, 76, 64)
        canvas.drawOval(RectF(-95f, -168f, 68f, -28f), paint)
        canvas.drawOval(RectF(18f, -235f, 145f, -120f), paint)

        path.reset()
        path.moveTo(-78f, -112f)
        path.lineTo(-235f, -58f)
        path.lineTo(-75f, -58f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.rgb(112, 53, 49)
        canvas.drawRect(-52f, -48f, -12f, 14f, paint)
        canvas.drawRect(20f, -48f, 60f, 14f, paint)
        canvas.drawOval(RectF(56f, -118f, 151f, -84f), paint)

        paint.color = Color.WHITE
        canvas.drawCircle(102f, -196f, 15f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(107f, -194f, 7f, paint)

        paint.color = Color.rgb(230, 213, 171)
        for (i in 0 until 5) {
            path.reset()
            val x = -60f + i * 34f
            path.moveTo(x, -165f)
            path.lineTo(x + 16f, -217f)
            path.lineTo(x + 30f, -160f)
            path.close()
            canvas.drawPath(path, paint)
        }

        if (logic.enemyAttackFlashSeconds > 0f) {
            paint.color = Color.argb(205, 255, 115, 75)
            canvas.drawCircle(160f, -120f, 65f + logic.enemyAttackFlashSeconds * 70f, paint)
        }
        if (f.hitFlashSeconds > 0f) {
            val alpha = (185f * (f.hitFlashSeconds / 0.18f).coerceIn(0f, 1f)).roundToInt()
            paint.color = Color.argb(alpha, 255, 241, 180)
            canvas.drawOval(RectF(-245f, -250f, 165f, 18f), paint)
        }
        canvas.restore()
    }

    private fun drawImpactEffects(canvas: Canvas) {
        impactEffects.forEach { fx ->
            val progress = (fx.ageSeconds / fx.durationSeconds).coerceIn(0f, 1f)
            val fade = (1f - progress).coerceIn(0f, 1f)
            val baseColor = when {
                fx.guarded -> Color.rgb(105, 225, 255)
                fx.target == CombatTarget.PLAYER -> Color.rgb(255, 82, 72)
                fx.weakPoint -> Color.rgb(255, 176, 38)
                else -> Color.rgb(255, 235, 135)
            }
            val alpha = (255f * fade).roundToInt().coerceIn(0, 255)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (fx.weakPoint) 12f else 8f
            paint.color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            val ringRadius = 28f + progress * if (fx.weakPoint) 92f else 68f
            canvas.drawCircle(fx.x, fx.y, ringRadius, paint)

            val rays = if (fx.weakPoint) 12 else 8
            val inner = 18f + progress * 20f
            val outer = if (fx.weakPoint) 120f else 88f
            for (i in 0 until rays) {
                val angle = i * (Math.PI * 2.0 / rays)
                val x1 = fx.x + (cos(angle) * inner).toFloat()
                val y1 = fx.y + (sin(angle) * inner).toFloat()
                val x2 = fx.x + (cos(angle) * outer * fade).toFloat()
                val y2 = fx.y + (sin(angle) * outer * fade).toFloat()
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
            paint.style = Paint.Style.FILL

            textPaint.color = Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
            textPaint.textSize = if (fx.weakPoint) 48f else 36f
            val popupY = fx.y - 58f - progress * 115f
            val damageLabel = if (fx.guarded) "GUARD  -${fx.damage.roundToInt()}" else "-${fx.damage.roundToInt()}"
            canvas.drawText(damageLabel, fx.x, popupY, textPaint)
            if (fx.weakPoint) {
                textPaint.textSize = 35f
                canvas.drawText("WEAK!", fx.x, popupY - 47f, textPaint)
            }
        }
    }

    private fun drawControls(canvas: Canvas) {
        paint.color = Color.argb(92, 255, 255, 255)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = Color.argb(165, 255, 255, 255)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(190, 79, 157, 210)
        canvas.drawCircle(joystickKnobX, joystickKnobY, 66f, paint)
        textPaint.textSize = 26f
        textPaint.color = Color.WHITE
        canvas.drawText("上=ジャンプ", joystickCenterX, joystickCenterY - joystickRadius - 18f, textPaint)

        buttons.forEach { button ->
            val pressed = pointerButtons.values.any { it == button.type }
            paint.color = when (button.type) {
                ButtonType.PUNCH -> Color.argb(if (pressed) 235 else 185, 226, 84, 73)
                ButtonType.KICK -> Color.argb(if (pressed) 235 else 185, 240, 147, 56)
                ButtonType.GUARD -> Color.argb(if (pressed) 235 else 185, 62, 141, 208)
                ButtonType.SPECIAL_1 -> Color.argb(if (pressed) 235 else 185, 127, 86, 211)
                ButtonType.SPECIAL_2 -> Color.argb(if (pressed) 235 else 185, 53, 177, 165)
            }
            canvas.drawCircle(button.x, button.y, button.radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = Color.argb(220, 255, 255, 255)
            canvas.drawCircle(button.x, button.y, button.radius, paint)
            paint.style = Paint.Style.FILL
            textPaint.textSize = if (button.label.length >= 4) 28f else 32f
            textPaint.color = Color.WHITE
            canvas.drawText(button.label, button.x, button.y + 10f, textPaint)
        }
    }

    private fun drawResult(canvas: Canvas) {
        val winner = logic.winner ?: return
        paint.color = Color.argb(190, 0, 0, 0)
        canvas.drawRect(0f, 0f, GameLogic.WORLD_WIDTH, GameLogic.WORLD_HEIGHT, paint)
        textPaint.color = Color.WHITE
        textPaint.textSize = 86f
        val result = when (winner) {
            "PLAYER" -> "YOU WIN"
            "ENEMY" -> "YOU LOSE"
            else -> "DRAW"
        }
        canvas.drawText(result, 960f, 470f, textPaint)
        textPaint.textSize = 38f
        canvas.drawText("タップで再戦", 960f, 550f, textPaint)
    }

    private fun toWorldX(screenX: Float): Float = screenX * GameLogic.WORLD_WIDTH / width
    private fun toWorldY(screenY: Float): Float = screenY * GameLogic.WORLD_HEIGHT / height

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()).toFloat()
}
