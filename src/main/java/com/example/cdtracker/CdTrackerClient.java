package com.example.cdtracker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class CdTrackerClient implements ClientModInitializer {
    public static KeyBinding menuKey;

    @Override
    public void onInitializeClient() {
        CdConfig.load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.cdtracker.menu",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.cdtracker"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                client.setScreen(new CdScreen(client.currentScreen));
            }
            CdTracker.tick(client);
        });

        HudRenderCallback.EVENT.register(CdTracker::render);
    }
}
