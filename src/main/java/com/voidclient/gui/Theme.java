package com.voidclient.gui;

import net.minecraft.client.gui.DrawContext;

public final class Theme {
    public static final int PURPLE = 0xA855F7;   // Neon Purple (highlight)
    public static final int DEEP = 0x6D28D9;     // Dark Purple (primary)
    public static final int BG = 0x0E0E10;
    public static final int CARD = 0x161618;
    public static final int CARD2 = 0x1E1E22;
    public static final int MUTED = 0x9CA3AF;

    public static int argb(float a, int rgb) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** สี่เหลี่ยมมุมมน (วาดเป็นแถว ถูกกับ FPS) */
    public static void rrect(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        c.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** แผง HUD: glow ม่วง + พื้นดำโปร่ง + แถบ accent ซ้าย */
    public static void panel(DrawContext c, int x, int y, int w, int h, float opacity) {
        rrect(c, x - 2, y - 2, x + w + 2, y + h + 2, 4, argb(opacity * 0.14f, PURPLE));
        rrect(c, x, y, x + w, y + h, 3, argb(opacity, BG));
        c.fill(x, y + 2, x + 2, y + h - 2, argb(1f, PURPLE));
    }

    private Theme() {}
}
