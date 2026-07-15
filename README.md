# LiteminerWGAddon

[English](README_EN.md) | Русский

LiteminerWGAddon is an integration addon that connects LiteMiner VeinMine with WorldGuard region protection.

The project prevents LiteMiner from bypassing WorldGuard protected regions by checking permissions before VeinMine operations.

## ⚠️ Важно

**Этот проект требует ОБА компонента:**

1. **NeoForge Mod:**  
   `LiteminerWGAddon-x.x.x.jar`

2. **Bukkit Plugin:**  
   `LiteminerWGPlugin-x.x.x.jar`

**Оба файла обязательны.**  
Установка только одного компонента не будет работать.

## Возможности

✅ **Интеграция между LiteMiner и WorldGuard** — связывает функцию VeinMine с системой защиты регионов  
✅ **Блокировка VeinMine в защищённых регионах** — предотвращает разрушение блоков внутри WorldGuard зон  
✅ **Соблюдение разрешений WorldGuard** — проверяет флаги и границы регионов  
✅ **Использование Architectury API** — чистая интеграция событий  
✅ **Мост совместимости** — связь между NeoForge модом и Bukkit плагинами  
✅ **Для модифицированных серверов** — разработано для NeoForge серверов  

## Как это работает

NeoForge мод подключается к событиям LiteMiner, используя Architectury API.

Перед началом VeinMine аддон проверяет разрешения WorldGuard через мост плагина.

Если WorldGuard запрещает разрушение блоков:
- Операция VeinMine отменяется
- Защищённые блоки остаются целыми

## Требования

| Компонент | Версия |
|-----------|--------|
| **Minecraft** | 1.21.1 |
| **Загрузчик** | NeoForge 21.1.228 |

**Требуемые моды:**
- LiteMiner 1.0.3+1.21.1
- LiteminerWGAddon NeoForge Mod

**Требуемые плагины:**
- WorldEdit Youer
- WorldGuard
- LiteminerWGPlugin

**Требуемое ПО сервера:**

Окружение NeoForge сервера с поддержкой Bukkit плагинов.

⚠️ **Важно:** Этот проект был разработан и протестирован с **WorldEdit-Youer**. Другие реализации WorldEdit могут не работать.

## Установка

### Шаг 1 — Установите NeoForge мод

Загрузите:
```
LiteminerWGAddon-x.x.x.jar
```

Поместите в:
```
server/mods/
```

---

### Шаг 2 — Установите Bukkit плагин

Загрузите:
```
LiteminerWGPlugin-x.x.x.jar
```

Поместите в:
```
server/plugins/
```

---

### Шаг 3 — Установите зависимости

Установите:
- WorldEdit Youer
- WorldGuard
- LiteMiner

Перезапустите сервер.

## Сборка из исходного кода

**Требования:**
- Java 21
- Gradle

**Команда:**

```bash
./gradlew build
```

## Совместимость

**Протестировано:**

| Компонент | Версия |
|-----------|--------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| LiteMiner | 1.0.3+1.21.1 |
| WorldEdit | WorldEdit-Youer |
| WorldGuard | Совместимая версия для протестированного окружения |

**Не гарантируется:**
- Paper
- Spigot
- Bukkit без поддержки NeoForge
- Другие реализации WorldEdit

## Лицензия

MIT License


