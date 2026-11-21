package com.quinceyys.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class QysUtilsConfigScreen extends Screen {
    private static final FontDescription UI_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("qys_utils", "ui"));

    private static Component ui(String value) {
        return Component.literal(value).withStyle(style -> style.withFont(UI_FONT));
    }
    private final Screen parent;
    private final boolean maceTab;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public QysUtilsConfigScreen(Screen parent) {
        this(parent, false);
    }

    private QysUtilsConfigScreen(Screen parent, boolean maceTab) {
        super(ui("QYS-UTILS"));
        this.parent = parent;
        this.maceTab = maceTab;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(448, this.width - 16);
        panelHeight = Math.min(322, this.height - 16);
        panelX = (this.width - panelWidth) / 2;
        panelY = (this.height - panelHeight) / 2;

        int tabY = panelY + 34;
        int tabWidth = Math.min(132, (panelWidth - 36) / 2);
        addRenderableWidget(Button.builder(ui("AUTOTOTEM"), b -> openTab(false))
                .bounds(panelX + 14, tabY, tabWidth, 20).build());
        addRenderableWidget(Button.builder(ui("MACE HITTER"), b -> openTab(true))
                .bounds(panelX + 22 + tabWidth, tabY, tabWidth, 20).build());

        int leftX = panelX + 14;
        int rightX = panelX + panelWidth / 2 + 4;
        int colWidth = panelWidth / 2 - 22;
        int startY = panelY + 68;
        int rowHeight = 21;

        if (!maceTab) {
            int y = startY;
            addToggle(leftX, y, colWidth, "AutoTotem Enabled",
                    () -> AutoTotemModule.enabled, v -> AutoTotemModule.enabled = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Health Trigger",
                    () -> AutoTotemModule.healthMode, v -> AutoTotemModule.healthMode = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Fall Prediction",
                    () -> AutoTotemModule.predictFall, v -> AutoTotemModule.predictFall = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Missing Armor",
                    () -> AutoTotemModule.missingArmor, v -> AutoTotemModule.missingArmor = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Safety Check",
                    () -> AutoTotemModule.safety, v -> AutoTotemModule.safety = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Switch Back",
                    () -> AutoTotemModule.switchBack, v -> AutoTotemModule.switchBack = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Send Directly",
                    () -> AutoTotemModule.sendDirectly, v -> AutoTotemModule.sendDirectly = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Ignore Elytra",
                    () -> AutoTotemModule.ignoreElytra, v -> AutoTotemModule.ignoreElytra = v);

            y = startY;
            addIntStepper(rightX, y, colWidth, "Health Threshold", 0, 20, 1,
                    () -> AutoTotemModule.healthThreshold, v -> AutoTotemModule.healthThreshold = v); y += rowHeight;
            addIntStepper(rightX, y, colWidth, "Safe Health", 0, 20, 1,
                    () -> AutoTotemModule.safeHealthThreshold, v -> AutoTotemModule.safeHealthThreshold = v); y += rowHeight;
            addIntStepper(rightX, y, colWidth, "Switch Delay (ms)", 0, 5000, 50,
                    () -> AutoTotemModule.switchDelayMs, v -> AutoTotemModule.switchDelayMs = v); y += rowHeight;
            addIntStepper(rightX, y, colWidth, "Switch Back (ms)", 0, 5000, 50,
                    () -> AutoTotemModule.switchBackDelayMs, v -> AutoTotemModule.switchBackDelayMs = v); y += rowHeight;
            addIntStepper(rightX, y, colWidth, "Reaction Ticks", 0, 20, 1,
                    () -> AutoTotemModule.reactionTicks, v -> AutoTotemModule.reactionTicks = v); y += rowHeight;

            addModeButton(rightX, y, colWidth);
        } else {
            int y = startY;
            addToggle(leftX, y, colWidth, "Mace Hitter Enabled",
                    () -> MaceHitterModule.enabled, v -> MaceHitterModule.enabled = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Rotate to Target",
                    () -> MaceHitterModule.rotate, v -> MaceHitterModule.rotate = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Auto-Swap Mace",
                    () -> MaceHitterModule.autoSwap, v -> MaceHitterModule.autoSwap = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Auto-Jump",
                    () -> MaceHitterModule.autoJump, v -> MaceHitterModule.autoJump = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Falling Only",
                    () -> MaceHitterModule.fallingOnly, v -> MaceHitterModule.fallingOnly = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Shield-Stun Priority",
                    () -> MaceHitterModule.shieldStun, v -> MaceHitterModule.shieldStun = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Require Mace",
                    () -> MaceHitterModule.requireMace, v -> MaceHitterModule.requireMace = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Ignore Walls",
                    () -> MaceHitterModule.ignoreWalls, v -> MaceHitterModule.ignoreWalls = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Players Only",
                    () -> MaceHitterModule.playersOnly, v -> MaceHitterModule.playersOnly = v); y += rowHeight;
            addToggle(leftX, y, colWidth, "Ignore Teammates",
                    () -> MaceHitterModule.ignoreTeammates, v -> MaceHitterModule.ignoreTeammates = v);

            y = startY;
            addDoubleStepper(rightX, y, colWidth, "Target Range", 1.0, 6.0, 0.1,
                    () -> MaceHitterModule.range, v -> MaceHitterModule.range = v); y += rowHeight;
            addIntStepper(rightX, y, colWidth, "Attack Delay (ticks)", 0, 20, 1,
                    () -> MaceHitterModule.delayTicks, v -> MaceHitterModule.delayTicks = v);
            addRenderableWidget(Button.builder(ui("SLAM NOW  [G]"), b -> MaceHitterModule.slamNow())
                    .bounds(rightX, startY + rowHeight * 3, colWidth, 20).build());
        }

        addRenderableWidget(Button.builder(ui("DONE  •  SAVE SETTINGS"), b -> onClose())
                .bounds(panelX + panelWidth / 2 - 94, panelY + panelHeight - 32, 188, 22).build());
    }

    private void openTab(boolean showMace) {
        Minecraft.getInstance().gui.setScreen(new QysUtilsConfigScreen(parent, showMace));
    }

    private void addToggle(int x, int y, int width, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        Button button = Button.builder(toggleText(label, getter.getAsBoolean()), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            b.setMessage(toggleText(label, value));
            persist();
        }).bounds(x, y, width, 19).build();
        addRenderableWidget(button);
    }

    private void addIntStepper(int x, int y, int width, String label, int min, int max, int step,
                               IntSupplier getter, IntConsumer setter) {
        Button[] valueButton = new Button[1];
        addRenderableWidget(Button.builder(ui("−"), b -> {
            setter.accept(Math.max(min, getter.getAsInt() - step));
            valueButton[0].setMessage(intText(label, getter.getAsInt()));
            persist();
        }).bounds(x, y, 22, 19).build());

        valueButton[0] = Button.builder(intText(label, getter.getAsInt()), b -> {})
                .bounds(x + 24, y, width - 48, 19).build();
        addRenderableWidget(valueButton[0]);

        addRenderableWidget(Button.builder(ui("+"), b -> {
            setter.accept(Math.min(max, getter.getAsInt() + step));
            valueButton[0].setMessage(intText(label, getter.getAsInt()));
            persist();
        }).bounds(x + width - 22, y, 22, 19).build());
    }

    private void addDoubleStepper(int x, int y, int width, String label, double min, double max, double step,
                                  DoubleSupplier getter, DoubleConsumer setter) {
        Button[] valueButton = new Button[1];
        addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            setter.accept(Math.max(min, Math.round((getter.getAsDouble() - step) * 10.0) / 10.0));
            valueButton[0].setMessage(doubleText(label, getter.getAsDouble()));
            persist();
        }).bounds(x, y, 22, 19).build());

        valueButton[0] = Button.builder(doubleText(label, getter.getAsDouble()), b -> {})
                .bounds(x + 24, y, width - 48, 19).build();
        addRenderableWidget(valueButton[0]);

        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            setter.accept(Math.min(max, Math.round((getter.getAsDouble() + step) * 10.0) / 10.0));
            valueButton[0].setMessage(doubleText(label, getter.getAsDouble()));
            persist();
        }).bounds(x + width - 22, y, 22, 19).build());
    }

    private void addModeButton(int x, int y, int width) {
        addRenderableWidget(Button.builder(modeText(), b -> {
            String[] modes = {"Smart", "Switch", "PickUp"};
            int index = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equalsIgnoreCase(AutoTotemModule.switchMode)) index = i;
            }
            AutoTotemModule.switchMode = modes[(index + 1) % modes.length];
            b.setMessage(modeText());
            persist();
        }).bounds(x, y, width, 20).build());
    }

    private static Component toggleText(String label, boolean enabled) {
        return ui(label + ": " + (enabled ? "ON" : "OFF"));
    }

    private static Component intText(String label, int value) {
        return ui(label + ": " + value);
    }

    private static Component doubleText(String label, double value) {
        return ui(label + ": " + String.format(java.util.Locale.ROOT, "%.1f", value));
    }

    private static Component modeText() {
        return ui("Switch Mode: " + AutoTotemModule.switchMode);
    }

    private static void persist() {
        AutoTotemModule.saveConfig();
        MaceHitterModule.saveConfig();
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, this.width, this.height, 0xB8000000);
        fillSquircle(graphics, panelX, panelY, panelWidth, panelHeight, 18, 0xFF141923);
        graphics.fill(panelX + 18, panelY + 2, panelX + panelWidth - 18, panelY + 28, 0xFF293449);
        graphics.fill(panelX + 2, panelY + 14, panelX + panelWidth - 2, panelY + 28, 0xFF293449);

        int contentY = panelY + 62;
        int contentHeight = panelHeight - 106;
        int half = panelWidth / 2 - 18;
        fillSquircle(graphics, panelX + 9, contentY, half, contentHeight, 10, 0xFF1C2431);
        fillSquircle(graphics, panelX + panelWidth / 2 + 6, contentY, half, contentHeight, 10, 0xFF1C2431);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.text(this.font, ui("QYS-UTILS"), panelX + 16, panelY + 9, 0xFFFFFFFF, true);
        graphics.text(this.font, ui("UNIFIED CLIENT UTILITIES  /  26.3"), panelX + 116, panelY + 9, 0xFFD5DCEC, false);
        graphics.text(this.font, ui(maceTab ? "MACE HITTER CONFIGURATION" : "AUTOTOTEM CONFIGURATION"),
                panelX + 16, panelY + 56, 0xFFE4EAF5, false);
    }

    @Override
    public void onClose() {
        persist();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
