# Система моделей героев

Эта система обеспечивает легкое добавление и управление 3D моделями героев.

## Структура

### HeroModelManager
Центральный менеджер для получения путей к моделям и текстурам.
- `getModelsPathForRace(race)` - получить путь к папке с моделями расы
- `getModelPath(race, modelName)` - получить полный путь к модели
- `getAvailableModels(race)` - получить список доступных моделей
- `getDefaultModel(race)` - получить дефолтную модель для расы

### HeroModelConfig
Конфигурация модели героя с параметрами отрисовки.
- `modelPath` - путь к файлу модели
- `texturePath` - путь к текстуре (опционально)
- `scale` - масштаб модели
- `rotationY` - поворот по оси Y
- `offsetY` - смещение по оси Y

### HeroModelLoader
Загрузчик моделей из assets.
- `loadModel(config)` - загрузить модель
- `loadTexture(config)` - загрузить текстуру
- `modelExists(config)` - проверить существование модели

## Использование

```kotlin
// 1. Получить конфигурацию модели
val config = HeroModelConfig.createDefault(Race.HUMANS, "hero_humans_warrior")

// 2. Загрузить модель
val loader = HeroModelLoader(context)
val modelStream = loader.loadModel(config)

// 3. Использовать в рендерере
// (будет реализовано позже)
```

## Добавление новых моделей

1. Поместите файл модели в `app/src/main/assets/models/heroes/{race}/`
2. При необходимости добавьте текстуру в `app/src/main/assets/textures/heroes/{race}/`
3. Модель автоматически будет доступна через HeroModelManager

## Расширение

Для добавления поддержки новых форматов моделей:
1. Расширьте `HeroModelLoader` методами парсинга
2. Добавьте поддержку в рендерер
3. Обновите список поддерживаемых форматов в документации

