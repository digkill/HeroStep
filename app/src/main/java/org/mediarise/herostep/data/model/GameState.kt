package org.mediarise.herostep.data.model

data class DiceRoll(
    val dieOne: Int,
    val dieTwo: Int
) {
    val total: Int
        get() = dieOne + dieTwo
}

data class GameState(
    val board: GameBoard,
    val playerHero: Hero,
    val aiHeroes: List<Hero>,
    var currentTurn: Int = 1,
    var currentHeroIndex: Int = 0,
    var currentDiceRoll: DiceRoll? = null,
    var hasRolledDice: Boolean = false
) {
    val turnOrder: List<Hero>
        get() = listOf(playerHero) + aiHeroes

    val currentHero: Hero
        get() = turnOrder[currentHeroIndex]

    val isPlayerTurn: Boolean
        get() = currentHero.id == playerHero.id

    fun setupNewGameTurnState() {
        turnOrder.forEach { hero ->
            hero.movementPoints = 0
        }
        currentTurn = 1
        currentHeroIndex = 0
        currentDiceRoll = null
        hasRolledDice = false
    }

    fun applyDiceRoll(diceRoll: DiceRoll) {
        currentDiceRoll = diceRoll
        hasRolledDice = true
        currentHero.movementPoints = diceRoll.total
    }

    fun endCurrentTurn() {
        currentHero.movementPoints = 0
        currentDiceRoll = null
        hasRolledDice = false

        currentHeroIndex = (currentHeroIndex + 1) % turnOrder.size
        if (currentHeroIndex == 0) {
            currentTurn++
        }
    }
}

