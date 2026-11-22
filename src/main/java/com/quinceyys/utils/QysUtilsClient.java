package com.quinceyys.utils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class QysUtilsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoTotemModule.initialize();
        MaceHitterModule.initialize();
        AutoPlaceCombatModule.initialize();
        AutoSprintModule.initialize();
        CombatAssistModule.initialize();
        ChatGameGuesserModule.initialize();
        AutoTntCartModule.initialize();
        QysKeybindManager.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(QysUtilsConfigScreen::tickCursorRestore);
        System.out.println("[QYS-UTILS] AutoTotem, Mace Hitter, Combat Assist, Crystal/Anchor, Auto Sprint, Chat Game Guesser, client-side AutoTntCart, and shared keybinds initialized.");
    }
}
