package org.example.wryndel.liteminerwgaddon.compat;

// Reflection-only bridge; avoid strong compile-time deps
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class BukkitBridge {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LiteminerWG:BukkitBridge");

    // Called reflectively from the mod constructor registration code
    public static void handleEvent(Object event) {
        try {
            if (event == null) return;

            // Only handle events that look like a BlockEvent.BreakEvent by name
            String eventClassName = event.getClass().getName();
            if (!eventClassName.contains("BlockEvent") || !eventClassName.endsWith("BreakEvent")) return;

            // Extract player reflectively (method getPlayer() or field player)
            Object playerObj = null;
            try {
                Method m = event.getClass().getMethod("getPlayer");
                playerObj = m.invoke(event);
            } catch (NoSuchMethodException ignored) {
                try {
                    java.lang.reflect.Field f = event.getClass().getField("player");
                    playerObj = f.get(event);
                } catch (NoSuchFieldException ignored2) {
                }
            }

            // Determine if we have a player and check its type (real player vs fake)
            boolean hasPlayer = playerObj != null;
            String playerClass = hasPlayer ? playerObj.getClass().getName() : "";

            // Treat FakePlayer or Liteminer indicators specially
            boolean isFakePlayer = false;
            if (hasPlayer) {
                String lower = playerClass.toLowerCase();
                if (lower.contains("fakeplayer") || lower.contains("fake") || lower.contains("liteminer")) isFakePlayer = true;
            }
            // Also consider null-player breaks (no player) as potential Liteminer actions
            boolean fakeOrNoPlayer = isFakePlayer || !hasPlayer;

            // Extract level/world from player (method getLevel() or field level)
            Object level = null;
            try {
                Method gm = playerObj.getClass().getMethod("getLevel");
                level = gm.invoke(playerObj);
            } catch (NoSuchMethodException ignored) {
                try {
                    java.lang.reflect.Field lf = playerObj.getClass().getField("level");
                    level = lf.get(playerObj);
                } catch (NoSuchFieldException ignored2) {
                }
            }
            if (level == null) {
                // try to extract level from event if no player-level available
                try {
                    Method getWorld = event.getClass().getMethod("getWorld");
                    level = getWorld.invoke(event);
                } catch (NoSuchMethodException ignored) {
                    try {
                        Method getLevel = event.getClass().getMethod("getLevel");
                        level = getLevel.invoke(event);
                    } catch (NoSuchMethodException ignored2) {
                    }
                }
            }
            if (level == null) return;

            // Extract position (method getPos() or getPos field)
            Object posObj = null;
            try {
                Method pm = event.getClass().getMethod("getPos");
                posObj = pm.invoke(event);
            } catch (NoSuchMethodException ignored) {
                try {
                    java.lang.reflect.Field pf = event.getClass().getField("pos");
                    posObj = pf.get(event);
                } catch (NoSuchFieldException ignored2) {
                }
            }
            if (posObj == null) return;

            // Now try to get coordinates from posObj (getX/getY/getZ or getX() style)
            Method getX = null, getY = null, getZ = null;
            try {
                getX = posObj.getClass().getMethod("getX");
                getY = posObj.getClass().getMethod("getY");
                getZ = posObj.getClass().getMethod("getZ");
            } catch (NoSuchMethodException ignored) {
            }
            if (getX == null) return;
            int x = ((Number) getX.invoke(posObj)).intValue();
            int y = ((Number) getY.invoke(posObj)).intValue();
            int z = ((Number) getZ.invoke(posObj)).intValue();

            // If this is a fake player (Liteminer) or there is no player, try to detect WorldGuard region at the location and cancel without relying on a Bukkit Player
            if (fakeOrNoPlayer) {
                try {
                    // attempt to find a Bukkit World corresponding to the Minecraft level
                    Class<?> bukkitBukkitClass = forNameOrNull("org.bukkit.Bukkit");
                    Object bukkitWorld = null;

                    // try to determine dimension path from level.dimension().location().getPath()
                    String dimPath = null;
                    try {
                        Method dim = level.getClass().getMethod("dimension");
                        Object dimKey = dim.invoke(level);
                        if (dimKey != null) {
                            Method loc = dimKey.getClass().getMethod("location");
                            Object resLoc = loc.invoke(dimKey);
                            if (resLoc != null) {
                                Method getPath = resLoc.getClass().getMethod("getPath");
                                dimPath = (String) getPath.invoke(resLoc);
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                    }

                    if (bukkitBukkitClass != null) {
                        Method getWorlds = bukkitBukkitClass.getMethod("getWorlds");
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
                        if (worlds != null) {
                            for (Object w : worlds) {
                                try {
                                    Method getName = w.getClass().getMethod("getName");
                                    String name = (String) getName.invoke(w);
                                    if (dimPath != null && name != null && (name.equalsIgnoreCase(dimPath) || name.toLowerCase().contains(dimPath))) {
                                        bukkitWorld = w;
                                        break;
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                            if (bukkitWorld == null && !worlds.isEmpty()) bukkitWorld = worlds.get(0);
                        }
                    }

                    if (bukkitWorld != null) {
                        // Try to call WorldGuard API reflectively to get applicable regions
                        Class<?> wgClass = forNameOrNull("com.sk89q.worldguard.WorldGuard");
                        Class<?> bukkitAdapter = forNameOrNull("com.sk89q.worldedit.bukkit.BukkitAdapter");
                        if (wgClass != null && bukkitAdapter != null) {
                            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
                            Object platform = wgInstance.getClass().getMethod("getPlatform").invoke(wgInstance);
                            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);

                            Object weWorld = null;
                            try {
                                Method adaptWorld = bukkitAdapter.getMethod("adapt", Class.forName("org.bukkit.World"));
                                weWorld = adaptWorld.invoke(null, bukkitWorld);
                            } catch (Throwable ignored) {
                            }

                            if (weWorld != null) {
                                Object regionManager = container.getClass().getMethod("get", weWorld.getClass()).invoke(container, weWorld);
                                if (regionManager != null) {
                                    Class<?> blockVector3 = forNameOrNull("com.sk89q.worldedit.math.BlockVector3");
                                    if (blockVector3 != null) {
                                        Method at = blockVector3.getMethod("at", int.class, int.class, int.class);
                                        Object vec = at.invoke(null, x, y, z);
                                        Method getApplicable = regionManager.getClass().getMethod("getApplicableRegions", vec.getClass());
                                        Object appSet = getApplicable.invoke(regionManager, vec);
                                        if (appSet != null) {
                                            Method getRegions = appSet.getClass().getMethod("getRegions");
                                            Object regions = getRegions.invoke(appSet);
                                            if (regions instanceof java.util.Collection && !((java.util.Collection<?>) regions).isEmpty()) {
                                                try {
                                                    Method setCanceled = event.getClass().getMethod("setCanceled", boolean.class);
                                                    setCanceled.invoke(event, true);
                                                } catch (NoSuchMethodException ignored) {
                                                }
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.info("BukkitBridge: WG check failed", t);
                }
                // if WG check didn't cancel, continue to try normal Bukkit event flow below
            }

            // Try to call Bukkit's BlockBreakEvent via reflection to let WorldGuard handle it
            Class<?> craftServerClass = forNameOrNull("org.bukkit.craftbukkit.CraftServer");
            Class<?> bukkitServerClass = forNameOrNull("org.bukkit.Server");
            Class<?> craftWorldClass = forNameOrNull("org.bukkit.craftbukkit.CraftWorld");
            Class<?> craftPlayerClass = forNameOrNull("org.bukkit.craftbukkit.entity.CraftPlayer");
            Class<?> bukkitEntityPlayerClass = forNameOrNull("org.bukkit.entity.Player");

            Class<?> bukkitBlockClass = forNameOrNull("org.bukkit.block.Block");
            Class<?> blockBreakEventClass = forNameOrNull("org.bukkit.event.block.BlockBreakEvent");
            Class<?> bukkitEventClass = forNameOrNull("org.bukkit.event.Event");
            Class<?> bukkitEventPriorityClass = forNameOrNull("org.bukkit.event.EventPriority");
            Class<?> bukkitBukkitClass = forNameOrNull("org.bukkit.Bukkit");

            if (blockBreakEventClass == null || bukkitBukkitClass == null) {
                // No Bukkit present
                return;
            }

            // Convert player to CraftPlayer via getBukkitEntity()
            Method getBukkitEntity = playerObj.getClass().getMethod("getBukkitEntity");
            Object craftPlayer = getBukkitEntity.invoke(playerObj);

            // Get CraftWorld from craftPlayer#getWorld()
            Object craftWorld = null;
            try {
                Method getWorldFromPlayer = craftPlayer.getClass().getMethod("getWorld");
                craftWorld = getWorldFromPlayer.invoke(craftPlayer);
            } catch (NoSuchMethodException ignored) {
            }

            if (craftWorld == null) return;

            // Create a Bukkit Block object via CraftWorld#getBlockAt(x,y,z)
            Object bukkitBlock = null;
            Method getBlockAt = craftWorld.getClass().getMethod("getBlockAt", int.class, int.class, int.class);
            bukkitBlock = getBlockAt.invoke(craftWorld, x, y, z);

            if (bukkitBlock == null) return;

            // Construct new BlockBreakEvent(player, block)
            Constructor<?> ctor = blockBreakEventClass.getConstructor(bukkitEntityPlayerClass, bukkitBlockClass);
            Object bukkitEvent = ctor.newInstance(craftPlayer, bukkitBlock);

            // Call Bukkit.getServer().getPluginManager().callEvent(event)
            Method getPluginManager = bukkitBukkitClass.getMethod("getPluginManager");
            Object pluginManager = getPluginManager.invoke(null);
            Method callEvent = pluginManager.getClass().getMethod("callEvent", bukkitEventClass);
            callEvent.invoke(pluginManager, bukkitEvent);

            // Check if event is cancelled
            Method isCancelled = blockBreakEventClass.getMethod("isCancelled");
            Boolean cancelled = (Boolean) isCancelled.invoke(bukkitEvent);
            if (Boolean.TRUE.equals(cancelled)) {
                try {
                    Method setCanceled = event.getClass().getMethod("setCanceled", boolean.class);
                    setCanceled.invoke(event, true);
                } catch (NoSuchMethodException ignored) {
                    // can't cancel
                }
            }

        } catch (Throwable t) {
            LOGGER.info("BukkitBridge: failed to call Bukkit BlockBreakEvent", t);
        }
    }

    // Helper used by mixin to check WorldGuard regions for a block position.
    public static boolean isInWorldGuardRegion(Object level, int x, int y, int z) {
        try {
            Class<?> bukkitBukkitClass = forNameOrNull("org.bukkit.Bukkit");
            Object bukkitWorld = null;

            // try to determine dimension path from level.dimension().location().getPath()
            String dimPath = null;
            try {
                Method dim = level.getClass().getMethod("dimension");
                Object dimKey = dim.invoke(level);
                if (dimKey != null) {
                    Method loc = dimKey.getClass().getMethod("location");
                    Object resLoc = loc.invoke(dimKey);
                    if (resLoc != null) {
                        Method getPath = resLoc.getClass().getMethod("getPath");
                        dimPath = (String) getPath.invoke(resLoc);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            LOGGER.debug("BukkitBridge: checking WG for pos {} {} {} in dimPath={}", x, y, z, dimPath);

            if (bukkitBukkitClass != null) {
                Method getWorlds = bukkitBukkitClass.getMethod("getWorlds");
                @SuppressWarnings("unchecked")
                java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
                if (worlds != null) {
                    for (Object w : worlds) {
                        try {
                            Method getName = w.getClass().getMethod("getName");
                            String name = (String) getName.invoke(w);
                            if (dimPath != null && name != null && (name.equalsIgnoreCase(dimPath) || name.toLowerCase().contains(dimPath))) {
                                bukkitWorld = w;
                                break;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    if (bukkitWorld == null && !worlds.isEmpty()) bukkitWorld = worlds.get(0);
                }
            }

            if (bukkitWorld != null) {
                Class<?> wgClass = forNameOrNull("com.sk89q.worldguard.WorldGuard");
                Class<?> bukkitAdapter = forNameOrNull("com.sk89q.worldedit.bukkit.BukkitAdapter");
                if (wgClass != null && bukkitAdapter != null) {
                    Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
                    Object platform = wgInstance.getClass().getMethod("getPlatform").invoke(wgInstance);
                    Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);

                    Object weWorld = null;
                    try {
                        Method adaptWorld = bukkitAdapter.getMethod("adapt", Class.forName("org.bukkit.World"));
                        weWorld = adaptWorld.invoke(null, bukkitWorld);
                    } catch (Throwable ignored) {
                    }

                    if (weWorld != null) {
                        Object regionManager = container.getClass().getMethod("get", weWorld.getClass()).invoke(container, weWorld);
                        if (regionManager != null) {
                            Class<?> blockVector3 = forNameOrNull("com.sk89q.worldedit.math.BlockVector3");
                            if (blockVector3 != null) {
                                Method at = blockVector3.getMethod("at", int.class, int.class, int.class);
                                Object vec = at.invoke(null, x, y, z);
                                Method getApplicable = regionManager.getClass().getMethod("getApplicableRegions", vec.getClass());
                                Object appSet = getApplicable.invoke(regionManager, vec);
                                if (appSet != null) {
                                    Method getRegions = appSet.getClass().getMethod("getRegions");
                                    Object regions = getRegions.invoke(appSet);
                                    if (regions instanceof java.util.Collection && !((java.util.Collection<?>) regions).isEmpty()) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.info("BukkitBridge: WG check failed", t);
        }
        return false;
    }

    // Check if a specific player can break a specific block (respects WorldGuard permissions)
    public static boolean canPlayerBreakBlock(Object player, Object level, int x, int y, int z) {
        try {
            if (player == null || level == null) {
                return true;  // No player = allow (shouldn't happen but be safe)
            }

            Class<?> bukkitBukkitClass = forNameOrNull("org.bukkit.Bukkit");
            Class<?> bukkitEventClass = forNameOrNull("org.bukkit.event.Event");
            Class<?> blockBreakEventClass = forNameOrNull("org.bukkit.event.block.BlockBreakEvent");
            Class<?> bukkitEntityPlayerClass = forNameOrNull("org.bukkit.entity.Player");
            Class<?> bukkitBlockClass = forNameOrNull("org.bukkit.block.Block");

            if (blockBreakEventClass == null || bukkitBukkitClass == null) {
                return true;  // No Bukkit, allow break
            }

            // Convert Minecraft player to Bukkit player
            Method getBukkitEntity = player.getClass().getMethod("getBukkitEntity");
            Object craftPlayer = getBukkitEntity.invoke(player);

            if (craftPlayer == null) {
                return true;  // Can't get Bukkit player, allow
            }

            // Get the world for this player
            Object craftWorld = null;
            try {
                Method getWorldFromPlayer = craftPlayer.getClass().getMethod("getWorld");
                craftWorld = getWorldFromPlayer.invoke(craftPlayer);
            } catch (NoSuchMethodException ignored) {
                return true;  // Can't get world, allow
            }

            if (craftWorld == null) {
                return true;  // No world found, allow
            }

            // Get BlockBreakEvent via Bukkit.getServer().getPluginManager().callEvent()
            Object bukkitBlock = null;
            try {
                Method getBlockAt = craftWorld.getClass().getMethod("getBlockAt", int.class, int.class, int.class);
                bukkitBlock = getBlockAt.invoke(craftWorld, x, y, z);
            } catch (Throwable ignored) {
                return true;  // Can't get block, allow
            }

            if (bukkitBlock == null) {
                return true;  // Block not found, allow
            }

            // Construct new BlockBreakEvent(player, block)
            Constructor<?> ctor = blockBreakEventClass.getConstructor(bukkitEntityPlayerClass, bukkitBlockClass);
            Object bukkitEvent = ctor.newInstance(craftPlayer, bukkitBlock);

            // Let plugins (WorldGuard, etc) evaluate the event
            Method getPluginManager = bukkitBukkitClass.getMethod("getPluginManager");
            Object pluginManager = getPluginManager.invoke(null);
            Method callEvent = pluginManager.getClass().getMethod("callEvent", bukkitEventClass);
            callEvent.invoke(pluginManager, bukkitEvent);

            // Check if event was cancelled
            Method isCancelled = blockBreakEventClass.getMethod("isCancelled");
            Boolean cancelled = (Boolean) isCancelled.invoke(bukkitEvent);

            // Return true if NOT cancelled (player CAN break), false if cancelled (player CANNOT break)
            return !Boolean.TRUE.equals(cancelled);

        } catch (Throwable t) {
            LOGGER.debug("BukkitBridge: canPlayerBreakBlock check failed, allowing", t);
            return true;  // On error, allow break (fail-safe)
        }
    }

    public static class FlagQueryResult {
        public final String state;
        public final java.util.List<String> regions;

        public FlagQueryResult(String state, java.util.List<String> regions) {
            this.state = state;
            this.regions = regions == null ? java.util.Collections.emptyList() : regions;
        }
    }

    public static Boolean queryStateFlag(Object level, Object playerObj, int x, int y, int z, Object stateFlag) {
        FlagQueryResult detailed = queryStateFlagDetailed(level, playerObj, x, y, z, stateFlag);
        if (detailed == null || detailed.state == null) {
            return null;
        }
        if ("DENY".equalsIgnoreCase(detailed.state)) {
            return false;
        }
        if ("ALLOW".equalsIgnoreCase(detailed.state)) {
            return true;
        }
        return null;
    }

    public static FlagQueryResult queryStateFlagDetailed(Object level, Object playerObj, int x, int y, int z, Object stateFlag) {
        try {
            if (level == null || stateFlag == null) {
                return new FlagQueryResult(null, null);
            }

            Class<?> bukkitBukkitClass = forNameOrNull("org.bukkit.Bukkit");
            if (bukkitBukkitClass == null) {
                return new FlagQueryResult(null, null);
            }

            Object bukkitWorld = getBukkitWorldForLevel(level, bukkitBukkitClass);
            if (bukkitWorld == null) {
                return new FlagQueryResult(null, null);
            }

            Class<?> wgClass = forNameOrNull("com.sk89q.worldguard.WorldGuard");
            Class<?> bukkitAdapter = forNameOrNull("com.sk89q.worldedit.bukkit.BukkitAdapter");
            if (wgClass == null || bukkitAdapter == null) {
                return new FlagQueryResult(null, null);
            }

            Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
            Object platform = wgInstance.getClass().getMethod("getPlatform").invoke(wgInstance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            if (container == null) {
                return new FlagQueryResult(null, null);
            }

            Object weWorld = adaptWorld(bukkitAdapter, bukkitWorld);
            if (weWorld == null) {
                return new FlagQueryResult(null, null);
            }

            Object regionManager = container.getClass().getMethod("get", weWorld.getClass()).invoke(container, weWorld);
            if (regionManager == null) {
                return new FlagQueryResult(null, null);
            }

            Class<?> blockVector3 = forNameOrNull("com.sk89q.worldedit.math.BlockVector3");
            if (blockVector3 == null) {
                return new FlagQueryResult(null, null);
            }
            Method at = blockVector3.getMethod("at", int.class, int.class, int.class);
            Object vec = at.invoke(null, x, y, z);

            Method getApplicable = regionManager.getClass().getMethod("getApplicableRegions", vec.getClass());
            Object appSet = getApplicable.invoke(regionManager, vec);
            if (appSet == null) {
                return new FlagQueryResult(null, null);
            }

            Object bukkitPlayer = convertToBukkitPlayer(playerObj);
            Object playerForQuery = bukkitPlayer != null ? bukkitPlayer : playerObj;
            String state = queryApplicableRegionSet(appSet, playerForQuery, stateFlag);
            java.util.List<String> regionNames = getRegionNames(appSet);
            LOGGER.debug("BukkitBridge: WG flag query pos={} player={} stateFlag={} resultState={} regions={}", x + "," + y + "," + z, playerForQuery == null ? "null" : playerForQuery.getClass().getName(), stateFlag == null ? "null" : stateFlag.getClass().getName(), state, regionNames);
            return new FlagQueryResult(state, regionNames);
        } catch (Throwable t) {
            LOGGER.debug("BukkitBridge: WG flag query failed", t);
            return new FlagQueryResult(null, null);
        }
    }

    private static java.util.List<String> getRegionNames(Object appSet) {
        try {
            Method getRegions = appSet.getClass().getMethod("getRegions");
            Object regions = getRegions.invoke(appSet);
            if (regions instanceof java.util.Collection<?>) {
                java.util.List<String> names = new java.util.ArrayList<>();
                for (Object region : (java.util.Collection<?>) regions) {
                    if (region == null) continue;
                    String name = null;
                    try {
                        Method getId = region.getClass().getMethod("getId");
                        Object id = getId.invoke(region);
                        if (id != null) name = id.toString();
                    } catch (Throwable ignored) {
                    }
                    if (name == null) {
                        try {
                            Method getName = region.getClass().getMethod("getName");
                            Object nm = getName.invoke(region);
                            if (nm != null) name = nm.toString();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (name == null) {
                        name = region.toString();
                    }
                    names.add(name);
                }
                return names;
            }
        } catch (Throwable ignored) {
        }
        return java.util.Collections.emptyList();
    }

    private static Object getBukkitWorldForLevel(Object level, Class<?> bukkitBukkitClass) {
        try {
            String dimPath = null;
            try {
                Method dim = level.getClass().getMethod("dimension");
                Object dimKey = dim.invoke(level);
                if (dimKey != null) {
                    Method loc = dimKey.getClass().getMethod("location");
                    Object resLoc = loc.invoke(dimKey);
                    if (resLoc != null) {
                        Method getPath = resLoc.getClass().getMethod("getPath");
                        dimPath = (String) getPath.invoke(resLoc);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            Method getWorlds = bukkitBukkitClass.getMethod("getWorlds");
            @SuppressWarnings("unchecked")
            java.util.List<Object> worlds = (java.util.List<Object>) getWorlds.invoke(null);
            if (worlds != null) {
                for (Object w : worlds) {
                    try {
                        Method getName = w.getClass().getMethod("getName");
                        String name = (String) getName.invoke(w);
                        if (dimPath != null && name != null && (name.equalsIgnoreCase(dimPath) || name.toLowerCase().contains(dimPath))) {
                            return w;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (!worlds.isEmpty()) {
                    return worlds.get(0);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object adaptWorld(Class<?> bukkitAdapter, Object bukkitWorld) {
        try {
            Method adaptWorld = bukkitAdapter.getMethod("adapt", Class.forName("org.bukkit.World"));
            return adaptWorld.invoke(null, bukkitWorld);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object convertToBukkitPlayer(Object playerObj) {
        try {
            if (playerObj == null) {
                return null;
            }
            Method getBukkitEntity = playerObj.getClass().getMethod("getBukkitEntity");
            return getBukkitEntity.invoke(playerObj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String queryApplicableRegionSet(Object appSet, Object playerObj, Object stateFlag) {
        try {
            Method queryStateMethod = null;
            for (Method method : appSet.getClass().getMethods()) {
                if (!method.getName().equals("queryState")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[1].isAssignableFrom(stateFlag.getClass())) {
                    if (playerObj != null && params[0].isAssignableFrom(playerObj.getClass())) {
                        LOGGER.debug("BukkitBridge: Calling ApplicableRegionSet.queryState(player, flag) with player={} flagClass={}", playerObj.getClass().getName(), stateFlag.getClass().getName());
                        Object state = method.invoke(appSet, playerObj, stateFlag);
                        return state == null ? null : state.toString();
                    }
                    if (params[0].isAssignableFrom(stateFlag.getClass())) {
                        LOGGER.debug("BukkitBridge: Calling ApplicableRegionSet.queryState(null, flag) with flagClass={}", stateFlag.getClass().getName());
                        Object state = method.invoke(appSet, null, stateFlag);
                        return state == null ? null : state.toString();
                    }
                }
                if (params.length == 1 && params[0].isAssignableFrom(stateFlag.getClass())) {
                    queryStateMethod = method;
                    LOGGER.debug("BukkitBridge: Calling ApplicableRegionSet.queryState(flag) with flagClass={}", stateFlag.getClass().getName());
                    Object state = queryStateMethod.invoke(appSet, stateFlag);
                    return state == null ? null : state.toString();
                }
            }

            Method getRegions = appSet.getClass().getMethod("getRegions");
            Object regions = getRegions.invoke(appSet);
            if (regions instanceof java.util.Collection) {
                boolean allowSeen = false;
                for (Object region : (java.util.Collection<?>) regions) {
                    try {
                        Method getFlag = findMethod(region.getClass(), "getFlag", stateFlag.getClass());
                        if (getFlag == null) {
                            continue;
                        }
                        Object value = getFlag.invoke(region, stateFlag);
                        if (value != null) {
                            String name = value.toString();
                            if ("DENY".equalsIgnoreCase(name)) {
                                return "DENY";
                            }
                            if ("ALLOW".equalsIgnoreCase(name)) {
                                allowSeen = true;
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
                if (allowSeen) {
                    return "ALLOW";
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?> argType) {
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (param.isAssignableFrom(argType)) {
                return method;
            }
        }
        return null;
    }

    private static Class<?> forNameOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Query the LiteminerWGPlugin via Bukkit Event System to check if a player can use LiteMiner at a location.
     * This uses the LiteminerBreakCheckEvent which the plugin listens to.
     * 
     * @param player The Minecraft player object
     * @param level The Minecraft level/world object
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     * @return true if player can break blocks with LiteMiner, false if blocked by WorldGuard
     */
    public static boolean canLiteminerBreakViaPlugin(Object player, Object level, int x, int y, int z) {
        try {
            if (player == null || level == null) {
                LOGGER.debug("BukkitBridge: Player or level is null, allowing action (fail-safe).");
                return true;
            }

            // Convert Minecraft player to Bukkit player
            Object bukkitPlayer = convertToBukkitPlayer(player);
            if (bukkitPlayer == null) {
                LOGGER.debug("BukkitBridge: Could not convert to Bukkit player, allowing action (fail-safe).");
                return true;
            }

            // Get Bukkit world
            Object bukkitWorld = null;
            try {
                Method getWorld = bukkitPlayer.getClass().getMethod("getWorld");
                bukkitWorld = getWorld.invoke(bukkitPlayer);
            } catch (Throwable ignored) {
                LOGGER.debug("BukkitBridge: Could not get Bukkit world from player.");
                return true;
            }

            if (bukkitWorld == null) {
                LOGGER.debug("BukkitBridge: Bukkit world is null, allowing action (fail-safe).");
                return true;
            }

            // Create Bukkit Location
            Class<?> locationClass = forNameOrNull("org.bukkit.Location");
            if (locationClass == null) {
                LOGGER.debug("BukkitBridge: Location class not found, allowing action (fail-safe).");
                return true;
            }

            Constructor<?> locationCtor = locationClass.getConstructor(
                    forNameOrNull("org.bukkit.World"), double.class, double.class, double.class
            );
            Object bukkitLocation = locationCtor.newInstance(bukkitWorld, x + 0.5, y + 0.5, z + 0.5);

            // Prefer calling the plugin's public API directly (avoids classloader / event class mismatches).
            try {
                Class<?> bukkitClass = forNameOrNull("org.bukkit.Bukkit");
                if (bukkitClass != null) {
                    Method getPluginManager = bukkitClass.getMethod("getPluginManager");
                    Object pluginManager = getPluginManager.invoke(null);
                    if (pluginManager != null) {
                        // Try to find the plugin instance by name
                        try {
                            Method getPlugin = pluginManager.getClass().getMethod("getPlugin", String.class);
                            Object pluginInstance = getPlugin.invoke(pluginManager, "LiteminerWGPlugin");
                            if (pluginInstance != null) {
                                // Attempt to call static method canLiteminerBreak(Player, Location) on the plugin class
                                Class<?> pluginClass = pluginInstance.getClass();
                                try {
                                    Method canMethod = pluginClass.getMethod("canLiteminerBreak", forNameOrNull("org.bukkit.entity.Player"), locationClass);
                                    Object result = canMethod.invoke(null, bukkitPlayer, bukkitLocation);
                                    if (result instanceof Boolean) {
                                        return (Boolean) result;
                                    }
                                } catch (NoSuchMethodException ignored) {
                                    // method not available on plugin class - fall through to event approach
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("BukkitBridge: direct plugin API call failed, falling back to event approach.", t);
            }

            // Fallback: Create LiteminerBreakCheckEvent via reflection and call it on the Bukkit plugin manager
            Class<?> eventClass = forNameOrNull("org.example.wryndel.liteminerwgplugin.event.LiteminerBreakCheckEvent");
            if (eventClass == null) {
                LOGGER.debug("BukkitBridge: LiteminerBreakCheckEvent class not found, allowing action (fail-safe).");
                return true;
            }

            // Construct event: LiteminerBreakCheckEvent(Player player, Location location, boolean isAllowed, String reason)
            try {
                Constructor<?> eventCtor = eventClass.getConstructor(
                        forNameOrNull("org.bukkit.entity.Player"),
                        locationClass,
                        boolean.class,
                        String.class
                );
                Object event = eventCtor.newInstance(bukkitPlayer, bukkitLocation, true, "");

                if (forNameOrNull("org.bukkit.Bukkit") == null) {
                    LOGGER.debug("BukkitBridge: Bukkit class not found, allowing action (fail-safe).");
                    return true;
                }

                Method getPluginManager = Class.forName("org.bukkit.Bukkit").getMethod("getPluginManager");
                Object pluginManager = getPluginManager.invoke(null);

                Class<?> eventBaseClass = forNameOrNull("org.bukkit.event.Event");
                Method callEvent = pluginManager.getClass().getMethod("callEvent", eventBaseClass);
                callEvent.invoke(pluginManager, event);

                // Read the result from the event via isAllowed() method
                Method isAllowedMethod = eventClass.getMethod("isAllowed");
                Object allowedObj = isAllowedMethod.invoke(event);
                if (allowedObj instanceof Boolean) {
                    boolean allowed = (Boolean) allowedObj;
                    LOGGER.debug("BukkitBridge: LiteminerBreakCheckEvent.isAllowed() returned {}.", allowed);
                    return allowed;
                }

            } catch (Throwable t) {
                LOGGER.warn("BukkitBridge: Error creating/firing LiteminerBreakCheckEvent.", t);
                return true; // Fail-safe allow
            }

            // If event was fired but unable to read result, allow by default
            LOGGER.debug("BukkitBridge: Could not read LiteminerBreakCheckEvent result, allowing action (fail-safe)." );
            return true;

        } catch (Throwable t) {
            LOGGER.error("BukkitBridge: Unexpected error in canLiteminerBreakViaPlugin.", t);
            return true; // Fail-safe allow
        }
    }
}
