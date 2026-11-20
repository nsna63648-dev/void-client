package com.voidclient.module;

import com.voidclient.config.VoidConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.particle.ParticlesMode;

import java.util.List;

/** Preset: LOW (ภาพสวย) / BALANCED (สมดุล) / HIGH (รีด FPS สูงสุดเห็นผลทันที) */
public final class FpsPresets {
    private static void setMod(String id, boolean state) {
        for (Module m : ModuleManager.ALL) {
            if (m.id.equals(id) && m.enabled() != state) {
                m.setEnabled(state);
            }
        }
    }

    public static void apply(String preset) {
        MinecraftClient mc = MinecraftClient.getInstance();
        GameOptions o = mc.options;
        if (o == null) return;

        int rd, sim, biome, mip, maxFps, blur;
        double ent;
        ParticlesMode pm;
        CloudRenderMode cl;
        boolean shadows, vsync;

        List<String> boostMods = List.of(
                "sodiumopt", "entityculling", "particleopt",
                "memoryopt", "chunkopt", "animopt", "noeffects"
        );

        switch (preset) {
            case "LOW" -> {
                rd = 14; sim = 10; ent = 1.0;
                pm = ParticlesMode.ALL; cl = CloudRenderMode.FAST;
                shadows = true; vsync = false;
                biome = 3; mip = 4; maxFps = 260; blur = 5;
                for (String id : boostMods) setMod(id, false);
            }
            case "HIGH" -> {
                // รีด FPS สูงสุด: ปลดล็อกเฟรมเรต + ปิด VSync + ลดระยะเรนเดอร์ + เปิดโมดูลเพิ่ม FPS ทั้งหมด
                rd = 6; sim = 4; ent = 0.5;
                pm = ParticlesMode.MINIMAL; cl = CloudRenderMode.OFF;
                shadows = false; vsync = false;
                biome = 0; mip = 0; maxFps = 260; blur = 0;
                for (String id : boostMods) setMod(id, true);
            }
            default -> { // BALANCED
                rd = 10; sim = 6; ent = 0.75;
                pm = ParticlesMode.DECREASED; cl = CloudRenderMode.OFF;
                shadows = false; vsync = false;
                biome = 1; mip = 2; maxFps = 260; blur = 0;
                setMod("sodiumopt", true);
                setMod("particleopt", true);
                setMod("memoryopt", true);
                setMod("animopt", true);
                setMod("noeffects", true);
                setMod("entityculling", false);
                setMod("chunkopt", false);
            }
        }

        try { o.getViewDistance().setValue(rd); } catch (Throwable ignored) {}
        try { o.getSimulationDistance().setValue(sim); } catch (Throwable ignored) {}
        try { o.getEntityDistanceScaling().setValue(ent); } catch (Throwable ignored) {}
        try { o.getParticles().setValue(pm); } catch (Throwable ignored) {}
        try { o.getCloudRenderMode().setValue(cl); } catch (Throwable ignored) {}
        try { o.getEntityShadows().setValue(shadows); } catch (Throwable ignored) {}
        try { o.getEnableVsync().setValue(vsync); } catch (Throwable ignored) {}
        try { o.getMaxFps().setValue(maxFps); } catch (Throwable ignored) {}
        try { o.getBiomeBlendRadius().setValue(biome); } catch (Throwable ignored) {}
        try { o.getMipmapLevels().setValue(mip); } catch (Throwable ignored) {}
        try { o.getMenuBackgroundBlurriness().setValue(blur); } catch (Throwable ignored) {}

        VoidConfig.data.preset = preset;
        VoidConfig.save();
        o.write();

        if ("HIGH".equals(preset)) {
            System.gc(); // คืน RAM ที่ค้างอยู่ทันที
        }
    }
}
