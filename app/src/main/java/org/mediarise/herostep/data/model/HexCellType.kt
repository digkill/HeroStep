package org.mediarise.herostep.data.model

enum class HexCellType(val displayName: String, val movementCost: Int) {
    FOREST("Лес", 2),
    PLAINS("Равнина", 1),
    MOUNTAINS("Горы", 3),
    LAKE("Озеро", 4),
    WASTELAND("Пустошь", 1),
    CORRUPTED_LAND("Оскверненная земля", 2)
}

