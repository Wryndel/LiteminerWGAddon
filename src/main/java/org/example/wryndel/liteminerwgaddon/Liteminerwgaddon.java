package org.example.wryndel.liteminerwgaddon;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.example.wryndel.liteminerwgaddon.compat.WorldGuardCompat;
import org.slf4j.Logger;

@Mod(Liteminerwgaddon.MODID)
public class Liteminerwgaddon {
    public static final String MODID = "liteminerwgaddon";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Liteminerwgaddon(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("LiteminerWGAddon loaded");
        
        // WorldGuard flag registration is handled by the Bukkit plugin; mod should not register flags.
    }
}
