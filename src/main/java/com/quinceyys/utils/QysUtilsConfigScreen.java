package com.quinceyys.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class QysUtilsConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 23;

    private final Screen parent;
    private final int selectedTab;
    private int scrollOffset;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public QysUtilsConfigScreen(Screen parent) {
        this(parent, 0);
    }

    private QysUtilsConfigScreen(Screen parent, int selectedTab) {
        super(Component.literal("QYS-UTILS"));
        this.parent = parent;
        this.selectedTab = selectedTab;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, this.width - 16);
        panelHeight = Math.min(382, this.height - 16);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        int tabWidth = Math.min(164, (panelWidth - 32) / 3);
        int tabStartX = panelX + 10;
        int firstTabY = panelY + 30;
        int secondTabY = panelY + 52;

        addTab("AUTO TOTEM", 0, tabStartX, firstTabY, tabWidth);
        addTab("MACE HITTER", 1, tabStartX + tabWidth + 4, firstTabY, tabWidth);
        addTab("COMBAT", 2, tabStartX + (tabWidth + 4) * 2, firstTabY, tabWidth);
        addTab("CRYSTAL / ANCHOR", 3, tabStartX, secondTabY, tabWidth);
        addTab("MOVEMENT", 4, tabStartX + tabWidth + 4, secondTabY, tabWidth);
        addTab("CHAT GAMES", 5, tabStartX + (tabWidth + 4) * 2, secondTabY, tabWidth);

        int leftX = panelX + 14;
        int rightX = panelX + panelWidth / 2 + 4;
        int colWidth = panelWidth / 2 - 22;
        int contentTop = contentTop();
        int startY = contentTop + (selectedTab == 2 ? 18 : 2);
        int y;

        if (selectedTab == 0) {
            y = startY;
            addToggle(leftX, y, colWidth, "AutoTotem Enabled",
                    () -> AutoTotemModule.enabled, v -> AutoTotemModule.enabled = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Health Trigger",
                    () -> AutoTotemModule.healthMode, v -> AutoTotemModule.healthMode = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Fall Prediction",
                    () -> AutoTotemModule.predictFall, v -> AutoTotemModule.predictFall = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Missing Armor",
                    () -> AutoTotemModule.missingArmor, v -> AutoTotemModule.missingArmor = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Safety Check",
                    () -> AutoTotemModule.safety, v -> AutoTotemModule.safety = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Switch Back",
                    () -> AutoTotemModule.switchBack, v -> AutoTotemModule.switchBack = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Send Directly",
                    () -> AutoTotemModule.sendDirectly, v -> AutoTotemModule.sendDirectly = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Ignore Elytra",
                    () -> AutoTotemModule.ignoreElytra, v -> AutoTotemModule.ignoreElytra = v);

            y = startY;
            addSlider(rightX, y, colWidth, "Health Threshold", 0, 20, 1,
                    () -> AutoTotemModule.healthThreshold, v -> AutoTotemModule.healthThreshold = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Safe Health", 0, 20, 1,
                    () -> AutoTotemModule.safeHealthThreshold, v -> AutoTotemModule.safeHealthThreshold = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Switch Delay (ms)", 0, 5000, 50,
                    () -> AutoTotemModule.switchDelayMs, v -> AutoTotemModule.switchDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Switch Back (ms)", 0, 5000, 50,
                    () -> AutoTotemModule.switchBackDelayMs, v -> AutoTotemModule.switchBackDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Reaction Ticks", 0, 20, 1,
                    () -> AutoTotemModule.reactionTicks, v -> AutoTotemModule.reactionTicks = (int) Math.round(v)); y += ROW_HEIGHT;
            addModeButton(rightX, y, colWidth);
        } else if (selectedTab == 1) {
            y = startY;
            addToggle(leftX, y, colWidth, "Mace Hitter Enabled",
                    () -> MaceHitterModule.enabled, v -> MaceHitterModule.enabled = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Rotate to Target",
                    () -> MaceHitterModule.rotate, v -> MaceHitterModule.rotate = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Auto-Swap Mace",
                    () -> MaceHitterModule.autoSwap, v -> MaceHitterModule.autoSwap = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Auto-Jump",
                    () -> MaceHitterModule.autoJump, v -> MaceHitterModule.autoJump = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Falling Only",
                    () -> MaceHitterModule.fallingOnly, v -> MaceHitterModule.fallingOnly = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Shield-Stun Priority",
                    () -> MaceHitterModule.shieldStun, v -> MaceHitterModule.shieldStun = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Require Mace",
                    () -> MaceHitterModule.requireMace, v -> MaceHitterModule.requireMace = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Ignore Walls",
                    () -> MaceHitterModule.ignoreWalls, v -> MaceHitterModule.ignoreWalls = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Players Only",
                    () -> MaceHitterModule.playersOnly, v -> MaceHitterModule.playersOnly = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Ignore Teammates",
                    () -> MaceHitterModule.ignoreTeammates, v -> MaceHitterModule.ignoreTeammates = v);

            y = startY;
            addSlider(rightX, y, colWidth, "Target Range", 1.0, 6.0, 0.1,
                    () -> MaceHitterModule.range, v -> MaceHitterModule.range = v); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Attack Delay (ticks)", 0, 20, 1,
                    () -> MaceHitterModule.delayTicks, v -> MaceHitterModule.delayTicks = (int) Math.round(v)); y += ROW_HEIGHT;
            addButton(rightX, y, colWidth, "SLAM NOW  [G]", b -> MaceHitterModule.slamNow());
        } else if (selectedTab == 2) {
            y = startY;
            addToggle(leftX, y, colWidth, "Aim Assist",
                    () -> CombatAssistModule.aimAssistEnabled, v -> CombatAssistModule.aimAssistEnabled = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Aim While Holding Attack",
                    () -> CombatAssistModule.aimRequireAttackHeld, v -> CombatAssistModule.aimRequireAttackHeld = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Aim at Players",
                    () -> CombatAssistModule.aimPlayers, v -> CombatAssistModule.aimPlayers = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Aim at Hostile Mobs",
                    () -> CombatAssistModule.aimHostileMobs, v -> CombatAssistModule.aimHostileMobs = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Aim at Passive Mobs",
                    () -> CombatAssistModule.aimPassiveMobs, v -> CombatAssistModule.aimPassiveMobs = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Ignore Teammates",
                    () -> CombatAssistModule.aimIgnoreTeammates, v -> CombatAssistModule.aimIgnoreTeammates = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Require Line of Sight",
                    () -> CombatAssistModule.aimRequireLineOfSight, v -> CombatAssistModule.aimRequireLineOfSight = v); y += ROW_HEIGHT;
            addSlider(leftX, y, colWidth, "Aim Range", 1.0, 6.0, 0.1,
                    () -> CombatAssistModule.aimRange, v -> CombatAssistModule.aimRange = v); y += ROW_HEIGHT;
            addSlider(leftX, y, colWidth, "Aim FOV", 10.0, 180.0, 1.0,
                    () -> CombatAssistModule.aimFov, v -> CombatAssistModule.aimFov = v); y += ROW_HEIGHT;
            addSlider(leftX, y, colWidth, "Aim Strength", 0.01, 0.60, 0.01,
                    () -> CombatAssistModule.aimStrength, v -> CombatAssistModule.aimStrength = v);

            y = startY;
            addToggle(rightX, y, colWidth, "Trigger Bot",
                    () -> CombatAssistModule.triggerBotEnabled, v -> CombatAssistModule.triggerBotEnabled = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Require Attack Held",
                    () -> CombatAssistModule.triggerRequireAttackHeld, v -> CombatAssistModule.triggerRequireAttackHeld = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Weapon Only",
                    () -> CombatAssistModule.triggerWeaponOnly, v -> CombatAssistModule.triggerWeaponOnly = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Trigger on Players",
                    () -> CombatAssistModule.triggerPlayers, v -> CombatAssistModule.triggerPlayers = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Trigger on Hostile Mobs",
                    () -> CombatAssistModule.triggerHostileMobs, v -> CombatAssistModule.triggerHostileMobs = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Trigger on Passive Mobs",
                    () -> CombatAssistModule.triggerPassiveMobs, v -> CombatAssistModule.triggerPassiveMobs = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Ignore Teammates",
                    () -> CombatAssistModule.triggerIgnoreTeammates, v -> CombatAssistModule.triggerIgnoreTeammates = v); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Attack Delay (ticks)", 0, 20, 1,
                    () -> CombatAssistModule.triggerDelayTicks, v -> CombatAssistModule.triggerDelayTicks = (int) Math.round(v));
        } else if (selectedTab == 3) {
            y = startY;
            addToggle(leftX, y, colWidth, "Break Your Crystals",
                    () -> AutoPlaceCombatModule.autoBreakOwnCrystals,
                    v -> AutoPlaceCombatModule.autoBreakOwnCrystals = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Require Right-Click",
                    () -> AutoPlaceCombatModule.crystalRequireUseHeld,
                    v -> AutoPlaceCombatModule.crystalRequireUseHeld = v); y += ROW_HEIGHT;
            addSlider(leftX, y, colWidth, "Break Delay (ms)", 0, 1000, 25,
                    () -> AutoPlaceCombatModule.crystalBreakDelayMs,
                    v -> AutoPlaceCombatModule.crystalBreakDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(leftX, y, colWidth, "Max Breaks/Sec", 1, 20, 1,
                    () -> AutoPlaceCombatModule.crystalMaxBreaksPerSecond,
                    v -> AutoPlaceCombatModule.crystalMaxBreaksPerSecond = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(leftX, y, colWidth, "Placement Window (ms)", 250, 5000, 250,
                    () -> AutoPlaceCombatModule.crystalPlacementWindowMs,
                    v -> AutoPlaceCombatModule.crystalPlacementWindowMs = (int) Math.round(v));

            y = startY;
            addToggle(rightX, y, colWidth, "Auto Anchor Combo",
                    () -> AutoPlaceCombatModule.autoDetonateOwnAnchors,
                    v -> AutoPlaceCombatModule.autoDetonateOwnAnchors = v); y += ROW_HEIGHT;
            addToggle(rightX, y, colWidth, "Require Right-Click",
                    () -> AutoPlaceCombatModule.anchorRequireUseHeldAtDetonate,
                    v -> AutoPlaceCombatModule.anchorRequireUseHeldAtDetonate = v); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Place Delay (ms)", 0, 2000, 50,
                    () -> AutoPlaceCombatModule.anchorPlaceDelayMs,
                    v -> AutoPlaceCombatModule.anchorPlaceDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Switch Delay (ms)", 0, 2000, 50,
                    () -> AutoPlaceCombatModule.anchorSwitchDelayMs,
                    v -> AutoPlaceCombatModule.anchorSwitchDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Charge Delay (ms)", 0, 3000, 50,
                    () -> AutoPlaceCombatModule.anchorChargeDelayMs,
                    v -> AutoPlaceCombatModule.anchorChargeDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Detonate Delay (ms)", 0, 3000, 50,
                    () -> AutoPlaceCombatModule.anchorDetonateDelayMs,
                    v -> AutoPlaceCombatModule.anchorDetonateDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Combo Cooldown (ms)", 0, 5000, 100,
                    () -> AutoPlaceCombatModule.anchorCooldownMs,
                    v -> AutoPlaceCombatModule.anchorCooldownMs = (int) Math.round(v));
        } else if (selectedTab == 4) {
            y = startY;
            addToggle(leftX, y, colWidth, "Auto Sprint",
                    () -> AutoSprintModule.enabled,
                    v -> AutoSprintModule.enabled = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Pause While Using Items",
                    () -> AutoSprintModule.pauseWhileUsingItems,
                    v -> AutoSprintModule.pauseWhileUsingItems = v);
        } else {
            y = startY;
            addToggle(leftX, y, colWidth, "Chat Game Guesser",
                    () -> ChatGameGuesserModule.enabled,
                    v -> ChatGameGuesserModule.enabled = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Auto Send Answers",
                    () -> ChatGameGuesserModule.autoSend,
                    v -> ChatGameGuesserModule.autoSend = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Recognized Prompts Only",
                    () -> ChatGameGuesserModule.onlyRecognizedPrompts,
                    v -> ChatGameGuesserModule.onlyRecognizedPrompts = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Solve Math",
                    () -> ChatGameGuesserModule.solveMath,
                    v -> ChatGameGuesserModule.solveMath = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Solve Scrambles",
                    () -> ChatGameGuesserModule.solveScramble,
                    v -> ChatGameGuesserModule.solveScramble = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Solve Reversed Text",
                    () -> ChatGameGuesserModule.solveReverse,
                    v -> ChatGameGuesserModule.solveReverse = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Solve Typing Prompts",
                    () -> ChatGameGuesserModule.solveTyping,
                    v -> ChatGameGuesserModule.solveTyping = v); y += ROW_HEIGHT;
            addToggle(leftX, y, colWidth, "Solve Trivia",
                    () -> ChatGameGuesserModule.solveTrivia,
                    v -> ChatGameGuesserModule.solveTrivia = v);

            y = startY;
            addSlider(rightX, y, colWidth, "Answer Delay (ms)", 0, 5000, 50,
                    () -> ChatGameGuesserModule.answerDelayMs,
                    v -> ChatGameGuesserModule.answerDelayMs = (int) Math.round(v)); y += ROW_HEIGHT;
            addSlider(rightX, y, colWidth, "Min Send Interval (ms)", 500, 10000, 100,
                    () -> ChatGameGuesserModule.minimumSendIntervalMs,
                    v -> ChatGameGuesserModule.minimumSendIntervalMs = (int) Math.round(v));
        }

        addButton(panelX + panelWidth / 2 - 96, panelY + panelHeight - 31, 192,
                "DONE  •  SAVE SETTINGS", b -> onClose());
    }

    private void addTab(String label, int tab, int x, int y, int width) {
        addButton(x, y, width, label, b -> openTab(tab));
    }

    private void openTab(int tab) {
        Minecraft.getInstance().gui.setScreen(new QysUtilsConfigScreen(parent, tab));
    }

    private void addButton(int x, int y, int width, String label,
                           java.util.function.Consumer<Button> action) {
        Button button = Button.builder(Component.literal(fit(label, 31)), b -> action.accept(b))
                .bounds(x, y, width, 20).build();
        addRenderableWidget(button);
    }

    private void addToggle(int x, int y, int width, String label,
                           BooleanSupplier getter, Consumer<Boolean> setter) {
        int displayY = displayY(y);
        Button button = Button.builder(toggleText(label, getter.getAsBoolean()), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            b.setMessage(toggleText(label, value));
            persist();
        }).bounds(x, displayY, width, 19).build();
        button.visible = rowVisible(y);
        addRenderableWidget(button);
    }

    private void addSlider(int x, int y, int width, String label, double min, double max, double step,
                           DoubleSupplier getter, DoubleConsumer setter) {
        int displayY = displayY(y);
        ConfigSlider slider = new ConfigSlider(
                x, displayY, width, 19, label, min, max, step, getter.getAsDouble(),
                setter, this::persist
        );
        slider.visible = rowVisible(y);
        addRenderableWidget(slider);
    }

    private void addModeButton(int x, int y, int width) {
        int displayY = displayY(y);
        Button button = Button.builder(modeText(), b -> {
            String[] modes = {"Smart", "Switch", "PickUp"};
            int index = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equalsIgnoreCase(AutoTotemModule.switchMode)) index = i;
            }
            AutoTotemModule.switchMode = modes[(index + 1) % modes.length];
            b.setMessage(modeText());
            persist();
        }).bounds(x, displayY, width, 20).build();
        button.visible = rowVisible(y);
        addRenderableWidget(button);
    }

    private int displayY(int y) {
        return y - scrollOffset * ROW_HEIGHT;
    }

    private int contentTop() {
        return panelY + 108;
    }

    private int contentBottom() {
        return panelY + panelHeight - 47;
    }

    private int rowsTop() {
        return contentTop() + (selectedTab == 2 ? 18 : 2);
    }

    private boolean rowVisible(int y) {
        int displayY = displayY(y);
        return displayY >= rowsTop() && displayY + 20 <= contentBottom();
    }

    private int rowCount() {
        return switch (selectedTab) {
            case 0 -> 8;
            case 1 -> 10;
            case 2 -> 10;
            case 3 -> 7;
            case 4 -> 2;
            case 5 -> 8;
            default -> 1;
        };
    }

    private int visibleRows() {
        return Math.max(1, (contentBottom() - rowsTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, rowCount() - visibleRows());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= panelX && mouseX <= panelX + panelWidth
                && mouseY >= contentTop() && mouseY <= contentBottom() && scrollY != 0.0) {
            int next = Math.max(0, Math.min(maxScroll(),
                    scrollOffset + (scrollY < 0 ? 1 : -1)));
            if (next != scrollOffset) {
                scrollOffset = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static Component toggleText(String label, boolean enabled) {
        return Component.literal(fit(label + ": " + (enabled ? "ON" : "OFF"), 31));
    }

    private static String fit(String text, int maxChars) {
        if (text.length() <= maxChars) return text;
        return text.substring(0, Math.max(1, maxChars - 1)) + "…";
    }

    private static Component modeText() {
        return Component.literal("Switch Mode: " + AutoTotemModule.switchMode);
    }

    private static void persist() {
        AutoTotemModule.saveConfig();
        MaceHitterModule.saveConfig();
        AutoPlaceCombatModule.saveConfig();
        AutoSprintModule.saveConfig();
        CombatAssistModule.saveConfig();
        ChatGameGuesserModule.saveConfig();
    }

    private static final class ConfigSlider extends AbstractSliderButton {
        private final String label;
        private final double minimum;
        private final double maximum;
        private final double step;
        private final DoubleConsumer setter;
        private final Runnable onChange;

        private ConfigSlider(int x, int y, int width, int height, String label,
                             double minimum, double maximum, double step, double initial,
                             DoubleConsumer setter, Runnable onChange) {
            super(x, y, width, height, Component.empty(),
                    Math.max(0.0, Math.min(1.0, (initial - minimum) / (maximum - minimum))));
            this.label = label;
            this.minimum = minimum;
            this.maximum = maximum;
            this.step = step;
            this.setter = setter;
            this.onChange = onChange;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double current = minimum + value * (maximum - minimum);
            current = Math.round(current / step) * step;
            String formatted = step >= 1.0
                    ? String.format(java.util.Locale.ROOT, "%.0f", current)
                    : String.format(java.util.Locale.ROOT, "%.2f", current);
            setMessage(Component.literal(fit(label + ": " + formatted, 31)));
        }

        @Override
        protected void applyValue() {
            double current = minimum + value * (maximum - minimum);
            current = Math.round(current / step) * step;
            current = Math.max(minimum, Math.min(maximum, current));
            setter.accept(current);
            updateMessage();
            onChange.run();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB8000000);
        fillSquircle(graphics, panelX, panelY, panelWidth, panelHeight, 18, 0xFF141923);
        graphics.fill(panelX + 18, panelY + 2, panelX + panelWidth - 18, panelY + 28, 0xFF293449);
        graphics.fill(panelX + 2, panelY + 14, panelX + panelWidth - 2, panelY + 28, 0xFF293449);

        int contentY = contentTop();
        int contentHeight = contentBottom() - contentY;
        int half = panelWidth / 2 - 18;
        fillSquircle(graphics, panelX + 9, contentY, half, contentHeight, 10, 0xFF1C2431);
        fillSquircle(graphics, panelX + panelWidth / 2 + 6, contentY, half, contentHeight, 10, 0xFF1C2431);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.text(this.font, "QYS-UTILS", panelX + 16, panelY + 8, 0xFFFFFFFF, true);
        graphics.text(this.font, "UNIFIED CLIENT UTILITIES  /  26.3",
                panelX + 116, panelY + 8, 0xFFD5DCEC, false);

        String title = switch (selectedTab) {
            case 1 -> "MACE HITTER CONFIGURATION";
            case 2 -> "COMBAT ASSIST CONFIGURATION";
            case 3 -> "CRYSTAL / ANCHOR CONFIGURATION";
            case 4 -> "MOVEMENT CONFIGURATION";
            case 5 -> "CHAT GAME GUESSER";
            default -> "AUTOTOTEM CONFIGURATION";
        };
        graphics.text(this.font, title, panelX + 16, panelY + 82, 0xFFE4EAF5, false);

        if (selectedTab == 2) {
            graphics.text(this.font, "AIM ASSIST", panelX + 18, contentTop() + 5, 0xFFE4EAF5, true);
            graphics.text(this.font, "TRIGGER BOT", panelX + panelWidth / 2 + 12,
                    contentTop() + 5, 0xFFE4EAF5, true);
        } else if (selectedTab == 3) {
            graphics.text(this.font, "OWN-PLACED CRYSTALS", panelX + 18, panelY + 100, 0xFFE4EAF5, true);
            graphics.text(this.font, "OWN-PLACED ANCHORS", panelX + panelWidth / 2 + 12,
                    panelY + 100, 0xFFE4EAF5, true);
        } else if (selectedTab == 4) {
            graphics.text(this.font, "AUTOMATIC SPRINT", panelX + panelWidth / 2 + 18,
                    contentTop() + 12, 0xFFE4EAF5, true);
            graphics.text(this.font, "Sprints while moving forward.", panelX + panelWidth / 2 + 18,
                    contentTop() + 32, 0xFFD5DCEC, false);
            graphics.text(this.font, "Pauses while sneaking, in menus,", panelX + panelWidth / 2 + 18,
                    contentTop() + 46, 0xFFD5DCEC, false);
            graphics.text(this.font, "or using items when selected.", panelX + panelWidth / 2 + 18,
                    contentTop() + 60, 0xFFD5DCEC, false);
        } else if (selectedTab == 5) {
            graphics.text(this.font, "SUPPORTED PROMPTS", panelX + panelWidth / 2 + 18,
                    contentTop() + 12, 0xFFE4EAF5, true);
            graphics.text(this.font, "Math, word scrambles, reverse text,", panelX + panelWidth / 2 + 18,
                    contentTop() + 32, 0xFFD5DCEC, false);
            graphics.text(this.font, "typing prompts and built-in trivia.", panelX + panelWidth / 2 + 18,
                    contentTop() + 46, 0xFFD5DCEC, false);
            graphics.text(this.font, "Auto Send is off by default.", panelX + panelWidth / 2 + 18,
                    contentTop() + 66, 0xFFD5DCEC, false);
        }

        if (maxScroll() > 0) {
            int trackX = panelX + panelWidth - 9;
            int trackTop = contentTop() + 2;
            int trackBottom = contentBottom() - 2;
            int trackHeight = trackBottom - trackTop;
            graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0xFF394558);
            int thumbHeight = Math.max(20, trackHeight * visibleRows() / rowCount());
            int thumbY = trackTop + (trackHeight - thumbHeight) * scrollOffset / maxScroll();
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, 0xFFB5C4DA);
        }
    }

    private void fillSquircle(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                              int radius, int color) {
        int r = Math.max(1, Math.min(radius, Math.min(width, height) / 2));
        for (int row = 0; row < height; row++) {
            int fromEdge = Math.min(row, height - 1 - row);
            int inset = 0;
            if (fromEdge < r) {
                double t = 1.0 - fromEdge / (double) r;
                double curve = 1.0 - Math.pow(t, 4.0);
                inset = (int) Math.round(r * (1.0 - Math.pow(curve, 0.25)));
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    @Override
    public void onClose() {
        persist();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
