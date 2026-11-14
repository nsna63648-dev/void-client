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

/**
 * หน้าต่างหลักของ VOID CLIENT — จัดวางตามภาพ StarZ Launcher
 * พิกัดทุกอย่างอ้างอิงจากภาพต้นฉบับ 1920x1080 (หน้าต่าง 508..1412 x 258..822) แล้วสเกลตามจอ
 */
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

    // ---------- coordinate helpers (design space -> screen) ----------
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
        Theme.rrect(c, X(x1), Y(y1), X(x2), Y(y2), Math.max(3, Math.round(14 * k)), Theme.argb(a * cur, rgb));
    }

    private void btn(DrawContext c, float x1, float y1, float x2, float y2, String label, float px,
                     boolean primary, int mx, int my, Runnable run) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(2, Math.round(10 * k));
        if (hov) Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, rad + 2, Theme.argb(0.30f * cur, Theme.PURPLE)); // hover glow
        if (primary) {
            Theme.rrect(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0xA855F7 : 0x7E22CE));
            c.fillGradient(a + rad / 2, b + 1, d - rad / 2, e - 1, Theme.argb(cur, hov ? 0xB36BFA : 0x9D26D9), Theme.argb(cur, 0x5B3FD0));
        } else {
            Theme.rrect(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0x2A2A30 : Theme.CARD2));
        }
        txt(c, label, (x1 + x2) / 2f - tw(label, px) / 2f, (y1 + y2) / 2f - px / 2f, px, 0xFFFFFF, 1f);
        hits.add(new Hit(a, b, d, e, run));
    }

    private void slider(DrawContext c, float x1, float y1, float x2, String label, float val, float min, float max, Consumer<Float> set) {
        txt(c, String.format("%s  %.2f", label, val), x1, y1, 13, 0xFFFFFF, 0.9f);
        int a = X(x1), b = Y(y1 + 24), d = X(x2), e = Y(y1 + 32);
        Theme.rrect(c, a, b, d, e, 2, Theme.argb(cur, 0x2A2A30));
        float t = (val - min) / (max - min);
        Theme.rrect(c, a, b, a + Math.max(2, Math.round((d - a) * t)), e, 2, Theme.argb(cur, Theme.PURPLE));
        sliders.add(new Sl(a, b - 4, d, e + 4, min, max, set));
    }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    // ---------- render ----------
    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear(); sliders.clear();
        long now = System.currentTimeMillis();
        boolean anim = !ModuleManager.animationsOff();
        float g = anim ? ease(Math.min(1f, (now - openAt) / 260f)) : 1f;
        float pg = anim ? ease(Math.min(1f, (now - pageAt) / 200f)) : 1f;
        oy = Math.round((1f - g) * 12);

        c.fill(0, 0, width, height, Theme.argb(0.55f * g, 0x000000));

        int pw = Math.min(width - 16, 640);
        k = pw / 904f;
        int ph = Math.round(564 * k);
        left = (width - pw) / 2; top = (height - ph) / 2;

        // glow + window
        cur = g;
        Theme.rrect(c, left - 3, top + oy - 3, left + pw + 3, top + ph + oy + 3, Math.round(24 * k) + 3, Theme.argb(0.20f * g, Theme.PURPLE));
        card(c, 508, 258, 1412, 822, 0x0B0B0D, 0.97f);

        // logo card
        card(c, 533, 283, 782, 374, Theme.CARD, 1f);
        card(c, 553, 303, 607, 357, 0x1A1A1E, 1f);
        txt(c, "V", 566, 311, 40, Theme.PURPLE, 1f);
        txt(c, "VOID", 631, 300, 30, Theme.PURPLE, 1f);
        txt(c, "CLIENT v1", 631, 334, 20, 0xFFFFFF, 1f);

        // title card + window controls
        card(c, 821, 283, 1387, 374, Theme.CARD, 1f);
        txt(c, switch (page) { case HOME -> "Home"; case FEATURES -> "Features"; case FPS -> "FPS Boost"; case CPVP -> "CPvP"; case SETTINGS -> "Settings"; },
                849, 300, 50, 0xFFFFFF, 1f);
        btn(c, 1297, 314, 1325, 342, "-", 22, false, mx, my, this::close);
        btn(c, 1336, 314, 1364, 342, "x", 20, false, mx, my, this::close);

        // sidebar
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
                Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, 8, Theme.argb(0.30f * cur, Theme.PURPLE));
                Theme.rrect(c, a, b, d, e, Math.max(3, Math.round(10 * k)), Theme.argb(cur, 0x7C3AED));
            } else if (hov) Theme.rrect(c, a, b, d, e, Math.max(3, Math.round(10 * k)), Theme.argb(cur, 0x232328));
            txt(c, ICONS[i], 560 - tw(ICONS[i], 24) / 2f, cy[i] - 12, 24, sel ? 0xFFFFFF : 0xB8B8C0, 1f);
            hits.add(new Hit(a, b, d, e, () -> {
                if (p == null) { client.setScreen(new HudEditScreen()); return; }
                page = p; pageAt = System.currentTimeMillis(); scroll = 0; selected = null; searchFocus = false;
            }));
        }

        // content
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

        card(c, 653, 420, 1095, 602, 0x1B1B1E, 1f);
        txt(c, greet + ", " + name + " !", 680, 440, 30, 0xFFFFFF, 1f);
        boolean inWorld = client.world != null;
        btn(c, 680, 490, 1068, 548, inWorld ? "RESUME" : "PLAY", 26, true, mx, my, () -> {
            if (inWorld) close(); else client.setScreen(new SelectWorldScreen(this));
        });
        String mcv = "Minecraft 1.21.11 - Fabric";
        txt(c, mcv, 874 - tw(mcv, 14) / 2f, 570, 14, 0xFFFFFF, 0.9f);

        card(c, 1120, 420, 1363, 602, 0x1B1B1E, 1f);
        txt(c, "Client Version", 1135, 438, 20, 0xFFFFFF, 1f);
        txt(c, "v1.0.0", 1135, 466, 40, 0xFFFFFF, 1f);
        btn(c, 1135, 553, 1293, 586, "Manage Features", 13, true, mx, my, () -> { page = Page.FEATURES; pageAt = System.currentTimeMillis(); });

        card(c, 653, 627, 1363, 770, 0x1B1B1E, 1f);
        card(c, 675, 657, 757, 747, 0x241B3A, 1f);
        txt(c, "VC", 716 - tw("VC", 30) / 2f, 686, 30, Theme.PURPLE, 1f);
        txt(c, "Join our community !", 781, 650, 30, 0xFFFFFF, 1f);
        txt(c, "For support, presets and updates from the VOID community.", 781, 690, 16, 0xFFFFFF, 0.9f);
        String url = VoidConfig.data.discordUrl;
        btn(c, 781, 722, 851, 751, "Join >", 13, true, mx, my, () -> { if (url != null && !url.isBlank()) Util.getOperatingSystem().open(url); });
        if (url == null || url.isBlank()) txt(c, "(ใส่ discordUrl ใน config/voidclient.json)", 866, 730, 12, 0x9CA3AF, 1f);
    }

    private List<Module> filtered(Category[] cats) {
        List<Module> out = new ArrayList<>();
        String q = search.toLowerCase();
        for (Module m : ModuleManager.ALL) {
            if (cats != null && !List.of(cats).contains(m.cat)) continue;
            if (!q.isEmpty() && !(m.name.toLowerCase().contains(q) || m.cat.label.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) continue;
            out.add(m);
        }
        return out;
    }

    private void list(DrawContext c, int mx, int my, Category[] cats) {
        // toolbar
        if (page == Page.FEATURES) {
            card(c, 650, 412, 1000, 452, searchFocus ? 0x2A2140 : 0x1E1E22, 1f);
            String t = search.isEmpty() ? "Search features..." : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
            txt(c, t, 664, 422, 18, search.isEmpty() ? 0x9CA3AF : 0xFFFFFF, 1f);
            hits.add(new Hit(X(650), Y(412), X(1000), Y(452), () -> searchFocus = true));
        } else if (page == Page.FPS) {
            String[][] p = {{"LOW", "Low"}, {"BALANCED", "Balanced"}, {"HIGH", "High Performance"}};
            float x = 650;
            for (String[] pr : p) {
                float w = pr[0].equals("HIGH") ? 190 : 130;
                btn(c, x, 412, x + w, 452, pr[1], 16, pr[0].equals(VoidConfig.data.preset), mx, my, () -> FpsPresets.apply(pr[0]));
                x += w + 12;
            }
        } else {
            btn(c, 650, 412, 840, 452, "Crystal PvP Preset", 16, false, mx, my, () -> ModuleManager.cpvpPreset(true));
            btn(c, 852, 412, 1042, 452, "Anchor PvP Preset", 16, false, mx, my, () -> ModuleManager.cpvpPreset(false));
        }

        List<Module> mods = filtered(cats);
        final float rowH = 52, y0 = 464, y1 = 720;
        float max = Math.max(0, mods.size() * rowH - (y1 - y0));
        scroll = Math.max(0, Math.min(max, scroll));
        c.enableScissor(X(640), Y(y0), X(1377), Y(y1));
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float ry = y0 + i * rowH - scroll;
            if (ry + rowH < y0 || ry > y1) continue;
            boolean sel = m == selected;
            card(c, 650, ry, 1365, ry + 46, sel ? 0x2A2140 : 0x1E1E22, 1f);
            card(c, 662, ry + 7, 694, ry + 39, 0x2E1F52, 1f);
            txt(c, m.cat.icon, 678 - tw(m.cat.icon, 18) / 2f, ry + 14, 18, Theme.PURPLE, 1f);
            txt(c, m.name, 708, ry + 6, 18, 0xFFFFFF, 1f);
            txt(c, m.desc, 708, ry + 28, 12, 0x9CA3AF, 1f);
            if (ry >= y0 - 10 && ry + 46 <= y1 + 10) {
                hits.add(new Hit(X(650), Y(ry), X(1365), Y(ry + 46), () -> selected = m));
            }
            // toggle pill
            int a = X(1290), b = Y(ry + 13), d = X(1346), e = Y(ry + 33);
            Theme.rrect(c, a, b, d, e, (e - b) / 2, Theme.argb(cur, m.enabled() ? 0x7C3AED : 0x3A3A42));
            int kn = e - b - 4;
            int kx = m.enabled() ? d - kn - 2 : a + 2;
            Theme.rrect(c, kx, b + 2, kx + kn, b + 2 + kn, kn / 2, Theme.argb(cur, 0xFFFFFF));
            if (ry >= y0 - 10 && ry + 46 <= y1 + 10) hits.add(new Hit(a, b, d, e, () -> m.setEnabled(!m.enabled())));
        }
        c.disableScissor();
        if (mods.isEmpty()) txt(c, "No features found", 660, 480, 16, 0x9CA3AF, 1f);

        // detail strip
        card(c, 650, 728, 1365, 788, 0x1B1B1E, 1f);
        if (selected == null) {
            txt(c, "Select a feature to adjust size / opacity / options", 668, 752, 14, 0x9CA3AF, 1f);
        } else {
            List<Runnable> dummy = new ArrayList<>();
            int col = 0; float[] xs = {668, 908, 1148};
            final Module m = selected;
            if (m.isHud() && col < 3) {
                slider(c, xs[col], 734, xs[col] + 210, "Scale", m.s.scale, 0.5f, 2f, v -> m.s.scale = v); col++;
            }
            if (m.isHud() && col < 3) {
                slider(c, xs[col], 734, xs[col] + 210, "Opacity", m.s.opacity, 0.2f, 1f, v -> m.s.opacity = v); col++;
            }
            for (Setting st : m.settings) {
                if (col >= 3) break;
                slider(c, xs[col], 734, xs[col] + 210, st.label(), m.val(st.key(), st.def()), st.min(), st.max(), v -> m.set(st.key(), v)); col++;
            }
            if (col == 0) txt(c, m.name + " - no extra options", 668, 752, 14, 0x9CA3AF, 1f);
        }
    }

    private void settings(DrawContext c, int mx, int my) {
        btn(c, 660, 420, 940, 466, "Edit HUD Layout", 18, true, mx, my, () -> client.setScreen(new HudEditScreen()));
        btn(c, 960, 420, 1240, 466, "Reset All Settings", 18, false, mx, my, () -> { ModuleManager.resetAll(); selected = null; });
        txt(c, "Menu key: RIGHT SHIFT  (เปลี่ยนได้ใน Options > Controls > VOID CLIENT)", 662, 490, 16, 0xFFFFFF, 1f);
        txt(c, "Minecraft 1.21.11  |  Fabric  |  Java 21", 662, 520, 16, 0x9CA3AF, 1f);
        txt(c, "Sodium: " + (ModuleManager.sodiumLoaded() ? "detected" : "not installed (แนะนำให้ติดตั้ง)"), 662, 548, 16, 0x9CA3AF, 1f);
        txt(c, "Config: config/voidclient.json (บันทึกอัตโนมัติ)", 662, 576, 16, 0x9CA3AF, 1f);
        txt(c, "HUD color: Neon Purple  |  ไม่มีระบบอัตโนมัติ / ไม่แก้ไฟล์เกมต้นฉบับ", 662, 604, 16, 0x9CA3AF, 1f);
    }

    // ---------- input (1.21.11 API: Click / KeyInput / CharInput) ----------
    @Override public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        searchFocus = false;
        for (Sl s : sliders) if (mx >= s.x1 && mx <= s.x2 && my >= s.y1 && my <= s.y2) { active = s; dragSlider(mx); return true; }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x1 && mx <= h.x2 && my >= h.y1 && my <= h.y2) { h.run.run(); return true; }
        }
        return super.mouseClicked(click, doubled);
    }

    private void dragSlider(double mx) {
        if (active == null) return;
        float t = (float) ((mx - active.x1) / (active.x2 - active.x1));
        t = Math.max(0f, Math.min(1f, t));
        active.set.accept(active.min + t * (active.max - active.min));
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (active != null) { dragSlider(click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override public boolean mouseReleased(Click click) {
        if (active != null) { active = null; VoidConfig.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        scroll -= (float) v * 30f;
        return true;
    }

    @Override public boolean charTyped(CharInput input) {
        if (searchFocus && page == Page.FEATURES && search.length() < 24) {
            search += new String(Character.toChars(input.codepoint()));
            scroll = 0; return true;
        }
        return super.charTyped(input);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (searchFocus) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); scroll = 0; return true; }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE || input.key() == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
        }
        return super.keyPressed(input);
    }
}
