package com.voidclient.mixin;

import com.voidclient.module.ModuleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity && ModuleManager.seeInvisiblesOn()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ItemEntity && ModuleManager.itemEspOn()) {
            cir.setReturnValue(true);
        } else if (self instanceof LivingEntity && !(self instanceof PlayerEntity) && ModuleManager.mobEspOn()) {
            cir.setReturnValue(true);
        } else if (self instanceof PlayerEntity && ModuleManager.playerEspOn()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColorValue(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof ItemEntity && ModuleManager.itemEspOn()) {
            cir.setReturnValue(0x38BDF8);
        } else if (self instanceof PlayerEntity && ModuleManager.playerEspOn()) {
            cir.setReturnValue(0xA855F7);
        } else if (self instanceof LivingEntity && ModuleManager.mobEspOn()) {
            cir.setReturnValue(0x4ADE80);
        }
    }
}
