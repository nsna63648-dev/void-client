package com.voidclient.mixin;

import com.voidclient.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    /** ซ่อน crosshair ปกติเมื่อเปิด Custom Crosshair */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void voidclient$crosshair(DrawContext ctx, RenderTickCounter tc, CallbackInfo ci) {
        if (ModuleManager.crosshairOn()) ci.cancel();
    }
}
