package com.quinceyys.utils;

import net.fabricmc.api.ClientModInitializer;

public final class QysUtilsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AutoTotemModule.initialize();
        MaceHitterModule.initialize();
        AutoPlaceCombatModule.initialize();
        System.out.println("[QYS-UTILS] AutoTotem, Mace Hitter, and Crystal/Anchor utilities initialized.");
    }
}
