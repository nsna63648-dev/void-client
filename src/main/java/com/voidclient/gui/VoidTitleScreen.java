package com.voidclient.gui;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class VoidTitleScreen extends Screen {
    private record Hit(int x1, int y1, int x2, int y2, Runnable run) {}
    private record Star(float x, float y, float sz, float a) {}

    private final List<Hit> hits = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final long openAt = System.currentTimeMillis();
    private SkinTextures cachedSkin;
    private boolean skinRequested;

    public VoidTitleScreen() {
        super(Text.literal("VOID CLIENT"));
        Random r = new Random(2026);
        for (int i = 0; i < 45; i++) {
            stars.add(new Star(r.nextFloat(), r.nextFloat(), 1.5f + r.nextFloat() * 2.5f, 0.2f + r.nextFloat() * 0.6f));
        }
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private SkinTextures getSkinTextures() {
        if (cachedSkin != null) return cachedSkin;
        var session = client.getSession();
        if (session == null) return DefaultSkinHelper.getSteve();
        GameProfile profile = new GameProfile(session.getUuidOrNull(), session.getUsername());
        if (!skinRequested) {
            skinRequested = true;
            client.getSkinProvider().fetchSkinTextures(profile).thenAccept(opt -> opt.ifPresent(st -> cachedSkin = st));
        }
        return DefaultSkinHelper.getSkinTextures(profile);
    }

    private void drawStyledBtn(DrawContext c, int x, int y, int w, int h, String icon, String label, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
        int rad = 7;
        if (hov) Theme.rrect(c, x - 3, y - 3, x + w + 3, y + h + 3, rad + 3, Theme.argb(0.32f, Theme.PURPLE));

        int fill = hov ? Theme.argb(0.78f, 0x221538) : Theme.argb(0.65f, 0x110B1D);
        int border = hov ? Theme.argb(0.95f, Theme.PURPLE_LIGHT) : Theme.argb(0.75f, Theme.PURPLE);
        Theme.strokeCard(c, x, y, x + w, y + h, rad, fill, border);

        c.fill(x + h, y + 3, x + h + 1, y + h - 3, Theme.argb(0.45f, Theme.PURPLE));
        c.drawText(textRenderer, icon, x + (h - textRenderer.getWidth(icon)) / 2, y + (h - 8) / 2, hov ? 0xFFFFFF : Theme.PURPLE_LIGHT, false);

        int textAreaX = x + h;
        int textAreaW = w - h;
        c.drawText(textRenderer, label, textAreaX + (textAreaW - textRenderer.getWidth(label)) / 2, y + (h - 8) / 2, 0xFFFFFF, true);
        hits.add(new Hit(x, y, x + w, y + h, action));
    }

    private void drawMiniIconBtn(DrawContext c, int x, int y, int size, String icon, int mx, int my, Runnable action) {
        boolean hov = mx >= x && mx <= x + size && my >= y && my <= y + size;
        if (hov) Theme.rrect(c, x - 2, y - 2, x + size + 2, y + size + 2, 8, Theme.argb(0.35f, Theme.PURPLE));
        Theme.strokeCard(c, x, y, x + size, y + size, 6,
                hov ? Theme.argb(0.8f, 0x261740) : Theme.argb(0.68f, 0x130D21),
                hov ? Theme.argb(0.95f, Theme.PURPLE_LIGHT) : Theme.argb(0.75f, Theme.PURPLE));
        c.drawText(textRenderer, icon, x + (size - textRenderer.getWidth(icon)) / 2, y + (size - 8) / 2, hov ? 0xFFFFFF : Theme.PURPLE_LIGHT, false);
        hits.add(new Hit(x, y, x + size, y + size, action));
    }

    private void drawLeftAccountCard(DrawContext c) {
        int cardW = 118;
        int cardH = 148;
        int cx = Math.max(16, width / 2 - 248);
        int cy = height / 2 - cardH / 2 + 4;

        Theme.rrect(c, cx - 3, cy - 3, cx + cardW + 3, cy + cardH + 3, 13, Theme.argb(0.26f, Theme.PURPLE));
        Theme.strokeCard(c, cx, cy, cx + cardW, cy + cardH, 10, Theme.argb(0.72f, 0x110B1E), Theme.argb(0.85f, Theme.PURPLE));

        Theme.rrect(c, cx + 16, cy + 8, cx + cardW - 16, cy + 21, 5, Theme.argb(0.8f, 0x2A184A));
        String tag = "ACCOUNT SKIN";
        c.drawText(textRenderer, tag, cx + (cardW - textRenderer.getWidth(tag)) / 2, cy + 11, Theme.PURPLE_LIGHT, false);

        int skinSize = 56;
        int sx = cx + (cardW - skinSize) / 2;
        int sy = cy + 28;
        Theme.strokeCard(c, sx - 3, sy - 3, sx + skinSize + 3, sy + skinSize + 3, 8, Theme.argb(0.9f, 0x1B1130), Theme.argb(0.85f, Theme.PURPLE_LIGHT));
        PlayerSkinDrawer.draw(c, getSkinTextures(), sx, sy, skinSize);

        int plateY = cy + 94;
        Theme.strokeCard(c, cx + 10, plateY, cx + cardW - 10, plateY + 24, 6, Theme.argb(0.85f, 0x1D1233), Theme.argb(0.75f, Theme.PURPLE));
        String username = client.getSession() != null ? client.getSession().getUsername() : "Player";
        if (textRenderer.getWidth(username) > cardW - 26) {
            username = username.substring(0, Math.min(username.length(), 12)) + "..";
        }
        c.drawText(textRenderer, username, cx + (cardW - textRenderer.getWidth(username)) / 2, plateY + 8, 0xFFFFFF, true);

        String status = "● Active Profile";
        c.drawText(textRenderer, status, cx + (cardW - textRenderer.getWidth(status)) / 2, plateY + 30, 0x4ADE80, false);
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        hits.clear();
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

        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(centerX, topY);
        m.scale(2.8f, 2.8f);
        String title = "VOID CLIENT";
        int tw = textRenderer.getWidth(title);
        c.drawText(textRenderer, title, -tw / 2 + 1, 1, Theme.argb(0.8f, 0x3B0764), false);
        c.drawText(textRenderer, title, -tw / 2, 0, 0xFFFFFF, false);
        m.popMatrix();

        String sub = "✦  MINECRAFT 1.21.11  •  FABRIC  ✦";
        c.drawText(textRenderer, sub, centerX - textRenderer.getWidth(sub) / 2, topY + 30, Theme.PURPLE_LIGHT, true);

        float pulse = 1.0f + 0.06f * (float) Math.sin(time * 4f);
        m.pushMatrix();
        m.translate(centerX + 115, topY + 20);
        m.rotate(-0.32f);
        m.scale(pulse, pulse);
        c.drawText(textRenderer, "Have fun! ♥", 0, 0, 0xE879F9, true);
        m.popMatrix();

        int btnW = 220, btnH = 24, btnX = centerX - btnW / 2, startY = topY + 52, gap = 29;
        drawStyledBtn(c, btnX, startY, btnW, btnH, "▶", "Singleplayer", mx, my, () -> client.setScreen(new SelectWorldScreen(this)));
        drawStyledBtn(c, btnX, startY + gap, btnW, btnH, "👥", "Multiplayer", mx, my, () -> client.setScreen(new MultiplayerScreen(this)));
        drawStyledBtn(c, btnX, startY + gap * 2, btnW, btnH, "★", "Void Client Menu", mx, my, () -> client.setScreen(new VoidScreen()));

        int halfW = (btnW - 8) / 2;
        drawStyledBtn(c, btnX, startY + gap * 3, halfW, btnH, "⚙", "Options...", mx, my, () -> client.setScreen(new OptionsScreen(this, client.options)));
        drawStyledBtn(c, btnX + halfW + 8, startY + gap * 3, halfW, btnH, "✖", "Quit Game", mx, my, () -> client.scheduleStop());

        int bSize = 24, by = height - 42;
        drawMiniIconBtn(c, 16, by, bSize, "V", mx, my, () -> client.setScreen(new VoidScreen()));
        drawMiniIconBtn(c, 46, by, bSize, "🌐", mx, my, () -> client.setScreen(new LanguageOptionsScreen(this, client.options, client.getLanguageManager())));
        drawMiniIconBtn(c, 76, by, bSize, "♿", mx, my, () -> client.setScreen(new AccessibilityOptionsScreen(this, client.options)));

        c.drawText(textRenderer, "Minecraft 1.21.11  |  Void Client v1.0.0", 10, height - 12, Theme.MUTED, true);
        String copy = "Copyright Mojang Studios. Do not distribute!";
        c.drawText(textRenderer, copy, width - textRenderer.getWidth(copy) - 10, height - 12, Theme.MUTED, true);
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

