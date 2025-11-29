package org.mediarise.herostep.data.model

data class Unit(
    val id: String,
    val name: String,
    val race: Race,
    var health: Int,
    val maxHealth: Int,
    val attack: Int,
    val defense: Int,
    val movementPoints: Int,
    val cost: Int, // Стоимость найма в золоте
    var currentCell: HexCell? = null
) {
    fun isAlive(): Boolean = health > 0
    fun canMove(): Boolean = movementPoints > 0 && isAlive()
}

