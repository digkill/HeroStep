package org.mediarise.herostep.data.model

data class HexCell(
    val x: Int,
    val y: Int,
    val type: HexCellType,
    var hasTavern: Boolean = false,
    var hasMob: Boolean = false,
    var mob: Mob? = null,
    var unit: Unit? = null,
    var hero: Hero? = null
) {
    fun isOccupied(): Boolean = unit != null || hero != null
    fun canMoveTo(): Boolean = !isOccupied()

    // Важно: не включаем hero/unit/mob в equals/hashCode, чтобы избежать рекурсии
    // (HexCell -> Hero -> currentCell -> HexCell) и падений в Set/Map.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HexCell) return false
        return x == other.x && y == other.y
    }

    override fun hashCode(): Int = 31 * x + y
}

