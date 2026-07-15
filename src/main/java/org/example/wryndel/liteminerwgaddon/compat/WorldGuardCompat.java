package org.example.wryndel.liteminerwgaddon.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldGuardCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiteminerWG:WorldGuardCompat");
    private static final Object LOCK = new Object();
    private static final String BUKKIT_CLASS = "org.bukkit.Bukkit";
    private static final String WORLDGUARD_PLUGIN_NAME = "WorldGuard";
    private static final long INITIALIZE_RETRY_MS = 5000L;

    private static volatile boolean initialized = false;
    private static volatile boolean worldGuardPresent = false;
    private static volatile long lastInitializeAttempt = 0L;

    // Thread-local context populated by the mixin when LiteMiner is running on the server thread
    private static final ThreadLocal<ServerPlayer> CURRENT_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<ServerLevel> CURRENT_LEVEL = new ThreadLocal<>();

    private WorldGuardCompat() {
    }

    private static Object getBukkitServer() {
        Class<?> bukkitClass = forNameOrNull(BUKKIT_CLASS);
        if (bukkitClass == null) {
            return null;
        }
        try {
            return bukkitClass.getMethod("getServer").invoke(null);
        } catch (Throwable t) {
            LOGGER.debug("WorldGuardCompat: Bukkit.getServer() failed.", t);
            return null;
        }
    }

    private static Object getPluginManager(Object server) {
        if (server == null) {
            return null;
        }
        try {
            return server.getClass().getMethod("getPluginManager").invoke(server);
        } catch (Throwable t) {
            LOGGER.debug("WorldGuardCompat: Bukkit.getPluginManager() failed.", t);
            return null;
        }
    }

    private static Object getWorldGuardPlugin(Object pluginManager) {
        if (pluginManager == null) {
            return null;
        }
        try {
            return pluginManager.getClass().getMethod("getPlugin", String.class).invoke(pluginManager, WORLDGUARD_PLUGIN_NAME);
        } catch (Throwable t) {
            LOGGER.debug("WorldGuardCompat: Bukkit plugin lookup failed.", t);
            return null;
        }
    }

    private static void logWorldGuardPluginInfo(Object plugin) {
        if (plugin == null) {
            return;
        }
        try {
            var getDescription = plugin.getClass().getMethod("getDescription");
            Object description = getDescription.invoke(plugin);
            String version = "unknown";
            if (description != null) {
                try {
                    Object versionValue = description.getClass().getMethod("getVersion").invoke(description);
                    if (versionValue != null) {
                        version = versionValue.toString();
                    }
                } catch (Throwable t) {
                    LOGGER.debug("WorldGuardCompat: Failed to read WorldGuard plugin version.", t);
                }
            }
            Boolean enabled = isPluginEnabled(plugin);
            LOGGER.info("WorldGuardCompat: WorldGuard plugin version={}, enabled={}, class={}", version, enabled, plugin.getClass().getName());
        } catch (Throwable t) {
            LOGGER.warn("WorldGuardCompat: Failed to read WorldGuard plugin info.", t);
        }
    }

    private static Boolean isPluginEnabled(Object plugin) {
        if (plugin == null) {
            return null;
        }
        try {
            return (Boolean) plugin.getClass().getMethod("isEnabled").invoke(plugin);
        } catch (Throwable t) {
            LOGGER.debug("WorldGuardCompat: Failed to detect plugin enabled state.", t);
            return null;
        }
    }

    private static void logObjectDetails(String label, Object object) {
        if (object == null) {
            LOGGER.warn("WorldGuardCompat: {} is null", label);
            return;
        }
        Class<?> clazz = object.getClass();
        ClassLoader loader = clazz.getClassLoader();
        String loaderName = loader == null ? "bootstrap" : loader.toString();
        java.util.List<String> methodSignatures = new java.util.ArrayList<>();
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            StringBuilder sb = new StringBuilder();
            sb.append(method.getName()).append('(');
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(params[i].getSimpleName());
            }
            sb.append(')');
            methodSignatures.add(sb.toString());
        }
        LOGGER.warn("WorldGuardCompat: {} class={} loader={} methods=[{}]",
                label,
                clazz.getName(),
                loaderName,
                String.join(", ", methodSignatures));
    }

    private static void logClassDetails(String label, Class<?> clazz) {
        if (clazz == null) {
            LOGGER.warn("WorldGuardCompat: {} is null", label);
            return;
        }
        ClassLoader loader = clazz.getClassLoader();
        String loaderName = loader == null ? "bootstrap" : loader.toString();
        java.util.List<String> methodSignatures = new java.util.ArrayList<>();
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            StringBuilder sb = new StringBuilder();
            sb.append(method.getName()).append('(');
            Class<?>[] params = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(params[i].getSimpleName());
            }
            sb.append(')');
            methodSignatures.add(sb.toString());
        }
        LOGGER.warn("WorldGuardCompat: {} class={} loader={} methods=[{}]",
                label,
                clazz.getName(),
                loaderName,
                String.join(", ", methodSignatures));
    }

    private static void logFlagRegistryDetails(Object flagRegistry, String flagName) {
        if (flagRegistry == null) {
            LOGGER.warn("WorldGuardCompat: FlagRegistry is null.");
            return;
        }
        LOGGER.info("WorldGuardCompat: FlagRegistry implementation: {}", flagRegistry.getClass().getName());
        Object retrieved = retrieveFlag(flagRegistry, flagName);
        if (retrieved != null) {
            LOGGER.info("WorldGuardCompat: Successfully retrieved {} from FlagRegistry.", flagName);
        } else {
            LOGGER.warn("WorldGuardCompat: Failed to retrieve {} from FlagRegistry.", flagName);
        }
        java.util.Collection<?> allFlags = retrieveAllFlags(flagRegistry);
        if (allFlags != null) {
            LOGGER.info("WorldGuardCompat: Found {} registered flags.", allFlags.size());
            if (containsFlag(allFlags, flagName)) {
                LOGGER.info("WorldGuardCompat: {} was found in FlagRegistry.", flagName);
            } else {
                LOGGER.warn("WorldGuardCompat: {} wasn't found in FlagRegistry.", flagName);
            }
        } else {
            LOGGER.info("WorldGuardCompat: Could not enumerate all flags from FlagRegistry.");
        }
    }

    private static Object retrieveFlag(Object flagRegistry, String flagName) {
        if (flagRegistry == null) {
            return null;
        }
        try {
            var getMethod = flagRegistry.getClass().getMethod("get", String.class);
            return getMethod.invoke(flagRegistry, flagName);
        } catch (NoSuchMethodException e) {
            LOGGER.error("WorldGuardCompat: FlagRegistry.get(String) method is not available.", e);
        } catch (Throwable t) {
            LOGGER.error("WorldGuardCompat: retrieveFlag() failed.", t);
        }
        return null;
    }

    private static java.util.Collection<?> retrieveAllFlags(Object flagRegistry) {
        if (flagRegistry == null) {
            return null;
        }
        try {
            var getAllMethod = flagRegistry.getClass().getMethod("getAll");
            Object value = getAllMethod.invoke(flagRegistry);
            if (value instanceof java.util.Collection<?>) {
                return (java.util.Collection<?>) value;
            }
            if (value instanceof java.util.Map<?, ?>) {
                return ((java.util.Map<?, ?>) value).values();
            }
            if (value instanceof java.lang.Iterable<?>) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (Object element : (java.lang.Iterable<?>) value) {
                    list.add(element);
                }
                return list;
            }
            if (value != null && value.getClass().isArray()) {
                return java.util.Arrays.asList((Object[]) value);
            }
            LOGGER.warn("WorldGuardCompat: FlagRegistry.getAll() returned {}, not an enumerable collection.", value != null ? value.getClass().getName() : "null");
            return null;
        } catch (NoSuchMethodException e) {
            LOGGER.debug("WorldGuardCompat: FlagRegistry.getAll() method is not available.", e);
        } catch (Throwable t) {
            LOGGER.error("WorldGuardCompat: retrieveAllFlags() failed.", t);
        }
        try {
            var getFlagsMethod = flagRegistry.getClass().getMethod("getFlags");
            Object value = getFlagsMethod.invoke(flagRegistry);
            if (value instanceof java.util.Collection<?>) {
                return (java.util.Collection<?>) value;
            }
            LOGGER.warn("WorldGuardCompat: FlagRegistry.getFlags() returned {}, not a Collection.", value != null ? value.getClass().getName() : "null");
            return null;
        } catch (NoSuchMethodException e) {
            LOGGER.debug("WorldGuardCompat: FlagRegistry.getFlags() method is not available.", e);
        } catch (Throwable t) {
            LOGGER.error("WorldGuardCompat: retrieveAllFlags() fallback failed.", t);
        }
        return null;
    }

    private static boolean containsFlag(java.util.Collection<?> flags, String flagName) {
        if (flags == null) {
            return false;
        }
        for (Object flag : flags) {
            if (flag == null) {
                continue;
            }
            try {
                var getNameMethod = flag.getClass().getMethod("getName");
                Object name = getNameMethod.invoke(flag);
                if (flagName.equals(name != null ? name.toString() : null)) {
                    return true;
                }
            } catch (NoSuchMethodException e) {
                // Fallback to toString search if no getName available
            } catch (Throwable t) {
                LOGGER.debug("WorldGuardCompat: containsFlag() failed on flag object.", t);
            }
            if (flagName.equals(flag.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEnabled() {
        ensureInitialized();
        return worldGuardPresent;
    }

    public static boolean canLiteMinerBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return canLiteminerBreak(player, level, pos);
    }

    public static CanBreakResult canLiteMinerBreakDetailed(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) {
            return new CanBreakResult(true, "null", "liteminer-break", "", "fail-safe allow");
        }

        ensureInitialized();

        if (!worldGuardPresent) {
            return new CanBreakResult(true, "WorldGuard absent", "liteminer-break", "", "WorldGuard is not present");
        }

        // Primary path: query via Bukkit plugin bridge (event). If unavailable, fail-safe allow.
        try {
            Boolean eventResult = BukkitBridge.canLiteminerBreakViaPlugin(player, level, pos.getX(), pos.getY(), pos.getZ());
            if (eventResult != null) {
                return new CanBreakResult(eventResult, eventResult ? "ALLOW" : "DENY", "via-plugin", "", "result from plugin event");
            }
        } catch (Throwable t) {
            LOGGER.debug("WorldGuardCompat: Event System check failed in detailed query, falling back to allow.", t);
        }

        return new CanBreakResult(true, "null", "liteminer-break", "", "no plugin response, fail-safe allow");
    }

    public static record CanBreakResult(boolean allowed, String state, String flagValue, String regions, String note) {
    }

    public static boolean canLiteminerBreak(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) {
            return true; // fail-safe allow when missing context
        }

        ensureInitialized();

        if (!worldGuardPresent) {
            return true;
        }

        // Query via Bukkit plugin bridge; if unavailable, allow (fail-safe)
        try {
            Boolean eventResult = BukkitBridge.canLiteminerBreakViaPlugin(player, level, pos.getX(), pos.getY(), pos.getZ());
            if (eventResult != null) {
                LOGGER.debug("WorldGuardCompat: Using Event System result: allowed={} for player={} at {}", eventResult, player.getScoreboardName(), pos);
                return eventResult;
            }
        } catch (Throwable t) {
            LOGGER.debug("WorldGuardCompat: Event System check failed, allowing by default.", t);
        }

        return true;
    }

    /**
     * No registration attempts are performed by the mod. The Bukkit plugin is the sole owner
     * of WorldGuard flag registration. The mod only queries the Bukkit plugin via the bridge/event.
     */
    public static boolean registerFlags() {
        LOGGER.debug("WorldGuardCompat: registerFlags() called on mod side - no-op (plugin owns flags).");
        return false;
    }

    public static void startRegistrationRetry() {
        LOGGER.debug("WorldGuardCompat: startRegistrationRetry() called on mod side - no-op (plugin owns flags).");
    }

    private static void ensureInitialized() {
        if (worldGuardPresent) {
            return;
        }
        long now = System.currentTimeMillis();
        if (initialized && now - lastInitializeAttempt < INITIALIZE_RETRY_MS) {
            return;
        }
        synchronized (LOCK) {
            if (worldGuardPresent) {
                return;
            }
            if (initialized && now - lastInitializeAttempt < INITIALIZE_RETRY_MS) {
                return;
            }
            lastInitializeAttempt = now;
            initialized = true;
            Object bukkitServer = getBukkitServer();
            if (bukkitServer == null) {
                worldGuardPresent = false;
                LOGGER.debug("WorldGuardCompat: Bukkit not detected; LiteMiner will operate normally");
            } else {
                Object pluginManager = getPluginManager(bukkitServer);
                if (pluginManager == null) {
                    worldGuardPresent = false;
                    LOGGER.debug("WorldGuardCompat: Bukkit found but plugin manager is unavailable.");
                } else {
                    Object worldGuardPlugin = getWorldGuardPlugin(pluginManager);
                    if (worldGuardPlugin == null) {
                        worldGuardPresent = false;
                        LOGGER.debug("WorldGuardCompat: WorldGuard plugin isn't loaded yet; LiteMiner will operate normally");
                    } else {
                        Boolean enabled = isPluginEnabled(worldGuardPlugin);
                        if (Boolean.FALSE.equals(enabled)) {
                            worldGuardPresent = false;
                            LOGGER.debug("WorldGuardCompat: WorldGuard plugin found but not enabled; LiteMiner will operate normally");
                        } else {
                            worldGuardPresent = true;
                            LOGGER.debug("WorldGuardCompat: WorldGuard plugin detected during initialization.");
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by mixins to set the current player/level context for subsequent Level modifications
     * so that setBlockAndUpdate/removeBlock checks can use the original player.
     */
    public static void setCurrentContext(ServerPlayer player, ServerLevel level) {
        CURRENT_PLAYER.set(player);
        CURRENT_LEVEL.set(level);
    }

    public static void clearCurrentContext() {
        CURRENT_PLAYER.remove();
        CURRENT_LEVEL.remove();
    }

    public static ServerPlayer getCurrentPlayer() {
        return CURRENT_PLAYER.get();
    }

    public static ServerLevel getCurrentLevel() {
        return CURRENT_LEVEL.get();
    }

    public static boolean isLiteMinerContext() {
        return CURRENT_PLAYER.get() != null && CURRENT_LEVEL.get() != null;
    }

    private static Class<?> forNameOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Class<?> forNameOrNull(String name, ClassLoader classLoader) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable e) {
            return null;
        }
    }
}
