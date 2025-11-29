package org.mediarise.herostep.data.model

data class Hero(
    val id: String,
    val name: String,
    val race: Race,
    val profession: Profession,
    var level: Int = 1,
    var experience: Int = 0,
    var health: Int = 100,
    var maxHealth: Int = 100,
    var attack: Int = 10,
    var defense: Int = 5,
    var movementPoints: Int = 3,
    var maxMovementPoints: Int = 3,
    val skills: MutableList<Skill> = mutableListOf(),
    val inventory: MutableList<Item> = mutableListOf(),
    var gold: Int = 500,
    var currentCell: HexCell? = null
) {
    fun isAlive(): Boolean = health > 0
    fun canMove(): Boolean = movementPoints > 0 && isAlive()
    
    fun addExperience(exp: Int) {
        experience += exp
        val expForNextLevel = level * 100
        if (experience >= expForNextLevel) {
            levelUp()
        }
    }
    
    private fun levelUp() {
        level++
        experience = 0
        maxHealth += 20
        health = maxHealth
        attack += 2
        defense += 1
        maxMovementPoints += 1
        movementPoints = maxMovementPoints
    }
}

