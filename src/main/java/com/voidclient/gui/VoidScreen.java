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
        List<String> icons = List.of("H", "F", "B", "C", "E", "S");
        List<Page> pages = new ArrayList<>();
        pages.add(Page.HOME);
        pages.add(Page.FEATURES);
        pages.add(Page.FPS);
        pages.add(Page.CPVP);
        pages.add(null);
        pages.add(Page.SETTINGS);
        List<Integer> cy = List.of(433, 498, 563, 628, 693, 758);

        for (int i = 0; i < 6; i++) {
            final Page p = pages.get(i);
            int centerY = cy.get(i);
            String icon = icons.get(i);
            boolean sel = p == page;
            float y1 = centerY - 22, y2 = centerY + 22;
            int a = X(538), b = Y(y1), d = X(583), e = Y(y2);
            boolean hov = mx >= a && mx <= d && my >= b && my <= e;
            if (sel) {
                Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, 10, Theme.argb(0.35f * cur, Theme.PURPLE));
                Theme.strokeCard(c, a, b, d, e, Math.max(3, Math.round(10 * k)), Theme.argb(cur, 0x7C3AED), Theme.argb(0.9f * cur, Theme.PURPLE_LIGHT));
            } else if (hov) {
                Theme.strokeCard(c, a, b, d, e, Math.max(3, Math.round(10 * k)), Theme.argb(cur, 0x22222E), Theme.argb(0.5f * cur, Theme.BORDER));
            }
            txt(c, icon, 560 - tw(icon, 24) / 2f, centerY - 12, 24, sel ? 0xFFFFFF : 0xB8B8C0, 1f);
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
            case FPS -> list(c, mx, my, List.of(Category.PERFORMANCE));
            case CPVP -> list(c, mx, my, List.of(Category.CRYSTAL, Category.ANCHOR, Category.COMBAT));
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
        if (url == null || url.isBlank()) txt(c, "(ใส่ discordUrl ใน config/voidclient.json)", 870, 730, 12, Theme.MUTED, 1f);
    }

    private List<Module> filtered(List<Category> cats) {

        List<Module> out = new ArrayList<>();
        String q = search.toLowerCase();
        for (Module m : ModuleManager.ALL) {
            if (cats != null && !cats.contains(m.cat)) continue;
            if (!q.isEmpty() && !(m.name.toLowerCase().contains(q) || m.cat.label.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) continue;
            out.add(m);
        }
        return out;
    }

    private void list(DrawContext c, int mx, int my, List<Category> cats) {
        if (page == Page.FEATURES) {
            if (searchFocus) glowCard(c, 650, 412, 1020, 452, 0x241D38, Theme.PURPLE);
            else card(c, 650, 412, 1020, 452, Theme.CARD2, 1f);
            String t = search.isEmpty() ? "Search features..." : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
            txt(c, t, 666, 422, 17, search.isEmpty() ? Theme.MUTED : 0xFFFFFF, 1f);
            hits.add(new Hit(X(650), Y(412), X(1020), Y(452), () -> searchFocus = true));
        } else if (page == Page.FPS) {
            String curPreset = VoidConfig.data.preset;
            btn(c, 650, 412, 780, 452, "Low", 16, "LOW".equals(curPreset), mx, my, () -> FpsPresets.apply("LOW"));
            btn(c, 792, 412, 922, 452, "Balanced", 16, "BALANCED".equals(curPreset), mx, my, () -> FpsPresets.apply("BALANCED"));
            btn(c, 934, 412, 1124, 452, "High Performance", 16, "HIGH".equals(curPreset), mx, my, () -> FpsPresets.apply("HIGH"));
        } else {
            btn(c, 650, 412, 840, 452, "Crystal PvP Preset", 16, false, mx, my, () -> ModuleManager.cpvpPreset(true));
            btn(c, 852, 412, 1042, 452, "Anchor PvP Preset", 16, false, mx, my, () -> ModuleManager.cpvpPreset(false));
        }

        List<Module> mods = filtered(cats);
        final float rowH = 54, y0 = 464, y1 = 720;
        float contentH = mods.size() * rowH;
        float viewH = y1 - y0;
        float max = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(max, scroll));

        c.enableScissor(X(640), Y(y0), X(1377), Y(y1));
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            float ry = y0 + i * rowH - scroll;
            if (ry + rowH < y0 || ry > y1) continue;
            boolean sel = m == selected;
            if (sel) glowCard(c, 650, ry, 1356, ry + 46, 0x231C36, Theme.PURPLE);
            else card(c, 650, ry, 1356, ry + 46, Theme.CARD2, 1f);

            glowCard(c, 662, ry + 7, 694, ry + 39, 0x261A42, m.enabled() ? Theme.PURPLE : Theme.BORDER);
            txt(c, m.cat.icon, 678 - tw(m.cat.icon, 18) / 2f, ry + 14, 18, m.enabled() ? Theme.PURPLE_LIGHT : Theme.MUTED, 1f);
            txt(c, m.name, 708, ry + 6, 18, 0xFFFFFF, 1f);
            txt(c, m.desc, 708, ry + 28, 12, Theme.MUTED, 1f);

            if (ry >= y0 - 10 && ry + 46 <= y1 + 10) {
                hits.add(new Hit(X(650), Y(ry), X(1275), Y(ry + 46), () -> selected = m));
            }

            // Toggle pill with neon glow
            int a = X(1284), b = Y(ry + 13), d = X(1340), e = Y(ry + 33);
            if (m.enabled()) {
                Theme.rrect(c, a - 2, b - 2, d + 2, e + 2, (e - b) / 2 + 2, Theme.argb(0.32f * cur, Theme.PURPLE));
            }
            Theme.rrect(c, a, b, d, e, (e - b) / 2, Theme.argb(cur, m.enabled() ? 0x7C3AED : 0x323240));
            int kn = e - b - 4;
            int kx = m.enabled() ? d - kn - 2 : a + 2;
            Theme.rrect(c, kx, b + 2, kx + kn, b + 2 + kn, kn / 2, Theme.argb(cur, 0xFFFFFF));
            if (ry >= y0 - 10 && ry + 46 <= y1 + 10) hits.add(new Hit(a, b, d, e, () -> { m.setEnabled(!m.enabled()); VoidConfig.save(); }));
        }
        c.disableScissor();

        // Scrollbar indicator
        if (max > 0) {
            int sbX1 = X(1363), sbX2 = X(1368);
            int sbY1 = Y(y0), sbY2 = Y(y1);
            Theme.rrect(c, sbX1, sbY1, sbX2, sbY2, 2, Theme.argb(0.4f * cur, Theme.BORDER));
            float ratio = viewH / contentH;
            int thumbH = Math.max(16, Math.round((sbY2 - sbY1) * ratio));
            int thumbY = sbY1 + Math.round(((sbY2 - sbY1) - thumbH) * (scroll / max));
            Theme.rrect(c, sbX1, thumbY, sbX2, thumbY + thumbH, 2, Theme.argb(0.85f * cur, Theme.PURPLE));
        }

        if (mods.isEmpty()) txt(c, "No features found", 660, 480, 16, Theme.MUTED, 1f);

        // Detail strip
        card(c, 650, 728, 1365, 788, Theme.CARD2, 1f);
        if (selected == null) {
            txt(c, "Select a feature to adjust size / opacity / options", 668, 752, 14, Theme.MUTED, 1f);
        } else {
            int col = 0;
            List<Float> xs = List.of(668f, 908f, 1148f);
            final Module m = selected;
            if (m.isHud() && col < 3) {
                float xPos = xs.get(col);
                slider(c, xPos, 734, xPos + 210, "Scale", m.s.scale, 0.5f, 2f, v -> m.s.scale = v); col++;
            }
            if (m.isHud() && col < 3) {
                float xPos = xs.get(col);
                slider(c, xPos, 734, xPos + 210, "Opacity", m.s.opacity, 0.2f, 1f, v -> m.s.opacity = v); col++;
            }
            for (Setting st : m.settings) {
                if (col >= 3) break;
                float xPos = xs.get(col);
                slider(c, xPos, 734, xPos + 210, st.label(), m.val(st.key(), st.def()), st.min(), st.max(), v -> m.set(st.key(), v)); col++;
            }
            if (col == 0) txt(c, m.name + " - no extra options", 668, 752, 14, Theme.MUTED, 1f);
        }
    }

    private void settings(DrawContext c, int mx, int my) {
        btn(c, 660, 420, 940, 466, "Edit HUD Layout", 18, true, mx, my, () -> client.setScreen(new HudEditScreen()));
        btn(c, 960, 420, 1240, 466, "Reset All Settings", 18, false, mx, my, () -> { ModuleManager.resetAll(); selected = null; });
        txt(c, "Menu key: RIGHT SHIFT  (เปลี่ยนได้ใน Options > Controls > VOID CLIENT)", 662, 490, 16, 0xFFFFFF, 1f);
        txt(c, "Minecraft 1.21.11  |  Fabric  |  Java 21", 662, 520, 16, Theme.MUTED, 1f);
        txt(c, "Sodium: " + (ModuleManager.sodiumLoaded() ? "detected" : "not installed (แนะนำให้ติดตั้ง)"), 662, 548, 16, Theme.MUTED, 1f);
        txt(c, "Config: config/voidclient.json (บันทึกอัตโนมัติ)", 662, 576, 16, Theme.MUTED, 1f);
        txt(c, "HUD color: Cyber Violet  |  ไม่มีระบบอัตโนมัติ / ไม่แก้ไฟล์เกมต้นฉบับ", 662, 604, 16, Theme.MUTED, 1f);
    }

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

    @Override public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        scroll -= (float) (verticalAmount * 28f);
        return true;
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (searchFocus) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean charTyped(CharInput input) {
        if (searchFocus && input.codepoint() >= 32) {
            search += Character.toString(input.codepoint());
            return true;
        }
        return super.charTyped(input);
    }

    @Override public void close() {
        VoidConfig.save();
        super.close();
    }
}
