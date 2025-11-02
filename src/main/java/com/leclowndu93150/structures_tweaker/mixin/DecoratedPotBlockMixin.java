package com.leclowndu93150.structures_tweaker.mixin;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.events.DynamicStructureFlags;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DecoratedPotBlock.class)
public abstract class DecoratedPotBlockMixin {
    @Inject(method = "useItemOn", 
            at = @At("HEAD"), 
            cancellable = true)
    private void onUseItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (level.isClientSide() || !(level instanceof ServerLevel)) {
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
            
            if (!flags.canInteract()) {
                cir.setReturnValue(ItemInteractionResult.FAIL);
            }
        }
    }
}
