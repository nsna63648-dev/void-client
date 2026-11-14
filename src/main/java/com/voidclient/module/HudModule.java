package com.voidclient.module;

import com.voidclient.gui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Module ที่วาด HUD ได้ ปรับตำแหน่ง/ขนาด/ความโปร่งใสได้ */
public abstract class HudModule extends Module {
    public int w = 60, h = 14;

    protected HudModule(String id, String name, String desc, Category cat, boolean en, int x, int y) {
        super(id, name, desc, cat, en, x, y);
    }

    @Override public boolean isHud() { return true; }

    protected List<String> lines(MinecraftClient mc) { return List.of(); }

    @Override public void render(DrawContext c, MinecraftClient mc) { draw(c, mc, false); }

    public void draw(DrawContext c, MinecraftClient mc, boolean edit) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate((float) s.x, (float) s.y);
        m.scale(s.scale, s.scale);
        content(c, mc, edit);
        m.popMatrix();
    }

    protected void content(DrawContext c, MinecraftClient mc, boolean edit) {
        List<String> ls = lines(mc);
        if (ls.isEmpty()) {
            if (!edit) return;
            ls = List.of(name);
        }
        int tw = 0;
        for (String l : ls) tw = Math.max(tw, mc.textRenderer.getWidth(l));
        w = tw + 10;
        h = ls.size() * 10 + 4;
        Theme.panel(c, 0, 0, w, h, s.opacity);
        int y = 3;
        for (String l : ls) { c.drawText(mc.textRenderer, l, 6, y, 0xFFFFFFFF, true); y += 10; }
    }

    public boolean hit(double mx, double my) {
        return mx >= s.x - 2 && my >= s.y - 2 && mx <= s.x + w * s.scale + 2 && my <= s.y + h * s.scale + 2;
    }
}
