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
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoidTitleScreen extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Star(float x, float y, float sz, float a, float speed) {}
    private record LabelTask(String text, float x, float y, float scale, int color, boolean shadow, float rot) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<LabelTask> texts = new ArrayList<>();
    private final long openAt = System.currentTimeMillis();

    private SkinTextures cachedMojangSkin;
    private Identifier customHeadTex;
    private String lastFetchedUser = "";

    public VoidTitleScreen() {
        super(Text.literal("VOID CLIENT"));
        Random r = new Random(2026);
        for (int i = 0; i < 65; i++) {
            stars.add(new Star(r.nextFloat(), r.nextFloat(), 1.2f + r.nextFloat() * 2.6f, 0.25f + r.nextFloat() * 0.65f, 4f + r.nextFloat() * 12f));
        }
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private void addTxt(String s, float x, float y, int color, boolean shadow) {
        texts.add(new LabelTask(s, x, y, 1.0f, color, shadow, 0f));
    }

    private void addScaledTxt(String s, float x, float y, float scale, int color, boolean shadow, float rot) {
        texts.add(new LabelTask(s, x, y, scale, color, shadow, rot));
    }

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

        // 2. ค้นหาจาก Mojang API + ดึงรูปหัวสกินจริงจาก Minotar/Crafthead (รองรับทั้งไอดีแท้และ Launcher)
        CompletableFuture.runAsync(() -> {
            HttpClient http = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(6))
                    .build();

            // 2A: ลอง Mojang Profile
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://mojang.com" + username))
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

            // 2B: โหลดภาพหัวสกินจากชื่อรหัสโดยตรง (รองรับ TLauncher / Mojang / Ely.by)
            String[] avatarUrls = {
                    "https://minotar.net" + username + "/64.png",
                    "https://mc-heads.net" + username + "/64"
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

    private void drawStyledBtn(DrawContext c, int x, int y, int w, int h, int iconType, String label, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int rad = 7;
        if (hov) Theme.rrect(c, x - 3, y - 3, x + w + 3, y + h + 3, rad + 3, Theme.argb(0.35f, Theme.PURPLE));

        int fill = hov ? Theme.argb(0.84f, 0x261740) : Theme.argb(0.72f, 0x130C22);
        int border = hov ? Theme.argb(1.0f, Theme.PURPLE_LIGHT) : Theme.argb(0.8f, Theme.PURPLE);
        Theme.strokeCard(c, x, y, x + w, y + h, rad, fill, border);

        c.fill(x + h, y + 3, x + h + 1, y + h - 3, Theme.argb(0.55f, Theme.PURPLE));

        // Draw crisp vector-style pixel icon inside left box
        int ic = hov ? 0xFFFFFFFF : (0xFF000000 | Theme.PURPLE_LIGHT);
        int cx = x + h / 2;
        int cy = y + h / 2;
        drawVectorIcon(c, iconType, cx, cy, ic);

        int textAreaX = x + h;
        int textAreaW = w - h;
        addTxt(label, textAreaX + (textAreaW - textRenderer.getWidth(label)) / 2f, y + (h - 8) / 2f, 0xFFFFFFFF, true);

        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    private void drawMiniIconBtn(DrawContext c, int x, int y, int size, int iconType, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + size && my >= y && my <= y + size;
        if (hov) Theme.rrect(c, x - 2, y - 2, x + size + 2, y + size + 2, 8, Theme.argb(0.35f, Theme.PURPLE));
        Theme.strokeCard(c, x, y, x + size, y + size, 6,
                hov ? Theme.argb(0.85f, 0x2A1A46) : Theme.argb(0.75f, 0x140D24),
                hov ? Theme.argb(1.0f, Theme.PURPLE_LIGHT) : Theme.argb(0.8f, Theme.PURPLE));
        int ic = hov ? 0xFFFFFFFF : (0xFF000000 | Theme.PURPLE_LIGHT);
        drawVectorIcon(c, iconType, x + size / 2, y + size / 2, ic);
        hits.add(new Hit(x, y, x + size, y + size, action));
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
            case 6 -> { // Void V Emblem (Bottom-left 1)
                                for (int i = 0; i < 6; i++) {
                    c.fill(cx - 5 + i / 2, cy - 3 + i, cx - 3 + i / 2, cy - 2 + i, col);
                    c.fill(cx + 3 - i / 2, cy - 3 + i, cx + 5 - i / 2, cy - 2 + i, col);
                }
            }
            case 7 -> { // Globe Icon (Bottom-left 2 - Language)
                c.fill(cx - 3, cy - 5, cx + 4, cy - 4, col);
                c.fill(cx - 3, cy + 4, cx + 4, cy + 5, col);
                c.fill(cx - 5, cy - 3, cx - 4, cy + 4, col);
                c.fill(cx + 4, cy - 3, cx + 5, cy + 4, col);
                c.fill(cx - 4, cy, cx + 5, cy + 1, col);
                c.fill(cx, cy - 4, cx + 1, cy + 5, col);
            }
            case 8 -> { // Accessibility Person Icon (Bottom-left 3)
                c.fill(cx - 1, cy - 5, cx + 2, cy - 3, col);
                c.fill(cx - 5, cy - 2, cx + 6, cy - 1, col);
                c.fill(cx - 1, cy - 1, cx + 2, cy + 2, col);
                c.fill(cx - 3, cy + 2, cx - 1, cy + 5, col);
                c.fill(cx + 2, cy + 2, cx + 4, cy + 5, col);
            }
        }
    }

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
        // ขยับออกไปทางซ้ายให้มีช่องไฟห่างจากเมนูตรงกลางมากขึ้น
        int cx = Math.max(14, width / 2 - 278);
        int cy = height / 2 - cardH / 2 + 4;

        Theme.rrect(c, cx - 3, cy - 3, cx + cardW + 3, cy + cardH + 3, 13, Theme.argb(0.28f, Theme.PURPLE));
        Theme.strokeCard(c, cx, cy, cx + cardW, cy + cardH, 10, Theme.argb(0.78f, 0x110B1E), Theme.argb(0.88f, Theme.PURPLE));

        Theme.rrect(c, cx + 14, cy + 8, cx + cardW - 14, cy + 22, 5, Theme.argb(0.85f, 0x2E1A52));
        String tag = "PROFILE";
        addTxt(tag, cx + (cardW - textRenderer.getWidth(tag)) / 2f, cy + 11, 0xFF000000 | Theme.PURPLE_LIGHT, false);

        int skinSize = 62;
        int sx = cx + (cardW - skinSize) / 2;
        int sy = cy + 28;
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

    // การ์ดข้อมูล Client & Clock ทางขวาของหน้าจอ (ช่วยให้หน้าจอสมดุลและไม่โล่ง)
    private void drawRightStatusCard(DrawContext c, int mx, int my) {
        if (width < 580) return; // ซ่อนอัตโนมัติถ้าจอเล็กมาก
        int cardW = 124;
        int cardH = 156;
        int cx = Math.min(width - cardW - 14, width / 2 + 154);
        int cy = height / 2 - cardH / 2 + 4;

        Theme.rrect(c, cx - 3, cy - 3, cx + cardW + 3, cy + cardH + 3, 13, Theme.argb(0.24f, Theme.PURPLE));
        Theme.strokeCard(c, cx, cy, cx + cardW, cy + cardH, 10, Theme.argb(0.75f, 0x110B1E), Theme.argb(0.82f, Theme.PURPLE));

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

        // Quick Edit HUD Button inside Right Card
        int bx = cx + 12, by = cy + 102, bw = cardW - 24, bh = 22;
        boolean hov = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        Theme.strokeCard(c, bx, by, bx + bw, by + bh, 6,
                hov ? Theme.argb(0.9f, 0x7E22CE) : Theme.argb(0.8f, 0x5B21B6),
                Theme.argb(0.95f, Theme.PURPLE_LIGHT));
        String hudLbl = "Edit HUD";
        addTxt(hudLbl, bx + (bw - textRenderer.getWidth(hudLbl)) / 2f, by + 7, 0xFFFFFFFF, true);
        hits.add(new Hit(bx, by, bx + bw, by + bh, () -> client.setScreen(new HudEditScreen())));

        String ver = "Release v1.0.0";
        addTxt(ver, cx + (cardW - textRenderer.getWidth(ver)) / 2f, cy + 132, 0xFF000000 | Theme.MUTED, false);
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear();
        texts.clear();

        renderPanoramaBackground(c, delta);
        c.fillGradient(0, 0, width, height, Theme.argb(0.64f, 0x090414), Theme.argb(0.82f, 0x1A0A36));

        // Ambient Nebula Glow Behind Title
        int centerX = width / 2;
        int topY = Math.max(28, height / 2 - 112);
        Theme.rrect(c, centerX - 150, topY - 12, centerX + 150, topY + 42, 24, Theme.argb(0.18f, Theme.PURPLE));

        // Animated Stars & Shooting Star Effect
        float time = (System.currentTimeMillis() - openAt) / 1000f;
        for (Star s : stars) {
            int sx = (int) ((s.x * width + time * s.speed) % width);
            int sy = (int) ((s.y * height + (float) Math.sin(time * 1.4f + s.x * 10f) * 5f + height) % height);
            int sz = Math.round(s.sz);
            Theme.rrect(c, sx, sy, sx + sz, sy + sz, sz / 2, Theme.argb(s.a * 0.8f, Theme.PURPLE_LIGHT));
        }

        // Shooting star streak every few seconds
        float shootCycle = (time % 5f) / 5f;
        if (shootCycle < 0.25f) {
            float p = shootCycle / 0.25f;
            int shX = (int) (width * (0.85f - p * 0.45f));
            int shY = (int) (height * (0.08f + p * 0.22f));
            c.fill(shX, shY, shX + 14, shY + 2, Theme.argb((1f - p) * 0.85f, 0xFFFFFF));
            c.fill(shX + 14, shY - 1, shX + 28, shY + 1, Theme.argb((1f - p) * 0.45f, Theme.PURPLE_LIGHT));
        }

        // Side Cards (Left: Profile Skin | Right: Void Status)
        drawLeftAccountCard(c);
        drawRightStatusCard(c, mx, my);

        // Center Title Logo
        String title = "VOID CLIENT";
        float titleScale = 2.8f;
        float tw = textRenderer.getWidth(title) * titleScale;
        addScaledTxt(title, centerX - tw / 2f + 2f, topY + 2f, titleScale, Theme.argb(0.85f, 0x3B0764), false, 0f);
        addScaledTxt(title, centerX - tw / 2f, topY, titleScale, 0xFFFFFFFF, false, 0f);

        String sub = "MINECRAFT 1.21.11  •  FABRIC EDITION";
        addTxt(sub, centerX - textRenderer.getWidth(sub) / 2f, topY + 30, 0xFF000000 | Theme.PURPLE_LIGHT, true);

        float pulse = 1.0f + 0.06f * (float) Math.sin(time * 4f);
        addScaledTxt("Have fun!", centerX + 112, topY + 20, pulse, 0xFFE879F9, true, -0.32f);

        // Main Center Menu Buttons (with Graphic Icons)
        int btnW = 220, btnH = 24, btnX = centerX - btnW / 2, startY = topY + 52, gap = 29;
        drawStyledBtn(c, btnX, startY, btnW, btnH, 1, "Singleplayer", mx, my, () -> client.setScreen(new SelectWorldScreen(this)));
        drawStyledBtn(c, btnX, startY + gap, btnW, btnH, 2, "Multiplayer", mx, my, () -> client.setScreen(new MultiplayerScreen(this)));
        drawStyledBtn(c, btnX, startY + gap * 2, btnW, btnH, 3, "Void Client Menu", mx, my, () -> client.setScreen(new VoidScreen()));

        int halfW = (btnW - 8) / 2;
        drawStyledBtn(c, btnX, startY + gap * 3, halfW, btnH, 4, "Options...", mx, my, () -> client.setScreen(new OptionsScreen(this, client.options)));
        drawStyledBtn(c, btnX + halfW + 8, startY + gap * 3, halfW, btnH, 5, "Quit Game", mx, my, () -> client.scheduleStop());

        // Bottom-Left Graphic Icon Buttons (Void Logo, Globe, Accessibility)
        int bSize = 24, by = height - 42;
        drawMiniIconBtn(c, 16, by, bSize, 6, mx, my, () -> client.setScreen(new VoidScreen()));
        drawMiniIconBtn(c, 46, by, bSize, 7, mx, my, () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())));
        drawMiniIconBtn(c, 76, by, bSize, 8, mx, my, () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options)));

        // Bottom-Left Footer (ลบ Copyright Mojang ด้านขวาออกแล้ว)
        addTxt("Minecraft 1.21.11  |  Logged in as: " + getAccountName(), 16, height - 12, 0xFF000000 | Theme.MUTED, true);

        // Render ALL Texts LAST
        var m = c.getMatrices();
        for (LabelTask t : texts) {
            m.pushMatrix();
            m.translate(t.x, t.y);
            if (t.rot != 0f) m.rotate(t.rot);
            if (t.scale != 1.0f) m.scale(t.scale, t.scale);
            c.drawText(textRenderer, t.text, 0, 0, t.color, t.shadow);
            m.popMatrix();
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
