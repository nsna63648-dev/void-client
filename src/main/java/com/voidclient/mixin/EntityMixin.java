package com.voidclient.mixin;

import com.voidclient.module.ModuleManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isInvisible", at = @At("HEAD"), cancellable = true)
    private void onIsInvisible(CallbackInfoReturnable<Boolean> cir) {
        // ตรวจสอบว่า Entity นี้เป็นผู้เล่น และฟีเจอร์ SeeInvisibles เปิดอยู่หรือไม่
        if ((Object) this instanceof PlayerEntity) {
            // *หมายเหตุ: เปลี่ยนชื่อเมธอดเช็กสถานะให้ตรงกับใน ModuleManager.java ของคุณ
            if (ModuleManager.isEnabled("SeeInvisibles")) { 
                cir.setReturnValue(false);
            }
        }
    }
}
