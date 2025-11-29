package org.mediarise.herostep.data.model

data class GameState(
    val board: GameBoard,
    val playerHero: Hero,
    val aiHeroes: List<Hero>,
    var currentTurn: Int = 0,
    var isPlayerTurn: Boolean = true
) {
    fun nextTurn() {
        if (isPlayerTurn) {
            isPlayerTurn = false
            // AI ход
        } else {
            isPlayerTurn = true
            currentTurn++
            playerHero.movementPoints = playerHero.maxMovementPoints
        }
    }
}

