package com.voidclient.mixin;

import com.voidclient.module.ModuleManager;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particle Optimizer: ลด particle ระเบิด (Crystal/Anchor) ฝั่งภาพอย่างเดียว */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void voidclient$cull(ParticleEffect e, double x, double y, double z,
                                 double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        if (ModuleManager.dropParticle(e)) cir.setReturnValue(null);
    }
}
