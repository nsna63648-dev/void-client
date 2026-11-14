package com.voidclient.module;

import com.voidclient.config.VoidConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** ฐานของทุก Feature — โหลด/ทำงานเฉพาะเมื่อ enabled */
public abstract class Module {
    public final String id, name, desc;
    public final Category cat;
    public final VoidConfig.State s;
    public final List<Setting> settings = new ArrayList<>();
    private final boolean defEnabled;
    private final int defX, defY;

    protected Module(String id, String name, String desc, Category cat, boolean en, int x, int y) {
        this.id = id; this.name = name; this.desc = desc; this.cat = cat;
        this.defEnabled = en; this.defX = x; this.defY = y;
        this.s = VoidConfig.state(id, en, x, y);
    }

    public Module with(Setting... st) { settings.addAll(List.of(st)); return this; }

    public boolean enabled() { return s.enabled; }

    public void setEnabled(boolean b) {
        if (s.enabled == b) return;
        s.enabled = b;
        try { onToggle(b); } catch (Throwable t) { System.err.println("[VOID] " + id + " toggle error: " + t); }
        VoidConfig.save();
    }

    public float val(String k, float d) { return s.v.getOrDefault(k, d); }

    public void set(String k, float v) {
        s.v.put(k, v);
        try { onSetting(k, v); } catch (Throwable t) { System.err.println("[VOID] " + id + " setting error: " + t); }
    }

    public void reset() {
        s.enabled = defEnabled; s.x = defX; s.y = defY; s.scale = 1f; s.opacity = 0.85f; s.v.clear();
    }

    public void onToggle(boolean on) {}
    public void onSetting(String k, float v) {}
    public void tick(MinecraftClient mc) {}
    public boolean isHud() { return false; }
    public void render(DrawContext c, MinecraftClient mc) {}
}
