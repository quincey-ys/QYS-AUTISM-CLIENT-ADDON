package com.quinceyys.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Rail-placement triggered TNT minecart helper.
 * It uses ordinary client interaction calls and is disabled by default.
 */
public final class AutoTntCartModule {
    private static final Path CONFIG = Path.of("config", "qys-auto-tnt-cart.properties");

    public static boolean enabled = false;
    /** Single = one run then disarm, Repeat = one run per rail placement, Manual Assist = place cart and select bow. */
    public static String mode = "Single";
    public static int railToCartDelayMs = 120;
    public static int cartToBowDelayMs = 120;
    public static int bowChargeMs = 1100;
    public static int repeatCooldownMs = 600;

    private enum State { IDLE, WAIT_FOR_RAIL, PLACE_CART, SWITCH_TO_BOW, DRAW_BOW, RESTORE_SLOT }

    private static State state = State.IDLE;
    private static BlockPos expectedRail;
    private static BlockPos clickedBlock;
    private static long stateDueAt;
    private static long queuedAt;
    private static long lastSequenceAt;
    private static int originalSlot = -1;
    private static int cartSlot = -1;
    private static int bowSlot = -1;

    private AutoTntCartModule() {}

    public static void initialize() {
        loadConfig();

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof LocalPlayer local) || hand != InteractionHand.MAIN_HAND
                    || !enabled || state != State.IDLE || !isRailItem(local.getMainHandItem())) {
                return net.minecraft.world.InteractionResult.PASS;
            }

            queueRail(hit.getBlockPos().relative(hit.getDirection()), hit.getBlockPos());
            return net.minecraft.world.InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(AutoTntCartModule::onEndTick);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) cancelAndRestore(Minecraft.getInstance());
        saveConfig();
    }

    public static void activateAtCrosshair(Minecraft client) {
        if (!enabled || state != State.IDLE || client.player == null || client.level == null
                || !(client.hitResult instanceof BlockHitResult hit)) return;
        if (isRail(client.level.getBlockState(hit.getBlockPos()).getBlock())) {
            queueRail(hit.getBlockPos(), hit.getBlockPos());
        }
    }

    private static void queueRail(BlockPos expected, BlockPos clicked) {
        long now = System.currentTimeMillis();
        if (!enabled || state != State.IDLE || now - lastSequenceAt < repeatCooldownMs) return;
        expectedRail = expected.immutable();
        clickedBlock = clicked.immutable();
        queuedAt = now;
        stateDueAt = now + 80L;
        state = State.WAIT_FOR_RAIL;
    }

    private static void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null) {
            if (state != State.IDLE) cancelAndRestore(client);
            return;
        }
        if (!enabled) {
            if (state != State.IDLE) cancelAndRestore(client);
            return;
        }

        long now = System.currentTimeMillis();
        switch (state) {
            case IDLE -> { }
            case WAIT_FOR_RAIL -> {
                if (now - queuedAt > 1800L) {
                    finish(client, false);
                    return;
                }
                if (now < stateDueAt) return;
                BlockPos rail = findPlacedRail(client, expectedRail, clickedBlock);
                if (rail == null) return;

                cartSlot = findHotbarSlot(player, Items.TNT_MINECART);
                bowSlot = findHotbarSlot(player, Items.BOW);
                if (cartSlot < 0 || bowSlot < 0) {
                    finish(client, false);
                    return;
                }
                originalSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(cartSlot);
                expectedRail = rail;
                state = State.PLACE_CART;
                stateDueAt = now + railToCartDelayMs;
            }
            case PLACE_CART -> {
                if (now < stateDueAt) return;
                BlockPos rail = expectedRail;
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(rail.getX() + 0.5, rail.getY() + 0.08, rail.getZ() + 0.5),
                        Direction.UP, rail, false
                );
                client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                state = State.SWITCH_TO_BOW;
                stateDueAt = now + cartToBowDelayMs;
            }
            case SWITCH_TO_BOW -> {
                if (now < stateDueAt) return;
                player.getInventory().setSelectedSlot(bowSlot);
                if (mode.equalsIgnoreCase("Manual Assist")) {
                    finish(client, true);
                    return;
                }
                client.options.keyUse.setDown(true);
                state = State.DRAW_BOW;
                stateDueAt = now + bowChargeMs;
            }
            case DRAW_BOW -> {
                if (now < stateDueAt) return;
                client.options.keyUse.setDown(false);
                client.gameMode.releaseUsingItem(player);
                state = State.RESTORE_SLOT;
                stateDueAt = now + 120L;
            }
            case RESTORE_SLOT -> {
                if (now < stateDueAt) return;
                finish(client, false);
            }
        }
    }

    private static BlockPos findPlacedRail(Minecraft client, BlockPos expected, BlockPos clicked) {
        if (expected != null && isRail(client.level.getBlockState(expected).getBlock())) return expected;
        if (clicked != null && isRail(client.level.getBlockState(clicked).getBlock())) return clicked;
        if (clicked != null && isRail(client.level.getBlockState(clicked.above()).getBlock())) return clicked.above();
        if (expected != null && isRail(client.level.getBlockState(expected.below()).getBlock())) return expected.below();
        return null;
    }

    private static boolean isRailItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof BaseRailBlock;
    }

    private static boolean isRail(net.minecraft.world.level.block.Block block) {
        return block instanceof BaseRailBlock;
    }

    private static int findHotbarSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static void finish(Minecraft client, boolean leaveBowSelected) {
        LocalPlayer player = client.player;
        client.options.keyUse.setDown(false);
        if (!leaveBowSelected && player != null && originalSlot >= 0) {
            player.getInventory().setSelectedSlot(originalSlot);
        }
        state = State.IDLE;
        expectedRail = null;
        clickedBlock = null;
        originalSlot = -1;
        cartSlot = -1;
        bowSlot = -1;
        lastSequenceAt = System.currentTimeMillis();
        if (mode.equalsIgnoreCase("Single")) enabled = false;
        saveConfig();
    }

    private static void cancelAndRestore(Minecraft client) {
        LocalPlayer player = client.player;
        client.options.keyUse.setDown(false);
        if (player != null && originalSlot >= 0) player.getInventory().setSelectedSlot(originalSlot);
        state = State.IDLE;
        expectedRail = null;
        clickedBlock = null;
        originalSlot = -1;
        cartSlot = -1;
        bowSlot = -1;
    }

    public static void cycleMode() {
        String[] modes = {"Single", "Repeat", "Manual Assist"};
        int index = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equalsIgnoreCase(mode)) index = i;
        }
        mode = modes[(index + 1) % modes.length];
        saveConfig();
    }

    public static void loadConfig() {
        Properties p = new Properties();
        try {
            Files.createDirectories(CONFIG.getParent());
            if (Files.exists(CONFIG)) {
                try (InputStream input = Files.newInputStream(CONFIG)) {
                    p.load(input);
                }
            }
            enabled = Boolean.parseBoolean(p.getProperty("enabled", "false"));
            mode = p.getProperty("mode", "Single");
            if (!mode.equalsIgnoreCase("Single") && !mode.equalsIgnoreCase("Repeat")
                    && !mode.equalsIgnoreCase("Manual Assist")) mode = "Single";
            railToCartDelayMs = integer(p, "railToCartDelayMs", 120, 0, 5000);
            cartToBowDelayMs = integer(p, "cartToBowDelayMs", 120, 0, 5000);
            bowChargeMs = integer(p, "bowChargeMs", 1100, 200, 2000);
            repeatCooldownMs = integer(p, "repeatCooldownMs", 600, 0, 10000);
            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoTntCart] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("mode", mode);
        p.setProperty("railToCartDelayMs", Integer.toString(railToCartDelayMs));
        p.setProperty("cartToBowDelayMs", Integer.toString(cartToBowDelayMs));
        p.setProperty("bowChargeMs", Integer.toString(bowChargeMs));
        p.setProperty("repeatCooldownMs", Integer.toString(repeatCooldownMs));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                p.store(output, "QYS-UTILS Auto TNT Cart settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoTntCart] Config save failed: " + exception.getMessage());
        }
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
