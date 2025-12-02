package org.mediarise.herostep.game

import org.mediarise.herostep.data.model.GameBoard
import org.mediarise.herostep.data.model.HexCell
import org.mediarise.herostep.data.model.Hero

/**
 * Класс для поиска пути на гексагональной сетке
 */
class Pathfinder(private val board: GameBoard) {
    
    /**
     * Находит путь от начальной ячейки до целевой
     * @return Список ячеек от начальной до целевой (включая обе), или null если путь не найден
     */
    fun findPath(start: HexCell, target: HexCell, hero: Hero): List<HexCell>? {
        return try {
            if (start == target) return listOf(start)
            
            val openSet = mutableSetOf<HexCell>()
            val closedSet = mutableSetOf<HexCell>()
            val cameFrom = mutableMapOf<HexCell, HexCell>()
            val gScore = mutableMapOf<HexCell, Int>()
            val fScore = mutableMapOf<HexCell, Int>()
            
            openSet.add(start)
            gScore[start] = 0
            fScore[start] = heuristic(start, target)
            
            var iterations = 0
            val maxIterations = 1000 // Защита от бесконечного цикла
            
            while (openSet.isNotEmpty() && iterations < maxIterations) {
                iterations++
                
                // Находим ячейку с минимальным fScore
                val current = openSet.minByOrNull { fScore.getOrDefault(it, Int.MAX_VALUE) } ?: break
                
                if (current == target) {
                    // Восстанавливаем путь
                    return reconstructPath(cameFrom, current)
                }
                
                openSet.remove(current)
                closedSet.add(current)
                
                try {
                    val neighbors = board.getNeighbors(current)
                    for (neighbor in neighbors) {
                        if (neighbor in closedSet) continue
                        if (!neighbor.canMoveTo()) continue
                        
                        val tentativeGScore = gScore.getOrDefault(current, Int.MAX_VALUE) + neighbor.type.movementCost
                        
                        if (neighbor !in openSet) {
                            openSet.add(neighbor)
                        } else if (tentativeGScore >= gScore.getOrDefault(neighbor, Int.MAX_VALUE)) {
                            continue
                        }
                        
                        cameFrom[neighbor] = current
                        gScore[neighbor] = tentativeGScore
                        fScore[neighbor] = tentativeGScore + heuristic(neighbor, target)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Pathfinder", "Error getting neighbors: ${e.message}", e)
                    break
                }
            }
            
            if (iterations >= maxIterations) {
                android.util.Log.w("Pathfinder", "Path finding reached max iterations")
            }
            
            null // Путь не найден
        } catch (e: Exception) {
            android.util.Log.e("Pathfinder", "Error finding path: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Находит все доступные ячейки для перемещения героя
     * @return Множество ячеек, до которых герой может добраться с текущими очками перемещения
     */
    fun findReachableCells(start: HexCell, hero: Hero): Set<HexCell> {
        return try {
            val reachable = mutableSetOf<HexCell>()
            val visited = mutableSetOf<HexCell>()
            val queue = mutableListOf<Pair<HexCell, Int>>() // Ячейка и оставшиеся очки перемещения
            
            // Проверяем, что у героя есть очки перемещения
            val movementPoints = hero.movementPoints.coerceAtLeast(0)
            if (movementPoints <= 0) {
                return setOf(start) // Возвращаем только текущую ячейку
            }
            
            queue.add(Pair(start, movementPoints))
            visited.add(start)
            reachable.add(start)
            
            var iterations = 0
            val maxIterations = 500 // Защита от бесконечного цикла
            
            while (queue.isNotEmpty() && iterations < maxIterations) {
                iterations++
                val (current, remainingPoints) = queue.removeAt(0)
                
                if (remainingPoints <= 0) continue
                
                try {
                    val neighbors = board.getNeighbors(current)
                    for (neighbor in neighbors) {
                        if (neighbor in visited) continue
                        if (!neighbor.canMoveTo()) continue
                        
                        val cost = neighbor.type.movementCost.coerceAtLeast(1)
                        if (cost > remainingPoints) continue
                        
                        val newRemaining = remainingPoints - cost
                        visited.add(neighbor)
                        reachable.add(neighbor)
                        
                        if (newRemaining > 0) {
                            queue.add(Pair(neighbor, newRemaining))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Pathfinder", "Error finding neighbors: ${e.message}", e)
                    break
                }
            }
            
            if (iterations >= maxIterations) {
                android.util.Log.w("Pathfinder", "Reachable cells search reached max iterations")
            }
            
            reachable
        } catch (e: Exception) {
            android.util.Log.e("Pathfinder", "Error finding reachable cells: ${e.message}", e)
            e.printStackTrace()
            setOf(start) // Возвращаем хотя бы стартовую ячейку
        }
    }
    
    /**
     * Вычисляет эвристику (расстояние) между двумя ячейками
     */
    private fun heuristic(a: HexCell, b: HexCell): Int {
        // Используем манхэттенское расстояние для гексагональной сетки
        val dx = kotlin.math.abs(a.x - b.x)
        val dy = kotlin.math.abs(a.y - b.y)
        val dz = kotlin.math.abs((-a.x - a.y) - (-b.x - b.y))
        return (dx + dy + dz) / 2
    }
    
    /**
     * Восстанавливает путь от целевой ячейки до начальной
     */
    private fun reconstructPath(cameFrom: Map<HexCell, HexCell>, current: HexCell): List<HexCell> {
        val path = mutableListOf<HexCell>()
        var node: HexCell? = current
        
        while (node != null) {
            path.add(0, node)
            node = cameFrom[node]
        }
        
        return path
    }
    
    /**
     * Вычисляет стоимость перемещения по пути
     */
    fun calculatePathCost(path: List<HexCell>): Int {
        if (path.size <= 1) return 0
        
        var cost = 0
        for (i in 1 until path.size) {
            cost += path[i].type.movementCost
        }
        return cost
    }
}

