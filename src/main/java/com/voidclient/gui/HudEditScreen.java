package com.voidclient.gui;

import com.voidclient.config.VoidConfig;
import com.voidclient.module.HudModule;
import com.voidclient.module.Module;
import com.voidclient.module.ModuleManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** ลากจัดตำแหน่ง HUD */
public class HudEditScreen extends Screen {
    private HudModule drag;
    private double ox, oy;

    public HudEditScreen() { super(Text.literal("HUD Editor")); }

    @Override public boolean shouldPause() { return false; }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0x66000000);
        for (Module m : ModuleManager.ALL) {
            if (m.enabled() && m instanceof HudModule h) {
                h.draw(c, client, true);
                int x1 = h.s.x - 2, y1 = h.s.y - 2, x2 = (int) (h.s.x + h.w * h.s.scale) + 2, y2 = (int) (h.s.y + h.h * h.s.scale) + 2;
                int col = Theme.argb(h == drag ? 1f : 0.55f, Theme.PURPLE);
                c.fill(x1, y1, x2, y1 + 1, col); c.fill(x1, y2 - 1, x2, y2, col);
                c.fill(x1, y1, x1 + 1, y2, col); c.fill(x2 - 1, y1, x2, y2, col);
            }
        }
        String hint = "Drag HUD to move  -  ESC to go back";
        c.drawText(textRenderer, hint, (width - textRenderer.getWidth(hint)) / 2, height - 20, 0xFFFFFFFF, true);
    }

    @Override public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = ModuleManager.ALL.size() - 1; i >= 0; i--) {
            Module m = ModuleManager.ALL.get(i);
            if (m.enabled() && m instanceof HudModule h && h.hit(click.x(), click.y())) {
                drag = h; ox = click.x() - h.s.x; oy = click.y() - h.s.y;
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override public boolean mouseDragged(Click click, double dx, double dy) {
        if (drag == null) return false;
        drag.s.x = Math.max(0, Math.min(width - 10, (int) (click.x() - ox)));
        drag.s.y = Math.max(0, Math.min(height - 10, (int) (click.y() - oy)));
        return true;
    }

    @Override public boolean mouseReleased(Click click) {
        if (drag != null) { drag = null; VoidConfig.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) { client.setScreen(new VoidScreen()); return true; }
        return super.keyPressed(input);
    }
}
