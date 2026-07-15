package org.example.wryndel.liteminerwgplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.example.wryndel.liteminerwgplugin.event.LiteminerBreakCheckEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LiteminerWGPlugin - Bukkit plugin for LiteMiner + WorldGuard integration on Youer.
 * 
 * This plugin:
 * 1. Registers the custom liteminer-break StateFlag in WorldGuard
 * 2. Listens to LiteminerBreakCheckEvent from the NeoForge mod
 * 3. Checks the WorldGuard liteminer-break flag state
 * 4. Fires LiteminerBreakCheckEvent with the result
 * 
 * The NeoForge mod receives the event result and applies it.
 * No direct communication between mod and plugin - only via Bukkit Event System.
 */
public class LiteminerWGPlugin extends JavaPlugin implements Listener {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiteminerWG:Plugin");
    private static StateFlag liteminerBreakFlag = null;
    private static final String FLAG_NAME = "liteminer-break";
    private boolean debug = false;
    
    @Override
    public void onLoad() {
        // Load config as early as possible
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);
        
        // Register flag as early as possible, before WorldGuard loads regions
        if (debug) LOGGER.debug("LiteminerWGPlugin: onLoad() - attempting early registration...");
        registerLiteminerBreakFlag();
    }
    
    @Override
    public void onEnable() {
        LOGGER.info("LiteminerWGPlugin: Enabling plugin...");
        
        // Inform status
        if (liteminerBreakFlag != null) {
            LOGGER.info("LiteminerWGPlugin: liteminer-break StateFlag is registered and available.");
        } else {
            LOGGER.warn("LiteminerWGPlugin: liteminer-break StateFlag could not be registered. Plugin will operate in fail-safe mode. " +
                    "This usually happens if regions.yml references 'liteminer-break' or if WorldGuard flags window closed already.");
        }
        
        // Register this plugin as event listener
        getServer().getPluginManager().registerEvents(this, this);
        LOGGER.info("LiteminerWGPlugin: Registered event listeners.");
        
        LOGGER.info("LiteminerWGPlugin: Plugin enabled successfully.");
    }
    
    @Override
    public void onDisable() {
        LOGGER.info("LiteminerWGPlugin: Disabling plugin...");
        liteminerBreakFlag = null;
    }
    
    /**
     * Register the custom liteminer-break StateFlag in WorldGuard.
     * This should be called from onLoad() to register before WorldGuard loads regions.
     * 
     * @return true if registration was successful, false otherwise
     */
    private boolean registerLiteminerBreakFlag() {
        try {
            FlagRegistry flagRegistry = WorldGuard.getInstance().getFlagRegistry();

            // Check if flag already exists
            Flag<?> existingFlag = flagRegistry.get(FLAG_NAME);
            if (existingFlag != null) {
                // If it's already a StateFlag, use it
                if (existingFlag instanceof StateFlag) {
                    liteminerBreakFlag = (StateFlag) existingFlag;
                    if (debug) LOGGER.debug("LiteminerWGPlugin: Reusing existing StateFlag for liteminer-break.");
                    return true;
                }
                
                // If it's an UnknownFlag, we cannot register over it now
                String existingClassName = existingFlag.getClass().getSimpleName();
                if (existingClassName != null && existingClassName.contains("UnknownFlag")) {
                    LOGGER.warn("LiteminerWGPlugin: UnknownFlag 'liteminer-break' detected. " +
                            "This means regions.yml references 'liteminer-break' but StateFlag was not registered before WorldGuard loaded regions. " +
                            "Plugin will continue in fail-safe mode (all blocks are allowed). " +
                            "To fix: ensure LiteminerWGPlugin loads before WorldGuard (use loadbefore in plugin.yml).");
                    // Do NOT try to replace UnknownFlag - registration window is closed
                    return false;
                }
                
                // Other unrecognized flag type
                LOGGER.warn("LiteminerWGPlugin: Existing 'liteminer-break' flag has unexpected type: {}", 
                        existingFlag.getClass().getName());
                return false;
            }

            // No existing flag, attempt registration
            StateFlag candidate = new StateFlag(FLAG_NAME, true);
            try {
                flagRegistry.register(candidate);
                liteminerBreakFlag = candidate;
                LOGGER.info("LiteminerWGPlugin: Successfully registered StateFlag 'liteminer-break'.");
                return true;
            } catch (IllegalStateException ise) {
                // New flags cannot be registered at this time - registration window closed
                LOGGER.warn("LiteminerWGPlugin: Registration window closed - cannot register new flags. " +
                        "Ensure plugin loads before WorldGuard. IllegalStateException: {}", ise.getMessage());
                return false;
            } catch (Exception e) {
                // Handle flag conflict or other registration errors
                if (e.getClass().getSimpleName().contains("FlagConflictException")) {
                    if (debug) LOGGER.debug("LiteminerWGPlugin: Flag conflict - flag already queued for registration.");
                    // Try to use the conflicting flag
                    Flag<?> existing = flagRegistry.get(FLAG_NAME);
                    if (existing instanceof StateFlag) {
                        liteminerBreakFlag = (StateFlag) existing;
                        return true;
                    }
                    return false;
                }
                LOGGER.error("LiteminerWGPlugin: Error registering liteminer-break flag.", e);
                return false;
            }

        } catch (Exception e) {
            LOGGER.error("LiteminerWGPlugin: Error accessing WorldGuard flag registry.", e);
            return false;
        }
    }
    
    /**
     * Listen to LiteminerBreakCheckEvent from the NeoForge mod.
     * Query WorldGuard and fire the event back with results.
     * 
     * We listen to the event, process it, and fire it after setting additional info.
     * The NeoForge mod will receive the processed event.
     */
    @EventHandler
    public void onLiteminerBreakCheck(LiteminerBreakCheckEvent event) {
        try {
            Player player = event.getPlayer();
            Location location = event.getLocation();
            
            if (player == null || location == null || location.getWorld() == null) {
                LOGGER.debug("LiteminerWGPlugin: Player or location is null in event, allowing action.");
                event.setAllowed(true);
                event.setReason("no player or location");
                return;
            }
            
            if (liteminerBreakFlag == null) {
                LOGGER.debug("LiteminerWGPlugin: liteminer-break flag is null, allowing action (fail-safe).");
                event.setAllowed(true);
                event.setReason("flag not registered (fail-safe)");
                return;
            }
            
            // Check WorldGuard
            boolean allowed = checkWorldGuardFlag(player, location);
            
            // Update the event with the result
            event.setAllowed(allowed);
            event.setReason(allowed ? "allowed by WorldGuard" : "denied by liteminer-break flag");
            
            LOGGER.debug("LiteminerWGPlugin: LiteMiner check for player {} at {}: allowed={}, reason={}",
                    player.getName(), location, allowed, event.getReason());
            
        } catch (Exception e) {
            LOGGER.error("LiteminerWGPlugin: Error processing LiteminerBreakCheckEvent.", e);
            event.setAllowed(true);
            event.setReason("error during check (fail-safe)");
        }
    }
    
    /**
     * Check WorldGuard liteminer-break flag for a player at a location.
     * 
     * @param player The player
     * @param location The location
     * @return true if allowed, false if denied
     */
    private static boolean checkWorldGuardFlag(Player player, Location location) {
        try {
            if (liteminerBreakFlag == null) {
                LOGGER.debug("LiteminerWGPlugin: liteminer-break flag is null, allowing (fail-safe).");
                return true;
            }
            
            org.bukkit.World bukkitWorld = location.getWorld();
            if (bukkitWorld == null) {
                LOGGER.debug("LiteminerWGPlugin: Could not get Bukkit world, allowing (fail-safe).");
                return true;
            }
            
            // Convert Bukkit world to WorldEdit world
            World weWorld = BukkitAdapter.adapt(bukkitWorld);
            if (weWorld == null) {
                LOGGER.debug("LiteminerWGPlugin: Could not adapt Bukkit world to WorldEdit world, allowing (fail-safe).");
                return true;
            }
            
            // Get the region manager
            RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(weWorld);
            if (regionManager == null) {
                LOGGER.debug("LiteminerWGPlugin: No region manager for world {}, allowing (fail-safe).", bukkitWorld.getName());
                return true; // No regions in this world
            }
            
            // Get applicable regions at the location
            ApplicableRegionSet regions = regionManager.getApplicableRegions(
                    BukkitAdapter.asBlockVector(location)
            );
            
            // Query the flag state (player context may not be available in all WG versions)
            StateFlag.State state = null;
            try {
                // Use reflection to call queryState(player, flag) if available in this WG version
                java.lang.reflect.Method m = ApplicableRegionSet.class.getMethod("queryState", Object.class, Flag.class);
                Object res = m.invoke(regions, player, liteminerBreakFlag);
                if (res instanceof StateFlag.State) {
                    state = (StateFlag.State) res;
                }
            } catch (NoSuchMethodException ignored) {
                // method not present - fall back
            } catch (Exception e) {
                LOGGER.debug("LiteminerWGPlugin: Reflection queryState failed, falling back.", e);
            }

            if (state == null) {
                // Fallback: query without a specific RegionAssociable
                state = regions.queryState(null, liteminerBreakFlag);
            }
            
            boolean allowed = state == null || state == StateFlag.State.ALLOW;
            LOGGER.debug("LiteminerWGPlugin: WorldGuard query result for player {} at {}: state={}, allowed={}",
                    player.getName(), location, state, allowed);
            
            return allowed;
            
        } catch (Exception e) {
            LOGGER.error("LiteminerWGPlugin: Error checking WorldGuard flag for player {} at {}.",
                    player.getName(), location, e);
            return true; // Fail-safe allow
        }
    }

    /**
     * Public API used by other classes to check whether LiteMiner may break at a location.
     */
    public static boolean canLiteminerBreak(Player player, Location location) {
        return checkWorldGuardFlag(player, location);
    }

    /**
     * Public API to report whether the liteminer-break flag has been registered and is available.
     */
    public static boolean isFlagAvailable() {
        return liteminerBreakFlag != null;
    }
}
