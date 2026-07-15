package org.example.wryndel.liteminerwgplugin.api;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.example.wryndel.liteminerwgplugin.LiteminerWGPlugin;

/**
 * Implementation of ILiteminerWGApi for external access to liteminer-break flag checking.
 */
public class LiteminerWGApiImpl implements ILiteminerWGApi {
    
    private final LiteminerWGPlugin plugin;
    
    public LiteminerWGApiImpl(LiteminerWGPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean canLiteminerBreak(Player player, Location location) {
        return LiteminerWGPlugin.canLiteminerBreak(player, location);
    }
    
    @Override
    public boolean isFlagAvailable() {
        return LiteminerWGPlugin.isFlagAvailable();
    }
    
    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}
