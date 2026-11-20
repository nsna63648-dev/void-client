package com.voidclient.mixin;

import com.voidclient.gui.VoidCosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerCosmeticsMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && self == mc.player && VoidCosmetics.isCapeEnabled()) {
            VoidCosmetics.ensureTextures();
            SkinTextures orig = cir.getReturnValue();
            if (orig != null) {
                AssetInfo.TextureAsset capeAsset = new AssetInfo.TextureAssetInfo(VoidCosmetics.CAPE_TEX, VoidCosmetics.CAPE_TEX);
                cir.setReturnValue(new SkinTextures(orig.body(), capeAsset, orig.elytra(), orig.model(), orig.secure()));
            }
        }
    }
}
