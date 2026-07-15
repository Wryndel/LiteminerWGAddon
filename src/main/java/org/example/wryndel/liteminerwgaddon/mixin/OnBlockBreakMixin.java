package org.example.wryndel.liteminerwgaddon.mixin;

import net.minecraft.core.BlockPos;
import dev.architectury.event.EventResult;
import dev.architectury.utils.value.IntValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.example.wryndel.liteminerwgaddon.compat.WorldGuardCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "com.iamkaf.liteminer.event.OnBlockBreak")
public class OnBlockBreakMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("LiteminerWG:OnBlockBreakMixin");

    @ModifyVariable(
        method = "onBlockBreak",
        at = @At(
            value = "INVOKE",
            target = "java/util/List.iterator()Ljava/util/Iterator;"
        ),
        ordinal = 0,
        name = "blocks"
    )
    private static List<BlockPos> filterBlocks(List<BlockPos> blocks, Level level, BlockPos absoluteOrigin, BlockState blockState, ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || player == null || blocks == null) {
            return blocks;
        }

        // Publish current player/level context so Level mixins can use it when LiteMiner performs direct world changes
        org.example.wryndel.liteminerwgaddon.compat.WorldGuardCompat.setCurrentContext(player, serverLevel);
        
        List<BlockPos> filtered = new ArrayList<>(blocks.size());
        for (BlockPos pos : blocks) {
            boolean allowed = WorldGuardCompat.canLiteMinerBreak(player, serverLevel, pos);
            if (allowed) {
                filtered.add(pos);
                LOGGER.info("LiteminerWGAddon: ALLOWED {}", pos);
            } else {
                LOGGER.info("LiteminerWGAddon: BLOCKED {}", pos);
            }
        }
        
        LOGGER.info("LiteminerWGAddon: Filtered blocks from {} to {}", blocks.size(), filtered.size());
        // DO NOT clear context here - it's needed while LiteMiner breaks the filtered blocks via setBlockAndUpdate()
        // Context will remain active during the entire block-breaking operation and will be cleared by LiteMiner mod
        // or by Minecraft event loop after this method completes
        return filtered;
    }

    @Inject(
        method = "onBlockBreak",
        at = @At(
            value = "INVOKE",
            target = "Lcom/iamkaf/liteminer/shapes/Walker;walk(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Ljava/util/HashSet;"
        ),
        cancellable = true,
        remap = false,
        require = 1
    )
    private static void onBlockBreakBeforeWalk(
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

            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }

            boolean allowed = WorldGuardCompat.canLiteMinerBreak(player, serverLevel, absoluteOrigin);
            if (!allowed) {
                LOGGER.info("LiteminerWGAddon: Denied VeinMine start at {}", absoluteOrigin);
                cir.setReturnValue(EventResult.pass());
                cir.cancel();
            }
        } catch (Throwable t) {
            LOGGER.debug("LiteminerWGAddon: VeinMine guard check failed", t);
        }
    }
}




