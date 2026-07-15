# LiteminerWGAddon

[Русский](README.md) | English

LiteminerWGAddon is an integration addon that connects LiteMiner VeinMine with WorldGuard region protection.

The project prevents LiteMiner from bypassing WorldGuard protected regions by checking permissions before VeinMine operations.

## ⚠️ Important

**This project requires BOTH components:**

1. **NeoForge Mod:**  
   `LiteminerWGAddon-x.x.x.jar`

2. **Bukkit Plugin:**  
   `LiteminerWGPlugin-x.x.x.jar`

**Both files are required.**  
Installing only one component will not work.

## Features

✅ **Integration between LiteMiner and WorldGuard** — Connects VeinMine functionality with region protection system  
✅ **Prevents VeinMine in protected regions** — Blocks block breaking inside WorldGuard zones  
✅ **Respects WorldGuard permissions** — Checks flags and region boundaries  
✅ **Uses Architectury API** — Clean event integration  
✅ **Compatibility bridge** — Links NeoForge mod with Bukkit plugins  
✅ **Designed for modded servers** — Developed for NeoForge server environments  

## How It Works

The NeoForge mod hooks into LiteMiner events using Architectury API.

Before VeinMine starts, the addon checks WorldGuard permissions through the plugin bridge.

If WorldGuard denies block breaking:
- VeinMine operation is cancelled
- Protected blocks remain safe

## Requirements

| Component | Version |
|-----------|----------|
| **Minecraft** | 1.21.1 |
| **Loader** | NeoForge 21.1.228 |

**Required mods:**
- LiteMiner 1.0.3+1.21.1
- LiteminerWGAddon NeoForge Mod

**Required plugins:**
- WorldEdit Youer
- WorldGuard
- LiteminerWGPlugin

**Required server software:**

A NeoForge server environment with Bukkit plugin support.

⚠️ **Important:** This project was developed and tested with **WorldEdit-Youer**. Other WorldEdit implementations may not work.

## Installation

### Step 1 — Install NeoForge mod

Download:
```
LiteminerWGAddon-x.x.x.jar
```

Place it into:
```
server/mods/
```

---

### Step 2 — Install Bukkit plugin

Download:
```
LiteminerWGPlugin-x.x.x.jar
```

Place it into:
```
server/plugins/
```

---

### Step 3 — Install dependencies

Install:
- WorldEdit Youer
- WorldGuard
- LiteMiner

Restart the server.

## Building from source

**Requirements:**
- Java 21
- Gradle

**Command:**

```bash
./gradlew build
```

## Compatibility

**Tested:**

| Component | Version |
|-----------|----------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.228 |
| LiteMiner | 1.0.3+1.21.1 |
| WorldEdit | WorldEdit-Youer |
| WorldGuard | Compatible version for the tested server environment |

**Not guaranteed:**
- Paper
- Spigot
- Bukkit without NeoForge support
- Other WorldEdit implementations

## License

MIT License
