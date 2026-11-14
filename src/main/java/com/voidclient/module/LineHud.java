package com.voidclient.module;

import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.function.Function;

/** HUD แบบข้อความหลายบรรทัด สร้างได้ในบรรทัดเดียว */
public class LineHud extends HudModule {
    private final Function<MinecraftClient, List<String>> fn;

    public LineHud(String id, String name, String desc, Category cat, boolean en, int x, int y,
                   Function<MinecraftClient, List<String>> fn) {
        super(id, name, desc, cat, en, x, y);
        this.fn = fn;
    }

    @Override protected List<String> lines(MinecraftClient mc) {
        try { return fn.apply(mc); } catch (Throwable t) { return List.of(); }
    }
}
