package com.whalestorm.chestsorter.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;

public final class KeyBindings {

    /** Unbound by default so it can't clash with GTNH's many keybinds; set it in Options > Controls. */
    public static final KeyBinding TOGGLE = new KeyBinding(
        "key.chestsorter.toggle",
        Keyboard.KEY_NONE,
        "key.categories.chestsorter");

    /** Opens the settings screen. Unbound by default too. */
    public static final KeyBinding SETTINGS = new KeyBinding(
        "key.chestsorter.settings",
        Keyboard.KEY_NONE,
        "key.categories.chestsorter");

    private KeyBindings() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(SETTINGS);
    }
}
