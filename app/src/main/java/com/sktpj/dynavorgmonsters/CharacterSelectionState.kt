package com.sktpj.dynavorgmonsters

class CharacterSelectionState(
    private val rosterSize: Int,
) {
    init {
        require(rosterSize > 0)
    }

    var playerIndex: Int? = null
        private set

    var enemyIndex: Int? = null
        private set

    val ready: Boolean
        get() = playerIndex != null && enemyIndex != null

    fun selectPlayer(index: Int) {
        require(index in 0 until rosterSize)
        playerIndex = index
    }

    fun selectEnemy(index: Int) {
        require(index in 0 until rosterSize)
        enemyIndex = index
    }

    fun selectedPair(): Pair<Int, Int>? {
        val player = playerIndex ?: return null
        val enemy = enemyIndex ?: return null
        return player to enemy
    }
}
