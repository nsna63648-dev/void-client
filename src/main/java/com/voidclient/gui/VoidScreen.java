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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class VoidScreen extends Screen {
    private enum Nav { HOME, ALL_MODS, SETTINGS, BLOCKESP }
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Sl(int x1, int y1, int x2, int y2, float min, float max, Consumer<Float> set) {}
    private record SideItem(String label, float y, boolean active, Runnable run) {}

    private static final int MAX_SEARCH = 32;

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

    // ---- BlockESP page ----
    private List<BlockESP.BlockInfo> blockView = List.of();
    private String blockViewQuery = null;
    private boolean blockViewDirty = true;
    private boolean onlySelected = false;

    // ---- scrolling (smooth) ----
    private float scroll, scrollTarget, lastMaxScroll;

    // ---- layout ----
    private int left, top;
    private float k, cur = 1f;
    private int oy;

    // ---- animation state ----
    private final Map<String, Float> anim = new HashMap<>();
    private final Map<String, Long> pressAt = new HashMap<>();
    private long lastNanos = System.nanoTime();
    private float lerpK = 0.2f;
    private float scrollK = 0.3f;
    private float time = 0f;
    private float indY = Float.NaN;

    // ---- clipping for hit-testing inside the scissor area ----
    private int clipT = Integer.MIN_VALUE, clipB = Integer.MAX_VALUE;
    private boolean errLogged;

    public VoidScreen() { super(Text.literal("VOID CLIENT")); }

    @Override public boolean shouldPause() { return false; }

    // =====================================================================
    //  helpers
    // =====================================================================

    private int X(float d) { return left + Math.round((d - 508f) * k); }
    private int Y(float d) { return top + oy + Math.round((d - 258f) * k); }
    private float tw(String s, float px) { return textRenderer.getWidth(s) * px / 9f; }
    private int radius(float base, int min) { return Math.max(min, Math.round(base * k)); }

    private static int lerpRgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    /** Smooth (lerped + smoothstep) 0..1 value for a boolean target. Call ONCE per key per frame. */
    private float hover(String key, boolean on) {
        float target = on ? 1f : 0f;
        float v = anim.getOrDefault(key, 0f);
        v += (target - v) * lerpK;
        if (Math.abs(target - v) < 0.003f) v = target;
        anim.put(key, v);
        return v * v * (3f - 2f * v);
    }

    private boolean inRect(int mx, int my, int a, int b, int d, int e) {
        return mx >= a && mx <= d && my >= b && my <= e && my >= clipT && my <= clipB;
    }

    private void addHit(int a, int b, int d, int e, Runnable run) {
        int bb = Math.max(b, clipT), ee = Math.min(e, clipB);
        if (ee < bb) return;
        hits.add(new Hit(a, bb, d, ee, run));
    }

    private static void safe(String what, Runnable r) {
        try { r.run(); }
        catch (Throwable t) { System.err.println("[VOID] error in " + what + ": " + t); }
    }

    private void txt(DrawContext c, String s, float dx, float dy, float px, int rgb, float a) {
        if (a * cur < 0.02f) return;
        float sc = px * k / 9f;
        var m = c.getMatrices();
        m.pushMatrix();
        try {
            m.translate((float) X(dx), (float) Y(dy));
            m.scale(sc, sc);
            c.drawText(textRenderer, s, 0, 0, Theme.argb(a * cur, rgb), false);
        } finally {
            m.popMatrix();
        }
    }

    /** Glassmorphism panel in screen pixels: neon glow + translucent gradient + top highlight + border. */
    private void glass(DrawContext c, int a, int b, int d, int e, int rad, float glow, int borderRgb, float borderA,
                       int topRgb, int botRgb, float fillA) {
        if (glow > 0.01f) {
            for (int i = 3; i >= 1; i--) {
                Theme.rrect(c, a - i, b - i, d + i, e + i, rad + i, Theme.argb(glow * 0.10f * cur, Theme.PURPLE));
            }
        }
        Theme.strokeCard(c, a, b, d, e, rad, Theme.argb(fillA * cur, botRgb), Theme.argb(borderA * cur, borderRgb));
        int in = Math.max(2, rad / 2);
        if (d - a > 2 * in + 2 && e - b > 2 * in + 2) {
            c.fillGradient(a + in, b + in, d - in, e - in, Theme.argb(fillA * 0.9f * cur, topRgb), Theme.argb(fillA * cur, botRgb));
        }
        if (d - a > 2 * rad + 2) {
            c.fill(a + rad, b + 1, d - rad, b + 2, Theme.argb((0.10f + 0.12f * glow) * cur, 0xFFFFFF));
        }
    }

    private void card(DrawContext c, float x1, float y1, float x2, float y2, int rgb, float a) {
        glass(c, X(x1), Y(y1), X(x2), Y(y2), radius(12, 3), 0f, Theme.BORDER, 0.55f,
                lerpRgb(rgb, 0x2A2040, 0.30f), rgb, a * 0.96f);
    }

    private void glowCard(DrawContext c, float x1, float y1, float x2, float y2, int rgb, int glowRgb) {
        glass(c, X(x1), Y(y1), X(x2), Y(y2), radius(12, 3), 0.5f, glowRgb, 0.85f,
                lerpRgb(rgb, 0x3A2A66, 0.30f), rgb, 1f);
    }

    private void btn(DrawContext c, float x1, float y1, float x2, float y2, String label, float px,
                     boolean primary, int mx, int my, Runnable run) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        boolean hov = inRect(mx, my, a, b, d, e);
        String key = "btn:" + label + ":" + (int) x1 + ":" + (int) y1;
        float h = hover(key, hov);

        float pulse = 0f;
        Long pt = pressAt.get(key);
        if (pt != null) {
            pulse = 1f - (System.currentTimeMillis() - pt) / 380f;
            if (pulse <= 0f) { pressAt.remove(key); pulse = 0f; }
        }
        int rad = radius(8, 2);
        int g = Math.round(h * 1.5f - pulse * 2f);
        int ga = a - g, gb = b - g, gd = d + g, ge = e + g;

        if (pulse > 0f) {
            Theme.rrect(c, ga - 4, gb - 4, gd + 4, ge + 4, rad + 4, Theme.argb(0.40f * pulse * cur, Theme.PURPLE_LIGHT));
        }
        if (primary) {
            glass(c, ga, gb, gd, ge, rad, 0.35f + 0.5f * h + 0.5f * pulse, Theme.PURPLE_LIGHT, 0.9f,
                    lerpRgb(0x9D26D9, 0xB36BFA, h), 0x5B3FD0, 1f);
        } else {
            glass(c, ga, gb, gd, ge, rad, 0.6f * h + 0.4f * pulse, lerpRgb(Theme.BORDER, Theme.PURPLE, h), 0.6f + 0.35f * h,
                    lerpRgb(0x242432, 0x2E2447, h), 0x14141D, 0.92f);
        }
        txt(c, label, (x1 + x2) / 2f - tw(label, px) / 2f, (y1 + y2) / 2f - px / 2f, px, 0xFFFFFF, 1f);
        addHit(a, b, d, e, () -> { pressAt.put(key, System.currentTimeMillis()); run.run(); });
    }

    private void slider(DrawContext c, float x1, float y1, float x2, String label, float val, float min, float max, Consumer<Float> set) {
        txt(c, String.format("%s  %.2f", label, val), x1, y1, 13, 0xFFFFFF, 0.95f);
        int a = X(x1), b = Y(y1 + 22), d = X(x2), e = Y(y1 + 29);
        float t = clamp01((val - min) / (max - min));
        int fillW = Math.max(4, Math.round((d - a) * t));
        int mxp = -1, myp = -1;
        // (hover detection for knob glow is approximated by hovering the track)
        Theme.rrect(c, a, b, d, e, 3, Theme.argb(cur, 0x262634));
        Theme.rrect(c, a, b, a + fillW, e, 3, Theme.argb(cur, Theme.PURPLE));
        if (fillW > 8) c.fill(a + 2, b + 1, a + fillW - 2, b + 2, Theme.argb(0.35f * cur, Theme.PURPLE_LIGHT));
        int kx = a + fillW;
        int ky = (b + e) / 2;
        Theme.rrect(c, kx - 6, ky - 7, kx + 6, ky + 7, 6, Theme.argb(0.22f * cur, Theme.PURPLE_LIGHT));
        Theme.rrect(c, kx - 4, ky - 5, kx + 4, ky + 5, 4, Theme.argb(cur, 0xFFFFFF));
        addSlider(new Sl(a, b - 6, d, e + 6, min, max, set));
    }

    private void addSlider(Sl s) { sliders.add(s); }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t) * (1f - t); }

    // =====================================================================
    //  render
    // =====================================================================

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear(); sliders.clear();
        clipT = Integer.MIN_VALUE; clipB = Integer.MAX_VALUE;

        long nowN = System.nanoTime();
        float dt = Math.max(0f, Math.min(0.1f, (nowN - lastNanos) / 1_000_000_000f));
        lastNanos = nowN;

        long now = System.currentTimeMillis();
        boolean animOn = !ModuleManager.animationsOff();
        lerpK = animOn ? 1f - (float) Math.exp(-dt * 14f) : 1f;
        scrollK = animOn ? 1f - (float) Math.exp(-dt * 18f) : 1f;
        time = (now - openAt) / 1000f;

        float g = animOn ? ease(Math.min(1f, (now - openAt) / 240f)) : 1f;
        float pg = animOn ? ease(Math.min(1f, (now - pageAt) / 180f)) : 1f;
        oy = Math.round((1f - g) * 12);

        c.fillGradient(0, 0, width, height, Theme.argb(0.55f * g, 0x05050A), Theme.argb(0.68f * g, 0x0D0818));

        int pw = Math.min(width - 20, 740);
        k = pw / 904f;
        int ph = Math.round(564 * k);
        left = (width - pw) / 2; top = (height - ph) / 2;
        cur = g;

        // ---- main window panels (glass) ----
        glass(c, X(508), Y(320), X(680), Y(760), radius(14, 4), 0.35f, Theme.PURPLE, 0.6f, 0x1A1230, 0x08080F, 0.88f);

        // header
        glowCard(c, 535, 266, 577, 308, 0x1A1429, Theme.PURPLE);
        txt(c, "V", 547, 273, 28, Theme.PURPLE_LIGHT, 1f);
        txt(c, "VOID", 588, 276, 26, 0xFFFFFF, 1f);
        txt(c, "Categories", 594 - tw("Categories", 14) / 2f, 334, 14, Theme.MUTED, 1f);

        drawSidebar(c, mx, my);

        glass(c, X(696), Y(258), X(1412), Y(822), radius(16, 4), 0.35f, Theme.PURPLE, 0.65f, 0x161026, 0x07070D, 0.90f);
        // sliding shimmer along the top edge
        {
            float p = (time * 0.20f) % 1.3f - 0.15f;
            int s1 = Math.max(X(716), X(700 + p * 708));
            int s2 = Math.min(X(1392), X(700 + p * 708 + 140));
            if (s2 > s1) c.fill(s1, Y(259), s2, Y(259) + 1, Theme.argb(0.40f * cur, Theme.PURPLE_LIGHT));
        }

        // ---- search bar ----
        float sf = hover("search", searchFocus);
        glass(c, X(714), Y(274), X(1110), Y(312), radius(12, 3), sf * 0.8f,
                lerpRgb(Theme.BORDER, Theme.PURPLE_LIGHT, sf), 0.55f + 0.45f * sf,
                lerpRgb(0x1D1730, 0x261C42, sf), 0x0F0F18, 0.92f);
        String shown = search;
        while (shown.length() > 1 && tw(shown, 16) > 330) shown = shown.substring(1);
        String placeholder = nav == Nav.BLOCKESP ? "Search blocks..." : "Search mods...";
        String sText = search.isEmpty() ? placeholder : shown + (now / 500 % 2 == 0 && searchFocus ? "|" : "");
        txt(c, sText, 730, 285, 16, search.isEmpty() ? Theme.MUTED : 0xFFFFFF, 1f);
        addHit(X(714), Y(274), X(1110), Y(312), () -> { if (nav != Nav.BLOCKESP) nav = Nav.ALL_MODS; searchFocus = true; });

        // clear (X) button
        if (!search.isEmpty()) {
            int xa = X(1082), xb = Y(282), xd = X(1102), xe = Y(304);
            float xh = hover("clear", inRect(mx, my, xa, xb, xd, xe));
            glass(c, xa, xb, xd, xe, radius(7, 2), xh * 0.6f, lerpRgb(Theme.BORDER, Theme.PURPLE_LIGHT, xh), 0.7f,
                    lerpRgb(0x262634, 0x3A2A66, xh), 0x14141D, 0.95f);
            txt(c, "x", 1092 - tw("x", 13) / 2f, 286, 13, 0xFFFFFF, 1f);
            addHit(xa, xb, xd, xe, () -> { search = ""; scrollTarget = 0; });
        } else {
            anim.remove("clear");
        }

        btn(c, 1122, 274, 1280, 312, "Edit HUD Widgets", 13, true, mx, my, () -> client.setScreen(new HudEditScreen()));
        btn(c, 1290, 274, 1352, 312, "Home", 13, nav == Nav.HOME, mx, my, () -> { nav = Nav.HOME; pageAt = System.currentTimeMillis(); });
        btn(c, 1360, 274, 1394, 312, "X", 15, false, mx, my, this::close);

        // ---- page ----
        cur = g * pg;
        try {
            if (nav == Nav.HOME) home(c, mx, my);
            else if (nav == Nav.SETTINGS) settings(c, mx, my);
            else if (nav == Nav.BLOCKESP) blockEspPage(c, mx, my);
            else gridMods(c, mx, my);
        } catch (Throwable t) {
            if (!errLogged) { errLogged = true; System.err.println("[VOID] UI render error: " + t); t.printStackTrace(); }
            txt(c, "UI error - see latest.log", 718, 340, 16, 0xFF6B6B, 1f);
        }
    }

    // =====================================================================
    //  sidebar (smooth indicator)
    // =====================================================================

    private void drawSidebar(DrawContext c, int mx, int my) {
        final float sideH = 29, sideGap = 6;
        List<SideItem> items = new ArrayList<>();
        float sy = 358;

        items.add(new SideItem("Home", sy, nav == Nav.HOME, () -> {
            nav = Nav.HOME; pageAt = System.currentTimeMillis(); selected = null; searchFocus = false;
        }));
        sy += sideH + sideGap;
        items.add(new SideItem("All Mods", sy, nav == Nav.ALL_MODS && filterCat == null, () -> {
            nav = Nav.ALL_MODS; filterCat = null; pageAt = System.currentTimeMillis(); scroll = scrollTarget = 0; selected = null;
        }));
        sy += sideH + sideGap;
        for (Category cat : Category.values()) {
            boolean isSel = (nav == Nav.ALL_MODS && filterCat == cat) || (nav == Nav.BLOCKESP && cat == Category.VISUAL);
            items.add(new SideItem(cat.label, sy, isSel, () -> {
                nav = Nav.ALL_MODS; filterCat = cat; pageAt = System.currentTimeMillis(); scroll = scrollTarget = 0; selected = null;
            }));
            sy += sideH + sideGap;
        }
        items.add(new SideItem("Edit HUD", 684, false, () -> client.setScreen(new HudEditScreen())));
        items.add(new SideItem("Settings", 719, nav == Nav.SETTINGS, () -> {
            nav = Nav.SETTINGS; pageAt = System.currentTimeMillis(); selected = null; searchFocus = false;
        }));

        int rad = radius(8, 3);
        float[] hv = new float[items.size()];
        float target = 358;

        // pass 1: backgrounds (+hover glow)
        for (int i = 0; i < items.size(); i++) {
            SideItem it = items.get(i);
            if (it.active) target = it.y;
            int a = X(520), b = Y(it.y), d = X(668), e = Y(it.y + sideH);
            hv[i] = hover("side:" + it.label, inRect(mx, my, a, b, d, e));
            glass(c, a, b, d, e, rad, hv[i] * 0.5f, lerpRgb(Theme.BORDER, Theme.PURPLE, hv[i]), 0.45f + 0.35f * hv[i],
                    lerpRgb(0x1E1E2A, 0x2A2040, hv[i]), Theme.CARD2, 0.9f);
        }

        // pass 2: sliding indicator
        if (Float.isNaN(indY)) indY = target;
        indY += (target - indY) * lerpK;
        if (Math.abs(target - indY) < 0.2f) indY = target;
        int ia = X(520), ib = Y(indY), id = X(668), ie = Y(indY + sideH);
        glass(c, ia, ib, id, ie, rad, 0.7f, Theme.PURPLE_LIGHT, 0.95f, 0x34225C, 0x1E1238, 0.96f);
        int quarter = (ie - ib) / 4;
        c.fill(ia + 3, ib + quarter, ia + 5, ie - quarter, Theme.argb(cur, Theme.PURPLE_LIGHT));

        // pass 3: labels + hits
        for (int i = 0; i < items.size(); i++) {
            SideItem it = items.get(i);
            float ex = Math.max(0f, 1f - Math.abs(it.y - indY) / sideH); // how much the indicator sits under this item
            float t = Math.max(it.active ? 1f : 0f, Math.max(ex, hv[i] * 0.6f));
            int col = lerpRgb(0xD0D0DC, 0xFFFFFF, t);
            txt(c, it.label, (520 + 668) / 2f - tw(it.label, 14) / 2f, it.y + (sideH - 14) / 2f, 14, col, 1f);
            addHit(X(520), Y(it.y), X(668), Y(it.y + sideH), it.run);
        }
    }

    // =====================================================================
    //  mods grid
    // =====================================================================

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

    private void openBlockEsp() {
        nav = Nav.BLOCKESP;
        pageAt = System.currentTimeMillis();
        scroll = scrollTarget = 0;
        search = "";
        searchFocus = false;
        selected = null;
        blockViewDirty = true;
    }

    private void gridMods(DrawContext c, int mx, int my) {
        String headerTitle = filterCat == null ? "All Modifications" : filterCat.label;
        txt(c, headerTitle, 718, 326, 16, Theme.MUTED, 1f);

        String curPreset = VoidConfig.data.preset;
        String cp = ModuleManager.activeCpvp();
        btn(c, 1020, 322, 1090, 346, "FPS Low", 11, "LOW".equals(curPreset), mx, my, () -> safe("FPS Low preset", () -> FpsPresets.apply("LOW")));
        btn(c, 1096, 322, 1178, 346, "FPS High", 11, "HIGH".equals(curPreset), mx, my, () -> safe("FPS High preset", () -> FpsPresets.apply("HIGH")));
        btn(c, 1184, 322, 1284, 346, "Crystal Preset", 11, "CRYSTAL".equals(cp), mx, my, () -> safe("Crystal preset", () -> ModuleManager.cpvpPreset(true)));
        btn(c, 1290, 322, 1390, 346, "Anchor Preset", 11, "ANCHOR".equals(cp), mx, my, () -> safe("Anchor preset", () -> ModuleManager.cpvpPreset(false)));

        List<Module> mods = filtered();
        final int cols = 4;
        final float gridX = 716, gridY = 356, gridW = 666, viewBottom = 730, gap = 12;
        final float cardW = (gridW - gap * (cols - 1)) / cols, cardH = 112;

        int rows = (int) Math.ceil(mods.size() / (double) cols);
        float contentH = rows * (cardH + gap);
        float viewH = viewBottom - gridY;
        float maxScroll = Math.max(0, contentH - viewH);
        lastMaxScroll = maxScroll;
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * scrollK;
        if (Math.abs(scrollTarget - scroll) < 0.2f) scroll = scrollTarget;
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        int clipTop = Y(gridY), clipBottom = Y(viewBottom);
        c.enableScissor(X(710), clipTop, X(1390), clipBottom);
        clipT = clipTop; clipB = clipBottom;
        try {
            for (int i = 0; i < mods.size(); i++) {
                Module m = mods.get(i);
                int r = i / cols, col = i % cols;
                float cx1 = gridX + col * (cardW + gap);
                float cy1 = gridY + r * (cardH + gap) - scroll;
                float cx2 = cx1 + cardW, cy2 = cy1 + cardH;

                if (cy2 < gridY - 4 || cy1 > viewBottom + 4) continue; // off-screen: no work at all

                boolean isSel = (m == selected);
                boolean enabled = m.enabled();
                boolean hovCard = inRect(mx, my, X(cx1), Y(cy1), X(cx2), Y(cy2));
                float hc = hover("card:" + m.name, hovCard);
                float on = hover("on:" + m.name, enabled);
                float sel = hover("sel:" + m.name, isSel);

                float lift = hc * 2f;
                float y1 = cy1 - lift, y2 = cy2 - lift;
                float strong = Math.max(on, Math.max(hc, sel));

                glass(c, X(cx1), Y(y1), X(cx2), Y(y2), radius(12, 3),
                        Math.min(1f, on * 0.40f + sel * 0.70f + hc * 0.60f),
                        lerpRgb(lerpRgb(Theme.BORDER, Theme.PURPLE, Math.max(on, hc)), Theme.PURPLE_LIGHT, sel),
                        0.55f + 0.40f * strong,
                        lerpRgb(0x1C1C26, 0x261B44, Math.max(on, sel)),
                        lerpRgb(0x12121A, 0x0F0B1C, Math.max(on, sel)), 0.95f);

                String tag = m.cat.label.length() > 7 ? m.cat.label.substring(0, 7).toUpperCase() : m.cat.label.toUpperCase();
                float badgeW = tw(tag, 9) + 10;
                Theme.rrect(c, X(cx2 - badgeW - 6), Y(y1 + 6), X(cx2 - 6), Y(y1 + 19), 4, Theme.argb(cur, 0x2B1E4A));
                txt(c, tag, cx2 - badgeW - 1, y1 + 8, 9, Theme.PURPLE_LIGHT, 1f);

                float midX = (cx1 + cx2) / 2f;
                glass(c, X(midX - 18), Y(y1 + 18), X(midX + 18), Y(y1 + 54), radius(10, 3), on * 0.6f + hc * 0.3f,
                        lerpRgb(Theme.BORDER, Theme.PURPLE, on), 0.8f, 0x2A1F47, 0x1A1230, 1f);
                txt(c, m.cat.icon, midX - tw(m.cat.icon, 18) / 2f, y1 + 27, 18, lerpRgb(0xB8B8C8, Theme.PURPLE_LIGHT, on), 1f);

                String dispName = m.name;
                if (tw(dispName, 13) > cardW - 14) dispName = dispName.substring(0, Math.min(dispName.length(), 15)) + "..";
                txt(c, dispName, midX - tw(dispName, 13) / 2f, y1 + 62, 13, 0xFFFFFF, 1f);

                boolean hasOpts = m.isHud() || !m.settings.isEmpty();
                boolean isBlockEsp = m instanceof BlockESP;
                float barY1 = y2 - 26, barY2 = y2 - 6;

                if (hasOpts) {
                    float splitX = cx1 + (cardW - 16) * 0.60f + 6;
                    drawMiniToggle(c, mx, my, cx1 + 6, barY1, splitX - 2, barY2, m, on);
                    Runnable optRun = isBlockEsp ? this::openBlockEsp : () -> selected = (selected == m ? null : m);
                    drawMiniOpt(c, mx, my, splitX + 2, barY1, cx2 - 6, barY2, m, isSel, optRun);
                } else {
                    drawMiniToggle(c, mx, my, cx1 + 6, barY1, cx2 - 6, barY2, m, on);
                }
                Runnable bodyRun = isBlockEsp ? this::openBlockEsp : () -> selected = m;
                addHit(X(cx1), Y(cy1), X(cx2), Y(barY1 - 2), bodyRun);
            }
        } finally {
            c.disableScissor();
            clipT = Integer.MIN_VALUE; clipB = Integer.MAX_VALUE;
        }

        if (maxScroll > 0) {
            int sbX1 = X(1392), sbX2 = X(1397), sbY1 = Y(gridY), sbY2 = Y(viewBottom);
            Theme.rrect(c, sbX1, sbY1, sbX2, sbY2, 2, Theme.argb(0.4f * cur, Theme.BORDER));
            float ratio = viewH / contentH;
            int thumbH = Math.max(18, Math.round((sbY2 - sbY1) * ratio));
            int thumbY = sbY1 + Math.round(((sbY2 - sbY1) - thumbH) * (scroll / maxScroll));
            Theme.rrect(c, sbX1 - 1, thumbY - 1, sbX2 + 1, thumbY + thumbH + 1, 3, Theme.argb(0.18f * cur, Theme.PURPLE));
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

    private void drawMiniToggle(DrawContext c, int mx, int my, float x1, float y1, float x2, float y2, Module m, float on) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        float h = hover("tg:" + m.name, inRect(mx, my, a, b, d, e));
        int rad = radius(6, 2);
        glass(c, a, b, d, e, rad, on * 0.75f + h * 0.25f,
                lerpRgb(Theme.BORDER, Theme.PURPLE_LIGHT, Math.max(on, h * 0.6f)), 0.6f + 0.35f * Math.max(on, h),
                lerpRgb(lerpRgb(0x1E1E28, 0x2B2B3B, h), lerpRgb(0x8B3FF0, 0xA855F7, h), on),
                lerpRgb(0x14141C, 0x5B2BC0, on), 1f);
        String lbl = m.enabled() ? "ON" : "OFF";
        txt(c, lbl, (x1 + x2) / 2f - tw(lbl, 11) / 2f, (y1 + y2) / 2f - 5.5f, 11, lerpRgb(Theme.MUTED, 0xFFFFFF, on), 1f);
        addHit(a, b, d, e, () -> { m.setEnabled(!m.enabled()); ModuleManager.requestSave(); });
    }

    private void drawMiniOpt(DrawContext c, int mx, int my, float x1, float y1, float x2, float y2, Module m, boolean isSel, Runnable run) {
        int a = X(x1), b = Y(y1), d = X(x2), e = Y(y2);
        float h = hover("opt:" + m.name, inRect(mx, my, a, b, d, e));
        float s = Math.max(h, isSel ? 1f : 0f);
        glass(c, a, b, d, e, radius(6, 2), s * 0.5f, lerpRgb(Theme.BORDER, Theme.PURPLE_LIGHT, s), 0.6f + 0.35f * s,
                lerpRgb(0x1E1E28, 0x2E2447, s), 0x14141C, 0.95f);
        String lbl = "Opt >";
        txt(c, lbl, (x1 + x2) / 2f - tw(lbl, 10) / 2f, (y1 + y2) / 2f - 5f, 10, 0xFFFFFF, 1f);
        addHit(a, b, d, e, run);
    }

    // =====================================================================
    //  BlockESP page (block picker + color/range/tracer)
    // =====================================================================

    /** Cached, filtered block list. Rebuilt only when the query / selection filter changes. */
    private List<BlockESP.BlockInfo> blockView(BlockESP esp) {
        if (blockViewDirty || !search.equals(blockViewQuery)) {
            String q = search.toLowerCase().trim();
            List<BlockESP.BlockInfo> out = new ArrayList<>();
            for (BlockESP.BlockInfo b : BlockESP.catalog()) {
                if (onlySelected && !esp.has(b.id())) continue;
                if (!q.isEmpty() && !b.key().contains(q)) continue;
                out.add(b);
            }
            blockView = out;
            blockViewQuery = search;
            blockViewDirty = false;
        }
        return blockView;
    }

    private void blockEspPage(DrawContext c, int mx, int my) {
        final BlockESP esp = ModuleManager.blockEsp();
        if (esp == null) { txt(c, "BlockESP not loaded.", 718, 340, 16, 0xFF6B6B, 1f); return; }

        txt(c, "Block ESP", 718, 326, 16, Theme.PURPLE_LIGHT, 1f);

        final boolean tr = esp.tracerOn();
        final boolean en = esp.enabled();
        btn(c, 1020, 322, 1124, 346, "Tracer: " + (tr ? "ON" : "OFF"), 11, tr, mx, my,
                () -> { esp.set("tracer", tr ? 0f : 1f); ModuleManager.requestSave(); });
        btn(c, 1130, 322, 1234, 346, "ESP: " + (en ? "ON" : "OFF"), 11, en, mx, my,
                () -> { esp.setEnabled(!esp.enabled()); ModuleManager.requestSave(); });
        btn(c, 1290, 322, 1390, 346, "< Back", 11, false, mx, my, () -> {
            nav = Nav.ALL_MODS; filterCat = Category.VISUAL; pageAt = System.currentTimeMillis();
            scroll = scrollTarget = 0; search = ""; searchFocus = false;
        });

        // ---- sliders: range + color + alpha ----
        float sx = 718, sw = 110, step = 124;
        slider(c, sx,            356, sx + sw,            "Range", esp.val("range", 64f), 8f, 128f, v -> esp.set("range", v));
        slider(c, sx + step,     356, sx + step + sw,     "Red",   esp.val("r", 0.70f), 0f, 1f, v -> esp.set("r", v));
        slider(c, sx + step * 2, 356, sx + step * 2 + sw, "Green", esp.val("g", 0.30f), 0f, 1f, v -> esp.set("g", v));
        slider(c, sx + step * 3, 356, sx + step * 3 + sw, "Blue",  esp.val("b", 1.00f), 0f, 1f, v -> esp.set("b", v));
        slider(c, sx + step * 4, 356, sx + step * 4 + sw, "Alpha", esp.val("a", 0.25f), 0f, 1f, v -> esp.set("a", v));

        // color preview
        int rgb = (Math.round(clamp01(esp.val("r", 0.70f)) * 255) << 16)
                | (Math.round(clamp01(esp.val("g", 0.30f)) * 255) << 8)
                | Math.round(clamp01(esp.val("b", 1.00f)) * 255);
        txt(c, "Color", 1338, 356, 13, 0xFFFFFF, 0.95f);
        Theme.rrect(c, X(1338), Y(378), X(1390), Y(392), radius(5, 2), Theme.argb(cur, rgb));

        // ---- info row ----
        txt(c, "Selected: " + esp.selectedCount() + " blocks   |   click a block to add / remove", 718, 406, 12, Theme.MUTED, 1f);
        btn(c, 1130, 400, 1280, 422, onlySelected ? "Show: Selected" : "Show: All", 10, onlySelected, mx, my,
                () -> { onlySelected = !onlySelected; blockViewDirty = true; scroll = scrollTarget = 0; });
        btn(c, 1290, 400, 1390, 422, "Clear all", 10, false, mx, my,
                () -> { esp.clearBlocks(); blockViewDirty = true; });

        // ---- block list (2 columns, scrollable, only visible rows are drawn) ----
        List<BlockESP.BlockInfo> list = blockView(esp);
        final float gridX = 716, gridY = 432, gridW = 666, viewBottom = 800, gap = 8, rh = 26, rg = 6;
        final float cw = (gridW - gap) / 2f;
        int rows = (int) Math.ceil(list.size() / 2.0);
        float contentH = rows * (rh + rg);
        float viewH = viewBottom - gridY;
        float maxScroll = Math.max(0, contentH - viewH);
        lastMaxScroll = maxScroll;
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll += (scrollTarget - scroll) * scrollK;
        if (Math.abs(scrollTarget - scroll) < 0.2f) scroll = scrollTarget;
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        int clipTop = Y(gridY), clipBottom = Y(viewBottom);
        c.enableScissor(X(710), clipTop, X(1390), clipBottom);
        clipT = clipTop; clipB = clipBottom;
        try {
            for (int i = 0; i < list.size(); i++) {
                int r = i / 2, col = i % 2;
                float y1 = gridY + r * (rh + rg) - scroll;
                float y2 = y1 + rh;
                if (y2 < gridY - 4 || y1 > viewBottom + 4) continue; // off-screen: no work at all

                final BlockESP.BlockInfo bi = list.get(i);
                float x1 = gridX + col * (cw + gap), x2 = x1 + cw;
                boolean sel = esp.has(bi.id());
                boolean hov = inRect(mx, my, X(x1), Y(y1), X(x2), Y(y2));
                float hc = hover("blk:" + bi.id(), hov);
                float on = hover("bsel:" + bi.id(), sel);
                float strong = Math.max(on, hc);

                glass(c, X(x1), Y(y1), X(x2), Y(y2), radius(8, 2), on * 0.35f + hc * 0.35f,
                        lerpRgb(lerpRgb(Theme.BORDER, Theme.PURPLE, hc), Theme.PURPLE_LIGHT, on), 0.55f + 0.40f * strong,
                        lerpRgb(0x1C1C26, 0x2B1F4D, on), lerpRgb(0x12121A, 0x180F30, on), 0.95f);

                String tag = sel ? "ADDED" : "+ ADD";
                float tagW = tw(tag, 10);
                String nm = bi.name();
                boolean cut = false;
                while (nm.length() > 3 && tw(nm, 12) > cw - tagW - 26) { nm = nm.substring(0, nm.length() - 1); cut = true; }
                if (cut) nm += "..";
                txt(c, nm, x1 + 9, y1 + (rh - 12) / 2f, 12, lerpRgb(0xD0D0DC, 0xFFFFFF, strong), 1f);
                txt(c, tag, x2 - tagW - 9, y1 + (rh - 10) / 2f, 10, sel ? Theme.PURPLE_LIGHT : Theme.MUTED, 1f);

                addHit(X(x1), Y(y1), X(x2), Y(y2), () -> { esp.toggleBlock(bi.id()); blockViewDirty = true; });
            }
        } finally {
            c.disableScissor();
            clipT = Integer.MIN_VALUE; clipB = Integer.MAX_VALUE;
        }

        if (maxScroll > 0) {
            int sbX1 = X(1392), sbX2 = X(1397), sbY1 = Y(gridY), sbY2 = Y(viewBottom);
            Theme.rrect(c, sbX1, sbY1, sbX2, sbY2, 2, Theme.argb(0.4f * cur, Theme.BORDER));
            float ratio = viewH / contentH;
            int thumbH = Math.max(18, Math.round((sbY2 - sbY1) * ratio));
            int thumbY = sbY1 + Math.round(((sbY2 - sbY1) - thumbH) * (scroll / maxScroll));
            Theme.rrect(c, sbX1 - 1, thumbY - 1, sbX2 + 1, thumbY + thumbH + 1, 3, Theme.argb(0.18f * cur, Theme.PURPLE));
            Theme.rrect(c, sbX1, thumbY, sbX2, thumbY + thumbH, 2, Theme.argb(0.9f * cur, Theme.PURPLE));
        }

        if (list.isEmpty()) {
            txt(c, onlySelected ? "No blocks selected yet." : "No blocks found matching your search.", 720, 450, 16, Theme.MUTED, 1f);
        }
    }

    // =====================================================================
    //  home / settings
    // =====================================================================

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
        btn(c, 1000, 380, 1262, 428, "Reset All Settings", 17, false, mx, my, () -> safe("reset all", () -> { ModuleManager.resetAll(); selected = null; }));
        txt(c, "Menu key: RIGHT SHIFT  (เปลี่ยนได้ใน Options > Controls > VOID CLIENT)", 720, 456, 15, 0xFFFFFF, 1f);
        txt(c, "Minecraft 1.21.11  |  Fabric  |  Java 21", 720, 488, 15, Theme.MUTED, 1f);
        txt(c, "Sodium: " + (ModuleManager.sodiumLoaded() ? "detected" : "not installed (แนะนำให้ติดตั้ง)"), 720, 518, 15, Theme.MUTED, 1f);
        txt(c, "Config: config/voidclient.json (บันทึกอัตโนมัติ)", 720, 548, 15, Theme.MUTED, 1f);
        txt(c, "HUD color: Cyber Violet  |  ไม่มีระบบอัตโนมัติ / ไม่แก้ไฟล์เกมต้นฉบับ", 720, 578, 15, Theme.MUTED, 1f);
    }

    // =====================================================================
    //  input
    // =====================================================================

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        searchFocus = false;
        for (Sl s : sliders) if (mx >= s.x1 && mx <= s.x2 && my >= s.y1 && my <= s.y2) { active = s; dragSlider(mx); return true; }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x1 && mx <= h.x2 && my >= h.y1 && my <= h.y2) {
                safe("click", h.run);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private void dragSlider(double mx) {
        if (active == null) return;
        float t = (float) ((mx - active.x1) / (active.x2 - active.x1));
        t = Math.max(0f, Math.min(1f, t));
        final Sl s = active;
        final float v = s.min + t * (s.max - s.min);
        safe("slider", () -> s.set.accept(v));
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (active != null) { dragSlider(click.x()); return true; }
        return super.mouseDragged(click, dx, dy);
    }

    @Override public boolean mouseReleased(Click click) {
        if (active != null) { active = null; ModuleManager.requestSave(); return true; }
        return super.mouseReleased(click);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (nav == Nav.ALL_MODS || nav == Nav.BLOCKESP) {
            scrollTarget = Math.max(0f, Math.min(lastMaxScroll, scrollTarget - (float) (verticalAmount * 34f)));
        }
        return true;
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (searchFocus) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                scrollTarget = 0;
                return true;
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override public boolean charTyped(CharInput input) {
        if (searchFocus && input.codepoint() >= 32 && !Character.isISOControl(input.codepoint())) {
            if (search.length() < MAX_SEARCH) {
                search += Character.toString(input.codepoint());
                scrollTarget = 0;
            }
            return true;
        }
        return super.charTyped(input);
    }

    @Override public void removed() {
        ModuleManager.flushSave();
        super.removed();
    }

    @Override public void close() {
        ModuleManager.flushSave();
        super.close();
    }
}
