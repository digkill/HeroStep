package org.mediarise.herostep.data.model

data class Item(
    val id: String,
    val name: String,
    val description: String,
    val type: ItemType,
    val attackBonus: Int = 0,
    val defenseBonus: Int = 0,
    val healthBonus: Int = 0,
    val value: Int = 0 // Стоимость в золоте
)

enum class ItemType {
    WEAPON,
    ARMOR,
    ACCESSORY,
    CONSUMABLE
}

