package com.quinceyys.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Shared editable bindings for every QYS-UTILS module and action. */
public final class QysKeybindManager {
    private static final Path CONFIG = Path.of("config", "qys-utils-keybinds.properties");
    private static final Map<String, KeyMapping> BINDINGS = new LinkedHashMap<>();

    private static KeyMapping aimToggle;
    private static KeyMapping triggerToggle;
    private static KeyMapping crystalToggle;
    private static KeyMapping anchorToggle;
    private static KeyMapping sprintToggle;
    private static KeyMapping chatToggle;

    private QysKeybindManager() {}

    public static void initialize() {
        register("open_gui", "Open QYS-UTILS menu", AutoTotemModule.configKey);
        register("autototem_toggle", "Toggle AutoTotem", AutoTotemModule.toggleKey);
        register("mace_toggle", "Toggle Mace Hitter", MaceHitterModule.toggleKey);
        register("mace_slam", "Mace Hitter: Slam Now", MaceHitterModule.slamKey);

        aimToggle = create("key.qys_utils.aim_toggle", InputConstants.KEY_K);
        register("aim_toggle", "Toggle Aim Assist", aimToggle);
        triggerToggle = create("key.qys_utils.trigger_toggle", InputConstants.KEY_L);
        register("trigger_toggle", "Toggle Trigger Bot", triggerToggle);
        crystalToggle = create("key.qys_utils.crystal_toggle", InputConstants.KEY_J);
        register("crystal_toggle", "Toggle Own-Crystal Break", crystalToggle);
        anchorToggle = create("key.qys_utils.anchor_toggle", InputConstants.KEY_H);
        register("anchor_toggle", "Toggle Anchor Combo", anchorToggle);
        sprintToggle = create("key.qys_utils.sprint_toggle", InputConstants.KEY_P);
        register("sprint_toggle", "Toggle Auto Sprint", sprintToggle);
        chatToggle = create("key.qys_utils.chat_toggle", InputConstants.KEY_U);
        register("chat_toggle", "Toggle Chat Game Guesser", chatToggle);

        load();
        ClientTickEvents.END_CLIENT_TICK.register(QysKeybindManager::onEndTick);
    }

    private static KeyMapping create(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                translationKey, defaultKey, KeyMapping.Category.MISC
        ));
    }

    private static void register(String id, String label, KeyMapping mapping) {
        if (mapping == null) throw new IllegalStateException("Missing key mapping for " + id);
        BINDINGS.put(id, mapping);
        LABELS.put(id, label);
    }

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    private static void onEndTick(Minecraft client) {
        if (client.gui.screen() != null) {
            // Do not toggle modules while the user is typing or interacting with a screen.
            // Drain queued presses so they cannot fire after the screen closes.
            for (KeyMapping mapping : BINDINGS.values()) {
                while (mapping.consumeClick()) { }
            }
            return;
        }

        if (aimToggle.consumeClick()) {
            CombatAssistModule.aimAssistEnabled = !CombatAssistModule.aimAssistEnabled;
            CombatAssistModule.saveConfig();
        }
        if (triggerToggle.consumeClick()) {
            CombatAssistModule.triggerBotEnabled = !CombatAssistModule.triggerBotEnabled;
            CombatAssistModule.saveConfig();
        }
        if (crystalToggle.consumeClick()) {
            AutoPlaceCombatModule.autoBreakOwnCrystals = !AutoPlaceCombatModule.autoBreakOwnCrystals;
            AutoPlaceCombatModule.saveConfig();
        }
        if (anchorToggle.consumeClick()) {
            AutoPlaceCombatModule.autoDetonateOwnAnchors = !AutoPlaceCombatModule.autoDetonateOwnAnchors;
            AutoPlaceCombatModule.saveConfig();
        }
        if (sprintToggle.consumeClick()) {
            AutoSprintModule.enabled = !AutoSprintModule.enabled;
            AutoSprintModule.saveConfig();
        }
        if (chatToggle.consumeClick()) {
            ChatGameGuesserModule.enabled = !ChatGameGuesserModule.enabled;
            ChatGameGuesserModule.saveConfig();
        }
    }

    public static List<String> ids() {
        return List.copyOf(BINDINGS.keySet());
    }

    public static KeyMapping mapping(String id) {
        return BINDINGS.get(id);
    }

    public static String label(String id) {
        return LABELS.getOrDefault(id, id);
    }

    public static String keyText(String id) {
        KeyMapping mapping = BINDINGS.get(id);
        if (mapping == null) return "UNAVAILABLE";
        String name = mapping.saveString();
        if (name.startsWith("key.keyboard.")) return name.substring("key.keyboard.".length()).toUpperCase();
        if (name.startsWith("key.mouse.")) return "MOUSE " + name.substring("key.mouse.".length());
        if (name.equals("key.keyboard.unknown")) return "UNBOUND";
        return name.toUpperCase();
    }

    public static String conflictText(String id) {
        KeyMapping current = BINDINGS.get(id);
        if (current == null) return "";
        for (Map.Entry<String, KeyMapping> entry : BINDINGS.entrySet()) {
            if (!entry.getKey().equals(id) && current.saveString().equals(entry.getValue().saveString())) {
                return "  ! DUPLICATE";
            }
        }
        return "";
    }

    public static void setKey(String id, InputConstants.Key key) {
        KeyMapping mapping = BINDINGS.get(id);
        if (mapping == null) return;
        mapping.setKey(key);
        KeyMapping.resetMapping();
        save();
    }

    private static void setDefault(String id, int keyCode) {
        KeyMapping mapping = BINDINGS.get(id);
        if (mapping != null) {
            mapping.setKey(InputConstants.Type.KEYBOARD.getOrCreate(keyCode));
        }
    }

    public static void resetDefaults() {
        setDefault("open_gui", InputConstants.KEY_O);
        setDefault("autototem_toggle", InputConstants.KEY_T);
        setDefault("mace_toggle", InputConstants.KEY_R);
        setDefault("mace_slam", InputConstants.KEY_G);
        setDefault("aim_toggle", InputConstants.KEY_K);
        setDefault("trigger_toggle", InputConstants.KEY_L);
        setDefault("crystal_toggle", InputConstants.KEY_J);
        setDefault("anchor_toggle", InputConstants.KEY_H);
        setDefault("sprint_toggle", InputConstants.KEY_P);
        setDefault("chat_toggle", InputConstants.KEY_U);
        KeyMapping.resetMapping();
        save();
    }

    public static void load() {
        Properties properties = new Properties();
        try {
            Files.createDirectories(CONFIG.getParent());
            if (Files.exists(CONFIG)) {
                try (InputStream input = Files.newInputStream(CONFIG)) {
                    properties.load(input);
                }
            }
            for (Map.Entry<String, KeyMapping> entry : BINDINGS.entrySet()) {
                String saved = properties.getProperty(entry.getKey());
                if (saved != null && !saved.isBlank()) {
                    try {
                        entry.getValue().setKey(InputConstants.getKey(saved));
                    } catch (RuntimeException ignored) {
                        System.err.println("[QYS-UTILS/Keybinds] Ignoring invalid key for " + entry.getKey());
                    }
                }
            }
            KeyMapping.resetMapping();
            save();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/Keybinds] Config load failed: " + exception.getMessage());
        }
    }

    public static void save() {
        Properties properties = new Properties();
        for (Map.Entry<String, KeyMapping> entry : BINDINGS.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue().saveString());
        }
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS editable keyboard and mouse bindings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/Keybinds] Config save failed: " + exception.getMessage());
        }
    }
}
