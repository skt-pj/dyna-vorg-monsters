package com.sktpj.dynavorgmonsters

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

enum class CombatTarget { PLAYER, ENEMY }
enum class AttackKind { PUNCH, KICK, SPECIAL_1, SPECIAL_2, ENEMY_ATTACK, DIRECT }

data class CombatEvent(
    val target: CombatTarget,
    val attackKind: AttackKind,
    val damage: Float,
    val impactX: Float,
    val impactY: Float,
    val weakPoint: Boolean,
    val guarded: Boolean,
    val impactStrength: Float,
)

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
    var hitStunSeconds: Float = 0f,
    var knockbackVelocityX: Float = 0f,
    var hitFlashSeconds: Float = 0f,
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
        const val WEAK_POINT_MULTIPLIER = 1.60f
        const val ENEMY_HEAD_Y_OFFSET = 185f
        const val ENEMY_HEAD_TOLERANCE = 50f

        private const val PLAYER_SPEED = 440f
        private const val ENEMY_SPEED = 215f
        private const val GRAVITY = 1700f
        private const val JUMP_VELOCITY = -720f
        private const val FLIGHT_ACCEL = 2300f
        private const val NORMAL_HIT_STUN = 0.10f
        private const val WEAK_HIT_STUN = 0.22f
        private const val NORMAL_KNOCKBACK = 180f
        private const val WEAK_KNOCKBACK = 360f
        private const val KNOCKBACK_DAMPING = 5.5f
    }

    val player = Fighter(x = 520f, y = GROUND_Y, facing = 1f)
    val enemy = Fighter(x = 1400f, y = GROUND_Y, facing = -1f)
    val projectiles = mutableListOf<Projectile>()
    private val combatEvents = mutableListOf<CombatEvent>()

    var winner: String? = null
        private set
    var playerAttackFlashSeconds = 0f
        private set
    var enemyAttackFlashSeconds = 0f
        private set
    var playerSpecialFlashSeconds = 0f
        private set

    fun reset() {
        resetFighter(player, 520f, 1f)
        resetFighter(enemy, 1400f, -1f)
        projectiles.clear()
        combatEvents.clear()
        winner = null
        playerAttackFlashSeconds = 0f
        enemyAttackFlashSeconds = 0f
        playerSpecialFlashSeconds = 0f
    }

    fun consumeCombatEvents(): List<CombatEvent> {
        if (combatEvents.isEmpty()) return emptyList()
        return combatEvents.toList().also { combatEvents.clear() }
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
            hitEnemy(
                baseDamage = 9f,
                attackKind = AttackKind.PUNCH,
                contactY = player.y - 125f,
                allowWeakPoint = true,
                knockbackDirection = player.facing,
            )
        }
        return true
    }

    fun kick(): Boolean {
        if (!canPlayerAttack()) return false
        player.attackCooldownSeconds = 0.42f
        playerAttackFlashSeconds = 0.16f
        if (isEnemyInFrontRange(235f, 155f)) {
            hitEnemy(
                baseDamage = 13f,
                attackKind = AttackKind.KICK,
                contactY = player.y - 90f,
                allowWeakPoint = true,
                knockbackDirection = player.facing,
            )
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
            hitEnemy(
                baseDamage = 18f,
                attackKind = AttackKind.SPECIAL_2,
                contactY = enemy.y - 105f,
                allowWeakPoint = false,
                knockbackDirection = player.facing,
            )
        }
        return true
    }

    fun damagePlayer(amount: Float, attackerX: Float = enemy.x) {
        val impactDirection = sign(attackerX - player.x).takeIf { it != 0f } ?: 1f
        applyDamage(
            target = player,
            amount = amount,
            attackerX = attackerX,
            combatTarget = CombatTarget.PLAYER,
            attackKind = AttackKind.DIRECT,
            impactX = player.x + impactDirection * 55f,
            impactY = player.y - 125f,
            weakPoint = false,
            impactStrength = 0.68f,
        )
    }

    fun damageEnemy(amount: Float, attackerX: Float = player.x) {
        val direction = sign(enemy.x - attackerX).takeIf { it != 0f } ?: player.facing
        val effective = applyDamage(
            target = enemy,
            amount = amount,
            attackerX = attackerX,
            combatTarget = CombatTarget.ENEMY,
            attackKind = AttackKind.DIRECT,
            impactX = enemy.x - direction * 65f,
            impactY = enemy.y - 115f,
            weakPoint = false,
            impactStrength = 0.55f,
        )
        if (effective > 0f) applyEnemyHitReaction(false, direction)
    }

    fun update(deltaSeconds: Float, horizontalInput: Float, upHeld: Boolean) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        if (dt <= 0f) return

        player.attackCooldownSeconds = max(0f, player.attackCooldownSeconds - dt)
        enemy.attackCooldownSeconds = max(0f, enemy.attackCooldownSeconds - dt)
        playerAttackFlashSeconds = max(0f, playerAttackFlashSeconds - dt)
        enemyAttackFlashSeconds = max(0f, enemyAttackFlashSeconds - dt)
        playerSpecialFlashSeconds = max(0f, playerSpecialFlashSeconds - dt)
        player.hitFlashSeconds = max(0f, player.hitFlashSeconds - dt)
        enemy.hitFlashSeconds = max(0f, enemy.hitFlashSeconds - dt)

        if (winner != null) {
            player.vx = 0f
            enemy.vx = 0f
            enemy.knockbackVelocityX = 0f
            return
        }

        updatePlayer(dt, horizontalInput.coerceIn(-1f, 1f), upHeld)
        updateEnemy(dt)
        updateProjectiles(dt)
        resolveBodySeparation()
        resolveWinner()
    }

    private fun resetFighter(fighter: Fighter, x: Float, facing: Float) {
        fighter.x = x
        fighter.y = GROUND_Y
        fighter.vx = 0f
        fighter.vy = 0f
        fighter.hp = MAX_HP
        fighter.facing = facing
        fighter.jumpsUsed = 0
        fighter.flightUsedSeconds = 0f
        fighter.guarding = false
        fighter.attackCooldownSeconds = 0f
        fighter.hitStunSeconds = 0f
        fighter.knockbackVelocityX = 0f
        fighter.hitFlashSeconds = 0f
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
        if (enemy.hitStunSeconds > 0f) {
            enemy.hitStunSeconds = max(0f, enemy.hitStunSeconds - dt)
            enemy.x = (enemy.x + enemy.knockbackVelocityX * dt).coerceIn(110f, WORLD_WIDTH - 110f)
            enemy.knockbackVelocityX *= max(0f, 1f - KNOCKBACK_DAMPING * dt)
            if (enemy.hitStunSeconds <= 0f) enemy.knockbackVelocityX = 0f
            return
        }

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
                applyDamage(
                    target = player,
                    amount = 8f,
                    attackerX = enemy.x,
                    combatTarget = CombatTarget.PLAYER,
                    attackKind = AttackKind.ENEMY_ATTACK,
                    impactX = player.x + enemy.facing * -55f,
                    impactY = player.y - 125f,
                    weakPoint = false,
                    impactStrength = 0.68f,
                )
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
                    val direction = sign(projectile.vx).takeIf { it != 0f } ?: player.facing
                    hitEnemy(
                        baseDamage = 15f,
                        attackKind = AttackKind.SPECIAL_1,
                        contactY = projectile.y,
                        allowWeakPoint = true,
                        knockbackDirection = direction,
                        impactXOverride = projectile.x,
                    )
                }
            }
        }
        projectiles.removeAll { !it.active }
    }

    private fun hitEnemy(
        baseDamage: Float,
        attackKind: AttackKind,
        contactY: Float,
        allowWeakPoint: Boolean,
        knockbackDirection: Float,
        impactXOverride: Float? = null,
    ) {
        val weakPoint = allowWeakPoint && isEnemyWeakPoint(contactY)
        val amount = baseDamage * if (weakPoint) WEAK_POINT_MULTIPLIER else 1f
        val direction = knockbackDirection.takeIf { it != 0f } ?: player.facing
        val impactX = impactXOverride ?: if (weakPoint) {
            enemy.x + enemy.facing * 95f
        } else {
            enemy.x - direction * 65f
        }
        val impactY = if (weakPoint) enemyHeadY() else contactY.coerceIn(enemy.y - 220f, enemy.y - 45f)

        val effective = applyDamage(
            target = enemy,
            amount = amount,
            attackerX = player.x,
            combatTarget = CombatTarget.ENEMY,
            attackKind = attackKind,
            impactX = impactX,
            impactY = impactY,
            weakPoint = weakPoint,
            impactStrength = if (weakPoint) 1f else 0.56f,
        )
        if (effective > 0f) applyEnemyHitReaction(weakPoint, direction)
    }

    private fun applyEnemyHitReaction(weakPoint: Boolean, direction: Float) {
        enemy.hitStunSeconds = max(enemy.hitStunSeconds, if (weakPoint) WEAK_HIT_STUN else NORMAL_HIT_STUN)
        enemy.knockbackVelocityX = direction * if (weakPoint) WEAK_KNOCKBACK else NORMAL_KNOCKBACK
        enemy.hitFlashSeconds = max(enemy.hitFlashSeconds, if (weakPoint) 0.18f else 0.11f)
    }

    private fun isEnemyWeakPoint(contactY: Float): Boolean = abs(contactY - enemyHeadY()) <= ENEMY_HEAD_TOLERANCE

    private fun enemyHeadY(): Float = enemy.y - ENEMY_HEAD_Y_OFFSET

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

    private fun applyDamage(
        target: Fighter,
        amount: Float,
        attackerX: Float,
        combatTarget: CombatTarget,
        attackKind: AttackKind,
        impactX: Float,
        impactY: Float,
        weakPoint: Boolean,
        impactStrength: Float,
    ): Float {
        if (amount <= 0f || target.hp <= 0f) return 0f
        val attackerDirection = sign(attackerX - target.x)
        val guardingFront = target.guarding && attackerDirection != 0f && attackerDirection == target.facing
        val effective = if (guardingFront) amount * 0.30f else amount
        target.hp = (target.hp - effective).coerceIn(0f, MAX_HP)
        target.hitFlashSeconds = max(target.hitFlashSeconds, if (guardingFront) 0.07f else 0.12f)

        combatEvents += CombatEvent(
            target = combatTarget,
            attackKind = attackKind,
            damage = effective,
            impactX = impactX,
            impactY = impactY,
            weakPoint = weakPoint,
            guarded = guardingFront,
            impactStrength = if (guardingFront) impactStrength * 0.45f else impactStrength,
        )
        resolveWinner()
        return effective
    }
}
