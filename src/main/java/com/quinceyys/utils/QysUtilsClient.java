package com.quinceyys.utils;

import net.fabricmc.api.ClientModInitializer;

public final class QysUtilsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoTotemModule.initialize();
        MaceHitterModule.initialize();
        AutoPlaceCombatModule.initialize();
        AutoSprintModule.initialize();
        System.out.println("[QYS-UTILS] AutoTotem, Mace Hitter, Crystal/Anchor, and Auto Sprint initialized.");
    }
}
