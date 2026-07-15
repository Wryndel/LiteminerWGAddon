package org.example.wryndel.liteminerwgplugin.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;
import org.bukkit.Location;

/**
 * Bukkit Event triggered when LiteMiner block break is checked against WorldGuard permissions.
 * 
 * This event allows the NeoForge mod to learn the result of WorldGuard region check
 * without needing direct access to WorldGuard API or direct communication with the plugin.
 * 
 * The plugin generates this event after checking the liteminer-break flag.
 * The mod can listen to this event to make decisions about LiteMiner usage.
 */
public class LiteminerBreakCheckEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    
    private final Player player;
    private final Location location;
    private boolean isAllowed;
    private String reason;
    
    /**
     * Create a new LiteminerBreakCheckEvent.
     * 
     * @param player The player attempting to use LiteMiner
     * @param location The location of the block
     * @param isAllowed Whether the action is allowed by WorldGuard
     * @param reason Human-readable reason for the decision
     */
    public LiteminerBreakCheckEvent(Player player, Location location, boolean isAllowed, String reason) {
        this.player = player;
        this.location = location;
        this.isAllowed = isAllowed;
        this.reason = reason;
    }
    
    /**
     * Get the player attempting to use LiteMiner.
     * 
     * @return the player
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * Get the location of the block being broken.
     * 
     * @return the location
     */
    public Location getLocation() {
        return location;
    }
    
    /**
     * Check if breaking is allowed by WorldGuard.
     * 
     * @return true if allowed, false if denied by liteminer-break flag
     */
    public boolean isAllowed() {
        return isAllowed;
    }
    
    /**
     * Set whether breaking is allowed (can be modified by plugins).
     * 
     * @param allowed true if breaking should be allowed, false to deny
     */
    public void setAllowed(boolean allowed) {
        this.isAllowed = allowed;
    }
    
    /**
     * Get the reason for the decision.
     * 
     * @return reason string (e.g., "allowed by default", "denied by liteminer-break")
     */
    public String getReason() {
        return reason;
    }
    
    /**
     * Set the reason for the decision.
     * 
     * @param reason human-readable reason message
     */
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
