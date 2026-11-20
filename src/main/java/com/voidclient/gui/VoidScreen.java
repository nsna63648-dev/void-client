package com.voidclient.gui;

import com.voidclient.config.VoidConfig;
import com.voidclient.module.*;
import com.voidclient.module.Module;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class VoidScreen extends Screen {
    private enum Nav { HOME, ALL_MODS, SETTINGS }
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Sl(int x1, int y1, int x2, int y2, float min, float max, Consumer<Float> set) {}

    private Nav nav = Nav.ALL_MODS;
    private Category filterCat = null; // null = All Categories
    private final long openAt = System.currentTimeMillis();
    private long pageAt = openAt;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Sl> sliders = new ArrayList<>();
    private Sl active;
    private String search = "";
    private boolean searchFocus;
    private Module selected;
    private float scroll;

    private int left, top;
    private float k, cur = 1f;
    private int oy;

    public VoidScreen() { super(Text.literal("VOID CLIENT")); }

    @Override public boolean shouldPause() { return false; }

    private int X(float d) { return left + Math.round((d - 508f) * k); }
    private int Y(float d) { return top + oy + Math.round((d - 258f) * k); }
    private float tw(String s, float px) { return textRenderer.getWidth(s) * px / 9f; }

    private void txt(DrawContext c, String s, float dx, float dy, float px, int rgb, float a) {
        float sc = px * k / 9f;
        var m = c.getMatrices();
        m.pushMatrix(); m.translate((float) X(dx), (float) Y(dy)); m.scale(sc, sc);
        c.drawText(textRenderer, s, 0, 0, Theme.argb(a * cur, rgb), false);
        m.popMatrix();
    }

    private void card(DrawContext c, float x1, float y1, float x2, float y2, int rgb, float a) {
        int rad = Math.max(3, Math.round(12 * k));
        Theme.strokeCard(c, X(x1), Y(y1), X(x2), Y(y2), rad, Theme.argb(a * cur, rgb), Theme.argb(0.55f * cur, Theme.BORDER));
    }

    private void glowCard(DrawContext c, float x1, float y1, float x2, float y2, int rgb, int glowRgb) {
        int rad = Math.max(3, Math.round(12 * k));
        Theme.strokeCard(c, X(x1), Y(y1), X(x2), Y(y2), rad, Theme.argb(cur, rgb), Theme.argb(0.8f * cur, glowRgb));
    }

    private void btn(DrawContext c, float x1, float y1, float x2, float y2, String label, float px,
                     boolean primary, int mx, int my, Runnable run) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(2, Math.round(8 * k));
        if (hov) Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, rad + 2, Theme.argb(0.35f * cur, Theme.PURPLE));
        if (primary) {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0xA855F7 : 0x7E22CE), Theme.argb(0.85f * cur, Theme.PURPLE_LIGHT));
            c.fillGradient(a + rad / 2, b + 1, d - rad / 2, e - 1, Theme.argb(cur, hov ? 0xB36BFA : 0x9D26D9), Theme.argb(cur, 0x5B3FD0));
        } else {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0x262634 : Theme.CARD2), Theme.argb(0.6f * cur, hov ? Theme.PURPLE : Theme.BORDER));
        }
        txt(c, label, (x1 + x2) / 2f - tw(label, px) / 2f, (y1 + y2) / 2f - px / 2f, px, 0xFFFFFF, 1f);
        hits.add(new Hit(a, b, d, e, run));
    }

    private void slider(DrawContext c, float x1, float y1, float x2, String label, float val, float min, float max, Consumer<Float> set) {
        txt(c, String.format("%s  %.2f", label, val), x1, y1, 13, 0xFFFFFF, 0.95f);
        int a = X(x1), b = Y(y1 + 22), d = X(x2), e = Y(y1 + 29);
        Theme.rrect(c, a, b, d, e, 3, Theme.argb(cur, 0x262634));
        float t = Math.max(0f, Math.min(1f, (val - min) / (max - min)));
        int fillW = Math.max(4, Math.round((d - a) * t));
        Theme.rrect(c, a, b, a + fillW, e, 3, Theme.argb(cur, Theme.PURPLE));
        int kx = a + fillW;
        int ky = (b + e) / 2;
        Theme.rrect(c, kx - 4, ky - 5, kx + 4, ky + 5, 4, Theme.argb(cur, 0xFFFFFF));
        sliders.add(new Sl(a, b - 6, d, e + 6, min, max, set));
    }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear(); sliders.clear();
        long now = System.currentTimeMillis();
        boolean anim = !ModuleManager.animationsOff();
        float g = anim ? ease(Math.min(1f, (now - openAt) / 240f)) : 1f;
        float pg = anim ? ease(Math.min(1f, (now - pageAt) / 180f)) : 1f;
        oy = Math.round((1f - g) * 12);

        // Dark backdrop
        c.fillGradient(0, 0, width, height, Theme.argb(0.55f * g, 0x05050A), Theme.argb(0.68f * g, 0x0D0818));

        int pw = Math.min(width - 20, 740);
        k = pw / 904f;
        int ph = Math.round(564 * k);
        left = (width - pw) / 2; top = (height - ph) / 2;
        cur = g;

        // ===== LEFT SIDEBAR (LabyMod Categories Style) =====
        Theme.rrect(c, X(506), Y(318), X(682), Y(762), Math.round(16 * k), Theme.argb(0.22f * g, Theme.PURPLE));
        Theme.strokeCard(c, X(508), Y(320), X(680), Y(760), Math.max(4, Math.round(14 * k)), Theme.argb(0.95f * g, 0x0B0B12), Theme.argb(0.6f * g, Theme.PURPLE));

        // Logo header above sidebar
        glowCard(c, 535, 266, 577, 308, 0x1A1429, Theme.PURPLE);
        txt(c, "V", 547, 273, 28, Theme.PURPLE_LIGHT, 1f);
        txt(c, "VOID", 588, 276, 26, 0xFFFFFF, 1f);

        txt(c, "Categories", 594 - tw("Categories", 14) / 2f, 334, 14, Theme.MUTED, 1f);

        float sideY = 358;
        float sideH = 29;
        float sideGap = 6;

        // Home Tab
        sideY = drawSideItem(c, mx, my, sideY, sideH, "Home", nav == Nav.HOME, () -> {
            nav = Nav.HOME; pageAt = System.currentTimeMillis(); selected = null; searchFocus = false;
        }) + sideGap;

        // All Mods Tab
        sideY = drawSideItem(c, mx, my, sideY, sideH, "All Mods", nav == Nav.ALL_MODS && filterCat == null, () -> {
            nav = Nav.ALL_MODS; filterCat = null; pageAt = System.currentTimeMillis(); scroll = 0; selected = null;
        }) + sideGap;

        // Dynamic Categories from Category.values()
        for (Category cat : Category.values()) {
            boolean isSel = (nav == Nav.ALL_MODS && filterCat == cat);
            sideY = drawSideItem(c, mx, my, sideY, sideH, cat.label, isSel, () -> {
                nav = Nav.ALL_MODS; filterCat = cat; pageAt = System.currentTimeMillis(); scroll = 0; selected = null;
            }) + sideGap;
        }

        // Bottom Sidebar Buttons (HUD & Settings)
        drawSideItem(c, mx, my, 684, sideH, "Edit HUD", false, () -> client.setScreen(new HudEditScreen()));
        drawSideItem(c, mx, my, 719, sideH, "Settings", nav == Nav.SETTINGS, () -> {
            nav = Nav.SETTINGS; pageAt = System.currentTimeMillis(); selected = null; searchFocus = false;
        });

        // ===== MAIN RIGHT PANEL (LabyMod Grid Container) =====
        Theme.rrect(c, X(694), Y(256), X(1414), Y(824), Math.round(18 * k), Theme.argb(0.22f * g, Theme.PURPLE));
        Theme.strokeCard(c, X(696), Y(258), X(1412), Y(822), Math.max(4, Math.round(16 * k)), Theme.argb(0.96f * g, 0x09090F), Theme.argb(0.65f * g, Theme.PURPLE));

        // Top Bar: Search Input + Quick Action Buttons
        if (searchFocus) glowCard(c, 714, 274, 1110, 312, 0x201934, Theme.PURPLE);
        else card(c, 714, 274, 1110, 312, Theme.CARD2, 1f);
        String sText = search.isEmpty() ? "Search mods..." : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        txt(c, sText, 730, 285, 16, search.isEmpty() ? Theme.MUTED : 0xFFFFFF, 1f);
        hits.add(new Hit(X(714), Y(274), X(1110), Y(312), () -> {
            nav = Nav.ALL_MODS; searchFocus = true;
        }));

        // Quick HUD & Close buttons on top right
        btn(c, 1122, 274, 1280, 312, "Edit HUD Widgets", 13, true, mx, my, () -> client.setScreen(new HudEditScreen()));
        btn(c, 1290, 274, 1352, 312, "Home", 13, nav == Nav.HOME, mx, my, () -> { nav = Nav.HOME; pageAt = System.currentTimeMillis(); });
        btn(c, 1360, 274, 1394, 312, "X", 15, false, mx, my, this::close);

        cur = g * pg;
        if (nav == Nav.HOME) {
            home(c, mx, my);
        } else if (nav == Nav.SETTINGS) {
            settings(c, mx, my);
        } else {
            gridMods(c, mx, my);
        }
    }

    private float drawSideItem(DrawContext c, int mx, int my, float y1, float h, String label, boolean activeItem, Runnable onClick) {
        float x1 = 520, x2 = 668, y2 = y1 + h;
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(3, Math.round(8 * k));

        if (activeItem) {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, 0x261B42), Theme.argb(0.95f * cur, Theme.PURPLE_LIGHT));
        } else if (hov) {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, 0x1E1E2A), Theme.argb(0.65f * cur, Theme.PURPLE));
        } else {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, Theme.CARD2), Theme.argb(0.45f * cur, Theme.BORDER));
        }

        txt(c, label, (x1 + x2) / 2f - tw(label, 14) / 2f, y1 + (h - 14) / 2f, 14, activeItem ? 0xFFFFFF : 0xD0D0DC, 1f);
        hits.add(new Hit(a, b, d, e, onClick));
        return y2;
    }

    private List<Module> filtered() {
        List<Module> out = new ArrayList<>();
