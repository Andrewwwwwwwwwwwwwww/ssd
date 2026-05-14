package com.example.serverstatusdiscord;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ServerStatusDiscord implements ModInitializer {
    @Override
    public void onInitialize() {
        DiscordNotifier.loadConfig();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> DiscordNotifier.sendOnline());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DiscordNotifier.sendOffline());
    }
}
