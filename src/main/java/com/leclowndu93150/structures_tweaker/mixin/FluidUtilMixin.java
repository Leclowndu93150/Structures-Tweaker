package com.leclowndu93150.structures_tweaker.mixin;

import com.leclowndu93150.structures_tweaker.StructuresTweaker;
import com.leclowndu93150.structures_tweaker.cache.StructureCache;
import com.leclowndu93150.structures_tweaker.events.DynamicStructureFlags;
import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

@Mixin(FluidUtil.class)
public abstract class FluidUtilMixin {
    @Inject(method = "tryPlaceFluid(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/core/BlockPos;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler;Lnet/neoforged/neoforge/fluids/FluidStack;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void onTryPlaceFluid(@Nullable Player player, Level level, InteractionHand hand, BlockPos pos, IFluidHandler fluidSource, FluidStack resource, CallbackInfoReturnable<Boolean> cir) {
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
