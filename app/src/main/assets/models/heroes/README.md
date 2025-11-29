# Модели героев

Эта директория содержит 3D модели героев, организованные по расам и профессиям.

## Структура:

```
models/heroes/
├── humans/
│   ├── warrior/          # Модели людей-воинов
│   ├── archer/           # Модели людей-лучников
│   ├── rogue/            # Модели людей-разбойников
│   ├── mage/             # Модели людей-магов
│   └── priest/           # Модели людей-жрецов
├── orcs/
│   ├── warrior/
│   ├── archer/
│   ├── rogue/
│   ├── mage/
│   └── priest/
├── elves/
│   ├── warrior/
│   ├── archer/
│   ├── rogue/
│   ├── mage/
│   └── priest/
├── dwarves/
│   ├── warrior/
│   ├── archer/
│   ├── rogue/
│   ├── mage/
│   └── priest/
├── chaos_legion/
│   ├── warrior/
│   ├── archer/
│   ├── rogue/
│   ├── mage/
│   └── priest/
└── undead/
    ├── warrior/
    ├── archer/
    ├── rogue/
    ├── mage/
    └── priest/
```

## Формат файлов:

- Поддерживаемые форматы: `.obj`, `.fbx`, `.gltf`, `.glb`
- Текстуры должны быть в `textures/heroes/{race}/{profession}/`
- Имена файлов: `hero_{race}_{profession}.{ext}`

## Примеры:

- `humans/warrior/hero_humans_warrior.obj` - Человек-воин
- `orcs/archer/hero_orcs_archer.fbx` - Орк-лучник
- `elves/mage/hero_elves_mage.gltf` - Эльф-маг
- `dwarves/priest/hero_dwarves_priest.obj` - Дворф-жрец
- `chaos_legion/rogue/hero_chaos_legion_rogue.gltf` - Воин легиона хаоса-разбойник
- `undead/warrior/hero_undead_warrior.obj` - Нежить-воин

## Профессии:

Каждая раса может иметь следующие профессии:
- **warrior** - Воин
- **archer** - Лучник
- **rogue** - Разбойник
- **mage** - Маг
- **priest** - Жрец
