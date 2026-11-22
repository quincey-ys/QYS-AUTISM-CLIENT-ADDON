package com.quinceyys.utils;

import net.fabricmc.api.ClientModInitializer;

public final class QysUtilsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoTotemModule.initialize();
        MaceHitterModule.initialize();
        AutoPlaceCombatModule.initialize();
        AutoSprintModule.initialize();
        CombatAssistModule.initialize();
        ChatGameGuesserModule.initialize();
        QysKeybindManager.initialize();
        System.out.println("[QYS-UTILS] AutoTotem, Mace Hitter, Combat Assist, Crystal/Anchor, Auto Sprint, Chat Game Guesser, bundled AutoTntCart, and shared keybinds initialized.");
    }
}
