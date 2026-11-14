package com.voidclient.module;

import com.voidclient.gui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** HUD ที่วาดเองแบบพิเศษ: Keystrokes, Armor, Potion, Crosshair */
public final class Special {

    public static class Keystrokes extends HudModule {
        public Keystrokes() { super("keystrokes", "Keystrokes", "แสดงปุ่มที่กำลังกด + CPS", Category.HUD, true, 6, 90); }

        private void key(DrawContext c, MinecraftClient mc, String label, int x, int y, int bw, int bh, boolean down) {
            Theme.rrect(c, x, y, x + bw, y + bh, 3, down ? Theme.argb(0.95f, Theme.PURPLE) : Theme.argb(s.opacity, Theme.BG));
            int tw = mc.textRenderer.getWidth(label);
            c.drawText(mc.textRenderer, label, x + (bw - tw) / 2, y + (bh - 8) / 2, 0xFFFFFFFF, true);
        }

        @Override protected void content(DrawContext c, MinecraftClient mc, boolean edit) {
            var o = mc.options;
            w = 64; h = 64;
            key(c, mc, "W", 22, 0, 20, 20, o.forwardKey.isPressed());
            key(c, mc, "A", 0, 22, 20, 20, o.leftKey.isPressed());
            key(c, mc, "S", 22, 22, 20, 20, o.backKey.isPressed());
            key(c, mc, "D", 44, 22, 20, 20, o.rightKey.isPressed());
            key(c, mc, "LMB " + ModuleManager.cps(ModuleManager.LMB), 0, 44, 31, 18, o.attackKey.isPressed());
            key(c, mc, "RMB " + ModuleManager.cps(ModuleManager.RMB), 33, 44, 31, 18, o.useKey.isPressed());
        }
    }

    public static class Armor extends HudModule {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        public Armor() { super("armor", "Armor HUD", "แสดงเกราะ + ความทนทาน", Category.HUD, true, 6, 160); }

        @Override protected void content(DrawContext c, MinecraftClient mc, boolean edit) {
            if (mc.player == null) return;
            w = 52; h = 4 * 18 + 2;
            Theme.panel(c, 0, 0, w, h, s.opacity);
            int y = 2;
            for (EquipmentSlot sl : SLOTS) {
                ItemStack st = mc.player.getEquippedStack(sl);
                if (!st.isEmpty()) {
                    c.drawItem(st, 5, y);
                    if (st.isDamageable()) {
                        int pct = (st.getMaxDamage() - st.getDamage()) * 100 / st.getMaxDamage();
                        int col = pct > 50 ? 0xFFFFFFFF : pct > 20 ? 0xFFFFC107 : 0xFFFF5555;
                        c.drawText(mc.textRenderer, pct + "%", 24, y + 4, col, true);
                    }
                }
                y += 18;
            }
        }
    }

    public static class Potion extends HudModule {
        public Potion() { super("potion", "Potion HUD", "แสดงสถานะ Potion", Category.HUD, true, 6, 240); }
        @Override protected List<String> lines(MinecraftClient mc) {
            List<String> out = new ArrayList<>();
            if (mc.player == null) return out;
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                String t;
                if (e.isInfinite()) t = "**:**";
                else { int sec = e.getDuration() / 20; t = (sec / 60) + ":" + String.format("%02d", sec % 60); }
                out.add(e.getEffectType().value().getName().getString() + " " + (e.getAmplifier() + 1) + "  " + t);
            }
            return out;
        }
    }

    public static class Crosshair extends Module {
        public Crosshair() {
            super("crosshair", "Custom Crosshair", "ปรับแต่งเป้าเล็ง (ขนาด/ช่องว่าง/ความหนา)", Category.VISUAL, false, 0, 0);
            with(new Setting("size", "Size", 2, 14, 5), new Setting("gap", "Gap", 0, 8, 2), new Setting("thick", "Thickness", 1, 3, 1));
        }
        @Override public void render(DrawContext c, MinecraftClient mc) {
            int cx = c.getScaledWindowWidth() / 2, cy = c.getScaledWindowHeight() / 2;
            int size = (int) val("size", 5), gap = (int) val("gap", 2), t = (int) val("thick", 1);
            int col = Theme.argb(1f, Theme.PURPLE), out = 0xFF000000;
            int[][] arms = {{-gap - size, 0, -gap, 0}, {gap, 0, gap + size, 0}, {0, -gap - size, 0, -gap}, {0, gap, 0, gap + size}};
            for (int[] a : arms) {
                int x1 = cx + a[0], y1 = cy + a[1], x2 = cx + a[2], y2 = cy + a[3];
                if (a[1] == 0 && a[3] == 0) { y1 -= t / 2; y2 = y1 + t; } else { x1 -= t / 2; x2 = x1 + t; }
                c.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, out);
                c.fill(x1, y1, x2, y2, col);
            }
        }
    }
    private Special() {}
}
