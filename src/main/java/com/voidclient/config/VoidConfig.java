package com.voidclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** บันทึก/โหลดการตั้งค่าอัตโนมัติ -> .minecraft/config/voidclient.json */
public final class VoidConfig {
    public static class State {
        public boolean enabled;
        public int x, y;
        public float scale = 1f, opacity = 0.85f;
        public Map<String, Float> v = new HashMap<>();
    }

    public static class Data {
        public Map<String, State> modules = new HashMap<>();
        public String discordUrl = "";
        public String preset = "BALANCED";
    }

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("voidclient.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Data data = new Data();

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE), Data.class);
                if (d != null && d.modules != null) data = d;
            }
        } catch (Exception e) {
            System.err.println("[VOID] config load failed, using defaults: " + e);
            data = new Data();
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(data));
        } catch (Exception e) {
            System.err.println("[VOID] config save failed: " + e);
        }
    }

    public static State state(String id, boolean enabled, int x, int y) {
        return data.modules.computeIfAbsent(id, k -> {
            State s = new State();
            s.enabled = enabled; s.x = x; s.y = y;
            return s;
        });
    }
}
