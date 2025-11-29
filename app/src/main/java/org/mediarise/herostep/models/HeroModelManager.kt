package org.mediarise.herostep.models

import org.mediarise.herostep.data.model.Race

/**
 * Менеджер для управления моделями героев.
 * Организует загрузку и доступ к 3D моделям по расам.
 */
object HeroModelManager {
    
    /**
     * Базовый путь к моделям героев в assets
     */
    private const val MODELS_BASE_PATH = "models/heroes"
    
    /**
     * Базовый путь к текстурам героев в assets
     */
    private const val TEXTURES_BASE_PATH = "textures/heroes"
    
    /**
     * Маппинг рас к именам папок
     */
    private val raceToFolderMap = mapOf(
        Race.HUMANS to "humans",
        Race.ORCS to "orcs",
        Race.ELVES to "elves",
        Race.DWARVES to "dwarves",
        Race.CHAOS_LEGION to "chaos_legion",
        Race.UNDEAD to "undead"
    )
    
    /**
     * Получить путь к папке с моделями для расы
     */
    fun getModelsPathForRace(race: Race): String {
        val folder = raceToFolderMap[race] ?: "humans"
        return "$MODELS_BASE_PATH/$folder"
    }
    
    /**
     * Получить путь к папке с моделями для расы и профессии
     */
    fun getModelsPathForRaceAndProfession(race: Race, profession: org.mediarise.herostep.data.model.Profession): String {
        val folder = raceToFolderMap[race] ?: "humans"
        val professionName = profession.name.lowercase()
        return "$MODELS_BASE_PATH/$folder/$professionName"
    }
    
    /**
     * Получить путь к папке с текстурами для расы
     */
    fun getTexturesPathForRace(race: Race): String {
        val folder = raceToFolderMap[race] ?: "humans"
        return "$TEXTURES_BASE_PATH/$folder"
    }
    
    /**
     * Получить путь к папке с текстурами для расы и профессии
     */
    fun getTexturesPathForRaceAndProfession(race: Race, profession: org.mediarise.herostep.data.model.Profession): String {
        val folder = raceToFolderMap[race] ?: "humans"
        val professionName = profession.name.lowercase()
        return "$TEXTURES_BASE_PATH/$folder/$professionName"
    }
    
    /**
     * Получить полный путь к модели героя
     * @param race Раса героя
     * @param modelName Имя модели (без расширения)
     * @param extension Расширение файла (по умолчанию .obj)
     */
    fun getModelPath(race: Race, modelName: String, extension: String = "obj"): String {
        val folder = raceToFolderMap[race] ?: "humans"
        return "$MODELS_BASE_PATH/$folder/$modelName.$extension"
    }
    
    /**
     * Получить полный путь к модели героя по расе и профессии
     * @param race Раса героя
     * @param profession Профессия героя
     * @param extension Расширение файла (по умолчанию .obj)
     */
    fun getModelPath(race: Race, profession: org.mediarise.herostep.data.model.Profession, extension: String = "obj"): String {
        val folder = raceToFolderMap[race] ?: "humans"
        val professionName = profession.name.lowercase()
        val modelName = "hero_${folder}_$professionName"
        return "$MODELS_BASE_PATH/$folder/$professionName/$modelName.$extension"
    }
    
    /**
     * Получить полный путь к текстуре героя
     * @param race Раса героя
     * @param textureName Имя текстуры (без расширения)
     * @param extension Расширение файла (по умолчанию .png)
     */
    fun getTexturePath(race: Race, textureName: String, extension: String = "png"): String {
        val folder = raceToFolderMap[race] ?: "humans"
        return "$TEXTURES_BASE_PATH/$folder/$textureName.$extension"
    }
    
    /**
     * Получить полный путь к текстуре героя по расе и профессии
     * @param race Раса героя
     * @param profession Профессия героя
     * @param textureName Имя текстуры (без расширения, опционально)
     * @param extension Расширение файла (по умолчанию .png)
     */
    fun getTexturePath(race: Race, profession: org.mediarise.herostep.data.model.Profession, textureName: String? = null, extension: String = "png"): String {
        val folder = raceToFolderMap[race] ?: "humans"
        val professionName = profession.name.lowercase()
        val texture = textureName ?: "hero_${folder}_$professionName"
        return "$TEXTURES_BASE_PATH/$folder/$professionName/$texture.$extension"
    }
    
    /**
     * Получить список доступных моделей для расы по профессиям
     * @param race Раса героя
     * @return Список имен моделей (без расширений) для всех профессий
     */
    fun getAvailableModels(race: Race): List<String> {
        val folder = raceToFolderMap[race] ?: "humans"
        val professions = org.mediarise.herostep.data.model.Profession.values()
        
        // Генерируем имена моделей для всех профессий
        return professions.map { profession ->
            val professionName = profession.name.lowercase()
            "hero_${folder}_$professionName"
        }
    }
    
    /**
     * Получить имя модели для расы и профессии
     */
    fun getModelName(race: Race, profession: org.mediarise.herostep.data.model.Profession): String {
        val folder = raceToFolderMap[race] ?: "humans"
        val professionName = profession.name.lowercase()
        return "hero_${folder}_$professionName"
    }
    
    /**
     * Получить дефолтную модель для расы (воин)
     */
    fun getDefaultModel(race: Race): String {
        val folder = raceToFolderMap[race] ?: "humans"
        return "hero_${folder}_warrior"
    }
}

