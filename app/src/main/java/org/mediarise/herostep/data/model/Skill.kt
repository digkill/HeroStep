package org.mediarise.herostep.data.model

data class Skill(
    val id: String,
    val name: String,
    val description: String,
    val cooldown: Int,
    val manaCost: Int,
    val effect: SkillEffect
)

enum class SkillEffect {
    DAMAGE,
    HEAL,
    BUFF_ATTACK,
    BUFF_DEFENSE,
    DEBUFF_ENEMY
}

