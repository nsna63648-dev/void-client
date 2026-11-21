package com.voidclient.gui;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.voidclient.module.Module;
import com.voidclient.module.ModuleManager;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoidTitleScreen extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Star(float x, float y, float sz, float a, float speed) {}
    private record Particle(float x, float y, float r, float speed, float drift, float phase, float a, boolean pink) {}
    private record LabelTask(String text, float x, float y, float scale, int color, boolean shadow, float rot, boolean center) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<LabelTask> texts = new ArrayList<>();
    private final Map<String, Float> anim = new HashMap<>();
    private final long openAt = System.currentTimeMillis();

    private SkinTextures cachedMojangSkin;
    private Identifier customHeadTex;
    private String lastFetchedUser = "";

    // ---- frame state ----
    private float time = 0f;
    private long lastNanos = System.nanoTime();
    private float lerpK = 0.2f;

    // ---- tooltip state ----
    private String tipText = null;
    private float tipAlpha = 0f;
    private int tipMx, tipMy;

    public VoidTitleScreen() {
        super(Text.literal("VOID CLIENT"));
        Random r = new Random(2026);
        for (int i = 0; i < 65; i++) {
            stars.add(new Star(r.nextFloat(), r.nextFloat(), 1.2f + r.nextFloat() * 2.6f, 0.25f + r.nextFloat() * 0.65f, 4f + r.nextFloat() * 12f));
        }
        for (int i = 0; i < 48; i++) {
            particles.add(new Particle(
                    r.nextFloat(), r.nextFloat(),
                    1f + r.nextFloat() * 2f,
                    5f + r.nextFloat() * 11f,
                    r.nextFloat() * 4f - 2f,
                    r.nextFloat() * 6.28f,
                    0.25f + r.nextFloat() * 0.5f,
                    r.nextFloat() < 0.25f));
        }
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    // ===================== helpers =====================

    private void addTxt(String s, float x, float y, int color, boolean shadow) {
        texts.add(new LabelTask(s, x, y, 1.0f, color, shadow, 0f, false));
    }

    private void addScaledTxt(String s, float x, float y, float scale, int color, boolean shadow, float rot) {
        texts.add(new LabelTask(s, x, y, scale, color, shadow, rot, false));
    }

    private void addCenteredTxt(String s, float x, float y, float scale, int color, boolean shadow, float rot) {
        texts.add(new LabelTask(s, x, y, scale, color, shadow, rot, true));
    }

    private static int lerpRgb(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Smooth (lerped + smoothstep) hover value 0..1 for a given key. */
    private float hover(String key, boolean hov) {
        float target = hov ? 1f : 0f;
        float cur = anim.getOrDefault(key, 0f);
        cur += (target - cur) * lerpK;
        if (Math.abs(target - cur) < 0.003f) cur = target;
        anim.put(key, cur);
        return cur * cur * (3f - 2f * cur);
    }

    /** Glassmorphism panel: neon glow + dark translucent gradient + top highlight + border. */
    private void glassPanel(DrawContext c, int x1, int y1, int x2, int y2, int rad,
                            float glow, int borderRgb, float borderA,
                            int topRgb, int botRgb, float fillA) {
        if (glow > 0.01f) {
            for (int i = 4; i >= 1; i--) {
                Theme.rrect(c, x1 - i, y1 - i, x2 + i, y2 + i, rad + i, Theme.argb(glow * 0.085f, Theme.PURPLE));
            }
        }
        Theme.strokeCard(c, x1, y1, x2, y2, rad, Theme.argb(fillA, botRgb), Theme.argb(borderA, borderRgb));
        int in = Math.max(2, rad / 2);
        c.fillGradient(x1 + in, y1 + in, x2 - in, y2 - in, Theme.argb(fillA * 0.9f, topRgb), Theme.argb(fillA, botRgb));
        // glass top highlight
        c.fill(x1 + rad, y1 + 1, x2 - rad, y1 + 2, Theme.argb(0.10f + 0.10f * glow, 0xFFFFFF));
    }

    // ===================== skin fetching =====================

    private String getAccountName() {
        if (client != null && client.getSession() != null) {
            String name = client.getSession().getUsername();
            if (name != null && !name.isBlank()) return name;
        }
        return "Player";
    }

    private void triggerSkinFetch(String username, UUID sessionUuid) {
        lastFetchedUser = username;
        cachedMojangSkin = null;
        customHeadTex = null;

        // 1. ลองดึงจาก Session UUID โดยตรง
        GameProfile localProfile = new GameProfile(sessionUuid, username);
        client.getSkinProvider().fetchSkinTextures(localProfile).thenAccept(opt -> opt.ifPresent(st -> cachedMojangSkin = st));

        // 2. ค้นหาจาก Mojang API + ดึงรูปหัวสกินจริงจาก Minotar / MC-Heads
        CompletableFuture.runAsync(() -> {
            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(6))
                    .build();

            // 2A: Mojang Profile
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200 && res.body() != null && !res.body().isBlank()) {
                    var json = JsonParser.parseString(res.body()).getAsJsonObject();
                    String rawId = json.get("id").getAsString();
                    String realName = json.get("name").getAsString();
                    String dashed = rawId.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                    GameProfile realProfile = new GameProfile(UUID.fromString(dashed), realName);
                    client.execute(() -> client.getSkinProvider().fetchSkinTextures(realProfile).thenAccept(opt -> opt.ifPresent(st -> cachedMojangSkin = st)));
                }
            } catch (Exception ignored) {}

            // 2B: โหลดภาพหัวสกินจากชื่อโดยตรง (รองรับ TLauncher / Mojang / Ely.by)
            String[] avatarUrls = {
                    "https://minotar.net/helm/" + username + "/64.png",
                    "https://mc-heads.net/avatar/" + username + "/64"
            };
            for (String url : avatarUrls) {
                try {
                    HttpRequest imgReq = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("User-Agent", "VoidClient/1.0")
                            .timeout(Duration.ofSeconds(6)).GET().build();
                    HttpResponse<InputStream> imgRes = http.send(imgReq, HttpResponse.BodyHandlers.ofInputStream());
                    if (imgRes.statusCode() == 200) {
                        NativeImage img = NativeImage.read(imgRes.body());
                        Identifier id = Identifier.of("voidclient", "head_" + username.toLowerCase().replaceAll("[^a-z0-9_]", "_"));
                        client.execute(() -> {
                            client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> id.toString(), img));
                            customHeadTex = id;
                        });
                        break;
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    // ===================== buttons =====================

    private void drawStyledBtn(DrawContext c, int x, int y, int w, int h, int iconType, String label, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        float e = hover("btn:" + label, hov);
        int rad = 7;

        int ex = Math.round(e * 2f), ey = Math.round(e * 1f);
        int x1 = x - ex, y1 = y - ey, x2 = x + w + ex, y2 = y + h + ey;

        glassPanel(c, x1, y1, x2, y2, rad,
                e, lerpRgb(Theme.PURPLE, Theme.PURPLE_LIGHT, e), 0.78f + 0.22f * e,
                lerpRgb(0x24173F, 0x3C2470, e), 0x0D0818, 0.74f + 0.12f * e);

        // icon box tint + separator
        if (e > 0.01f) Theme.rrect(c, x + 3, y + 3, x + h - 3, y + h - 3, 5, Theme.argb(0.16f * e, Theme.PURPLE_LIGHT));
        c.fill(x + h, y + 3, x + h + 1, y + h - 3, Theme.argb(0.35f + 0.30f * e, Theme.PURPLE));

        int ic = 0xFF000000 | lerpRgb(Theme.PURPLE_LIGHT, 0xFFFFFF, e);
        drawVectorIcon(c, iconType, x + h / 2, y + h / 2, ic);

        int textAreaX = x + h;
        int textAreaW = w - h;
        int tc = 0xFF000000 | lerpRgb(0xD8CCF5, 0xFFFFFF, e);
        addTxt(label, textAreaX + (textAreaW - textRenderer.getWidth(label)) / 2f + e * 1.5f, y + (h - 8) / 2f, tc, true);

        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    private void drawMiniIconBtn(DrawContext c, int x, int y, int size, int iconType, String tooltip, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + size && my >= y && my <= y + size;
        float e = hover("mini:" + iconType, hov);
        int ex = Math.round(e * 1.5f);

        glassPanel(c, x - ex, y - ex, x + size + ex, y + size + ex, 6,
                e, lerpRgb(Theme.PURPLE, Theme.PURPLE_LIGHT, e), 0.78f + 0.22f * e,
                lerpRgb(0x261848, 0x3F2675, e), 0x0D0818, 0.74f + 0.12f * e);

        int ic = 0xFF000000 | lerpRgb(Theme.PURPLE_LIGHT, 0xFFFFFF, e);
        drawVectorIcon(c, iconType, x + size / 2, y + size / 2, ic);
        hits.add(new Hit(x, y, x + size, y + size, action));

        if (hov) {
            tipText = tooltip;
            tipAlpha = Math.max(0f, Math.min(1f, (e - 0.2f) / 0.6f));
            tipMx = mx;
            tipMy = my;
        }
    }

    // วาดไอคอนกราฟิกคมชัดด้วยพิกเซล (ไม่ใช้ตัวอักษร)
    private void drawVectorIcon(DrawContext c, int type, int cx, int cy, int col) {
        switch (type) {
            case 1 -> { // Play Triangle (Singleplayer)
                for (int i = -4; i <= 4; i++) {
                    int w = (4 - Math.abs(i)) + 1;
                    c.fill(cx - 2, cy + i, cx - 2 + w, cy + i + 1, col);
                }
            }
            case 2 -> { // Two People (Multiplayer)
                c.fill(cx - 4, cy - 3, cx - 1, cy, col);
                c.fill(cx - 5, cy + 1, cx, cy + 4, col);
                c.fill(cx + 1, cy - 4, cx + 4, cy - 1, col);
                c.fill(cx, cy, cx + 5, cy + 3, col);
            }
            case 3 -> { // Diamond / Star (Void Menu)
                for (int i = -4; i <= 4; i++) {
                    int w = 4 - Math.abs(i);
                    c.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, col);
                }
            }
            case 4 -> { // Gear / Sliders (Options)
                c.fill(cx - 4, cy - 3, cx + 5, cy - 2, col);
                c.fill(cx - 4, cy, cx + 5, cy + 1, col);
                c.fill(cx - 4, cy + 3, cx + 5, cy + 4, col);
                c.fill(cx - 2, cy - 4, cx, cy - 1, 0xFFFFFFFF);
                c.fill(cx + 1, cy - 1, cx + 3, cy + 2, 0xFFFFFFFF);
            }
            case 5 -> { // X Close (Quit)
                for (int i = -3; i <= 3; i++) {
                    c.fill(cx + i, cy + i, cx + i + 2, cy + i + 1, col);
                    c.fill(cx + i, cy - i, cx + i + 2, cy - i + 1, col);
                }
            }
            case 6 -> { // Void V Emblem
                for (int i = 0; i < 6; i++) {
                    c.fill(cx - 5 + i / 2, cy - 3 + i, cx - 3 + i / 2, cy - 2 + i, col);
                    c.fill(cx + 3 - i / 2, cy - 3 + i, cx + 5 - i / 2, cy - 2 + i, col);
                }
            }
            case 7 -> { // Globe Icon (Language)
                c.fill(cx - 3, cy - 5, cx + 4, cy - 4, col);
                c.fill(cx - 3, cy + 4, cx + 4, cy + 5, col);
                c.fill(cx - 5, cy - 3, cx - 4, cy + 4, col);
                c.fill(cx + 4, cy - 3, cx + 5, cy + 4, col);
                c.fill(cx - 4, cy, cx + 5, cy + 1, col);
                c.fill(cx, cy - 4, cx + 1, cy + 5, col);
            }
            case 8 -> { // Accessibility Person Icon
                c.fill(cx - 1, cy - 5, cx + 2, cy - 3, col);
                c.fill(cx - 5, cy - 2, cx + 6, cy - 1, col);
                c.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
                c.fill(cx - 3, cy + 2, cx - 1, cy + 5, col);
                c.fill(cx + 2, cy + 2, cx + 4, cy + 5, col);
            }
        }
    }

    // ===================== side cards =====================

    private void drawLeftAccountCard(DrawContext c) {
        String username = getAccountName();
        var session = client.getSession();
        UUID sessionUuid = (session != null && session.getUuidOrNull() != null)
                ? session.getUuidOrNull()
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());

        if (!username.equals(lastFetchedUser)) {
            triggerSkinFetch(username, sessionUuid);
        }

        int cardW = 124;
        int cardH = 156;
        int cx = Math.max(14, width / 2 - 278);
        int bob = Math.round((float) Math.sin(time * 1.2f) * 1.5f);
        int cy = height / 2 - cardH / 2 + 4 + bob;

        glassPanel(c, cx, cy, cx + cardW, cy + cardH, 10,
                0.55f, Theme.PURPLE, 0.88f, 0x22153C, 0x0C0716, 0.78f);

        Theme.rrect(c, cx + 14, cy + 8, cx + cardW - 14, cy + 22, 5, Theme.argb(0.85f, 0x2E1A52));
        String tag = "PROFILE";
        addTxt(tag, cx + (cardW - textRenderer.getWidth(tag)) / 2f, cy + 11, 0xFF000000 | Theme.PURPLE_LIGHT, false);

        int skinSize = 62;
        int sx = cx + (cardW - skinSize) / 2;
        int sy = cy + 28;
        float skinGlow = 0.5f + 0.5f * (float) Math.sin(time * 2f);
        Theme.rrect(c, sx - 5, sy - 5, sx + skinSize + 5, sy + skinSize + 5, 10, Theme.argb(0.10f + 0.10f * skinGlow, Theme.PURPLE_LIGHT));
        Theme.strokeCard(c, sx - 3, sy - 3, sx + skinSize + 3, sy + skinSize + 3, 8, Theme.argb(0.95f, 0x1B1130), Theme.argb(0.9f, Theme.PURPLE_LIGHT));

        if (customHeadTex != null) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, customHeadTex, sx, sy, 0f, 0f, skinSize, skinSize, 64, 64, 64, 64);
        } else if (cachedMojangSkin != null) {
            PlayerSkinDrawer.draw(c, cachedMojangSkin, sx, sy, skinSize);
        } else {
            PlayerSkinDrawer.draw(c, DefaultSkinHelper.getSkinTextures(new GameProfile(sessionUuid, username)), sx, sy, skinSize);
        }

        int plateY = cy + 98;
        Theme.strokeCard(c, cx + 10, plateY, cx + cardW - 10, plateY + 24, 6, Theme.argb(0.9f, 0x1F1338), Theme.argb(0.85f, Theme.PURPLE));
        String dispName = username;
        while (dispName.length() > 3 && textRenderer.getWidth(dispName) > cardW - 26) {
            dispName = dispName.substring(0, dispName.length() - 1);
        }
        if (!dispName.equals(username)) dispName += "..";
        addTxt(dispName, cx + (cardW - textRenderer.getWidth(dispName)) / 2f, plateY + 8, 0xFFFFFFFF, true);

        boolean loaded = (customHeadTex != null || cachedMojangSkin != null);
        String status = loaded ? "● Skin Ready" : "● Loading Skin..";
        addTxt(status, cx + (cardW - textRenderer.getWidth(status)) / 2f, plateY + 31, loaded ? 0xFF4ADE80 : 0xFFFACC15, false);
    }

    private void drawRightStatusCard(DrawContext c, int mx, int my) {
        if (width < 580) return; // ซ่อนอัตโนมัติถ้าจอเล็กมาก
        int cardW = 124;
        int cardH = 156;
        int cx = Math.min(width - cardW - 14, width / 2 + 154);
        int bob = Math.round((float) Math.sin(time * 1.2f + 1.8f) * 1.5f);
        int cy = height / 2 - cardH / 2 + 4 + bob;

        glassPanel(c, cx, cy, cx + cardW, cy + cardH, 10,
                0.50f, Theme.PURPLE, 0.82f, 0x22153C, 0x0C0716, 0.76f);

        Theme.rrect(c, cx + 14, cy + 8, cx + cardW - 14, cy + 22, 5, Theme.argb(0.85f, 0x2E1A52));
        String tag = "VOID STATUS";
        addTxt(tag, cx + (cardW - textRenderer.getWidth(tag)) / 2f, cy + 11, 0xFF000000 | Theme.PURPLE_LIGHT, false);

        // Digital Clock
        String clock = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        addScaledTxt(clock, cx + (cardW - textRenderer.getWidth(clock) * 1.8f) / 2f, cy + 32, 1.8f, 0xFFFFFFFF, true, 0f);

        // Active Modules Count
        long activeCount = ModuleManager.ALL.stream().filter(Module::enabled).count();
        String modTxt = "Mods: " + activeCount + "/" + ModuleManager.ALL.size();
        addTxt(modTxt, cx + (cardW - textRenderer.getWidth(modTxt)) / 2f, cy + 60, 0xFF000000 | Theme.PURPLE_LIGHT, false);

        // FPS Counter
        String fpsTxt = "FPS: " + client.getCurrentFps();
        addTxt(fpsTxt, cx + (cardW - textRenderer.getWidth(fpsTxt)) / 2f, cy + 76, 0xFF4ADE80, false);

        // Quick Edit HUD Button
        int bx = cx + 12, by = cy + 102, bw = cardW - 24, bh = 22;
        boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        float e = hover("hudbtn", hov);
        int ex = Math.round(e * 1.5f);
        glassPanel(c, bx - ex, by - ex, bx + bw + ex, by + bh + ex, 6,
                e, lerpRgb(Theme.PURPLE, Theme.PURPLE_LIGHT, e), 0.95f,
                lerpRgb(0x7E22CE, 0xA855F7, e), 0x4C1D95, 0.88f);
        String hudLbl = "Edit HUD";
        addTxt(hudLbl, bx + (bw - textRenderer.getWidth(hudLbl)) / 2f, by + 7, 0xFFFFFFFF, true);
        hits.add(new Hit(bx, by, bx + bw, by + bh, () -> client.setScreen(new HudEditScreen())));

        String ver = "Release v1.0.0";
        addTxt(ver, cx + (cardW - textRenderer.getWidth(ver)) / 2f, cy + 132, 0xFF000000 | Theme.MUTED, false);
    }

    // ===================== title =====================

    private void drawTitleLogo(DrawContext c, int centerX, int topY) {
        String title = "VOID CLIENT";
        float scale = 2.8f;
        float totalW = textRenderer.getWidth(title) * scale;
        float startX = centerX - totalW / 2f;

        // ambient nebula glow (pulsing)
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.6f);
        for (int i = 0; i < 4; i++) {
            int g = i * 9;
            Theme.rrect(c, centerX - 150 - g, topY - 12 - g / 2, centerX + 150 + g, topY + 42 + g / 2, 24 + g,
                    Theme.argb((0.06f + 0.04f * pulse), Theme.PURPLE));
        }

        // accent line under the logo
        int lineHalf = Math.round(48 + 14 * pulse);
        c.fill(centerX - lineHalf, topY + 25, centerX + lineHalf, topY + 26, Theme.argb(0.55f + 0.30f * pulse, Theme.PURPLE_LIGHT));
        c.fill(centerX - lineHalf + 10, topY + 26, centerX + lineHalf - 10, topY + 27, Theme.argb(0.25f, Theme.PURPLE));

        // layered 3D extrude (back to front), then glow, then shimmering face
        for (int d = 5; d >= 1; d--) {
            float x = startX;
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                addScaledTxt(ch, x + d * 0.8f, topY + d * 1.0f, scale, Theme.argb(0.20f + 0.09f * (6 - d), 0x3B0764), false, 0f);
                x += textRenderer.getWidth(ch) * scale;
            }
        }
        float[][] glowOff = {{-1.6f, 0}, {1.6f, 0}, {0, -1.6f}, {0, 1.6f}};
        for (float[] o : glowOff) {
            float x = startX;
            for (int i = 0; i < title.length(); i++) {
                String ch = String.valueOf(title.charAt(i));
                addScaledTxt(ch, x + o[0], topY + o[1], scale, Theme.argb(0.20f + 0.08f * pulse, Theme.PURPLE), false, 0f);
                x += textRenderer.getWidth(ch) * scale;
            }
        }
        float x = startX;
        for (int i = 0; i < title.length(); i++) {
            String ch = String.valueOf(title.charAt(i));
            float wave = 0.5f + 0.5f * (float) Math.sin(time * 2.2f - i * 0.55f);
            int col = 0xFF000000 | lerpRgb(Theme.PURPLE_LIGHT, 0xFFFFFF, 0.35f + 0.65f * wave);
            addScaledTxt(ch, x, topY, scale, col, false, 0f);
            x += textRenderer.getWidth(ch) * scale;
        }

        String sub = "MINECRAFT 1.21.11  •  FABRIC EDITION";
        addTxt(sub, centerX - textRenderer.getWidth(sub) / 2f, topY + 31, 0xFF000000 | Theme.PURPLE_LIGHT, true);

        // Bouncy splash text
        float bounce = Math.abs((float) Math.sin(time * 3.2f));
        float splashScale = 1.0f + 0.10f * bounce;
        float splashX = Math.min(centerX + 130, width - 40);
        float splashY = topY + 22 - bounce * 5f;
        float splashRot = -0.32f + 0.04f * (float) Math.sin(time * 3.2f);
        addCenteredTxt("Have fun!", splashX + 1f, splashY + 1f, splashScale, Theme.argb(0.6f, 0x3B0764), false, splashRot);
        addCenteredTxt("Have fun!", splashX, splashY, splashScale, 0xFFE879F9, true, splashRot);
    }

    // ===================== render =====================

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear();
        texts.clear();
        tipText = null;

        long now = System.nanoTime();
        float dt = Math.max(0f, Math.min(0.1f, (now - lastNanos) / 1_000_000_000f));
        lastNanos = now;
        lerpK = 1f - (float) Math.exp(-dt * 14f);
        time = (System.currentTimeMillis() - openAt) / 1000f;

        renderPanoramaBackground(c, delta);
        c.fillGradient(0, 0, width, height, Theme.argb(0.64f, 0x090414), Theme.argb(0.82f, 0x1A0A36));

        int centerX = width / 2;
        int topY = Math.max(28, height / 2 - 112);

        // Stars (drifting + twinkle)
        for (Star s : stars) {
            int sx = (int) ((s.x * width + time * s.speed) % width);
            int sy = (int) ((s.y * height + (float) Math.sin(time * 1.4f + s.x * 10f) * 5f + height) % height);
            int sz = Math.round(s.sz);
            Theme.rrect(c, sx, sy, sx + sz, sy + sz, sz / 2, Theme.argb(s.a * 0.8f, Theme.PURPLE_LIGHT));
        }

        // Floating particles (slowly rising, soft halo)
        for (Particle p : particles) {
            float py = ((p.y * height - time * p.speed) % height + height) % height;
            float px = ((p.x * width + (float) Math.sin(time * 0.5f + p.phase) * 12f + time * p.drift) % width + width) % width;
            float tw = 0.5f + 0.5f * (float) Math.sin(time * 1.5f + p.phase);
            float a = p.a * (0.35f + 0.65f * tw);
            int rgb = p.pink ? 0xE879F9 : Theme.PURPLE_LIGHT;
            int s = Math.max(1, Math.round(p.r));
            int ix = (int) px, iy = (int) py;
            if (p.r > 1.8f) Theme.rrect(c, ix - 2, iy - 2, ix + s + 2, iy + s + 2, s / 2 + 2, Theme.argb(a * 0.22f, rgb));
            Theme.rrect(c, ix, iy, ix + s, iy + s, Math.max(1, s / 2), Theme.argb(a, rgb));
        }

        // Shooting star
        float shootCycle = (time % 5f) / 5f;
        if (shootCycle < 0.25f) {
            float p = shootCycle / 0.25f;
            int shX = (int) (width * (0.85f - p * 0.45f));
            int shY = (int) (height * (0.08f + p * 0.22f));
            c.fill(shX, shY, shX + 14, shY + 2, Theme.argb((1f - p) * 0.85f, 0xFFFFFF));
            c.fill(shX + 14, shY - 1, shX + 28, shY + 1, Theme.argb((1f - p) * 0.45f, Theme.PURPLE_LIGHT));
        }

        // Side cards
        drawLeftAccountCard(c);
        drawRightStatusCard(c, mx, my);

        // Logo + subtitle + splash
        drawTitleLogo(c, centerX, topY);

        // Main center menu
        int btnW = 220, btnH = 24, btnX = centerX - btnW / 2, startY = topY + 52, gap = 29;
        drawStyledBtn(c, btnX, startY, btnW, btnH, 1, "Singleplayer", mx, my, () -> client.setScreen(new SelectWorldScreen(this)));
        drawStyledBtn(c, btnX, startY + gap, btnW, btnH, 2, "Multiplayer", mx, my, () -> client.setScreen(new MultiplayerScreen(this)));
        drawStyledBtn(c, btnX, startY + gap * 2, btnW, btnH, 3, "Void Client Menu", mx, my, () -> client.setScreen(new VoidScreen()));

        int halfW = (btnW - 8) / 2;
        drawStyledBtn(c, btnX, startY + gap * 3, halfW, btnH, 4, "Options...", mx, my, () -> client.setScreen(new OptionsScreen(this, client.options)));
        drawStyledBtn(c, btnX + halfW + 8, startY + gap * 3, halfW, btnH, 5, "Quit Game", mx, my, () -> client.scheduleStop());

        // Bottom-left icon buttons (with tooltips)
        int bSize = 24, by = height - 42;
        drawMiniIconBtn(c, 16, by, bSize, 6, "Void Client Menu", mx, my, () -> client.setScreen(new VoidScreen()));
        drawMiniIconBtn(c, 46, by, bSize, 7, "Language", mx, my, () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())));
        drawMiniIconBtn(c, 76, by, bSize, 8, "Accessibility", mx, my, () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options)));

        // Footer
        addTxt("Minecraft 1.21.11  |  Logged in as: " + getAccountName(), 16, height - 12, 0xFF000000 | Theme.MUTED, true);

        // Render ALL texts LAST
        var m = c.getMatrices();
        for (LabelTask t : texts) {
            m.pushMatrix();
            m.translate(t.x, t.y);
            if (t.rot != 0f) m.rotate(t.rot);
            if (t.scale != 1.0f) m.scale(t.scale, t.scale);
            int ox = t.center ? -textRenderer.getWidth(t.text) / 2 : 0;
            int oy = t.center ? -4 : 0;
            c.drawText(textRenderer, t.text, ox, oy, t.color, t.shadow);
            m.popMatrix();
        }

        // Tooltip (drawn after texts so it sits on top)
        if (tipText != null && tipAlpha > 0.05f) {
            int tw = textRenderer.getWidth(tipText) + 12;
            int th = 16;
            int tx = Math.max(4, Math.min(tipMx + 10, width - tw - 4));
            int ty = Math.max(4, tipMy - th - 8);
            Theme.rrect(c, tx - 2, ty - 2, tx + tw + 2, ty + th + 2, 7, Theme.argb(0.22f * tipAlpha, Theme.PURPLE));
            Theme.strokeCard(c, tx, ty, tx + tw, ty + th, 5,
                    Theme.argb(0.94f * tipAlpha, 0x120A20),
                    Theme.argb(0.95f * tipAlpha, Theme.PURPLE_LIGHT));
            int ta = Math.max(1, Math.round(tipAlpha * 255f));
            c.drawText(textRenderer, tipText, tx + 6, ty + 4, (ta << 24) | 0xFFFFFF, true);
        }
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x1 && mx <= h.x2 && my >= h.y1 && my <= h.y2) { h.run.run(); return true; }
            }
        }
        return super.mouseClicked(click, doubled);
    }
}
