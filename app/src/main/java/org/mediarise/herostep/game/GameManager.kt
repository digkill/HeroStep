package org.mediarise.herostep.game

import org.mediarise.herostep.data.model.*
import org.mediarise.herostep.data.model.Unit as GameUnit

class GameManager {
    private var gameState: GameState? = null
    private var pathfinder: Pathfinder? = null
    
    fun startNewGame(race: Race, heroName: String, profession: Profession? = null): GameState {
        val board = GameBoard()
        val hero = createHero(race, heroName, profession)
        val aiHeroes = createAIHeroes()
        
        // Получаем все краевые ячейки
        val edgeCells = board.getEdgeCells()
        
        // Сортируем краевые ячейки по углу для равномерного распределения
        val sortedEdgeCells = edgeCells.sortedBy { cell ->
            val angle = kotlin.math.atan2(cell.y.toDouble(), cell.x.toDouble())
            angle
        }
        
        // Всего героев: 1 игрок + 2 AI = 3
        val allHeroes = listOf(hero) + aiHeroes
        val totalHeroes = allHeroes.size
        
        // Равномерно распределяем героев по краю
        if (sortedEdgeCells.size >= totalHeroes) {
            val step = sortedEdgeCells.size / totalHeroes
            
            allHeroes.forEachIndexed { index, h ->
                val cellIndex = (index * step) % sortedEdgeCells.size
                val cell = sortedEdgeCells[cellIndex]
                cell.hero = h
                h.currentCell = cell
            }
        } else {
            // Если краевых ячеек меньше, размещаем на доступных
            sortedEdgeCells.forEachIndexed { index, cell ->
                if (index < allHeroes.size) {
                    cell.hero = allHeroes[index]
                    allHeroes[index].currentCell = cell
                }
            }
        }
        
        gameState = GameState(board, hero, aiHeroes)
        pathfinder = Pathfinder(board)
        return gameState!!
    }
    
    private fun createHero(race: Race, name: String, profession: Profession? = null): Hero {
        // Если профессия не указана, выбираем случайную
        val heroProfession = profession ?: Profession.values().random()
        
        // Комбинируем базовые статы расы и профессии
        val raceStats = getRaceBaseStats(race)
        val professionStats = heroProfession
        
        val finalHealth = (raceStats.health + professionStats.baseHealth) / 2
        val finalAttack = (raceStats.attack + professionStats.baseAttack) / 2
        val finalDefense = (raceStats.defense + professionStats.baseDefense) / 2
        val finalMovement = (raceStats.movement + professionStats.baseMovement) / 2
        
        return Hero(
            id = "hero_${System.currentTimeMillis()}",
            name = name,
            race = race,
            profession = heroProfession,
            health = finalHealth,
            maxHealth = finalHealth,
            attack = finalAttack,
            defense = finalDefense,
            movementPoints = finalMovement,
            maxMovementPoints = finalMovement
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
        // Создаем 2 AI героев с разными расами (без повторений)
        val allRaces = Race.values().toList()
        val availableRaces = allRaces.shuffled().take(2)
        val allProfessions = Profession.values().toList()
        
        return availableRaces.mapIndexed { index, race ->
            // Выбираем случайную профессию для каждого AI героя
            val profession = allProfessions.random()
            
            // Комбинируем базовые статы расы и профессии
            val raceStats = getRaceBaseStats(race)
            
            val finalHealth = (raceStats.health + profession.baseHealth) / 2
            val finalAttack = (raceStats.attack + profession.baseAttack) / 2
            val finalDefense = (raceStats.defense + profession.baseDefense) / 2
            val finalMovement = (raceStats.movement + profession.baseMovement) / 2
            
            Hero(
                id = "ai_${race.name}_$index",
                name = "${race.displayName} ${profession.displayName}",
                race = race,
                profession = profession,
                health = finalHealth,
                maxHealth = finalHealth,
                attack = finalAttack,
                defense = finalDefense,
                movementPoints = finalMovement,
                maxMovementPoints = finalMovement
            )
        }
    }
    
    fun moveHero(hero: Hero, targetCell: HexCell): Boolean {
        return try {
            val currentCell = hero.currentCell ?: return false
            val board = gameState?.board ?: return false
            val pathfinder = this.pathfinder ?: return false
            
            if (!hero.canMove()) return false
            if (!targetCell.canMoveTo()) return false
            
            // Если целевая ячейка - соседняя, используем старую логику
            val neighbors = board.getNeighbors(currentCell)
            if (targetCell in neighbors) {
                val movementCost = targetCell.type.movementCost
                if (hero.movementPoints < movementCost) return false
                
                // Перемещаем героя
                currentCell.hero = null
                targetCell.hero = hero
                hero.currentCell = targetCell
                hero.movementPoints -= movementCost
                
                return true
            }
            
            // Ищем путь до целевой ячейки
            val path = pathfinder.findPath(currentCell, targetCell, hero) ?: return false
            
            // Вычисляем стоимость пути
            val pathCost = pathfinder.calculatePathCost(path)
            
            // Проверяем, достаточно ли очков перемещения
            if (hero.movementPoints < pathCost) return false
            
            // Перемещаем героя
            currentCell.hero = null
            targetCell.hero = hero
            hero.currentCell = targetCell
            hero.movementPoints -= pathCost
            
            true
        } catch (e: Exception) {
            android.util.Log.e("GameManager", "Error moving hero: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Получает все доступные ячейки для перемещения героя
     */
    fun getReachableCells(hero: Hero): Set<HexCell> {
        return try {
            val currentCell = hero.currentCell ?: return emptySet()
            val pathfinder = this.pathfinder ?: return emptySet()
            val board = gameState?.board ?: return emptySet()
            
            // Дополнительная проверка
            if (board.getNeighbors(currentCell).isEmpty()) {
                android.util.Log.w("GameManager", "Current cell has no neighbors")
                return setOf(currentCell)
            }
            
            pathfinder.findReachableCells(currentCell, hero)
        } catch (e: Exception) {
            android.util.Log.e("GameManager", "Error finding reachable cells: ${e.message}", e)
            e.printStackTrace()
            emptySet()
        }
    }
    
    /**
     * Находит путь от текущей позиции героя до целевой ячейки
     */
    fun findPath(hero: Hero, targetCell: HexCell): List<HexCell>? {
        val currentCell = hero.currentCell ?: return null
        val pathfinder = this.pathfinder ?: return null
        
        return pathfinder.findPath(currentCell, targetCell, hero)
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

