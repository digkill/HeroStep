package org.mediarise.herostep.utils

import org.mediarise.herostep.data.model.Profession
import org.mediarise.herostep.data.model.Race

object DebugConfig {
    const val IsDebug = true
    
    // Параметры для режима отладки
    const val DEBUG_HERO_NAME = "Demo"
    val DEBUG_RACE = Race.HUMANS
    val DEBUG_PROFESSION = Profession.WARRIOR // По умолчанию воин в режиме отладки
}

