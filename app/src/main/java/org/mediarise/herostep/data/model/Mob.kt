package org.mediarise.herostep.data.model

data class Mob(
    val id: String,
    val name: String,
    var health: Int,
    val maxHealth: Int,
    val attack: Int,
    val defense: Int,
    val experienceReward: Int,
    val goldReward: Int,
    val lootTable: List<Item> = emptyList()
) {
    fun isAlive(): Boolean = health > 0
}

