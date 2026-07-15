package org.example.wryndel.liteminerwgaddon.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import dev.architectury.utils.value.IntValue;
import dev.architectury.event.EventResult;
import net.minecraft.world.level.block.state.BlockState;
import org.example.wryndel.liteminerwgaddon.compat.WorldGuardCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts LiteMiner's OnBlockBreak event and cancels VeinMine if WorldGuard denies the origin block.
 */
@Mixin(targets = "com.iamkaf.liteminer.event.OnBlockBreak")
public class OnBlockBreakEventMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiteminerWG:OnBlockBreakEvent");

    @Inject(
        method = "onBlockBreak",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 1
    )
    private static void onBlockBreakEvent(
        Level level,
        BlockPos absoluteOrigin,
        BlockState blockState,
        ServerPlayer player,
        IntValue intValue,
        CallbackInfoReturnable<EventResult> cir
    ) {
        try {
            if (level.isClientSide || player == null) {
                return;
            }

            // Only apply protection if we're in a LiteMiner-capable server context
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }

            // Check if the player is allowed to break this block
            // If WorldGuard denies this block, return interruptFalse() so Architectury sees a valid result
            boolean allowed = WorldGuardCompat.canLiteMinerBreak(player, serverLevel, absoluteOrigin);

            if (!allowed) {
                LOGGER.info("[LiteminerWG] CANCELLED LiteMiner event at pos={} player={}", absoluteOrigin, player.getScoreboardName());
                cir.setReturnValue(EventResult.interruptFalse());
                return;
            }

            // If allowed, set the context for subsequent block operations (OnBlockBreakMixin)
            WorldGuardCompat.setCurrentContext(player, serverLevel);
            LOGGER.debug("[LiteminerWG] LiteMiner event allowed at pos={} for player={}", absoluteOrigin, player.getScoreboardName());

        } catch (Throwable t) {
            LOGGER.debug("[LiteminerWG] OnBlockBreak event check failed", t);
            // On error, allow event to proceed (fail-safe)
        }
    }
}

