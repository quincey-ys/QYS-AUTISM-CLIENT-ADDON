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
import net.minecraft.world.item.ItemStack;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public final class AutoPlaceCombatModule {
    private static final Path CONFIG = Path.of("config", "qys-auto-place-combat.properties");
    private static final long PLACEMENT_TIMEOUT_MS = 2500L;

    public static boolean autoBreakOwnCrystals = false;
    public static boolean autoDetonateOwnAnchors = false;

    private static final Map<BlockPos, Long> crystalPlacements = new HashMap<>();
    private static final Map<BlockPos, AnchorAttempt> anchorPlacements = new HashMap<>();
    private static Object lastLevel;

    private AutoPlaceCombatModule() {}

    public static void initialize() {
        loadConfig();

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!(player instanceof LocalPlayer localPlayer) || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            ItemStack held = localPlayer.getMainHandItem();

            if (autoBreakOwnCrystals && held.is(Items.END_CRYSTAL)) {
                // Crystal entities spawn one block above the obsidian/bedrock that was clicked.
                crystalPlacements.put(hitResult.getBlockPos().immutable(), System.currentTimeMillis());
            }

            if (autoDetonateOwnAnchors && held.is(Items.RESPAWN_ANCHOR)) {
                // A block item is placed against the face that was clicked.
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

            BlockPos basePos = BlockPos.containing(
                    crystal.getX(),
                    crystal.getY() - 1.0,
                    crystal.getZ()
            );
            Long placedAt = crystalPlacements.remove(basePos);
            if (placedAt == null || System.currentTimeMillis() - placedAt > PLACEMENT_TIMEOUT_MS) return;

            // This is intentionally gated by the live use input: releasing right-click ends
            // the crystal-placement session. No rotation or crosshair targeting is performed.
            if (!client.options.keyUse.isDown()) return;

            client.gameMode.attack(player, crystal);
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoPlaceCombatModule::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        if (client.level == null || client.player == null) {
            crystalPlacements.clear();
            anchorPlacements.clear();
            lastLevel = null;
            return;
        }

        if (lastLevel != client.level) {
            crystalPlacements.clear();
            anchorPlacements.clear();
            lastLevel = client.level;
        }

        long now = System.currentTimeMillis();
        crystalPlacements.values().removeIf(createdAt -> now - createdAt > PLACEMENT_TIMEOUT_MS);

        if (!autoBreakOwnCrystals) crystalPlacements.clear();
        if (!autoDetonateOwnAnchors) {
            anchorPlacements.clear();
            return;
        }

        if (client.gameMode == null || !client.player.isAlive()) return;

        Iterator<Map.Entry<BlockPos, AnchorAttempt>> iterator = anchorPlacements.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, AnchorAttempt> entry = iterator.next();
            BlockPos pos = entry.getKey();
            AnchorAttempt attempt = entry.getValue();

            if (now - attempt.createdAt() > PLACEMENT_TIMEOUT_MS) {
                iterator.remove();
                continue;
            }

            if (!client.level.getBlockState(pos).is(Blocks.RESPAWN_ANCHOR)) continue;

            // Never perform the final click in a dimension where anchors are safe respawn points.
            iterator.remove();
            if (client.level.dimension().equals(Level.NETHER)) continue;

            int glowstoneSlot = findHotbarItem(client.player, Items.GLOWSTONE);
            if (glowstoneSlot < 0) continue;

            detonateAnchor(client, pos, attempt.anchorSlot(), glowstoneSlot);
            break;
        }
    }

    private static void detonateAnchor(Minecraft client, BlockPos pos, int anchorSlot, int glowstoneSlot) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return;

        BlockHitResult hit = new BlockHitResult(
                new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5),
                Direction.UP,
                pos,
                false
        );

        try {
            // MultiPlayerGameMode sends the held-slot change before each use packet, preserving
            // the required server-side order even though both interactions are issued immediately.
            player.getInventory().setSelectedSlot(glowstoneSlot);
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);

            player.getInventory().setSelectedSlot(anchorSlot);
            client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        } finally {
            player.getInventory().setSelectedSlot(anchorSlot);
        }
    }

    private static int findHotbarItem(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
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
            autoDetonateOwnAnchors = bool(properties, "autoDetonateOwnAnchors", false);
            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoPlaceCombat] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("autoBreakOwnCrystals", Boolean.toString(autoBreakOwnCrystals));
        properties.setProperty("autoDetonateOwnAnchors", Boolean.toString(autoDetonateOwnAnchors));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS Auto Place Combat settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoPlaceCombat] Config save failed: " + exception.getMessage());
        }
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private record AnchorAttempt(int anchorSlot, long createdAt) {}
}
