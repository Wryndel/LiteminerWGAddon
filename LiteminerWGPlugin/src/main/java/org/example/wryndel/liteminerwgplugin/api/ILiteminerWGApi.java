package org.example.wryndel.liteminerwgplugin.api;

import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * API interface for querying liteminer-break flag state.
 * 
 * This interface is exposed by LiteminerWGPlugin and can be used by other mods/plugins
 * to check if a player is allowed to use LiteMiner in a specific location.
 */
public interface ILiteminerWGApi {
    
    /**
     * Check if a player can use LiteMiner at a specific location.
     * 
     * @param player The player performing the action
     * @param location The location where the action is happening
     * @return true if allowed, false if denied
     */
    boolean canLiteminerBreak(Player player, Location location);
    
    /**
     * Check if the liteminer-break flag is properly registered and available.
     * 
     * @return true if flag is available, false otherwise
     */
    boolean isFlagAvailable();
    
    /**
     * Get the plugin version.
     * 
     * @return plugin version string
     */
    String getVersion();
}
