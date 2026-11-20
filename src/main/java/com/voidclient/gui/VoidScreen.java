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
    private Category filterCat = null;
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

        c.fillGradient(0, 0, width, height, Theme.argb(0.55f * g, 0x05050A), Theme.argb(0.68f * g, 0x0D0818));

        int pw = Math.min(width - 20, 740);
        k = pw / 904f;
        int ph = Math.round(564 * k);
        left = (width - pw) / 2; top = (height - ph) / 2;
        cur = g;

        Theme.rrect(c, X(506), Y(318), X(682), Y(762), Math.round(16 * k), Theme.argb(0.22f * g, Theme.PURPLE));
        Theme.strokeCard(c, X(508), Y(320), X(680), Y(760), Math.max(4, Math.round(14 * k)), Theme.argb(0.95f * g, 0x0B0B12), Theme.argb(0.6f * g, Theme.PURPLE));

        glowCard(c, 535, 266, 577, 308, 0x1A1429, Theme.PURPLE);
        txt(c, "V", 547, 273, 28, Theme.PURPLE_LIGHT, 1f);
        txt(c, "VOID", 588, 276, 26, 0xFFFFFF, 1f);
        txt(c, "Categories", 594 - tw("Categories", 14) / 2f, 334, 14, Theme.MUTED, 1f);

        float sideY = 358, sideH = 29, sideGap = 6;
        sideY = drawSideItem(c, mx, my, sideY, sideH, "Home", nav == Nav.HOME, () -> {
            nav = Nav.HOME; pageAt = System.currentTimeMillis(); selected = null; searchFocus = false;
        }) + sideGap;
        sideY = drawSideItem(c, mx, my, sideY, sideH, "All Mods", nav == Nav.ALL_MODS && filterCat == null, () -> {
            nav = Nav.ALL_MODS; filterCat = null; pageAt = System.currentTimeMillis(); scroll = 0; selected = null;
        }) + sideGap;

        for (Category cat : Category.values()) {
            boolean isSel = (nav == Nav.ALL_MODS && filterCat == cat);
            sideY = drawSideItem(c, mx, my, sideY, sideH, cat.label, isSel, () -> {
                nav = Nav.ALL_MODS; filterCat = cat; pageAt = System.currentTimeMillis(); scroll = 0; selected = null;
            }) + sideGap;
        }

        drawSideItem(c, mx, my, 684, sideH, "Edit HUD", false, () -> client.setScreen(new HudEditScreen()));
        drawSideItem(c, mx, my, 719, sideH, "Settings", nav == Nav.SETTINGS, () -> {
            nav = Nav.SETTINGS; pageAt = System.currentTimeMillis(); selected = null; searchFocus = false;
        });

        Theme.rrect(c, X(694), Y(256), X(1414), Y(824), Math.round(18 * k), Theme.argb(0.22f * g, Theme.PURPLE));
        Theme.strokeCard(c, X(696), Y(258), X(1412), Y(822), Math.max(4, Math.round(16 * k)), Theme.argb(0.96f * g, 0x09090F), Theme.argb(0.65f * g, Theme.PURPLE));

        if (searchFocus) glowCard(c, 714, 274, 1110, 312, 0x201934, Theme.PURPLE);
        else card(c, 714, 274, 1110, 312, Theme.CARD2, 1f);
        String sText = search.isEmpty() ? "Search mods..." : search + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
        txt(c, sText, 730, 285, 16, search.isEmpty() ? Theme.MUTED : 0xFFFFFF, 1f);
        hits.add(new Hit(X(714), Y(274), X(1110), Y(312), () -> { nav = Nav.ALL_MODS; searchFocus = true; }));

        btn(c, 1122, 274, 1280, 312, "Edit HUD Widgets", 13, true, mx, my, () -> client.setScreen(new HudEditScreen()));
        btn(c, 1290, 274, 1352, 312, "Home", 13, nav == Nav.HOME, mx, my, () -> { nav = Nav.HOME; pageAt = System.currentTimeMillis(); });
        btn(c, 1360, 274, 1394, 312, "X", 15, false, mx, my, this::close);

        cur = g * pg;
        if (nav == Nav.HOME) home(c, mx, my);
        else if (nav == Nav.SETTINGS) settings(c, mx, my);
        else gridMods(c, mx, my);
    }

    private float drawSideItem(DrawContext c, int mx, int my, float y1, float h, String label, boolean activeItem, Runnable onClick) {
        float x1 = 520, x2 = 668, y2 = y1 + h;
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(3, Math.round(8 * k));
        if (activeItem) Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, 0x261B42), Theme.argb(0.95f * cur, Theme.PURPLE_LIGHT));
        else if (hov) Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, 0x1E1E2A), Theme.argb(0.65f * cur, Theme.PURPLE));
        else Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, Theme.CARD2), Theme.argb(0.45f * cur, Theme.BORDER));
        txt(c, label, (x1 + x2) / 2f - tw(label, 14) / 2f, y1 + (h - 14) / 2f, 14, activeItem ? 0xFFFFFF : 0xD0D0DC, 1f);
        hits.add(new Hit(a, b, d, e, onClick));
        return y2;
    }
    private List<Module> filtered() {
        List<Module> out = new ArrayList<>();
        String q = search.toLowerCase();
        for (Module m : ModuleManager.ALL) {
            if (filterCat != null && m.cat != filterCat) continue;
            if (!q.isEmpty() && !(m.name.toLowerCase().contains(q) || m.cat.label.toLowerCase().contains(q) || m.desc.toLowerCase().contains(q))) continue;
            out.add(m);
        }
        return out;
    }

    private void gridMods(DrawContext c, int mx, int my) {
        String headerTitle = filterCat == null ? "All Modifications" : filterCat.label;
        txt(c, headerTitle, 718, 326, 16, Theme.MUTED, 1f);

        String curPreset = VoidConfig.data.preset;
        btn(c, 1020, 322, 1090, 346, "FPS Low", 11, "LOW".equals(curPreset), mx, my, () -> FpsPresets.apply("LOW"));
        btn(c, 1096, 322, 1178, 346, "FPS High", 11, "HIGH".equals(curPreset), mx, my, () -> FpsPresets.apply("HIGH"));
        btn(c, 1184, 322, 1284, 346, "Crystal Preset", 11, false, mx, my, () -> ModuleManager.cpvpPreset(true));
        btn(c, 1290, 322, 1390, 346, "Anchor Preset", 11, false, mx, my, () -> ModuleManager.cpvpPreset(false));

        List<Module> mods = filtered();
        final int cols = 4;
        final float gridX = 716, gridY = 356, gridW = 666, viewBottom = 730, gap = 12;
        final float cardW = (gridW - gap * (cols - 1)) / cols, cardH = 112;

        int rows = (int) Math.ceil(mods.size() / (double) cols);
        float contentH = rows * (cardH + gap);
        float viewH = viewBottom - gridY;
        float maxScroll = Math.max(0, contentH - viewH);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        c.enableScissor(X(710), Y(gridY), X(1390), Y(viewBottom));
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int r = i / cols, col = i % cols;
            float cx1 = gridX + col * (cardW + gap);
            float cy1 = gridY + r * (cardH + gap) - scroll;
            float cx2 = cx1 + cardW, cy2 = cy1 + cardH;

            if (cy2 < gridY || cy1 > viewBottom) continue;

            boolean isSel = (m == selected);
            if (isSel) glowCard(c, cx1, cy1, cx2, cy2, 0x201836, Theme.PURPLE_LIGHT);
            else if (m.enabled()) glowCard(c, cx1, cy1, cx2, cy2, 0x171424, Theme.PURPLE);
            else card(c, cx1, cy1, cx2, cy2, Theme.CARD2, 1f);

            String tag = m.cat.label.length() > 7 ? m.cat.label.substring(0, 7).toUpperCase() : m.cat.label.toUpperCase();
            float badgeW = tw(tag, 9) + 10;
            Theme.rrect(c, X(cx2 - badgeW - 6), Y(cy1 + 6), X(cx2 - 6), Y(cy1 + 19), 4, Theme.argb(cur, 0x2B1E4A));
            txt(c, tag, cx2 - badgeW - 1, cy1 + 8, 9, Theme.PURPLE_LIGHT, 1f);

            float midX = (cx1 + cx2) / 2f;
            glowCard(c, midX - 18, cy1 + 18, midX + 18, cy1 + 54, 0x22193B, m.enabled() ? Theme.PURPLE : Theme.BORDER);
            txt(c, m.cat.icon, midX - tw(m.cat.icon, 18) / 2f, cy1 + 27, 18, m.enabled() ? Theme.PURPLE_LIGHT : 0xB8B8C8, 1f);

            String dispName = m.name;
            if (tw(dispName, 13) > cardW - 14) dispName = dispName.substring(0, Math.min(dispName.length(), 15)) + "..";
            txt(c, dispName, midX - tw(dispName, 13) / 2f, cy1 + 62, 13, 0xFFFFFF, 1f);

            boolean hasOpts = m.isHud() || !m.settings.isEmpty();
            float barY1 = cy2 - 26, barY2 = cy2 - 6;

            if (cy1 >= gridY - 20 && cy2 <= viewBottom + 20) {
                if (hasOpts) {
                    float splitX = cx1 + (cardW - 16) * 0.60f + 6;
                    drawMiniToggle(c, mx, my, cx1 + 6, barY1, splitX - 2, barY2, m);
                    drawMiniOpt(c, mx, my, splitX + 2, barY1, cx2 - 6, barY2, isSel, () -> selected = (selected == m ? null : m));
                } else {
                    drawMiniToggle(c, mx, my, cx1 + 6, barY1, cx2 - 6, barY2, m);
                }
                hits.add(new Hit(X(cx1), Y(cy1), X(cx2), Y(barY1 - 2), () -> selected = m));
            }
        }
        c.disableScissor();

        if (maxScroll > 0) {
            int sbX1 = X(1392), sbX2 = X(1397), sbY1 = Y(gridY), sbY2 = Y(viewBottom);
            Theme.rrect(c, sbX1, sbY1, sbX2, sbY2, 2, Theme.argb(0.4f * cur, Theme.BORDER));
            float ratio = viewH / contentH;
            int thumbH = Math.max(18, Math.round((sbY2 - sbY1) * ratio));
            int thumbY = sbY1 + Math.round(((sbY2 - sbY1) - thumbH) * (scroll / maxScroll));
            Theme.rrect(c, sbX1, thumbY, sbX2, thumbY + thumbH, 2, Theme.argb(0.9f * cur, Theme.PURPLE));
        }

        if (mods.isEmpty()) txt(c, "No mods found matching your filter.", 720, 380, 16, Theme.MUTED, 1f);

        card(c, 714, 738, 1394, 806, Theme.CARD2, 1f);
        if (selected == null) {
            txt(c, "Click any mod card or 'Opt >' to adjust settings, scale, and opacity.", 732, 765, 14, Theme.MUTED, 1f);
        } else {
            txt(c, selected.name + ": " + selected.desc, 732, 745, 12, Theme.PURPLE_LIGHT, 1f);
            int col = 0;
            List<Float> xs = List.of(732f, 956f, 1180f);
            final Module m = selected;
            if (m.isHud() && col < 3) {
                float xPos = xs.get(col);
                slider(c, xPos, 762, xPos + 200, "Scale", m.s.scale, 0.5f, 2f, v -> m.s.scale = v); col++;
            }
            if (m.isHud() && col < 3) {
                float xPos = xs.get(col);
                slider(c, xPos, 762, xPos + 200, "Opacity", m.s.opacity, 0.2f, 1f, v -> m.s.opacity = v); col++;
            }
            for (Setting st : m.settings) {
                if (col >= 3) break;
                float xPos = xs.get(col);
                slider(c, xPos, 762, xPos + 200, st.label(), m.val(st.key(), st.def()), st.min(), st.max(), v -> m.set(st.key(), v)); col++;
            }
            if (col == 0) txt(c, "No extra sliders for this mod.", 732, 768, 13, Theme.MUTED, 1f);
        }
    }

    private void drawMiniToggle(DrawContext c, int mx, int my, float x1, float y1, float x2, float y2, Module m) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(2, Math.round(6 * k));
        if (m.enabled()) Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0x9333EA : 0x7C3AED), Theme.argb(0.85f * cur, Theme.PURPLE_LIGHT));
        else Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, hov ? 0x2B2B3B : 0x1E1E28), Theme.argb(0.6f * cur, Theme.BORDER));
        String lbl = m.enabled() ? "ON" : "OFF";
        txt(c, lbl, (x1 + x2) / 2f - tw(lbl, 11) / 2f, (y1 + y2) / 2f - 5.5f, 11, m.enabled() ? 0xFFFFFF : Theme.MUTED, 1f);
        hits.add(new Hit(a, b, d, e, () -> { m.setEnabled(!m.enabled()); VoidConfig.save(); }));
    }

    private void drawMiniOpt(DrawContext c, int mx, int my, float x1, float y1, float x2, float y2, boolean isSel, Runnable run) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = mx >= a && mx <= d && my >= b && my <= e;
        int rad = Math.max(2, Math.round(6 * k));
        Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(cur, (hov || isSel) ? 0x2E2447 : 0x1E1E28), Theme.argb(0.7f * cur, (hov || isSel) ? Theme.PURPLE_LIGHT : Theme.BORDER));
        String lbl = "Opt >";
        txt(c, lbl, (x1 + x2) / 2f - tw(lbl, 10) / 2f, (y1 + y2) / 2f - 5f, 10, 0xFFFFFF, 1f);
        hits.add(new Hit(a, b, d, e, run));
    }

    private void home(DrawContext c, int mx, int my) {
        int h = LocalTime.now().getHour();
        String greet = h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
        String name = client.getSession() != null ? client.getSession().getUsername() : "Player";

        card(c, 718, 336, 1130, 530, Theme.CARD2, 1f);
        txt(c, greet + ", " + name + " !", 742, 358, 26, 0xFFFFFF, 1f);
        boolean inWorld = client.world != null;
        btn(c, 742, 412, 1106, 470, inWorld ? "RESUME GAME" : "PLAY MINECRAFT", 22, true, mx, my, () -> {
            if (inWorld) close(); else client.setScreen(new SelectWorldScreen(this));
        });
        String mcv = "Minecraft 1.21.11 - Fabric";
        txt(c, mcv, 924 - tw(mcv, 14) / 2f, 494, 14, Theme.MUTED, 1f);

        card(c, 1148, 336, 1390, 530, Theme.CARD2, 1f);
        txt(c, "Client Version", 1168, 358, 18, Theme.MUTED, 1f);
        txt(c, "v1.0.0", 1168, 390, 38, 0xFFFFFF, 1f);
        btn(c, 1168, 476, 1370, 512, "Browse All Mods", 14, true, mx, my, () -> { nav = Nav.ALL_MODS; filterCat = null; pageAt = System.currentTimeMillis(); });

        card(c, 718, 550, 1390, 795, Theme.CARD2, 1f);
        glowCard(c, 742, 580, 824, 670, 0x241B3A, Theme.PURPLE);
        txt(c, "VC", 783 - tw("VC", 30) / 2f, 608, 30, Theme.PURPLE_LIGHT, 1f);
        txt(c, "Join our community !", 848, 576, 26, 0xFFFFFF, 1f);
        txt(c, "For support, presets and updates from the VOID community.", 848, 616, 15, Theme.MUTED, 1f);
        String url = VoidConfig.data.discordUrl;
        btn(c, 848, 650, 930, 682, "Join >", 14, true, mx, my, () -> { if (url != null && !url.isBlank()) Util.getOperatingSystem().open(url); });
        if (url == null || url.isBlank()) txt(c, "(ใส่ discordUrl ใน config/voidclient.json)", 946, 658, 12, Theme.MUTED, 1f);
    }

    private void settings(DrawContext c, int mx, int my) {
        txt(c, "Client Settings", 718, 334, 24, 0xFFFFFF, 1f);
        btn(c, 718, 380, 980, 428, "Edit HUD Layout", 17, true, mx, my, () -> client.setScreen(new HudEditScreen()));
        btn(c, 1000, 380, 1262, 428, "Reset All Settings", 17, false, mx, my, () -> { ModuleManager.resetAll(); selected = null; });
        txt(c, "Menu key: RIGHT SHIFT  (เปลี่ยนได้ใน Options > Controls > VOID CLIENT)", 720, 456, 15, 0xFFFFFF, 1f);
        txt(c, "Minecraft 1.21.11  |  Fabric  |  Java 21", 720, 488, 15, Theme.MUTED, 1f);
        txt(c, "Sodium: " + (ModuleManager.sodiumLoaded() ? "detected" : "not installed (แนะนำให้ติดตั้ง)"), 720, 518, 15, Theme.MUTED, 1f);
        txt(c, "Config: config/voidclient.json (บันทึกอัตโนมัติ)", 720, 548, 15, Theme.MUTED, 1f);
        txt(c, "HUD color: Cyber Violet  |  ไม่มีระบบอัตโนมัติ / ไม่แก้ไฟล์เกมต้นฉบับ", 720, 578, 15, Theme.MUTED, 1f);
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
