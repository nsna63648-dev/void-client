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

/** ศูนย์รวม Feature ทั้งหมด — เพิ่ม Feature ใหม่ได้ที่ init() บรรทัดเดียว */
public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static final ArrayDeque<Long> LMB = new ArrayDeque<>(), RMB = new ArrayDeque<>();

    private static Module fullbright, gamma, crosshair, memoryOpt, animOpt;
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
           // o.getGraphics().setValue(net.minecraft.client.option.GraphicsMode.FAST);
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
            if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult b) || b.getType() != HitResult.Type.BLOCK) return List.of();
            BlockState st = mc.world.getBlockState(b.getBlockPos());
            if (!st.isOf(Blocks.RESPAWN_ANCHOR)) return List.of();
            return List.of("Anchor charge  " + st.get(RespawnAnchorBlock.CHARGES) + " / 4");
        });
        particleCutters.add(add(new Toggle("anchorparticle", "Anchor Particle Optimizer", "ลด Particle ระเบิด Anchor", ANCHOR, false, null))
                .with(new Setting("keep", "Keep %", 0f, 1f, 0.25f)));

        // ---------- COMBAT ----------
        hud("totem", "Totem Counter", "จำนวน Totem", COMBAT, false, 380, 6, mc -> List.of("Totems  " + count(mc, Items.TOTEM_OF_UNDYING)));
        hud("health", "Health Display", "เลือด + absorption", COMBAT, false, 380, 24, mc -> mc.player == null ? List.of() :
                List.of(String.format("HP  %.1f%s", mc.player.getHealth(), mc.player.getAbsorptionAmount() > 0 ? String.format(" + %.1f", mc.player.getAbsorptionAmount()) : "")));
        hud("target", "Target Information HUD", "ชื่อ/เลือดเป้าหมายที่เล็งอยู่", COMBAT, false, 380, 42, mc -> {
            if (mc.targetedEntity instanceof LivingEntity le)
                return List.of(le.getName().getString(), String.format("HP  %.1f / %.1f", le.getHealth(), le.getMaxHealth()));
            return List.of();
        });
        hud("combattimer", "Combat Timer", "ตัวจับเวลาหลังสู้", COMBAT, false, 380, 72, mc ->
                combatTicks > 0 ? List.of(String.format("Combat  %.1fs", combatTicks / 20f)) : List.of());
        hud("cooldown", "Item Cooldown Display", "คูลดาวน์ Pearl / Gapple / Shield", COMBAT, false, 380, 90, mc -> {
            if (mc.player == null) return List.of();
            List<String> out = new ArrayList<>();
            Item[] items = {Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.CHORUS_FRUIT, Items.SHIELD};
            for (Item it : items) {
                float p = mc.player.getItemCooldownManager().getCooldownProgress(new ItemStack(it), 0f);
                if (p > 0f) out.add(new ItemStack(it).getName().getString() + "  " + Math.round(p * 100) + "%");
            }
            return out;
        });
        hud("enchants", "Enchantment Display", "เอนชานต์ของไอเทมในมือ", COMBAT, false, 380, 120, mc -> {
            if (mc.player == null) return List.of();
            List<String> out = new ArrayList<>();
            for (var e : mc.player.getMainHandStack().getEnchantments().getEnchantmentEntries())
                out.add(Enchantment.getName(e.getKey(), e.getIntValue()).getString());
            return out;
        });
        hud("nearby", "Surrounding Entity Counter", "จำนวน Entity รอบตัว 16 บล็อก", COMBAT, false, 380, 190, mc ->
                List.of("Nearby  " + nearby));
        hud("dmgind", "Custom Damage Indicators", "ตัวเลขดาเมจที่ได้รับ", COMBAT, false, 380, 208, mc ->
                dmgTicks > 0 ? List.of(String.format("-%.1f", lastDamage)) : List.of());
        add(new Toggle("pvpperf", "PVP Performance", "ลด Particle/เงา ระหว่างสู้เพื่อลด FPS drop", COMBAT, false, on -> {
            var o = MinecraftClient.getInstance().options;
            o.getParticles().setValue(on ? ParticlesMode.MINIMAL : ParticlesMode.ALL);
            o.getEntityShadows().setValue(!on);
        }));
    }

    // ---------- runtime ----------
    public static void tick(MinecraftClient mc) {
        boolean inWorld = mc.world != null;
        if (wasInWorld && !inWorld && memoryOpt != null && memoryOpt.enabled()) System.gc();
        wasInWorld = inWorld;

        for (Module m : ALL) if (m.enabled()) {
            try { m.tick(mc); } catch (Throwable t) { System.err.println("[VOID] tick error in " + m.id + ": " + t); }
        }
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null) { prevHealth = -1f; return; }

        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (prevHealth >= 0 && hp < prevHealth) { lastDamage = prevHealth - hp; dmgTicks = 60; combatTicks = 200; }
        prevHealth = hp;
        if (p.hurtTime > 0) combatTicks = 200;
        if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity) combatTicks = 200;
        if (combatTicks > 0) combatTicks--;
        if (dmgTicks > 0) dmgTicks--;
        if (p.age % 10 == 0)
            nearby = mc.world.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(16), e -> e != p).size();
    }

    public static void renderHud(DrawContext c, MinecraftClient mc) {
        if (mc.player == null) return;
        pollClicks(mc);
        for (Module m : ALL) if (m.enabled()) {
            try { m.render(c, mc); } catch (Throwable t) { System.err.println("[VOID] render error in " + m.id + ": " + t); }
        }
    }

    private static void pollClicks(MinecraftClient mc) {
        if (mc.currentScreen != null) { wasL = wasR = false; return; }
        long win = mc.getWindow().getHandle(), now = System.currentTimeMillis();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (l && !wasL) LMB.add(now);
        if (r && !wasR) RMB.add(now);
        wasL = l; wasR = r;
    }

    public static int cps(ArrayDeque<Long> q) {
        long now = System.currentTimeMillis();
        q.removeIf(t -> now - t > 1000);
        return q.size();
    }

    // ---------- helpers used by mixins / GUI ----------
    public static Double gammaOverride() {
        if (fullbright != null && fullbright.enabled()) return 16.0;
        if (gamma != null && gamma.enabled()) return (double) gamma.val("gamma", 1.5f);
        return null;
    }
    public static boolean crosshairOn() { return crosshair != null && crosshair.enabled(); }
    public static boolean animationsOff() { return animOpt != null && animOpt.enabled(); }

    public static boolean dropParticle(ParticleEffect e) {
        if (e.getType() != ParticleTypes.EXPLOSION && e.getType() != ParticleTypes.EXPLOSION_EMITTER) return false;
        float keep = 1f; boolean any = false;
        for (Module m : particleCutters) if (m.enabled()) { any = true; keep = Math.min(keep, m.val("keep", 0.25f)); }
        return any && RNG.nextFloat() > keep;
    }

    public static Module byId(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException(id);
    }

    public static void resetAll() {
        for (Module m : ALL) { if (m.enabled()) m.setEnabled(false); m.reset(); }
        VoidConfig.save();
    }

    public static boolean sodiumLoaded() { return FabricLoader.getInstance().isModLoaded("sodium"); }

    private static void restoreFps(MinecraftClient mc) {
        if (savedMaxFps >= 0 && mc.options != null) { mc.options.getMaxFps().setValue(savedMaxFps); savedMaxFps = -1; }
    }

    static void applyDistances(Module m) {
        var o = MinecraftClient.getInstance().options;
        o.getViewDistance().setValue((int) m.val("rd", 12f));
        o.getEntityDistanceScaling().setValue((double) m.val("ed", 1f));
    }

    private static int count(MinecraftClient mc, Item item) {
        if (mc.player == null) return 0;
        int n = 0; var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) { ItemStack st = inv.getStack(i); if (st.isOf(item)) n += st.getCount(); }
        return n;
    }

    // ---------- CPvP presets (เปิดเฉพาะ HUD/ตัวลดภาพ ไม่มีระบบอัตโนมัติ) ----------
    public static void cpvpPreset(boolean crystal) {
        String[] common = {"totem", "health", "combattimer", "cooldown", "dmgind", "pvpperf"};
        String[] cr = {"crystalhud", "crystalparticle", "lastdamage", "crystalind"};
        String[] an = {"anchorhud", "anchorstatus", "anchorparticle"};
        for (String id : common) byId(id).setEnabled(true);
        for (String id : crystal ? cr : an) byId(id).setEnabled(true);
        for (String id : crystal ? an : cr) byId(id).setEnabled(false);
    }

    private ModuleManager() {}
}
