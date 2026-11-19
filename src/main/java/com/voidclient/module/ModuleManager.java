package com.voidclient.module;

import com.voidclient.config.VoidConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.particle.ParticlesMode;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Function;

import static com.voidclient.module.Category.*;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static final ArrayDeque<Long> LMB = new ArrayDeque<>(), RMB = new ArrayDeque<>();

    private static Module fullbright, gamma, crosshair, seeInvisibles, memoryOpt, animOpt;
    private static final List<Module> particleCutters = new ArrayList<>();
    private static final Random RNG = new Random();

    private static int combatTicks, dmgTicks, nearby;
    private static float lastDamage, prevHealth = -1f;
    private static boolean wasL, wasR, wasInWorld;
    private static int savedMaxFps = -1;

    private static <T extends Module> T add(T m) { ALL.add(m); return m; }
    private static LineHud hud(String id, String name, String desc, Category c, boolean en, int x, int y,
                               Function<MinecraftClient, List<String>> f) {
        return add(new LineHud(id, name, desc, c, en, x, y, f));
    }

    public static void init() {
        ALL.clear(); particleCutters.clear();

        // ---------- HUD ----------
        hud("fps", "FPS Counter", "แสดง FPS", HUD, true, 6, 6, mc -> List.of("FPS  " + mc.getCurrentFps()));
        hud("ping", "Ping", "แสดง Ping", HUD, true, 6, 24, mc -> {
            var nh = mc.getNetworkHandler();
            var e = (nh == null || mc.player == null) ? null : nh.getPlayerListEntry(mc.player.getUuid());
            return List.of("PING  " + (e == null ? "-" : e.getLatency() + " ms"));
        });
        hud("cps", "CPS", "คลิกต่อวินาที", HUD, true, 6, 42, mc -> List.of("CPS  " + cps(LMB) + " | " + cps(RMB)));
        hud("coords", "Coordinates", "พิกัด XYZ", HUD, true, 6, 60, mc -> mc.player == null ? List.of() :
                List.of(String.format("XYZ  %.1f  %.1f  %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        hud("direction", "Direction HUD", "ทิศทางที่หัน", HUD, true, 150, 6, mc -> {
            if (mc.player == null) return List.of();
            float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
            String[] n = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            int i = Math.floorMod(Math.round((yaw + 180f) / 45f), 8);
            return List.of(n[i] + "  " + Math.round(yaw + 180f) + "°");
        });
        add(new Special.Keystrokes());
        add(new Special.Armor());
        add(new Special.Potion());

        // ---------- VISUAL ----------
        seeInvisibles = add(new Toggle("seeinvisibles", "See Invisibles", "มองเห็นผู้เล่นที่ดื่มยาล่องหน", VISUAL, false, null));
        fullbright = add(new Toggle("fullbright", "Fullbright", "เพิ่มความสว่างในพื้นที่มืด", VISUAL, false, null));
        gamma = add(new Toggle("gamma", "Gamma Control", "ปรับระดับความสว่าง", VISUAL, false, null))
                .with(new Setting("gamma", "Gamma", 0f, 10f, 1.5f));
        add(new Toggle("clearvision", "Clear Vision", "ลด screen shake / distortion / FOV effect", VISUAL, false, on -> {
            var o = MinecraftClient.getInstance().options; if (o == null) return;
            var self = byId("clearvision");
            if (on) {
                self.s.v.put("dist", o.getDistortionEffectScale().getValue().floatValue());
                self.s.v.put("fov", o.getFovEffectScale().getValue().floatValue());
                self.s.v.put("tilt", o.getDamageTiltStrength().getValue().floatValue());
                o.getDistortionEffectScale().setValue(0.0);
                o.getFovEffectScale().setValue(0.0);
                o.getDamageTiltStrength().setValue(0.0);
            } else {
                o.getDistortionEffectScale().setValue((double) self.val("dist", 1f));
                o.getFovEffectScale().setValue((double) self.val("fov", 1f));
                o.getDamageTiltStrength().setValue((double) self.val("tilt", 1f));
            }
        }));
        crosshair = add(new Special.Crosshair());

        // ---------- FPS BOOST ----------
        add(new Toggle("sodiumopt", "Sodium Optimization", "ตั้งค่าที่เข้ากับ Sodium (graphics fast, biome blend ต่ำ)", PERFORMANCE, false, on -> {
            if (!on) return;
            var o = MinecraftClient.getInstance().options;
            o.getBiomeBlendRadius().setValue(1);
        }));
        add(new Toggle("entityculling", "Entity Culling", "ลดระยะ render Entity (แนะนำใช้ร่วมม็อด Entity Culling)", PERFORMANCE, false, on -> {
            var o = MinecraftClient.getInstance().options;
            if (on) o.getEntityDistanceScaling().setValue(0.6); else o.getEntityDistanceScaling().setValue(1.0);
        }));
        add(new Toggle("particleopt", "Particle Optimization", "ลด Particle ที่ไม่จำเป็น", PERFORMANCE, false, on -> {
            MinecraftClient.getInstance().options.getParticles().setValue(on ? ParticlesMode.DECREASED : ParticlesMode.ALL);
        }));
        add(new Toggle("dynamicfps", "Dynamic FPS", "ลด FPS ตอนหน้าต่างไม่โฟกัส (ลด CPU/GPU)", PERFORMANCE, false, on -> {
            if (!on) restoreFps(MinecraftClient.getInstance());
        }).onTick(mc -> {
            var opt = mc.options.getMaxFps();
            if (!mc.isWindowFocused()) {
                if (savedMaxFps < 0) savedMaxFps = opt.getValue();
                opt.setValue(Math.min(savedMaxFps, 15));
            } else restoreFps(mc);
        }));
        memoryOpt = add(new Toggle("memoryopt", "Memory Optimization", "คืนหน่วยความจำเมื่อออกจากโลก", PERFORMANCE, false, null));
        add(new Toggle("chunkopt", "Chunk Rendering Optimization", "ลด simulation distance + biome blend", PERFORMANCE, false, on -> {
            var o = MinecraftClient.getInstance().options;
            if (on) { o.getSimulationDistance().setValue(8); o.getBiomeBlendRadius().setValue(1); }
        }));
        animOpt = add(new Toggle("animopt", "Animation Optimization", "ปิด Animation ของ VOID UI และ blur เมนู", PERFORMANCE, false, on -> {
            if (on) MinecraftClient.getInstance().options.getMenuBackgroundBlurriness().setValue(0);
        }));
        add(new Toggle("noeffects", "Disable Unneeded Effects", "ปิดเงา Entity / เมฆ", PERFORMANCE, false, on -> {
            var o = MinecraftClient.getInstance().options;
            o.getEntityShadows().setValue(!on);
            o.getCloudRenderMode().setValue(on ? net.minecraft.client.option.CloudRenderMode.OFF : net.minecraft.client.option.CloudRenderMode.FAST);
        }));
        add(new Toggle("distances", "Render & Entity Distance", "ตั้งระยะ Render / Entity", PERFORMANCE, false, on -> {
            var self = byId("distances");
            if (on) { applyDistances(self); }
        }).with(new Setting("rd", "Render Distance", 2, 32, 12), new Setting("ed", "Entity Distance", 0.5f, 5f, 1f))
          .set0());

        // ---------- CRYSTAL ----------
        hud("crystalhud", "Crystal HUD", "จำนวน End Crystal / Obsidian ในกระเป๋า", CRYSTAL, false, 260, 6, mc ->
                List.of("Crystals  " + count(mc, Items.END_CRYSTAL), "Obsidian  " + count(mc, Items.OBSIDIAN)));
        hud("crystalind", "Crystal Indicator", "ระยะ Crystal ที่ใกล้ที่สุดในสายตา", CRYSTAL, false, 260, 36, mc -> {
            if (mc.player == null || mc.world == null) return List.of();
            double best = 1e9; int n = 0;
            for (Entity e : mc.world.getEntitiesByClass(EndCrystalEntity.class, mc.player.getBoundingBox().expand(24), x -> true)) {
                if (!mc.player.canSee(e)) continue;
                n++; best = Math.min(best, mc.player.distanceTo(e));
            }
            return n == 0 ? List.of() : List.of(String.format("Crystal x%d  %.1fm", n, best));
        });
        particleCutters.add(add(new Toggle("crystalparticle", "Crystal Particle Optimizer", "ลด Particle ระเบิด Crystal", CRYSTAL, false, null))
                .with(new Setting("keep", "Keep %", 0f, 1f, 0.25f)));
        particleCutters.add(add(new Toggle("explosionvisual", "Crystal Explosion Visual", "ปรับความหนาแน่นเอฟเฟกต์ระเบิด", CRYSTAL, false, null))
                .with(new Setting("keep", "Intensity", 0f, 1f, 0.5f)));
        hud("lastdamage", "Crystal Damage Display", "ดาเมจล่าสุดที่ได้รับ (HUD)", CRYSTAL, false, 260, 66, mc ->
                dmgTicks > 0 ? List.of(String.format("-%.1f HP", lastDamage)) : List.of());
        add(new Toggle("crystalrender", "Crystal Render Optimizer", "ลด Entity distance ระหว่างสู้ (ภาพเท่านั้น)", CRYSTAL, false, on -> {
            var o = MinecraftClient.getInstance().options;
            o.getEntityShadows().setValue(!on);
        }));

        // ---------- ANCHOR ----------
        hud("anchorhud", "Anchor HUD", "Respawn Anchor / Glowstone ในกระเป๋า", ANCHOR, false, 260, 96, mc ->
                List.of("Anchors  " + count(mc, Items.RESPAWN_ANCHOR), "Glowstone  " + count(mc, Items.GLOWSTONE)));
        hud("anchorstatus", "Anchor Status Display", "สถานะ charge ของ Anchor ที่มองอยู่", ANCHOR, false, 260, 126, mc -> {
