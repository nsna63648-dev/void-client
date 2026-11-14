package com.voidclient.module;

import net.minecraft.client.MinecraftClient;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Feature ที่ไม่มี HUD (เปิด/ปิด + ตัวเลือก) */
public class Toggle extends Module {
    private final Consumer<Boolean> toggle;
    private Consumer<MinecraftClient> ticker;
    private BiConsumer<String, Float> setter;

    public Toggle(String id, String name, String desc, Category cat, boolean en, Consumer<Boolean> toggle) {
        super(id, name, desc, cat, en, 0, 0);
        this.toggle = toggle;
    }
    @Override public Toggle with(Setting... st) { super.with(st); return this; }
    public Toggle onTick(Consumer<MinecraftClient> t) { ticker = t; return this; }
    public Toggle set0() { return onSet((k, v) -> { if (enabled()) ModuleManager.applyDistances(this); }); }
    public Toggle onSet(BiConsumer<String, Float> t) { setter = t; return this; }

    @Override public void onToggle(boolean on) { if (toggle != null) toggle.accept(on); }
    @Override public void onSetting(String k, float v) { if (setter != null) setter.accept(k, v); }
    @Override public void tick(MinecraftClient mc) { if (ticker != null) ticker.accept(mc); }
}
