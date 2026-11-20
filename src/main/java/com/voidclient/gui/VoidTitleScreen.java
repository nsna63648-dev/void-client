package com.voidclient.gui;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screen.option.LanguageOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoidTitleScreen extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Star(float x, float y, float sz, float a) {}
    private record LabelTask(String text, float x, float y, float scale, int color, boolean shadow, float rot) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final List<LabelTask> texts = new ArrayList<>();
    private final long openAt = System.currentTimeMillis();

    private SkinTextures cachedSkin;
    private String lastFetchedUser = "";

    public VoidTitleScreen() {
        super(Text.literal("VOID CLIENT"));
        Random r = new Random(2026);
        for (int i = 0; i < 45; i++) {
            stars.add(new Star(r.nextFloat(), r.nextFloat(), 1.5f + r.nextFloat() * 2.5f, 0.25f + r.nextFloat() * 0.6f));
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

    private SkinTextures resolveAccountSkin() {
        String username = getAccountName();
        var session = client.getSession();
        UUID sessionUuid = (session != null && session.getUuidOrNull() != null)
                ? session.getUuidOrNull()
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());

        if (cachedSkin != null && username.equals(lastFetchedUser)) {
            return cachedSkin;
        }

        if (!username.equals(lastFetchedUser)) {
            lastFetchedUser = username;
            cachedSkin = null;

            // 1. ลองดึงจาก UUID ปัจจุบันก่อน
            GameProfile localProfile = new GameProfile(sessionUuid, username);
            client.getSkinProvider().fetchSkinTextures(localProfile).thenAccept(opt -> {
                if (opt.isPresent()) cachedSkin = opt.get();
            });

            // 2. ค้นหา UUID จริงจากชื่อรหัสผ่าน Mojang API (รองรับทั้งไอดีแท้และ Launcher)
            CompletableFuture.runAsync(() -> {
                try {
                    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://mojang.com" + username))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 200 && res.body() != null && !res.body().isBlank()) {
                        var json = JsonParser.parseString(res.body()).getAsJsonObject();
                        String rawId = json.get("id").getAsString();
                        String realName = json.get("name").getAsString();
                        String dashed = rawId.replaceFirst(
                                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                                "$1-$2-$3-$4-$5"
                        );
                        GameProfile realProfile = new GameProfile(UUID.fromString(dashed), realName);
                        client.execute(() -> {
                            client.getSkinProvider().fetchSkinTextures(realProfile).thenAccept(opt -> {
                                opt.ifPresent(st -> cachedSkin = st);
                            });
                        });
                    }
                } catch (Exception ignored) {}
            });
        }

        return DefaultSkinHelper.getSkinTextures(new GameProfile(sessionUuid, username));
    }

    private void drawStyledBtn(DrawContext c, int x, int y, int w, int h, String icon, String label, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int rad = 7;
        if (hov) Theme.rrect(c, x - 3, y - 3, x + w + 3, y + h + 3, rad + 3, Theme.argb(0.35f, Theme.PURPLE));

        int fill = hov ? Theme.argb(0.82f, 0x261740) : Theme.argb(0.72f, 0x130C22);
        int border = hov ? Theme.argb(1.0f, Theme.PURPLE_LIGHT) : Theme.argb(0.8f, Theme.PURPLE);
        Theme.strokeCard(c, x, y, x + w, y + h, rad, fill, border);

        c.fill(x + h, y + 3, x + h + 1, y + h - 3, Theme.argb(0.55f, Theme.PURPLE));
        addTxt(icon, x + (h - textRenderer.getWidth(icon)) / 2f, y + (h - 8) / 2f, hov ? 0xFFFFFFFF : (0xFF000000 | Theme.PURPLE_LIGHT), false);

        int textAreaX = x + h;
        int textAreaW = w - h;
        addTxt(label, textAreaX + (textAreaW - textRenderer.getWidth(label)) / 2f, y + (h - 8) / 2f, 0xFFFFFFFF, true);

        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    private void drawMiniIconBtn(DrawContext c, int x, int y, int size, String icon, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + size && my >= y && my <= y + size;
        if (hov) Theme.rrect(c, x - 2, y - 2, x + size + 2, y + size + 2, 8, Theme.argb(0.35f, Theme.PURPLE));
        Theme.strokeCard(c, x, y, x + size, y + size, 6,
                hov ? Theme.argb(0.85f, 0x2A1A46) : Theme.argb(0.75f, 0x140D24),
                hov ? Theme.argb(1.0f, Theme.PURPLE_LIGHT) : Theme.argb(0.8f, Theme.PURPLE));
        addTxt(icon, x + (size - textRenderer.getWidth(icon)) / 2f, y + (size - 8) / 2f, hov ? 0xFFFFFFFF : (0xFF000000 | Theme.PURPLE_LIGHT), false);
        hits.add(new Hit(x, y, x + size, y + size, action));
    }

    private void drawLeftAccountCard(DrawContext c) {
        int cardW = 126;
        int cardH = 154;
        int cx = Math.max(16, width / 2 - 256);
        int cy = height / 2 - cardH / 2 + 4;

        // Outer Neon Glow Card
        Theme.rrect(c, cx - 3, cy - 3, cx + cardW + 3, cy + cardH + 3, 13, Theme.argb(0.28f, Theme.PURPLE));
        Theme.strokeCard(c, cx, cy, cx + cardW, cy + cardH, 10, Theme.argb(0.78f, 0x110B1E), Theme.argb(0.88f, Theme.PURPLE));

        // Top Badge
        Theme.rrect(c, cx + 14, cy + 8, cx + cardW - 14, cy + 22, 5, Theme.argb(0.85f, 0x2E1A52));
        String tag = "ACCOUNT";
        addTxt(tag, cx + (cardW - textRenderer.getWidth(tag)) / 2f, cy + 11, 0xFF000000 | Theme.PURPLE_LIGHT, false);

        // Skin Frame (62x62)
        int skinSize = 62;
        int sx = cx + (cardW - skinSize) / 2;
        int sy = cy + 28;
        Theme.strokeCard(c, sx - 3, sy - 3, sx + skinSize + 3, sy + skinSize + 3, 8, Theme.argb(0.95f, 0x1B1130), Theme.argb(0.9f, Theme.PURPLE_LIGHT));
        PlayerSkinDrawer.draw(c, resolveAccountSkin(), sx, sy, skinSize);

        // Account Name Plate (แสดงชื่อรหัสของคนนั้น)
        int plateY = cy + 98;
        Theme.strokeCard(c, cx + 10, plateY, cx + cardW - 10, plateY + 24, 6, Theme.argb(0.9f, 0x1F1338), Theme.argb(0.85f, Theme.PURPLE));
        String username = getAccountName();
        String dispName = username;
        while (dispName.length() > 3 && textRenderer.getWidth(dispName) > cardW - 26) {
            dispName = dispName.substring(0, dispName.length() - 1);
        }
        if (!dispName.equals(username)) dispName += "..";
        addTxt(dispName, cx + (cardW - textRenderer.getWidth(dispName)) / 2f, plateY + 8, 0xFFFFFFFF, true);

        // Status Line
        String status = cachedSkin != null ? "● Skin Loaded" : "● Active ID";
        addTxt(status, cx + (cardW - textRenderer.getWidth(status)) / 2f, plateY + 31, 0xFF4ADE80, false);
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear();
        texts.clear();

        renderPanoramaBackground(c, delta);
        c.fillGradient(0, 0, width, height, Theme.argb(0.68f, 0x090414), Theme.argb(0.84f, 0x180930));

        float time = (System.currentTimeMillis() - openAt) / 1000f;
        for (Star s : stars) {
            int sx = (int) ((s.x * width + time * 8f * s.a) % width);
            int sy = (int) ((s.y * height + (float) Math.sin(time * 1.5f + s.x * 10f) * 6f + height) % height);
            int sz = Math.round(s.sz);
            Theme.rrect(c, sx, sy, sx + sz, sy + sz, sz / 2, Theme.argb(s.a * 0.75f, Theme.PURPLE_LIGHT));
        }

        drawLeftAccountCard(c);

        int centerX = width / 2;
        int topY = Math.max(28, height / 2 - 112);

        String title = "VOID CLIENT";
        float titleScale = 2.8f;
        float tw = textRenderer.getWidth(title) * titleScale;
        addScaledTxt(title, centerX - tw / 2f + 2f, topY + 2f, titleScale, Theme.argb(0.85f, 0x3B0764), false, 0f);
        addScaledTxt(title, centerX - tw / 2f, topY, titleScale, 0xFFFFFFFF, false, 0f);

        String sub = "✦  MINECRAFT 1.21.11  •  FABRIC  ✦";
        addTxt(sub, centerX - textRenderer.getWidth(sub) / 2f, topY + 30, 0xFF000000 | Theme.PURPLE_LIGHT, true);

        float pulse = 1.0f + 0.06f * (float) Math.sin(time * 4f);
        addScaledTxt("Have fun! ♥", centerX + 115, topY + 20, pulse, 0xFFE879F9, true, -0.32f);

        int btnW = 220, btnH = 24, btnX = centerX - btnW / 2, startY = topY + 52, gap = 29;
        drawStyledBtn(c, btnX, startY, btnW, btnH, ">", "Singleplayer", mx, my, () -> client.setScreen(new SelectWorldScreen(this)));
        drawStyledBtn(c, btnX, startY + gap, btnW, btnH, "M", "Multiplayer", mx, my, () -> client.setScreen(new MultiplayerScreen(this)));
        drawStyledBtn(c, btnX, startY + gap * 2, btnW, btnH, "*", "Void Client Menu", mx, my, () -> client.setScreen(new VoidScreen()));

        int halfW = (btnW - 8) / 2;
        drawStyledBtn(c, btnX, startY + gap * 3, halfW, btnH, "O", "Options...", mx, my, () -> client.setScreen(new OptionsScreen(this, client.options)));
        drawStyledBtn(c, btnX + halfW + 8, startY + gap * 3, halfW, btnH, "X", "Quit Game", mx, my, () -> client.scheduleStop());

        int bSize = 24, by = height - 42;
        drawMiniIconBtn(c, 16, by, bSize, "V", mx, my, () -> client.setScreen(new VoidScreen()));
        drawMiniIconBtn(c, 46, by, bSize, "L", mx, my, () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())));
        drawMiniIconBtn(c, 76, by, bSize, "A", mx, my, () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options)));

        addTxt("Minecraft 1.21.11  |  Logged in as: " + getAccountName(), 10, height - 12, 0xFF000000 | Theme.MUTED, true);
        String copy = "Copyright Mojang Studios. Do not distribute!";
        addTxt(copy, width - textRenderer.getWidth(copy) - 10, height - 12, 0xFF000000 | Theme.MUTED, true);

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
