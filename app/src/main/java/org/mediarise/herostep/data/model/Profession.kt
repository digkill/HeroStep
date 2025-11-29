package org.mediarise.herostep.data.model

/**
 * Профессии героев
 */
enum class Profession(
    val displayName: String,
    val description: String,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseHealth: Int,
    val baseMovement: Int
) {
    WARRIOR(
        displayName = "Воин",
        description = "Мастер ближнего боя с высокой защитой",
        baseAttack = 12,
        baseDefense = 8,
        baseHealth = 120,
        baseMovement = 2
    ),
    ARCHER(
        displayName = "Лучник",
        description = "Дальнобойный боец с высокой точностью",
        baseAttack = 10,
        baseDefense = 5,
        baseHealth = 90,
        baseMovement = 3
    ),
    ROGUE(
        displayName = "Разбойник",
        description = "Быстрый и ловкий боец с высоким уроном",
        baseAttack = 14,
        baseDefense = 4,
        baseHealth = 85,
        baseMovement = 4
    ),
    MAGE(
        displayName = "Маг",
        description = "Мастер магии с мощными заклинаниями",
        baseAttack = 15,
        baseDefense = 3,
        baseHealth = 75,
        baseMovement = 2
    ),
    PRIEST(
        displayName = "Жрец",
        description = "Поддерживающий боец с лечением",
        baseAttack = 8,
        baseDefense = 6,
        baseHealth = 100,
        baseMovement = 2
    )
}

