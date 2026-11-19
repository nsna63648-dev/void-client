package com.voidclient.gui;

import net.minecraft.client.gui.DrawContext;

public final class Theme {
    public static final int PURPLE = 0xA855F7;
    public static final int PURPLE_LIGHT = 0xC084FC;
    public static final int DEEP = 0x6D28D9;
    public static final int BG = 0x0B0B0F;
    public static final int CARD = 0x13131A;
    public static final int CARD2 = 0x1B1B24;
    public static final int BORDER = 0x2A2A38;
    public static final int MUTED = 0x9CA3AF;

    public static int argb(float a, int rgb) {
        int al = (int) (Math.max(0f, Math.min(1f, a)) * 255f);
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    public static void rrect(DrawContext c, int x1, int y1, int x2, int y2, int r, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        r = Math.max(0, Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));
        c.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    public static void strokeCard(DrawContext c, int x1, int y1, int x2, int y2, int r, int fillColor, int borderColor) {
        rrect(c, x1 - 1, y1 - 1, x2 + 1, y2 + 1, r + 1, borderColor);
        rrect(c, x1, y1, x2, y2, r, fillColor);
    }

    public static void panel(DrawContext c, int x, int y, int w, int h, float opacity) {
        rrect(c, x - 2, y - 2, x + w + 2, y + h + 2, 5, argb(opacity * 0.18f, PURPLE));
        strokeCard(c, x, y, x + w, y + h, 4, argb(opacity, BG), argb(opacity * 0.45f, PURPLE));
        c.fill(x, y + 3, x + 2, y + h - 3, argb(1f, PURPLE_LIGHT));
    }

    private Theme() {}
}

