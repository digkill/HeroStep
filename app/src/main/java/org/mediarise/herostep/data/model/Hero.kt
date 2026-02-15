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
    var currentCell: HexCell? = null,
    var animationEvent: HeroAnimationEvent = HeroAnimationEvent.IDLE,
    var animationEventStartTimeMs: Long = 0L
) {
    fun isAlive(): Boolean = health > 0
    fun canMove(): Boolean = movementPoints > 0 && isAlive()

    // Идентичность героя в игре определяется id, а не всем графом объектов.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Hero) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    fun triggerAnimation(event: HeroAnimationEvent, timestampMs: Long = System.currentTimeMillis()) {
        android.util.Log.d("Hero", "[$name] Triggering animation: $event at $timestampMs")
        animationEvent = event
        animationEventStartTimeMs = timestampMs
    }

    fun clearAnimation() {
        animationEvent = HeroAnimationEvent.IDLE
        animationEventStartTimeMs = System.currentTimeMillis()
        android.util.Log.d("Hero", "[$name] Animation cleared, returning to IDLE")
    }
    
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

