package com.voidclient;

import com.voidclient.config.VoidConfig;
import com.voidclient.gui.VoidScreen;
import com.voidclient.gui.VoidTitleScreen;
import com.voidclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VoidClient implements ClientModInitializer {
    public static final String ID = "voidclient";
    public static KeyBinding openMenu;

    @Override
    public void onInitializeClient() {
        VoidConfig.load();
        ModuleManager.init();

        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voidclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(ID, "main"))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openMenu.wasPressed()) mc.setScreen(new VoidScreen());
            ModuleManager.tick(mc);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden || mc.currentScreen instanceof VoidScreen) return;
            ModuleManager.renderHud(ctx, mc);
        });

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                mc.setScreen(new VoidTitleScreen());
            }
        });

        String sodium = FabricLoader.getInstance().isModLoaded("sodium") ? "with Sodium" : "without Sodium";
        System.out.println("[VOID CLIENT] loaded (" + sodium + ")");
    }
}
