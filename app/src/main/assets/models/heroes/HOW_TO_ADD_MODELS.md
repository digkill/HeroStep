# Как добавить модели героев

## Структура папок

Модели организованы по расам и профессиям в следующей структуре:
- `{race}/{profession}/` - где `{race}` это раса, а `{profession}` это профессия

Расы:
- `humans/` - Модели людей
- `orcs/` - Модели орков
- `elves/` - Модели эльфов
- `dwarves/` - Модели дворфов
- `chaos_legion/` - Модели легиона хаоса
- `undead/` - Модели нежити

Профессии (для каждой расы):
- `warrior/` - Воин
- `archer/` - Лучник
- `rogue/` - Разбойник
- `mage/` - Маг
- `priest/` - Жрец

## Формат файлов

### Поддерживаемые форматы моделей:
- `.obj` - Wavefront OBJ (рекомендуется для простых моделей)
- `.fbx` - Autodesk FBX
- `.gltf` / `.glb` - glTF (рекомендуется для современных моделей)

### Именование файлов:
```
hero_{race}_{profession}.{extension}
```

Где `{profession}` может быть:
- `warrior` - Воин
- `archer` - Лучник
- `rogue` - Разбойник
- `mage` - Маг
- `priest` - Жрец

Примеры:
- `hero_humans_warrior.obj`
- `hero_orcs_archer.fbx`
- `hero_elves_mage.gltf`
- `hero_dwarves_priest.obj`
- `hero_chaos_legion_rogue.gltf`

## Добавление новой модели

1. **Выберите папку расы и профессии** (например, `humans/warrior/`)

2. **Поместите файл модели** в соответствующую папку:
   ```
   app/src/main/assets/models/heroes/humans/warrior/hero_humans_warrior.obj
   ```

3. **Добавьте текстуру** (опционально) в соответствующую папку:
   ```
   app/src/main/assets/textures/heroes/humans/warrior/hero_humans_warrior.png
   ```

4. **Модель автоматически будет доступна** через `HeroModelManager`

## Использование в коде

```kotlin
// Получить путь к модели
val modelPath = HeroModelManager.getModelPath(Race.HUMANS, "hero_humans_warrior")

// Получить конфигурацию модели
val config = HeroModelConfig.createDefault(Race.HUMANS, "hero_humans_warrior")

// Загрузить модель
val loader = HeroModelLoader(context)
val modelStream = loader.loadModel(config)
```

## Требования к моделям

- **Размер**: Оптимизированы для мобильных устройств
- **Полигоны**: Рекомендуется до 5000 треугольников
- **Текстуры**: Максимальный размер 1024x1024
- **Ориентация**: Модель должна быть ориентирована правильно (лицом вперед)

## Примеры моделей

Для каждой расы доступны следующие варианты:

### Humans
- `hero_humans_warrior.obj` - Воин
- `hero_humans_mage.obj` - Маг
- `hero_humans_archer.obj` - Лучник

### Orcs
- `hero_orcs_berserker.obj` - Берсерк
- `hero_orcs_shaman.obj` - Шаман
- `hero_orcs_warrior.obj` - Воин

### Elves
- `hero_elves_archer.obj` - Лучник
- `hero_elves_ranger.obj` - Рейнджер
- `hero_elves_mage.obj` - Маг

### Dwarves
- `hero_dwarves_warrior.obj` - Воин
- `hero_dwarves_engineer.obj` - Инженер
- `hero_dwarves_guardian.obj` - Страж

### Chaos Legion
- `hero_chaos_demon.obj` - Демон
- `hero_chaos_warlock.obj` - Чернокнижник
- `hero_chaos_warrior.obj` - Воин

### Undead
- `hero_undead_lich.obj` - Лич
- `hero_undead_necromancer.obj` - Некромант
- `hero_undead_skeleton.obj` - Скелет

