package org.mediarise.herostep.data.model

enum class Race(val displayName: String, val description: String) {
    HUMANS(
        displayName = "Humans",
        description = "Balanced adventurers who rely on teamwork and steady growth."
    ),
    ORCS(
        displayName = "Orcs",
        description = "Relentless warriors that favor brute strength and aggression."
    ),
    ELVES(
        displayName = "Elves",
        description = "Swift and precise fighters with keen senses and archery skills."
    ),
    DWARVES(
        displayName = "Dwarves",
        description = "Sturdy defenders and craftsmen who excel at holding the line."
    ),
    CHAOS_LEGION(
        displayName = "Chaos Legion",
        description = "Unpredictable forces that thrive in turmoil and disruption."
    ),
    UNDEAD(
        displayName = "Undead",
        description = "Restless hordes animated by dark magic that never tire."
    )
}

