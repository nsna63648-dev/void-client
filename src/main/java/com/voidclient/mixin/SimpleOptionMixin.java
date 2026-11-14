package com.voidclient.mixin;

import com.voidclient.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright / Gamma: override ค่า gamma ตอนอ่านเท่านั้น ไม่แก้ไฟล์ options.txt */
@Mixin(SimpleOption.class)
public class SimpleOptionMixin<T> {
    @SuppressWarnings("unchecked")
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void voidclient$gamma(CallbackInfoReturnable<T> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        if ((Object) this != mc.options.getGamma()) return;
        Double v = ModuleManager.gammaOverride();
        if (v != null) cir.setReturnValue((T) (Object) v);
    }
}
