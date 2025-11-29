package org.mediarise.herostep.game

import org.mediarise.herostep.data.model.*
import org.mediarise.herostep.data.model.Unit as GameUnit

class GameManager {
    private var gameState: GameState? = null
    
    fun startNewGame(race: Race, heroName: String): GameState {
        val board = GameBoard()
        val hero = createHero(race, heroName)
        val aiHeroes = createAIHeroes()
        
        // Размещаем игрового героя на стартовой позиции
        val startCell = board.getCell(0, 0)
        startCell?.hero = hero
        hero.currentCell = startCell
        
        // Размещаем AI героев на случайных позициях
        val allCells = board.getAllCells().filter { it.hero == null && it != startCell }
        val randomCells = allCells.shuffled().take(aiHeroes.size)
        
        aiHeroes.forEachIndexed { index, aiHero ->
            if (index < randomCells.size) {
                val cell = randomCells[index]
                cell.hero = aiHero
                aiHero.currentCell = cell
            }
        }
        
        gameState = GameState(board, hero, aiHeroes)
        return gameState!!
    }
    
    private fun createHero(race: Race, name: String): Hero {
        val baseStats = getRaceBaseStats(race)
        return Hero(
            id = "hero_${System.currentTimeMillis()}",
            name = name,
            race = race,
            health = baseStats.health,
            maxHealth = baseStats.health,
            attack = baseStats.attack,
            defense = baseStats.defense,
            movementPoints = baseStats.movement,
            maxMovementPoints = baseStats.movement
        )
    }
    
    private fun getRaceBaseStats(race: Race): RaceStats {
        return when (race) {
            Race.HUMANS -> RaceStats(100, 10, 5, 3)
            Race.ORCS -> RaceStats(120, 12, 4, 3)
            Race.ELVES -> RaceStats(80, 8, 6, 4)
            Race.DWARVES -> RaceStats(110, 9, 7, 2)
            Race.CHAOS_LEGION -> RaceStats(130, 13, 3, 3)
            Race.UNDEAD -> RaceStats(90, 11, 5, 3)
        }
    }
    
    private data class RaceStats(
        val health: Int,
        val attack: Int,
        val defense: Int,
        val movement: Int
    )
    
    private fun createAIHeroes(): List<Hero> {
        // Создаем 4 героев с разными расами (без повторений)
        val allRaces = Race.values().toList()
        val availableRaces = allRaces.shuffled().take(4)
        
        return availableRaces.mapIndexed { index, race ->
            val stats = getRaceBaseStats(race)
            Hero(
                id = "ai_${race.name}_$index",
                name = "${race.displayName} Hero",
                race = race,
                health = stats.health,
                maxHealth = stats.health,
                attack = stats.attack,
                defense = stats.defense,
                movementPoints = stats.movement,
                maxMovementPoints = stats.movement
            )
        }
    }
    
    fun moveHero(hero: Hero, targetCell: HexCell): Boolean {
        val currentCell = hero.currentCell ?: return false
        val board = gameState?.board ?: return false
        
        if (!hero.canMove()) return false
        if (!targetCell.canMoveTo()) return false
        
        val neighbors = board.getNeighbors(currentCell)
        if (targetCell !in neighbors) return false
        
        val movementCost = targetCell.type.movementCost
        if (hero.movementPoints < movementCost) return false
        
        // Перемещаем героя
        currentCell.hero = null
        targetCell.hero = hero
        hero.currentCell = targetCell
        hero.movementPoints -= movementCost
        
        return true
    }
    
    fun attackMob(hero: Hero, mob: Mob): BattleResult {
        var mobHealth = mob.health
        var heroHealth = hero.health
        
        // Простой бой: герой атакует, моб контратакует
        val heroDamage = (hero.attack - mob.defense).coerceAtLeast(1)
        mobHealth -= heroDamage
        
        if (mobHealth > 0) {
            val mobDamage = (mob.attack - hero.defense).coerceAtLeast(1)
            heroHealth -= mobDamage
        }
        
        mob.health = mobHealth.coerceAtLeast(0)
        hero.health = heroHealth.coerceAtLeast(0)
        
        val heroWon = mob.health <= 0
        
        if (heroWon) {
            hero.addExperience(mob.experienceReward)
            hero.gold += mob.goldReward
            // Удаляем моба с клетки
            hero.currentCell?.mob = null
            hero.currentCell?.hasMob = false
        }
        
        return BattleResult(heroWon, heroDamage, mobDamage = if (!heroWon) mob.attack - hero.defense else 0)
    }
    
    fun hireUnit(hero: Hero, unit: GameUnit): Boolean {
        if (hero.gold < unit.cost) return false
        if (hero.currentCell?.hasTavern != true) return false
        
        hero.gold -= unit.cost
        // Добавляем юнита в отряд героя (упрощенная версия)
        return true
    }
    
    fun getCurrentGameState(): GameState? = gameState
}

data class BattleResult(
    val heroWon: Boolean,
    val heroDamage: Int,
    val mobDamage: Int
)

