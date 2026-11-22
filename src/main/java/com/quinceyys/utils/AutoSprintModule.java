package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Enables vanilla sprinting automatically while the player is moving forward.
 * It does not alter movement speed, send custom movement packets, or bypass
 * the server's sprint and movement checks.
 */
public final class AutoSprintModule {
    private static final Path CONFIG = Path.of("config", "qys-auto-sprint.properties");

    public static boolean enabled = false;
    public static boolean pauseWhileUsingItems = true;

    private static LocalPlayer trackedPlayer;
    private static boolean startedSprint;

    private AutoSprintModule() {}

    public static void initialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(AutoSprintModule::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;

        if (player != trackedPlayer) {
            releaseOwnedSprint();
            trackedPlayer = player;
            startedSprint = false;
        }

        if (player == null || client.level == null || !player.isAlive()) {
            releaseOwnedSprint();
            return;
        }

        boolean movingForward = client.options.keyUp.isDown()
                && !client.options.keyDown.isDown();
        boolean screenClosed = client.screen == null;
        boolean notSneaking = !player.isShiftKeyDown();
        boolean notFallFlying = !player.isFallFlying();
        boolean itemUseAllowsSprint = !pauseWhileUsingItems || !player.isUsingItem();

        boolean shouldAutoSprint = enabled
                && screenClosed
                && movingForward
                && notSneaking
                && notFallFlying
                && !player.isPassenger()
                && itemUseAllowsSprint;

        if (shouldAutoSprint) {
            if (!player.isSprinting()) {
                player.setSprinting(true);
                startedSprint = true;
            }
        } else {
            releaseOwnedSprint();
        }
    }

    private static void releaseOwnedSprint() {
        if (startedSprint && trackedPlayer != null && trackedPlayer.isSprinting()) {
            trackedPlayer.setSprinting(false);
        }
        startedSprint = false;
    }

    public static void loadConfig() {
        Properties properties = new Properties();
        try {
            Files.createDirectories(CONFIG.getParent());
            if (Files.exists(CONFIG)) {
                try (InputStream input = Files.newInputStream(CONFIG)) {
                    properties.load(input);
                }
            }

            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
            pauseWhileUsingItems = Boolean.parseBoolean(
                    properties.getProperty("pauseWhileUsingItems", "true")
            );
            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoSprint] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("pauseWhileUsingItems", Boolean.toString(pauseWhileUsingItems));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS Auto Sprint settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoSprint] Config save failed: " + exception.getMessage());
        }
    }
}
