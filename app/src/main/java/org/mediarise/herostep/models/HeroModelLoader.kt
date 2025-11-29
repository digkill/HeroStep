package org.mediarise.herostep.models

import android.content.Context
import android.util.Log
import org.mediarise.herostep.data.model.Hero
import org.mediarise.herostep.data.model.Race
import java.io.InputStream

/**
 * Загрузчик моделей героев из assets.
 * Пока заглушка для будущей реализации загрузки 3D моделей.
 */
class HeroModelLoader(private val context: Context) {
    
    /**
     * Загрузить модель героя из assets
     * @param config Конфигурация модели
     * @return InputStream для чтения файла модели или null, если файл не найден
     */
    fun loadModel(config: HeroModelConfig): InputStream? {
        // Сначала пробуем точный путь
        return try {
            context.assets.open(config.modelPath)
        } catch (e: Exception) {
            // Если не найден, ищем любой файл модели в папке профессии
            val modelPath = findModelPath(config)
            if (modelPath != null) {
                try {
                    context.assets.open(modelPath)
                } catch (e2: Exception) {
                    Log.w("HeroModelLoader", "Model not found in folder, using default cube")
                    null
                }
            } else {
                Log.w("HeroModelLoader", "Model not found: ${config.modelPath}, using default cube")
                null
            }
        }
    }
    
    /**
     * Загрузить текстуру героя из assets
     * @param config Конфигурация модели
     * @return InputStream для чтения файла текстуры или null, если файл не найден
     */
    fun loadTexture(config: HeroModelConfig): InputStream? {
        val texturePath = config.texturePath ?: return null
        return try {
            context.assets.open(texturePath)
        } catch (e: Exception) {
            Log.w("HeroModelLoader", "Texture not found: $texturePath")
            null
        }
    }
    
    /**
     * Проверить, существует ли модель
     * Ищет любой файл модели (.glb, .gltf, .obj) в папке профессии
     */
    fun modelExists(config: HeroModelConfig): Boolean {
        // Сначала проверяем точный путь
        return try {
            context.assets.open(config.modelPath).use { true }
        } catch (e: Exception) {
            // Если точный путь не найден, ищем любой файл модели в папке профессии
            val profession = config.profession ?: return false
            val race = config.race
            val folder = HeroModelManager.getModelsPathForRaceAndProfession(race, profession)
            
            // Пробуем найти любой файл модели в папке
            val extensions = listOf("glb", "gltf", "obj", "fbx")
            for (ext in extensions) {
                try {
                    // Ищем файлы с разными именами
                    val possibleNames = listOf(
                        "hero_${HeroModelManager.getModelsPathForRace(race).substringAfterLast("/")}_${profession.name.lowercase()}.$ext",
                        "hero.$ext",
                        "model.$ext"
                    )
                    
                    // Также пробуем найти любой файл в папке
                    val files = context.assets.list(folder)
                    if (files != null) {
                        for (file in files) {
                            if (file.endsWith(".$ext", ignoreCase = true)) {
                                return true
                            }
                        }
                    }
                } catch (e2: Exception) {
                    // Продолжаем поиск
                }
            }
            false
        }
    }
    
    /**
     * Найти путь к модели в папке профессии
     */
    fun findModelPath(config: HeroModelConfig): String? {
        // Сначала проверяем точный путь
        return try {
            context.assets.open(config.modelPath).use { config.modelPath }
        } catch (e: Exception) {
            // Ищем любой файл модели в папке профессии
            val profession = config.profession ?: return null
            val race = config.race
            val folder = HeroModelManager.getModelsPathForRaceAndProfession(race, profession)
            
            val extensions = listOf("glb", "gltf", "obj", "fbx")
            for (ext in extensions) {
                try {
                    val files = context.assets.list(folder)
                    if (files != null) {
                        for (file in files) {
                            if (file.endsWith(".$ext", ignoreCase = true)) {
                                return "$folder/$file"
                            }
                        }
                    }
                } catch (e2: Exception) {
                    // Продолжаем поиск
                }
            }
            null
        }
    }
    
    /**
     * Получить конфигурацию модели для героя
     */
    fun getModelConfigForHero(hero: Hero, modelName: String? = null): HeroModelConfig {
        return HeroModelConfig.createDefault(hero.race, hero.profession, modelName)
    }
    
    /**
     * Получить список доступных моделей для расы
     */
    fun getAvailableModelsForRace(race: Race): List<String> {
        return HeroModelManager.getAvailableModels(race)
    }
}

