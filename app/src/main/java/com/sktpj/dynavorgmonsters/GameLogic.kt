package com.sktpj.dynavorgmonsters

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

data class Fighter(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var hp: Float = 100f,
    var facing: Float = 1f,
    var jumpsUsed: Int = 0,
    var flightUsedSeconds: Float = 0f,
    var guarding: Boolean = false,
    var attackCooldownSeconds: Float = 0f,
)

data class Projectile(
    var x: Float,
    var y: Float,
    val vx: Float,
    val fromPlayer: Boolean,
    var active: Boolean = true,
)

class GameLogic {
    companion object {
        const val WORLD_WIDTH = 1920f
        const val WORLD_HEIGHT = 1080f
        const val GROUND_Y = 790f
        const val MAX_HP = 100f
        const val MAX_FLIGHT_SECONDS = 5f
        private const val PLAYER_SPEED = 440f
        private const val ENEMY_SPEED = 215f
        private const val GRAVITY = 1700f
        private const val JUMP_VELOCITY = -720f
        private const val FLIGHT_ACCEL = 2300f
    }

    val player = Fighter(x = 520f, y = GROUND_Y, facing = 1f)
    val enemy = Fighter(x = 1400f, y = GROUND_Y, facing = -1f)
    val projectiles = mutableListOf<Projectile>()

    var winner: String? = null
        private set
    var playerAttackFlashSeconds = 0f
        private set
    var enemyAttackFlashSeconds = 0f
        private set
    var playerSpecialFlashSeconds = 0f
        private set

    fun reset() {
        player.x = 520f
        player.y = GROUND_Y
        player.vx = 0f
        player.vy = 0f
        player.hp = MAX_HP
        player.facing = 1f
        player.jumpsUsed = 0
        player.flightUsedSeconds = 0f
        player.guarding = false
        player.attackCooldownSeconds = 0f

        enemy.x = 1400f
        enemy.y = GROUND_Y
        enemy.vx = 0f
        enemy.vy = 0f
        enemy.hp = MAX_HP
        enemy.facing = -1f
        enemy.jumpsUsed = 0
        enemy.flightUsedSeconds = 0f
        enemy.guarding = false
        enemy.attackCooldownSeconds = 0f

        projectiles.clear()
        winner = null
        playerAttackFlashSeconds = 0f
        enemyAttackFlashSeconds = 0f
        playerSpecialFlashSeconds = 0f
    }

    fun setGuarding(guarding: Boolean) {
        player.guarding = guarding && winner == null
    }

    fun jump(): Boolean {
        if (winner != null || player.jumpsUsed >= 2) return false
        if (player.y >= GROUND_Y - 0.5f && player.jumpsUsed == 0) {
            player.y = GROUND_Y - 1f
        }
        player.vy = JUMP_VELOCITY
        player.jumpsUsed += 1
        return true
    }

    fun punch(): Boolean {
        if (!canPlayerAttack()) return false
        player.attackCooldownSeconds = 0.28f
        playerAttackFlashSeconds = 0.12f
        if (isEnemyInFrontRange(175f, 135f)) {
            damageEnemy(9f)
        }
        return true
    }

    fun kick(): Boolean {
        if (!canPlayerAttack()) return false
        player.attackCooldownSeconds = 0.42f
        playerAttackFlashSeconds = 0.16f
        if (isEnemyInFrontRange(235f, 155f)) {
            damageEnemy(13f)
        }
        return true
    }

    fun special1(): Boolean {
        if (!canPlayerAttack()) return false
        player.attackCooldownSeconds = 0.72f
        playerSpecialFlashSeconds = 0.20f
        projectiles += Projectile(
            x = player.x + player.facing * 85f,
            y = player.y - 105f,
            vx = player.facing * 700f,
            fromPlayer = true,
        )
        return true
    }

    fun special2(): Boolean {
        if (!canPlayerAttack()) return false
        player.attackCooldownSeconds = 0.92f
        playerSpecialFlashSeconds = 0.28f
        if (abs(enemy.x - player.x) <= 310f && abs(enemy.y - player.y) <= 180f) {
            damageEnemy(18f)
        }
        return true
    }

    fun damagePlayer(amount: Float, attackerX: Float = enemy.x) {
        applyDamage(player, amount, attackerX)
    }

    fun damageEnemy(amount: Float, attackerX: Float = player.x) {
        applyDamage(enemy, amount, attackerX)
    }

    fun update(deltaSeconds: Float, horizontalInput: Float, upHeld: Boolean) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        if (dt <= 0f) return

        player.attackCooldownSeconds = max(0f, player.attackCooldownSeconds - dt)
        enemy.attackCooldownSeconds = max(0f, enemy.attackCooldownSeconds - dt)
        playerAttackFlashSeconds = max(0f, playerAttackFlashSeconds - dt)
        enemyAttackFlashSeconds = max(0f, enemyAttackFlashSeconds - dt)
        playerSpecialFlashSeconds = max(0f, playerSpecialFlashSeconds - dt)

        if (winner != null) {
            player.vx = 0f
            enemy.vx = 0f
            return
        }

        updatePlayer(dt, horizontalInput.coerceIn(-1f, 1f), upHeld)
        updateEnemy(dt)
        updateProjectiles(dt)
        resolveBodySeparation()
        resolveWinner()
    }

    private fun updatePlayer(dt: Float, horizontalInput: Float, upHeld: Boolean) {
        player.vx = if (player.guarding) horizontalInput * PLAYER_SPEED * 0.35f else horizontalInput * PLAYER_SPEED
        if (abs(horizontalInput) > 0.08f) {
            player.facing = sign(horizontalInput)
        } else {
            player.facing = if (enemy.x >= player.x) 1f else -1f
        }
        player.x = (player.x + player.vx * dt).coerceIn(110f, WORLD_WIDTH - 110f)

        val inAir = player.y < GROUND_Y - 0.5f || player.vy < 0f
        if (inAir && upHeld && player.flightUsedSeconds < MAX_FLIGHT_SECONDS) {
            val usable = min(dt, MAX_FLIGHT_SECONDS - player.flightUsedSeconds)
            player.vy = max(player.vy - FLIGHT_ACCEL * usable, -430f)
            player.flightUsedSeconds += usable
        }

        player.vy += GRAVITY * dt
        player.y += player.vy * dt
        if (player.y >= GROUND_Y) {
            player.y = GROUND_Y
            player.vy = 0f
            player.jumpsUsed = 0
            player.flightUsedSeconds = 0f
        }
    }

    private fun updateEnemy(dt: Float) {
        val dx = player.x - enemy.x
        enemy.facing = if (dx >= 0f) 1f else -1f

        if (abs(dx) > 155f) {
            enemy.vx = sign(dx) * ENEMY_SPEED
            enemy.x = (enemy.x + enemy.vx * dt).coerceIn(110f, WORLD_WIDTH - 110f)
        } else {
            enemy.vx = 0f
            if (enemy.attackCooldownSeconds <= 0f && abs(player.y - enemy.y) < 150f) {
                enemy.attackCooldownSeconds = 0.78f
                enemyAttackFlashSeconds = 0.18f
                damagePlayer(8f, enemy.x)
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        projectiles.forEach { projectile ->
            if (!projectile.active) return@forEach
            projectile.x += projectile.vx * dt
            if (projectile.x < -100f || projectile.x > WORLD_WIDTH + 100f) {
                projectile.active = false
                return@forEach
            }

            if (projectile.fromPlayer) {
                val hit = abs(projectile.x - enemy.x) <= 80f && abs(projectile.y - (enemy.y - 95f)) <= 115f
                if (hit) {
                    projectile.active = false
                    damageEnemy(15f, projectile.x)
                }
            }
        }
        projectiles.removeAll { !it.active }
    }

    private fun resolveBodySeparation() {
        val minDistance = 125f
        val dx = enemy.x - player.x
        val distance = abs(dx)
        if (distance in 0.001f..<minDistance) {
            val push = (minDistance - distance) * 0.5f
            val direction = sign(dx)
            player.x = (player.x - direction * push).coerceIn(110f, WORLD_WIDTH - 110f)
            enemy.x = (enemy.x + direction * push).coerceIn(110f, WORLD_WIDTH - 110f)
        }
    }

    private fun resolveWinner() {
        winner = when {
            player.hp <= 0f && enemy.hp <= 0f -> "DRAW"
            enemy.hp <= 0f -> "PLAYER"
            player.hp <= 0f -> "ENEMY"
            else -> null
        }
    }

    private fun canPlayerAttack(): Boolean = winner == null && player.attackCooldownSeconds <= 0f && !player.guarding

    private fun isEnemyInFrontRange(horizontalRange: Float, verticalRange: Float): Boolean {
        val dx = enemy.x - player.x
        val inFront = dx * player.facing >= 0f
        return inFront && abs(dx) <= horizontalRange && abs(enemy.y - player.y) <= verticalRange
    }

    private fun applyDamage(target: Fighter, amount: Float, attackerX: Float) {
        if (amount <= 0f || target.hp <= 0f) return
        val attackerDirection = sign(attackerX - target.x)
        val guardingFront = target.guarding && attackerDirection != 0f && attackerDirection == target.facing
        val effective = if (guardingFront) amount * 0.30f else amount
        target.hp = (target.hp - effective).coerceIn(0f, MAX_HP)
        resolveWinner()
    }
}
