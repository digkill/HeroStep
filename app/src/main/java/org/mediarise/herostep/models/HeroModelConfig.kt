package org.mediarise.herostep.models

import org.mediarise.herostep.data.model.Profession
import org.mediarise.herostep.data.model.Race

/**
 * Конфигурация модели героя.
 * Содержит информацию о пути к модели, текстурам и параметрах отрисовки.
 */
data class HeroModelConfig(
    val race: Race,
    val profession: Profession? = null,
    val modelName: String,
    val modelPath: String,
    val texturePath: String? = null,
    val scale: Float = 1.00f,
    val rotationY: Float = 0.0f,
    val offsetY: Float = 0.0f
) {
    companion object {
        /**
         * Создать конфигурацию для героя с дефолтными настройками
         * @param race Раса героя
         * @param profession Профессия героя (опционально)
         * @param modelName Имя модели (опционально, если не указано - используется профессия)
         */
        fun createDefault(race: Race, profession: Profession? = null, modelName: String? = null): HeroModelConfig {
            val name: String
            val path: String
            
            val texturePath: String?
            
            if (profession != null && modelName == null) {
                // Используем профессию для генерации имени модели
                val professionName = profession.name.lowercase()
                val folder = HeroModelManager.getModelsPathForRace(race).substringAfterLast("/")
                name = "hero_${folder}_$professionName"
                path = HeroModelManager.getModelPath(race, profession)
                texturePath = HeroModelManager.getTexturePath(race, profession, name)
            } else {
                // Используем указанное имя или дефолтное
                name = modelName ?: HeroModelManager.getDefaultModel(race)
                path = HeroModelManager.getModelPath(race, name)
                texturePath = HeroModelManager.getTexturePath(race, name)
            }
            
            return HeroModelConfig(
                race = race,
                profession = profession,
                modelName = name,
                modelPath = path,
                texturePath = texturePath,
                scale = 1.0f,
                rotationY = 0.0f,
                offsetY = 0.0f
            )
        }
    }
}

