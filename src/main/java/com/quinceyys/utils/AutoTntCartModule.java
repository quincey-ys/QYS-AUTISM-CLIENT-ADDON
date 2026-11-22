package com.quinceyys.utils;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Client-only AutoTntCart implementation. Every world change is submitted as a
 * normal vanilla interaction, so no custom server receiver or payload is needed.
 */
public final class AutoTntCartModule {
    private static final Path CONFIG = Path.of("config", "qys-auto-tnt-cart.properties");

    public static boolean enabled = true;
    public static boolean autoRestock = true;
    public static boolean smartAim = true;
    public static int cooldownMs = 500;
    public static int burstChargeTicks = 4;
    public static Mode currentMode = Mode.BURST;

    public static KeyMapping triggerKey;
    public static KeyMapping modeKey;

    private static Stage stage = Stage.IDLE;
    private static Mode activeMode;
    private static LocalPlayer activePlayer;
    private static int originalSlot;
    private static int lastProgrammaticSlot;
    private static float originalYaw;
    private static float originalPitch;
    private static boolean changedRotation;
    private static boolean bowStarted;
    private static int waitTicks;
    private static long lastUseAt;
    private static int railSlot = -1;
    private static int cartSlot = -1;
    private static int bowSlot = -1;
    private static Placement placement;
    private static MinecartTNT targetCart;
    private static final List<Placement> chain = new ArrayList<>();
    private static final List<RestockSwap> restockSwaps = new ArrayList<>();
    private static final Set<Integer> reservedHotbarSlots = new HashSet<>();
    private static int chainIndex;

    private AutoTntCartModule() {}

    public static void initialize() {
        loadConfig();
        triggerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.cart_trigger", InputConstants.KEY_C, KeyMapping.Category.MISC
        ));
        modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.qys_utils.cart_mode", InputConstants.KEY_V, KeyMapping.Category.MISC
        ));
        ClientTickEvents.END_CLIENT_TICK.register(AutoTntCartModule::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        if (client.gui.screen() != null) {
            drainKeys();
            if (stage != Stage.IDLE) finish(client, "AutoTntCart cancelled because a screen was opened.", false);
            return;
        }

        if (stage == Stage.IDLE) {
            while (modeKey.consumeClick()) {
                cycleMode();
                if (client.player != null) message(client, "AutoTntCart mode: " + currentMode.label);
            }
            while (triggerKey.consumeClick()) start(client);
            return;
        }

        if (client.player == null || client.player != activePlayer || client.level == null
                || client.gameMode == null) {
            finish(client, "AutoTntCart stopped after the world changed.", false);
            return;
        }

        LocalPlayer player = client.player;
        if (player.getInventory().getSelectedSlot() != lastProgrammaticSlot) {
            finish(client, "AutoTntCart cancelled after a manual hotbar change.", false);
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (stage) {
            case PLACE_RAIL -> placeRail(client);
            case WAIT_RAIL -> {
                if (!client.level.getBlockState(placement.position).is(Blocks.RAIL)) {
                    finish(client, "Rail placement was rejected. Check the supporting block and server rules.", false);
                } else {
                    stage = Stage.PLACE_CART;
                }
            }
            case PLACE_CART -> placeCart(client);
            case WAIT_CART -> {
                if (activeMode == Mode.BURST) {
                    if (!beginBow(client, bowSlot, center(placement.position))) {
                        finish(client, "Could not draw the Flame Bow.", false);
                    }
                } else {
                    finish(client, "TNT minecart placement requested.", true);
                }
            }
            case CHAIN_PLACE -> placeNextChainRail(client);
            case WAIT_CHAIN -> {
                Placement current = chain.get(chainIndex);
                if (!client.level.getBlockState(current.position).is(Blocks.RAIL)) {
                    finish(client, "Rail chain stopped because the server rejected a placement.", false);
                    return;
                }
                chainIndex++;
                if (chainIndex >= chain.size()) {
                    finish(client, "Placed " + chainIndex + " rail(s).", true);
                } else {
                    stage = Stage.CHAIN_PLACE;
                }
            }
            case BOW_CHARGE -> {
                if (bowStarted) {
                    client.gameMode.releaseUsingItem(player);
                    bowStarted = false;
                }
                restoreRotation(player);
                finish(client, activeMode == Mode.DETONATE
                        ? "Flame arrow fired toward the TNT minecart."
                        : "Burst completed using a vanilla Flame Bow shot.", true);
            }
            default -> { }
        }
    }

    private static void drainKeys() {
        if (triggerKey != null) while (triggerKey.consumeClick()) { }
        if (modeKey != null) while (modeKey.consumeClick()) { }
    }

    private static void start(Minecraft client) {
        LocalPlayer player = client.player;
        if (!enabled) {
            message(client, "AutoTntCart is disabled in its settings.");
            return;
        }
        if (player == null || client.level == null || client.gameMode == null || !player.isAlive()) return;

        long now = System.currentTimeMillis();
        if (now - lastUseAt < Math.max(0, cooldownMs)) {
            message(client, "AutoTntCart cooldown: " + (Math.max(0, cooldownMs) - (now - lastUseAt)) + " ms.");
            return;
        }
        if (client.hitResult == null || !(client.hitResult instanceof BlockHitResult hit)) {
            message(client, "Aim at a block first.");
            return;
        }
        if ((currentMode == Mode.PLACE || currentMode == Mode.BURST)
                && hit.getDirection() != Direction.UP) {
            message(client, "For Place and Burst, aim at the top of a supporting block.");
            return;
        }

        resetActionState();
        activePlayer = player;
        originalSlot = player.getInventory().getSelectedSlot();
        lastProgrammaticSlot = originalSlot;
        originalYaw = player.getYRot();
        originalPitch = player.getXRot();
        activeMode = currentMode;

        if (activeMode == Mode.PLACE || activeMode == Mode.BURST || activeMode == Mode.CHAIN) {
            placement = findPlacement(client, hit.getBlockPos(), hit.getDirection());
            if (placement == null) {
                finish(client, "No supported rail placement position was found.", false);
                return;
            }
        }

        if (activeMode == Mode.PLACE || activeMode == Mode.BURST) {
            railSlot = ensureHotbarItem(client, stack -> stack.is(Items.RAIL));
            cartSlot = ensureHotbarItem(client, stack -> stack.is(Items.TNT_MINECART));
            if (railSlot < 0 || cartSlot < 0) {
                finish(client, "Missing Rail or TNT Minecart. Enable Auto Restock or move them to your hotbar.", false);
                return;
            }
            if (activeMode == Mode.BURST) {
                bowSlot = ensureHotbarItem(client, stack -> isFlameBow(client, stack));
                if (bowSlot < 0) {
                    finish(client, "Missing a Flame-enchanted Bow.", false);
                    return;
                }
                if (!hasArrow(player)) {
                    finish(client, "A vanilla bow still needs at least one arrow in your inventory.", false);
                    return;
                }
            }
            stage = Stage.PLACE_RAIL;
        } else if (activeMode == Mode.CHAIN) {
            railSlot = ensureHotbarItem(client, stack -> stack.is(Items.RAIL));
            if (railSlot < 0) {
                finish(client, "Missing Rail. Enable Auto Restock or move rails to your hotbar.", false);
                return;
            }
            int available = player.getInventory().getItem(railSlot).getCount();
            Direction facing = player.getDirection();
            for (int i = 0; i < Math.min(5, available); i++) {
                BlockPos pos = placement.position.relative(facing, i);
                Placement next = validPlacement(client, pos);
                if (next == null) break;
                chain.add(next);
            }
            if (chain.isEmpty()) {
                finish(client, "No supported rail positions found for Chain mode.", false);
                return;
            }
            chainIndex = 0;
            stage = Stage.CHAIN_PLACE;
        } else {
            Vec3 center = center(hit.getBlockPos());
            List<MinecartTNT> carts = client.level.getEntitiesOfClass(
                    MinecartTNT.class, new AABB(center, center).inflate(1.5)
            );
            if (carts.isEmpty()) {
                finish(client, "No TNT minecart found near the targeted block.", false);
                return;
            }
            targetCart = carts.getFirst();
            bowSlot = ensureHotbarItem(client, stack -> isFlameBow(client, stack));
            if (bowSlot < 0) {
                finish(client, "Missing a Flame-enchanted Bow.", false);
                return;
            }
            if (!hasArrow(player)) {
                finish(client, "A vanilla bow still needs at least one arrow in your inventory.", false);
                return;
            }
            if (!beginBow(client, bowSlot, targetCart.position().add(0.0, 0.5, 0.0))) {
                finish(client, "Could not draw the Flame Bow.", false);
                return;
            }
        }

        lastUseAt = now;
    }

    private static void placeRail(Minecraft client) {
        selectSlot(activePlayer, railSlot);
        BlockHitResult hit = topHit(placement.support);
        InteractionResult result = client.gameMode.useItemOn(activePlayer, InteractionHand.MAIN_HAND, hit);
        if (!(result instanceof InteractionResult.Success)) {
            finish(client, "The server did not accept the rail placement.", false);
            return;
        }
        stage = Stage.WAIT_RAIL;
        waitTicks = 1;
    }

    private static void placeCart(Minecraft client) {
        selectSlot(activePlayer, cartSlot);
        BlockPos railPos = placement.position;
        BlockHitResult hit = new BlockHitResult(
                new Vec3(railPos.getX() + 0.5, railPos.getY() + 0.05, railPos.getZ() + 0.5),
                Direction.UP, railPos, false
        );
        InteractionResult result = client.gameMode.useItemOn(activePlayer, InteractionHand.MAIN_HAND, hit);
        if (!(result instanceof InteractionResult.Success)) {
            finish(client, "The server did not accept the TNT minecart placement.", false);
            return;
        }
        stage = Stage.WAIT_CART;
        waitTicks = 1;
    }

    private static void placeNextChainRail(Minecraft client) {
        if (chainIndex >= chain.size()) {
            finish(client, "Placed " + chainIndex + " rail(s).", true);
            return;
        }
        Placement next = chain.get(chainIndex);
        selectSlot(activePlayer, railSlot);
        InteractionResult result = client.gameMode.useItemOn(
                activePlayer, InteractionHand.MAIN_HAND, topHit(next.support)
        );
        if (!(result instanceof InteractionResult.Success)) {
            finish(client, "Rail chain stopped at position " + (chainIndex + 1) + ".", false);
            return;
        }
        stage = Stage.WAIT_CHAIN;
        waitTicks = 1;
    }

    private static boolean beginBow(Minecraft client, int slot, Vec3 target) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !player.isAlive()) return false;
        selectSlot(player, slot);
        aimAt(player, target);
        InteractionResult result = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        if (!(result instanceof InteractionResult.Success)) {
            restoreRotation(player);
            return false;
        }
        bowStarted = true;
        stage = Stage.BOW_CHARGE;
        waitTicks = Math.max(1, Math.min(20, burstChargeTicks));
        return true;
    }

    private static Placement findPlacement(Minecraft client, BlockPos target, Direction face) {
        Placement direct = validPlacement(client, target.relative(face));
        if (direct != null) return direct;
        if (!smartAim) return null;

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction direction : directions) {
            Placement candidate = validPlacement(client, target.relative(direction).above());
            if (candidate != null) return candidate;
        }
        return validPlacement(client, target.relative(face).above());
    }

    private static Placement validPlacement(Minecraft client, BlockPos pos) {
        if (client.level == null) return null;
        var state = client.level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return null;
        BlockPos support = pos.below();
        if (!client.level.getBlockState(support).isFaceSturdy(client.level, support, Direction.UP)) return null;
        return new Placement(pos.immutable(), support.immutable());
    }

    private static BlockHitResult topHit(BlockPos support) {
        return new BlockHitResult(
                new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5),
                Direction.UP, support, false
        );
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static void aimAt(LocalPlayer player, Vec3 target) {
        Vec3 delta = target.subtract(player.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        player.setYRot(yaw);
        player.setXRot(pitch);
        changedRotation = true;
    }

    private static void restoreRotation(LocalPlayer player) {
        if (!changedRotation || player == null) return;
        player.setYRot(originalYaw);
        player.setXRot(originalPitch);
        changedRotation = false;
    }

    private static void selectSlot(LocalPlayer player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        lastProgrammaticSlot = slot;
    }

    private static int ensureHotbarItem(Minecraft client, Predicate<ItemStack> matcher) {
        LocalPlayer player = client.player;
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            if (matcher.test(player.getInventory().getItem(i))) {
                reservedHotbarSlots.add(i);
                return i;
            }
        }
        if (!autoRestock || client.gameMode == null) return -1;

        for (int sourceSlot = 9; sourceSlot < 36; sourceSlot++) {
            ItemStack source = player.getInventory().getItem(sourceSlot);
            if (!matcher.test(source)) continue;
            int targetSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (!reservedHotbarSlots.contains(i) && player.getInventory().getItem(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }
            if (targetSlot < 0) {
                for (int i = 0; i < 9; i++) {
                    if (!reservedHotbarSlots.contains(i)) {
                        targetSlot = i;
                        break;
                    }
                }
            }
            if (targetSlot < 0) return -1;

            ItemStack displaced = player.getInventory().getItem(targetSlot).copy();
            ItemStack moved = source.copy();
            client.gameMode.handleContainerInput(
                    player.containerMenu.containerId, sourceSlot, targetSlot, ContainerInput.SWAP, player
            );
            restockSwaps.add(new RestockSwap(sourceSlot, targetSlot, displaced, moved));
            reservedHotbarSlots.add(targetSlot);
            return targetSlot;
        }
        return -1;
    }

    private static boolean isFlameBow(Minecraft client, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.BOW) || client.level == null) return false;
        var enchantments = client.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var flame = enchantments.getOrThrow(Enchantments.FLAME);
        return EnchantmentHelper.getItemEnchantmentLevel(flame, stack) > 0;
    }

    private static boolean hasArrow(LocalPlayer player) {
        if (player.getAbilities().instabuild) return true;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW)) return true;
        }
        return false;
    }

    private static void finish(Minecraft client, String text, boolean success) {
        LocalPlayer player = activePlayer;
        if (player != null && bowStarted && client.gameMode != null) {
            client.gameMode.releaseUsingItem(player);
        }
        bowStarted = false;
        if (player != null) restoreRotation(player);

        if (player != null && client.gameMode != null && client.gui.screen() == null) {
            for (int i = restockSwaps.size() - 1; i >= 0; i--) {
                RestockSwap swap = restockSwaps.get(i);
                ItemStack sourceNow = player.getInventory().getItem(swap.sourceSlot);
                ItemStack hotbarNow = player.getInventory().getItem(swap.hotbarSlot);
                boolean sourceStillDisplaced = swap.displaced.isEmpty()
                        ? sourceNow.isEmpty()
                        : ItemStack.isSameItemSameComponents(sourceNow, swap.displaced);
                boolean destinationStillOurs = hotbarNow.isEmpty()
                        || ItemStack.isSameItemSameComponents(hotbarNow, swap.moved);
                if (sourceStillDisplaced && destinationStillOurs && !swap.displaced.isEmpty()) {
                    client.gameMode.handleContainerInput(
                            player.containerMenu.containerId, swap.sourceSlot, swap.hotbarSlot,
                            ContainerInput.SWAP, player
                    );
                }
            }
            if (player.getInventory().getSelectedSlot() == lastProgrammaticSlot) {
                player.getInventory().setSelectedSlot(originalSlot);
            }
        }

        if (client.player != null && text != null && !text.isBlank()) {
            client.player.sendOverlayMessage(Component.literal(text));
        }
        resetActionState();
    }

    private static void resetActionState() {
        stage = Stage.IDLE;
        activeMode = null;
        activePlayer = null;
        waitTicks = 0;
        railSlot = -1;
        cartSlot = -1;
        bowSlot = -1;
        placement = null;
        targetCart = null;
        chain.clear();
        chainIndex = 0;
        restockSwaps.clear();
        reservedHotbarSlots.clear();
        changedRotation = false;
        bowStarted = false;
    }

    private static void message(Minecraft client, String text) {
        if (client.player != null) client.player.sendOverlayMessage(Component.literal(text));
    }

    public static void cycleMode() {
        currentMode = currentMode.next();
        saveConfig();
    }

    public static String modeLabel() {
        return currentMode.label;
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
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "true"));
            autoRestock = Boolean.parseBoolean(properties.getProperty("autoRestock", "true"));
            smartAim = Boolean.parseBoolean(properties.getProperty("smartAim", "true"));
            cooldownMs = integer(properties, "cooldownMs", 500, 0, 10000);
            burstChargeTicks = integer(properties, "burstChargeTicks", 4, 1, 20);
            int mode = integer(properties, "defaultMode", 0, 0, Mode.values().length - 1);
            currentMode = Mode.values()[mode];
            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoTntCart] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("autoRestock", Boolean.toString(autoRestock));
        properties.setProperty("smartAim", Boolean.toString(smartAim));
        properties.setProperty("cooldownMs", Integer.toString(cooldownMs));
        properties.setProperty("burstChargeTicks", Integer.toString(burstChargeTicks));
        properties.setProperty("defaultMode", Integer.toString(currentMode.ordinal()));
        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                properties.store(output, "QYS-UTILS client-side AutoTntCart settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/AutoTntCart] Config save failed: " + exception.getMessage());
        }
    }

    private static int integer(Properties properties, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum Stage {
        IDLE, PLACE_RAIL, WAIT_RAIL, PLACE_CART, WAIT_CART,
        CHAIN_PLACE, WAIT_CHAIN, BOW_CHARGE
    }

    public enum Mode {
        BURST("Burst"), PLACE("Place"), CHAIN("Chain"), DETONATE("Detonate");

        private final String label;

        Mode(String label) { this.label = label; }

        private Mode next() {
            Mode[] modes = values();
            return modes[(ordinal() + 1) % modes.length];
        }
    }

    private record Placement(BlockPos position, BlockPos support) {}
    private record RestockSwap(int sourceSlot, int hotbarSlot, ItemStack displaced, ItemStack moved) {}
}
