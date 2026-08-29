package com.sktpj.dynavorgmonsters

enum class SpriteMotion(val row: Int, val loops: Boolean) {
    FORWARD(0, true),
    BACKWARD(1, true),
    JUMP(2, true),
    PUNCH(3, false),
    KICK(4, false),
    GUARD(5, true),
    SPECIAL_1(6, false),
    SPECIAL_2(7, false),
}

class SpriteAnimationController(
    private val framesPerMotion: Int = 8,
    private val fps: Float = 15f,
) {
    init {
        require(framesPerMotion > 0)
        require(fps > 0f)
    }

    var currentMotion: SpriteMotion = SpriteMotion.FORWARD
        private set
    var frameIndex: Int = 0
        private set

    private var elapsedInFrame = 0f
    private var oneShotMotion: SpriteMotion? = null
    private var loopMotion: SpriteMotion? = null

    val isOneShotPlaying: Boolean
        get() = oneShotMotion != null

    fun reset() {
        currentMotion = SpriteMotion.FORWARD
        frameIndex = 0
        elapsedInFrame = 0f
        oneShotMotion = null
        loopMotion = null
    }

    fun startOneShot(motion: SpriteMotion) {
        require(!motion.loops)
        oneShotMotion = motion
        currentMotion = motion
        frameIndex = 0
        elapsedInFrame = 0f
    }

    fun update(deltaSeconds: Float, requestedLoopMotion: SpriteMotion?) {
        val dt = deltaSeconds.coerceAtLeast(0f)
        val activeOneShot = oneShotMotion
        if (activeOneShot != null) {
            advanceOneShot(dt, requestedLoopMotion)
            return
        }

        if (requestedLoopMotion == null) {
            setIdle()
            return
        }
        require(requestedLoopMotion.loops)

        if (loopMotion != requestedLoopMotion || currentMotion != requestedLoopMotion) {
            loopMotion = requestedLoopMotion
            currentMotion = requestedLoopMotion
            frameIndex = 0
            elapsedInFrame = 0f
        }
        advanceLoop(dt)
    }

    private fun advanceOneShot(deltaSeconds: Float, requestedLoopMotion: SpriteMotion?) {
        elapsedInFrame += deltaSeconds
        val frameDuration = 1f / fps
        while (elapsedInFrame >= frameDuration && oneShotMotion != null) {
            elapsedInFrame -= frameDuration
            if (frameIndex < framesPerMotion - 1) {
                frameIndex += 1
            } else {
                oneShotMotion = null
                elapsedInFrame = 0f
                if (requestedLoopMotion == null) {
                    setIdle()
                } else {
                    require(requestedLoopMotion.loops)
                    loopMotion = requestedLoopMotion
                    currentMotion = requestedLoopMotion
                    frameIndex = 0
                }
            }
        }
    }

    private fun advanceLoop(deltaSeconds: Float) {
        elapsedInFrame += deltaSeconds
        val frameDuration = 1f / fps
        while (elapsedInFrame >= frameDuration) {
            elapsedInFrame -= frameDuration
            frameIndex = (frameIndex + 1) % framesPerMotion
        }
    }

    private fun setIdle() {
        oneShotMotion = null
        loopMotion = null
        currentMotion = SpriteMotion.FORWARD
        frameIndex = 0
        elapsedInFrame = 0f
    }
}
