package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class AutoPlaceCombatModule {
    private static final Path CONFIG = Path.of("config", "qys-auto-place-combat.properties");
    private static final long PLACEMENT_TIMEOUT_MS = 5000L;

    // Crystal controls
    public static boolean autoBreakOwnCrystals = false;
    public static boolean crystalRequireUseHeld = true;
    public static int crystalBreakDelayMs = 150;
    public static int crystalMaxBreaksPerSecond = 4;
    public static int crystalPlacementWindowMs = 1500;

    // Anchor controls
    public static boolean autoDetonateOwnAnchors = false;
    public static boolean anchorRequireUseHeldAtDetonate = true;
    public static int anchorPlaceDelayMs = 150;
    public static int anchorSwitchDelayMs = 100;
    public static int anchorChargeDelayMs = 300;
    public static int anchorDetonateDelayMs = 150;
    public static int anchorCooldownMs = 750;

    private static final Map<BlockPos, Long> crystalPlacements = new LinkedHashMap<>();
    private static final Map<EndCrystal, PendingCrystalBreak> pendingCrystalBreaks = new LinkedHashMap<>();
    private static final Map<BlockPos, AnchorAttempt> anchorPlacements = new LinkedHashMap<>();

    private static Object lastLevel;
    private static long lastCrystalBreakAt;
    private static long lastAnchorStartedAt;

    private AutoPlaceCombatModule() {}

    public static void initialize() {
        loadConfig();

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!(player instanceof LocalPlayer localPlayer) || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (autoBreakOwnCrystals && localPlayer.getMainHandItem().is(Items.END_CRYSTAL)) {
                crystalPlacements.put(hitResult.getBlockPos().immutable(), System.currentTimeMillis());
            }

            if (autoDetonateOwnAnchors && localPlayer.getMainHandItem().is(Items.RESPAWN_ANCHOR)) {
                BlockPos placePos = hitResult.getBlockPos()
                        .relative(hitResult.getDirection())
                        .immutable();
                anchorPlacements.put(placePos, new AnchorAttempt(
                        localPlayer.getInventory().getSelectedSlot(),
                        System.currentTimeMillis()
                ));
            }

            return InteractionResult.PASS;
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!autoBreakOwnCrystals || !(entity instanceof EndCrystal crystal)) return;

            Minecraft client = Minecraft.getInstance();
            LocalPlayer player = client.player;
            if (player == null || client.level != level || client.gameMode == null || !player.isAlive()) return;
            if (crystalRequireUseHeld && !client.options.keyUse.isDown()) return;

            BlockPos basePos = BlockPos.containing(
                    crystal.getX(),
                    crystal.getY() - 1.0,
                    crystal.getZ()
            );
            Long placedAt = crystalPlacements.remove(basePos);
            if (placedAt == null || System.currentTimeMillis() - placedAt > crystalPlacementWindowMs) return;

            long dueAt = System.currentTimeMillis() + crystalBreakDelayMs;
            pendingCrystalBreaks.put(crystal, new PendingCrystalBreak(dueAt, System.currentTimeMillis()));
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoPlaceCombatModule::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        if (client.level == null || client.player == null) {
            crystalPlacements.clear();
            pendingCrystalBreaks.clear();
            anchorPlacements.clear();
            lastLevel = null;
            return;
        }

        if (lastLevel != client.level) {
            crystalPlacements.clear();
            pendingCrystalBreaks.clear();
            anchorPlacements.clear();
            lastLevel = client.level;
        }

        long now = System.currentTimeMillis();
        crystalPlacements.values().removeIf(createdAt -> now - createdAt > crystalPlacementWindowMs);

        if (autoBreakOwnCrystals) {
            processCrystalBreaks(client, now);
        } else {
            crystalPlacements.clear();
            pendingCrystalBreaks.clear();
        }

        if (autoDetonateOwnAnchors) {
            processAnchorCombos(client, now);
        } else {
            restorePendingAnchorSlot(client);
            anchorPlacements.clear();
        }
    }

    private static void processCrystalBreaks(Minecraft client, long now) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive()) {
            pendingCrystalBreaks.clear();
            return;
        }

        if (crystalRequireUseHeld && !client.options.keyUse.isDown()) {
            pendingCrystalBreaks.clear();
            return;
        }

        long rateInterval = 1000L / Math.max(1, crystalMaxBreaksPerSecond);
        long minimumInterval = Math.max(crystalBreakDelayMs, rateInterval);

        Iterator<Map.Entry<EndCrystal, PendingCrystalBreak>> iterator =
                pendingCrystalBreaks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EndCrystal, PendingCrystalBreak> entry = iterator.next();
            EndCrystal crystal = entry.getKey();
            PendingCrystalBreak pending = entry.getValue();

            if (!crystal.isAlive() || now - pending.queuedAt() > crystalPlacementWindowMs + 1000L) {
                iterator.remove();
                continue;
            }
            if (now < pending.dueAt() || now - lastCrystalBreakAt < minimumInterval) continue;

            // This attacks the matched entity directly. It never rotates the player's view.
            client.gameMode.attack(player, crystal);
            lastCrystalBreakAt = now;
            iterator.remove();
            break;
        }
    }

    private static void processAnchorCombos(Minecraft client, long now) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive()) return;

        Iterator<Map.Entry<BlockPos, AnchorAttempt>> iterator = anchorPlacements.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, AnchorAttempt> entry = iterator.next();
            BlockPos pos = entry.getKey();
            AnchorAttempt attempt = entry.getValue();

            if (now - attempt.createdAt > PLACEMENT_TIMEOUT_MS
                    + anchorPlaceDelayMs + anchorSwitchDelayMs
                    + anchorChargeDelayMs + anchorDetonateDelayMs) {
                restoreAttemptSlot(player, attempt);
                iterator.remove();
                continue;
            }

            if (!client.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) {
                // The placement may not have succeeded yet; allow a short window for block update.
                if (now - attempt.createdAt <= PLACEMENT_TIMEOUT_MS) continue;
                iterator.remove();
                continue;
            }

            // Anchors are safe respawn points in the Nether, so never run the detonation combo there.
            if (client.level.dimension().equals(Level.NETHER)) {
                iterator.remove();
                continue;
            }

            if (now < attempt.nextActionAt) continue;

            switch (attempt.phase) {
                case WAIT_FOR_PLACE -> {
                    long earliestStart = Math.max(
                            attempt.createdAt + anchorPlaceDelayMs,
                            lastAnchorStartedAt + anchorCooldownMs
                    );
                    if (now < earliestStart) continue;

                    int glowstoneSlot = findHotbarItem(player, Items.GLOWSTONE);
                    if (glowstoneSlot < 0) {
                        iterator.remove();
                        continue;
                    }

                    attempt.glowstoneSlot = glowstoneSlot;
                    player.getInventory().setSelectedSlot(glowstoneSlot);
                    attempt.phase = AnchorPhase.WAIT_FOR_GLOWSTONE_SWITCH;
                    attempt.nextActionAt = now + anchorSwitchDelayMs;
                    lastAnchorStartedAt = now;
                }
                case WAIT_FOR_GLOWSTONE_SWITCH -> {
                    if (player.getInventory().getSelectedSlot() != attempt.glowstoneSlot) {
                        iterator.remove();
                        continue;
                    }

                    interactWithAnchor(client, pos);
                    player.getInventory().setSelectedSlot(attempt.anchorSlot);
                    attempt.phase = AnchorPhase.WAIT_FOR_CHARGE;
                    attempt.nextActionAt = now + anchorChargeDelayMs;
                }
                case WAIT_FOR_CHARGE -> {
                    if (player.getInventory().getSelectedSlot() != attempt.anchorSlot) {
                        iterator.remove();
                        continue;
                    }
                    attempt.phase = AnchorPhase.WAIT_FOR_DETONATE;
                    attempt.nextActionAt = now + anchorDetonateDelayMs;
                }
                case WAIT_FOR_DETONATE -> {
                    if (player.getInventory().getSelectedSlot() != attempt.anchorSlot) {
                        iterator.remove();
                        continue;
                    }
                    if (anchorRequireUseHeldAtDetonate && !client.options.keyUse.isDown()) {
                        iterator.remove();
                        continue;
                    }

                    interactWithAnchor(client, pos);
                    iterator.remove();
                }
            }

            // Process only one anchor state transition per client tick.
            break;
        }
    }

    private static void interactWithAnchor(Minecraft client, BlockPos pos) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return;

        BlockHitResult hit = new BlockHitResult(
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                Direction.UP,
                pos,
                false
        );
        client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
    }

    private static int findHotbarItem(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static void restorePendingAnchorSlot(Minecraft client) {
        if (client.player == null) return;
        for (AnchorAttempt attempt : anchorPlacements.values()) {
            restoreAttemptSlot(client.player, attempt);
        }
    }

    private static void restoreAttemptSlot(LocalPlayer player, AnchorAttempt attempt) {
        if (attempt.glowstoneSlot >= 0
                && player.getInventory().getSelectedSlot() == attempt.glowstoneSlot) {
            player.getInventory().setSelectedSlot(attempt.anchorSlot);
        }
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

            autoBreakOwnCrystals = bool(properties, "autoBreakOwnCrystals", false);
            crystalRequireUseHeld = bool(properties, "crystalRequireUseHeld", true);
            crystalBreakDelayMs = integer(properties, "crystalBreakDelayMs", 150, 0, 5000);
            crystalMaxBreaksPerSecond = integer(properties, "crystalMaxBreaksPerSecond", 4, 1, 20);
            crystalPlacementWindowMs = integer(properties, "crystalPlacementWindowMs", 1500, 250, 5000);

            autoDetonateOwnAnchors = bool(properties, "autoDetonateOwnAnchors", false);
            anchorRequireUseHeldAtDetonate = bool(properties, "anchorRequireUseHeldAtDetonate", true);
            anchorPlaceDelayMs = integer(properties, "anchorPlaceDelayMs", 150, 0, 5000);
            anchorSwitchDelayMs = integer(properties, "anchorSwitchDelayMs", 100, 0, 5000);
            anchorChargeDelayMs = integer(properties, "anchorChargeDelayMs", 300, 0, 5000);
            anchorDetonateDelayMs = integer(properties, "anchorDetonateDelayMs", 150, 0, 5000);
            anchorCooldownMs = integer(properties, "anchorCooldownMs", 750, 0, 10000);

            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoPlaceCombat] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("autoBreakOwnCrystals", Boolean.toString(autoBreakOwnCrystals));
        properties.setProperty("crystalRequireUseHeld", Boolean.toString(crystalRequireUseHeld));
        properties.setProperty("crystalBreakDelayMs", Integer.toString(crystalBreakDelayMs));
        properties.setProperty("crystalMaxBreaksPerSecond", Integer.toString(crystalMaxBreaksPerSecond));
        properties.setProperty("crystalPlacementWindowMs", Integer.toString(crystalPlacementWindowMs));

        properties.setProperty("autoDetonateOwnAnchors", Boolean.toString(autoDetonateOwnAnchors));
        properties.setProperty("anchorRequireUseHeldAtDetonate", Boolean.toString(anchorRequireUseHeldAtDetonate));
        properties.setProperty("anchorPlaceDelayMs", Integer.toString(anchorPlaceDelayMs));
        properties.setProperty("anchorSwitchDelayMs", Integer.toString(anchorSwitchDelayMs));
        properties.setProperty("anchorChargeDelayMs", Integer.toString(anchorChargeDelayMs));
        properties.setProperty("anchorDetonateDelayMs", Integer.toString(anchorDetonateDelayMs));
        properties.setProperty("anchorCooldownMs", Integer.toString(anchorCooldownMs));

        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS Crystal / Anchor settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoPlaceCombat] Config save failed: " + exception.getMessage());
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties properties, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private enum AnchorPhase {
        WAIT_FOR_PLACE,
        WAIT_FOR_GLOWSTONE_SWITCH,
        WAIT_FOR_CHARGE,
        WAIT_FOR_DETONATE
    }

    private record PendingCrystalBreak(long dueAt, long queuedAt) {}

    private static final class AnchorAttempt {
        private final int anchorSlot;
        private final long createdAt;
        private int glowstoneSlot = -1;
        private AnchorPhase phase = AnchorPhase.WAIT_FOR_PLACE;
        private long nextActionAt;

        private AnchorAttempt(int anchorSlot, long createdAt) {
            this.anchorSlot = anchorSlot;
            this.createdAt = createdAt;
            this.nextActionAt = createdAt;
        }
    }
}
