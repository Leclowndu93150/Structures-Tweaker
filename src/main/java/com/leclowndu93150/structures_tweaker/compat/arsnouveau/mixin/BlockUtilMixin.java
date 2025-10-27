package com.leclowndu93150.structures_tweaker.compat.arsnouveau.mixin;

import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(value = BlockUtil.class, remap = false)
public class BlockUtilMixin {
    
    @Inject(method = "destroyBlockSafely", at = @At("HEAD"), cancellable = true)
    private static void onDestroyBlock(Level world, BlockPos pos, boolean dropBlock, LivingEntity caster, CallbackInfoReturnable<Boolean> cir) {
        if (!(world instanceof ServerLevel)) {
            return;
        }
        
        Player player = caster instanceof Player ? (Player) caster : null;
        
        AtomicBoolean shouldCancel = new AtomicBoolean(false);
        StructureEventHandler handler = StructuresTweaker.getEventHandler();
        
        if (handler != null) {
            handler.handleStructureEvent(world, pos, player, (structure, flags) -> {
                if (!flags.canBreakBlocks()) {
                    shouldCancel.set(true);
                    return true;
                }
                return false;
            });
        }
        
        if (shouldCancel.get()) {
            cir.setReturnValue(false);
        }
    }
}
