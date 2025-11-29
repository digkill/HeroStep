package org.mediarise.herostep.data.model

class GameBoard {
    private val cells = mutableListOf<HexCell>()
    private val boardRadius = 4 // axial hex radius (~61 cells)

    init {
        generateBoard()
    }

    private fun generateBoard() {
        val types = HexCellType.values()
        var count = 0

        // Build a roughly hex-shaped board around the origin.
        for (q in -boardRadius..boardRadius) {
            for (r in -boardRadius..boardRadius) {
                val s = -q - r
                if (kotlin.math.abs(s) <= boardRadius) {
                    // Используем комбинацию координат для разнообразия типов
                    val typeIndex = (kotlin.math.abs(q * 3 + r * 5) + count) % types.size
                    val type = types[typeIndex]
                    val cell = HexCell(q, r, type)

                    if (Math.random() < 0.1 && q != 0 && r != 0) {
                        cell.hasTavern = true
                    }
                    if (Math.random() < 0.15 && !cell.hasTavern) {
                        cell.hasMob = true
                        cell.mob = generateRandomMob()
                    }

                    cells.add(cell)
                    count++
                    
                    // Safety check to prevent infinite loops
                    if (count > 100) {
                        android.util.Log.w("GameBoard", "Generated $count cells, stopping to prevent freeze")
                        return
                    }
                }
            }
        }
        
        // Подсчитываем типы ячеек для отладки
        val typeCounts = cells.groupingBy { it.type }.eachCount()
        android.util.Log.d("GameBoard", "Generated $count cells successfully")
        android.util.Log.d("GameBoard", "Cell types distribution: $typeCounts")
    }
    
    private fun generateRandomMob(): Mob {
        val mobTypes = listOf(
            Mob("mob_1", "Goblin Raider", 30, 30, 5, 2, 10, 20),
            Mob("mob_2", "Orc Brute", 50, 50, 8, 4, 20, 35),
            Mob("mob_3", "Skeleton Archer", 40, 40, 6, 3, 15, 25),
            Mob("mob_4", "Bandit", 25, 25, 7, 1, 8, 15)
        )
        return mobTypes.random()
    }
    
    fun getCell(q: Int, r: Int): HexCell? {
        return cells.find { it.x == q && it.y == r }
    }
    
    fun getAllCells(): List<HexCell> = cells
    
    fun getNeighbors(cell: HexCell): List<HexCell> {
        val neighbors = mutableListOf<HexCell>()
        val directions = listOf(
            Pair(1, 0), Pair(1, -1), Pair(0, -1),
            Pair(-1, 0), Pair(-1, 1), Pair(0, 1)
        )
        
        for ((dq, dr) in directions) {
            val neighbor = getCell(cell.x + dq, cell.y + dr)
            if (neighbor != null) {
                neighbors.add(neighbor)
            }
        }
        
        return neighbors
    }
    
    fun getEdgeCells(): List<HexCell> {
        // Находим все ячейки на краю (где хотя бы одна координата равна радиусу или -радиусу)
        return cells.filter { cell ->
            val s = -cell.x - cell.y
            kotlin.math.abs(cell.x) == boardRadius || 
            kotlin.math.abs(cell.y) == boardRadius || 
            kotlin.math.abs(s) == boardRadius
        }
    }
}
