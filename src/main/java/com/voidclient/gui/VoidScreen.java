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
    private enum Page { HOME, FEATURES, FPS, CPVP, SETTINGS }
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Sl(int x1, int y1, int x2, int y2, float min, float max, Consumer<Float> set) {}

    private static final String[] ICONS = {"H", "F", "B", "C", "E", "S"};
    private Page page = Page.HOME;
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
        int rad = Math.max(3, Math.round(14 * k));
        Theme.strokeCard(c, X(x1), Y(y1), X(x2), Y(y2), rad, Theme.argb(a * cur, rgb), Theme.argb(0.55f * cur, Theme.BORDER));
    }

    private void glowCard(DrawContext c, float x1, float y1, float x2, float y2, int rgb, int glowRgb) {
        int rad = Math.max(3, Math.round(14 * k));
        Theme.strokeCard(c, X(x1), Y(y1), X(x2), Y(y2), rad, Theme.argb(cur, rgb), Theme.argb(0.75f * cur, glowRgb));
    }

    private void btn(DrawContext c, float x1, float y1, float x2, float y2, String label, float px,
                     boolean primary, int mx, int my, Runnable run) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(2, Math.round(10 * k));
        if (hov) Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, rad + 2, Theme.argb(0.35f * cur, Theme.PURPLE));
        if (primary) {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0xA855F7 : 0x7E22CE), Theme.argb(0.8f * cur, Theme.PURPLE_LIGHT));
            c.fillGradient(a + rad / 2, b + 1, d - rad / 2, e - 1, Theme.argb(cur, hov ? 0xB36BFA : 0x9D26D9), Theme.argb(cur, 0x5B3FD0));
        } else {
            Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0x262634 : Theme.CARD2), Theme.argb(0.6f * cur, hov ? Theme.PURPLE : Theme.BORDER));
        }
        txt(c, label, (x1 + x2) / 2f - tw(label, px) / 2f, (y1 + y2) / 2f - px / 2f, px, 0xFFFFFF, 1f);
        hits.add(new Hit(a, b, d, e, run));
    }

    private void slider(DrawContext c, float x1, float y1, float x2, String label, float val, float min, float max, Consumer<Float> set) {
        txt(c, String.format("%s  %.2f", label, val), x1, y1, 13, 0xFFFFFF, 0.95f);
        int a = X(x1), b = Y(y1 + 24), d = X(x2), e = Y(y1 + 32);
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
        float g = anim ? ease(Math.min(1f, (now - openAt) / 260f)) : 1f;
        float pg = anim ? ease(Math.min(1f, (now - pageAt) / 200f)) : 1f;
        oy = Math.round((1f - g) * 14);

        c.fillGradient(0, 0, width, height, Theme.argb(0.62f * g, 0x05050A), Theme.argb(0.72f * g, 0x0D0818));

        int pw = Math.min(width - 16, 650);
        k = pw / 904f;
        int ph = Math.round(564 * k);
        left = (width - pw) / 2; top = (height - ph) / 2;

        cur = g;
        Theme.rrect(c, left - 4, top + oy - 4, left + pw + 4, top + ph + oy + 4, Math.round(24 * k) + 4, Theme.argb(0.24f * g, Theme.PURPLE));
        Theme.strokeCard(c, X(508), Y(258), X(1412), Y(822), Math.max(4, Math.round(18 * k)), Theme.argb(0.96f * g, 0x09090E), Theme.argb(0.65f * g, Theme.PURPLE));

        // Logo card
        card(c, 533, 283, 782, 374, Theme.CARD, 1f);
        glowCard(c, 553, 303, 607, 357, 0x1A1429, Theme.PURPLE);
        txt(c, "V", 566, 311, 40, Theme.PURPLE_LIGHT, 1f);
        txt(c, "VOID", 631, 300, 30, Theme.PURPLE_LIGHT, 1f);
        txt(c, "CLIENT v1", 631, 334, 20, 0xFFFFFF, 1f);

        // Title card + window controls
        card(c, 821, 283, 1387, 374, Theme.CARD, 1f);
        txt(c, switch (page) { case HOME -> "Home"; case FEATURES -> "Features"; case FPS -> "FPS Boost"; case CPVP -> "CPvP"; case SETTINGS -> "Settings"; },
                849, 300, 48, 0xFFFFFF, 1f);
        btn(c, 1297, 314, 1325, 342, "-", 22, false, mx, my, this::close);
        btn(c, 1336, 314, 1364, 342, "x", 20, false, mx, my, this::close);

        // Sidebar
        card(c, 533, 396, 588, 797, Theme.CARD, 1f);
        Page[] pages = {Page.HOME, Page.FEATURES, Page.FPS, Page.CPVP, null, Page.SETTINGS};
        int[] cy = {433, 498, 563, 628, 693, 758};
        for (int i = 0; i < 6; i++) {
            final Page p = pages[i];
            boolean sel = p == page;
            float y1 = cy[i] - 22, y2 = cy[i] + 22;
            int a = X(538), b = Y(y1), d = X(583), e = Y(y2);
            boolean hov = mx >= a && mx <= d && my >= b && my <= e;
            if (sel) {
                Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, 10, Theme.argb(0.35f * cur, Theme.PURPLE));
                Theme.strokeCard(c, a, b, d, e, Math.max(3, Math.round(10 * k)), Theme.argb(cur, 0x7C3AED), Theme.argb(0.9f * cur, Theme.PURPLE_LIGHT));
            } else if (hov) {
                Theme.strokeCard(c, a, b, d, e, Math.max(3, Math.round(10 * k)), Theme.argb(cur, 0x22222E), Theme.argb(0.5f * cur, Theme.BORDER));
            }
            txt(c, ICONS[i], 560 - tw(ICONS[i], 24) / 2f, cy[i] - 12, 24, sel ? 0xFFFFFF : 0xB8B8C0, 1f);
            hits.add(new Hit(a, b, d, e, () -> {
                if (p == null) { client.setScreen(new HudEditScreen()); return; }
                page = p; pageAt = System.currentTimeMillis(); scroll = 0; selected = null; searchFocus = false;
            }));
        }

        // Content container
        card(c, 630, 396, 1387, 797, Theme.CARD, 1f);
        cur = g * pg;
        switch (page) {
            case HOME -> home(c, mx, my);
            case FEATURES -> list(c, mx, my, null);
            case FPS -> list(c, mx, my, new Category[]{Category.PERFORMANCE});
            case CPVP -> list(c, mx, my, new Category[]{Category.CRYSTAL, Category.ANCHOR, Category.COMBAT});
            case SETTINGS -> settings(c, mx, my);
        }
    }

    private void home(DrawContext c, int mx, int my) {
        int h = LocalTime.now().getHour();
        String greet = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = client.getSession() != null ? client.getSession().getUsername() : "Player";

        card(c, 653, 420, 1095, 602, Theme.CARD2, 1f);
        txt(c, greet + ", " + name + " !", 680, 440, 28, 0xFFFFFF, 1f);
        boolean inWorld = client.world != null;
        btn(c, 680, 490, 1068, 548, inWorld ? "RESUME" : "PLAY", 26, true, mx, my, () -> {
            if (inWorld) close(); else client.setScreen(new SelectWorldScreen(this));
        });
        String mcv = "Minecraft 1.21.11 - Fabric";
        txt(c, mcv, 874 - tw(mcv, 14) / 2f, 570, 14, Theme.MUTED, 1f);

        card(c, 1120, 420, 1363, 602, Theme.CARD2, 1f);
        txt(c, "Client Version", 1135, 438, 18, Theme.MUTED, 1f);
        txt(c, "v1.0.0", 1135, 466, 40, 0xFFFFFF, 1f);
        btn(c, 1135, 553, 1348, 586, "Manage Features", 14, true, mx, my, () -> { page = Page.FEATURES; pageAt = System.currentTimeMillis(); });

        card(c, 653, 627, 1363, 770, Theme.CARD2, 1f);
        glowCard(c, 675, 657, 757, 747, 0x241B3A, Theme.PURPLE);
        txt(c, "VC", 716 - tw("VC", 30) / 2f, 686, 30, Theme.PURPLE_LIGHT, 1f);
        txt(c, "Join our community !", 781, 650, 28, 0xFFFFFF, 1f);
        txt(c, "For support, presets and updates from the VOID community.", 781, 690, 15, Theme.MUTED, 1f);
        String url = VoidConfig.data.discordUrl;
        btn(c, 781, 722, 855, 751, "Join >", 13, true, mx, my, () -> { if (url != null && !url.isBlank()) Util.getOperatingSystem().open(url); });
