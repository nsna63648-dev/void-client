package com.voidclient.gui;

import com.voidclient.module.Module;
import com.voidclient.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public final class VoidCosmetics {
    public static final Identifier CAPE_TEX = Identifier.of("voidclient", "textures/void_cape_generated.png");
    public static final Identifier WING_TEX = Identifier.of("voidclient", "textures/void_wing_generated.png");
    private static boolean registered = false;

    public static boolean isCapeEnabled() {
        for (Module m : ModuleManager.ALL) if (m.id.equals("voidcape")) return m.enabled();
        return false;
    }

    public static boolean isWingsEnabled() {
        for (Module m : ModuleManager.ALL) if (m.id.equals("voidwings")) return m.enabled();
        return false;
    }

    public static void ensureTextures() {
        if (registered) return;
        registered = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        // 1. สร้างลายผ้าคลุม VOID Cape (64x32)
        NativeImage cape = new NativeImage(64, 32, false);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                int bg = (y < 17) ? (0xFF140926 + ((y * 2) << 16)) : 0xFF0B0514;
                cape.setColorArgb(x, y, bg);
            }
        }
        int borderCol = 0xFFA855F7;
        int lightCol = 0xFFE879F9;
        for (int x = 1; x <= 10; x++) {
            cape.setColorArgb(x, 1, borderCol);
            cape.setColorArgb(x, 16, borderCol);
        }
        for (int y = 1; y <= 16; y++) {
            cape.setColorArgb(1, y, borderCol);
            cape.setColorArgb(10, y, borderCol);
        }
        int[][] vPixels = {
                {3, 5}, {8, 5}, {3, 6}, {8, 6},
                {4, 7}, {7, 7}, {4, 8}, {7, 8},
                {5, 9}, {6, 9}, {5, 10}, {6, 10}
        };
        for (int[] p : vPixels) cape.setColorArgb(p, p, lightCol);
        cape.setColorArgb(4, 3, 0xFFC084FC);
        cape.setColorArgb(6, 3, 0xFFC084FC);
        cape.setColorArgb(7, 3, 0xFFC084FC);

        mc.getTextureManager().registerTexture(CAPE_TEX, new NativeImageBackedTexture(() -> "void_cape", cape));

        // 2. สร้างลายปีกเรืองแสงสีม่วง (16x16)
        NativeImage wing = new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean frame = (x == 0 || y == 0 || (x + y == 15) || (x == y && x < 10));
                int col = frame ? 0xFFC084FC : ((x + y < 15) ? 0x996D28D9 : 0x00000000);
                wing.setColorArgb(x, y, col);
            }
        }
        mc.getTextureManager().registerTexture(WING_TEX, new NativeImageBackedTexture(() -> "void_wing", wing));
    }

    public static void renderWings(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player, float tickDelta) {
        if (!isWingsEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || player != mc.player) return;
        ensureTextures();

        float time = (player.age + tickDelta) * 0.18f;
        float flap = MathHelper.sin(time) * 18.0f + (player.isSprinting() ? 14.0f : 0f);

        VertexConsumer vc = buffers.getBuffer(RenderLayers.entityTranslucent(WING_TEX));

        for (int side = -1; side <= 1; side += 2) {
            matrices.push();
            matrices.translate(side * 0.12f, 0.18f, 0.16f);
            if (player.isInSneakingPose()) {
                matrices.translate(0f, 0.12f, 0.05f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(25f));
            }
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * (26.0f + flap)));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -12.0f));

            MatrixStack.Entry entry = matrices.peek();
            float w = 0.85f * side;
            float h = 0.95f;

            vertex(vc, entry, 0f, 0f, 0f, 0f, 0f, light);
            vertex(vc, entry, w, -0.15f, 0.08f, 1f, 0f, light);
            vertex(vc, entry, w * 0.92f, h, 0.12f, 1f, 1f, light);
            vertex(vc, entry, 0f, h * 0.78f, 0f, 0f, 1f, light);

            vertex(vc, entry, 0f, h * 0.78f, 0f, 0f, 1f, light);
            vertex(vc, entry, w * 0.92f, h, 0.12f, 1f, 1f, light);
            vertex(vc, entry, w, -0.15f, 0.08f, 1f, 0f, light);
            vertex(vc, entry, 0f, 0f, 0f, 0f, 0f, light);

            matrices.pop();
        }
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int light) {
        vc.vertex(entry, x, y, z)
                .color(255, 255, 255, 235)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0f, 1f, 0f);
    }

    private VoidCosmetics() {}
}
