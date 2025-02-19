package com.leclowndu93150.structures_tweaker.mixin;

import com.leclowndu93150.structures_tweaker.events.StructureEventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void onStartFallFlying(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player)(Object)this;
        if (StructureEventHandler.shouldCancelElytraFlight(player.level(), player.blockPosition())) {
            cir.setReturnValue(Boolean.FALSE);
            player.displayClientMessage(Component.translatable("message.structures_tweaker.no_elytra"), true);
        }
    }
}
