package com.leclowndu93150.structures_tweaker.mixin;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.events.DynamicStructureFlags;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(BucketItem.class)
public abstract class BucketItemMixin {
    @Inject(method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z", 
            at = @At("HEAD"), 
            cancellable = true,
            remap = false)
    private void onEmptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult result, @Nullable ItemStack container, CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide() || !(level instanceof ServerLevel)) {
            return;
        }
        
        if (player == null) {
            return;
        }

        StructureEventHandler handler = StructuresTweaker.getEventHandler();
        if (handler == null) {
            return;
        }

        StructureCache cache = StructuresTweaker.getStructureCache();
        if (cache == null) {
            return;
        }

        ResourceLocation structureId = cache.getStructureAtPosition(level, pos);
        if (structureId == null) {
            return;
        }

        DynamicStructureFlags flags = handler.structureFlags.get(structureId);
        if (flags != null) {
            if (player.isCreative() && flags.creativeBypass()) {
                return;
            }
            
            if (!flags.canPlaceBlocks()) {
                cir.setReturnValue(false);
            }
        }
    }
}
