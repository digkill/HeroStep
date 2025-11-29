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
}

