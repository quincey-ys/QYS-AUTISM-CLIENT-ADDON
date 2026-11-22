package com.quinceyys.utils;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class CombatAssistModule {
    private static final Path CONFIG = Path.of("config", "qys-combat-assist.properties");

    public static boolean aimAssistEnabled = false;
    public static boolean aimRequireAttackHeld = true;
    public static boolean aimPlayers = true;
    public static boolean aimHostileMobs = false;
    public static boolean aimPassiveMobs = false;
    public static boolean aimIgnoreTeammates = true;
    public static boolean aimRequireLineOfSight = true;
    public static double aimRange = 4.5;
    public static double aimFov = 75.0;
    public static double aimStrength = 0.15;

    public static boolean triggerBotEnabled = false;
    public static boolean triggerRequireAttackHeld = true;
    public static boolean triggerWeaponOnly = true;
    public static boolean triggerPlayers = true;
    public static boolean triggerHostileMobs = false;
    public static boolean triggerPassiveMobs = false;
    public static boolean triggerIgnoreTeammates = true;
    public static int triggerDelayTicks = 2;

    private static long lastTriggerTick = Long.MIN_VALUE;
    private static Object trackedLevel;

    private CombatAssistModule() {}

    public static void initialize() {
        loadConfig();
        ClientTickEvents.END_CLIENT_TICK.register(CombatAssistModule::onEndTick);
    }

    private static void onEndTick(Minecraft client) {
        if (client.level != trackedLevel) {
            trackedLevel = client.level;
            lastTriggerTick = Long.MIN_VALUE;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || client.gameMode == null
                || !player.isAlive() || client.gui.screen() != null) {
            return;
        }

        if (aimAssistEnabled) applyAimAssist(client, player);
        if (triggerBotEnabled) applyTriggerBot(client, player);
    }

    private static void applyAimAssist(Minecraft client, LocalPlayer player) {
        if (aimRequireAttackHeld && !client.options.keyAttack.isDown()) return;
        if (player.isUsingItem() || player.isFallFlying() || player.isPassenger()) return;

        LivingEntity target = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye = player.getEyePosition();
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(
                    player, living, aimPlayers, aimHostileMobs, aimPassiveMobs,
                    aimIgnoreTeammates
            )) continue;

            double distance = player.distanceTo(living);
            if (distance > aimRange) continue;
            if (aimRequireLineOfSight && !player.hasLineOfSight(living)) continue;

            Vec3 targetPoint = living.getBoundingBox().getCenter();
            Angles angles = anglesTo(eye, targetPoint);
            double yawError = Math.abs(Mth.wrapDegrees(angles.yaw - currentYaw));
            double pitchError = Math.abs(angles.pitch - currentPitch);
            double angularError = Math.hypot(yawError, pitchError);

            if (angularError > aimFov * 0.5 || angularError >= bestScore) continue;
            bestScore = angularError;
            target = living;
        }

        if (target == null) return;

        Angles desired = anglesTo(eye, target.getBoundingBox().getCenter());
        float yawDelta = Mth.wrapDegrees(desired.yaw - player.getYRot());
        float pitchDelta = desired.pitch - player.getXRot();

        float strength = (float) Math.max(0.01, Math.min(1.0, aimStrength));
        float nextYaw = player.getYRot() + yawDelta * strength;
        float nextPitch = Mth.clamp(
                player.getXRot() + pitchDelta * strength,
                -90.0F,
                90.0F
        );

        player.setYRot(nextYaw);
        player.setXRot(nextPitch);
    }

    private static void applyTriggerBot(Minecraft client, LocalPlayer player) {
        if (triggerRequireAttackHeld && !client.options.keyAttack.isDown()) return;
        if (triggerWeaponOnly && !isHoldingWeapon(player.getMainHandItem())) return;
        if (!(client.hitResult instanceof EntityHitResult entityHit)) return;

        Entity entity = entityHit.getEntity();
        if (!(entity instanceof LivingEntity target) || !isValidTarget(
                player, target, triggerPlayers, triggerHostileMobs, triggerPassiveMobs,
                triggerIgnoreTeammates
        )) return;

        long tick = client.level.getGameTime();
        if (lastTriggerTick != Long.MIN_VALUE && tick - lastTriggerTick < triggerDelayTicks) return;
        if (player.getAttackStrengthScale(0.5F) < 0.95F) return;

        client.gameMode.attack(player, target);
        lastTriggerTick = tick;
    }

    private static boolean isValidTarget(
            LocalPlayer player,
            LivingEntity target,
            boolean players,
            boolean hostile,
            boolean passive,
            boolean ignoreTeammates
    ) {
        if (target == player || !target.isAlive() || target.isSpectator()) return false;

        if (target instanceof Player other) {
            return players && (!ignoreTeammates || !player.isAlliedTo(other));
        }

        if (!(target instanceof Mob mob)) return false;
        MobCategory category = mob.getType().getCategory();
        if (category == MobCategory.MONSTER) return hostile;
        return passive;
    }

    private static boolean isHoldingWeapon(ItemStack stack) {
        // DataComponents.TOOL is the vanilla marker used by tools and weapons.
        return stack.has(DataComponents.TOOL);
    }

    private static Angles anglesTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        return new Angles(yaw, pitch);
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

            aimAssistEnabled = bool(p, "aimAssistEnabled", false);
            aimRequireAttackHeld = bool(p, "aimRequireAttackHeld", true);
            aimPlayers = bool(p, "aimPlayers", true);
            aimHostileMobs = bool(p, "aimHostileMobs", false);
            aimPassiveMobs = bool(p, "aimPassiveMobs", false);
            aimIgnoreTeammates = bool(p, "aimIgnoreTeammates", true);
            aimRequireLineOfSight = bool(p, "aimRequireLineOfSight", true);
            aimRange = decimal(p, "aimRange", 4.5, 1.0, 6.0);
            aimFov = decimal(p, "aimFov", 75.0, 10.0, 180.0);
            aimStrength = decimal(p, "aimStrength", 0.15, 0.01, 1.0);

            triggerBotEnabled = bool(p, "triggerBotEnabled", false);
            triggerRequireAttackHeld = bool(p, "triggerRequireAttackHeld", true);
            triggerWeaponOnly = bool(p, "triggerWeaponOnly", true);
            triggerPlayers = bool(p, "triggerPlayers", true);
            triggerHostileMobs = bool(p, "triggerHostileMobs", false);
            triggerPassiveMobs = bool(p, "triggerPassiveMobs", false);
            triggerIgnoreTeammates = bool(p, "triggerIgnoreTeammates", true);
            triggerDelayTicks = integer(p, "triggerDelayTicks", 2, 0, 20);

            saveConfig();
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/CombatAssist] Config load failed: " + exception.getMessage());
        }
    }

    public static void saveConfig() {
        Properties p = new Properties();
        p.setProperty("aimAssistEnabled", Boolean.toString(aimAssistEnabled));
        p.setProperty("aimRequireAttackHeld", Boolean.toString(aimRequireAttackHeld));
        p.setProperty("aimPlayers", Boolean.toString(aimPlayers));
        p.setProperty("aimHostileMobs", Boolean.toString(aimHostileMobs));
        p.setProperty("aimPassiveMobs", Boolean.toString(aimPassiveMobs));
        p.setProperty("aimIgnoreTeammates", Boolean.toString(aimIgnoreTeammates));
        p.setProperty("aimRequireLineOfSight", Boolean.toString(aimRequireLineOfSight));
        p.setProperty("aimRange", Double.toString(aimRange));
        p.setProperty("aimFov", Double.toString(aimFov));
        p.setProperty("aimStrength", Double.toString(aimStrength));

        p.setProperty("triggerBotEnabled", Boolean.toString(triggerBotEnabled));
        p.setProperty("triggerRequireAttackHeld", Boolean.toString(triggerRequireAttackHeld));
        p.setProperty("triggerWeaponOnly", Boolean.toString(triggerWeaponOnly));
        p.setProperty("triggerPlayers", Boolean.toString(triggerPlayers));
        p.setProperty("triggerHostileMobs", Boolean.toString(triggerHostileMobs));
        p.setProperty("triggerPassiveMobs", Boolean.toString(triggerPassiveMobs));
        p.setProperty("triggerIgnoreTeammates", Boolean.toString(triggerIgnoreTeammates));
        p.setProperty("triggerDelayTicks", Integer.toString(triggerDelayTicks));

        try {
            Files.createDirectories(CONFIG.getParent());
            try (OutputStream output = Files.newOutputStream(CONFIG)) {
                p.store(output, "QYS-UTILS Combat Assist settings");
            }
        } catch (IOException exception) {
            System.err.println("[QYS-UTILS/CombatAssist] Config save failed: " + exception.getMessage());
        }
    }

    private static boolean bool(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, Boolean.toString(fallback)));
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max,
                    Integer.parseInt(p.getProperty(key, Integer.toString(fallback)))));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double decimal(Properties p, String key, double fallback, double min, double max) {
        try {
            double value = Double.parseDouble(p.getProperty(key, Double.toString(fallback)));
            return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private record Angles(float yaw, float pitch) {}
}
