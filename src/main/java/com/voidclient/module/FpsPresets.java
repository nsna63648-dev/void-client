package com.voidclient.module;

import com.voidclient.config.VoidConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticleMode;

/** Preset: LOW (คงภาพ) / BALANCED / HIGH (FPS สูงสุด) — แก้เฉพาะตัวเลือกในเกม ไม่แตะไฟล์เกมต้นฉบับ */
public final class FpsPresets {
    public static void apply(String preset) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameOptions o = mc.options;
        if (o == null) return;
        int rd, sim, biome, mip; double ent; ParticleMode pm; CloudRenderMode cl; boolean shadows; GraphicsMode gm;
        switch (preset) {
            case "LOW" -> { rd = 16; sim = 10; ent = 1.0; pm = ParticleMode.ALL; cl = CloudRenderMode.FAST; shadows = true; gm = GraphicsMode.FANCY; biome = 3; mip = 4; }
            case "HIGH" -> { rd = 8; sim = 5; ent = 0.5; pm = ParticleMode.MINIMAL; cl = CloudRenderMode.OFF; shadows = false; gm = GraphicsMode.FAST; biome = 0; mip = 0; }
            default -> { rd = 12; sim = 8; ent = 0.8; pm = ParticleMode.DECREASED; cl = CloudRenderMode.OFF; shadows = false; gm = GraphicsMode.FAST; biome = 1; mip = 2; }
        }
        try { o.getViewDistance().setValue(rd); } catch (Throwable ignored) {}
        try { o.getSimulationDistance().setValue(sim); } catch (Throwable ignored) {}
        try { o.getEntityDistanceScaling().setValue(ent); } catch (Throwable ignored) {}
        try { o.getParticles().setValue(pm); } catch (Throwable ignored) {}
        try { o.getCloudRenderMode().setValue(cl); } catch (Throwable ignored) {}
        try { o.getEntityShadows().setValue(shadows); } catch (Throwable ignored) {}
        try { o.getGraphics().setValue(gm); } catch (Throwable ignored) {}
        try { o.getBiomeBlendRadius().setValue(biome); } catch (Throwable ignored) {}
        try { o.getMipmapLevels().setValue(mip); } catch (Throwable ignored) {}
        VoidConfig.data.preset = preset;
        VoidConfig.save();
        o.write();
    }
    private FpsPresets() {}
}
