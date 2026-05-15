package com.example.serverstatusdiscord;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class ServerStatusDiscord implements ModInitializer {
    @Override
    public void onInitialize() {
        DiscordNotifier.loadConfig();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DiscordNotifier.sendOnline();
            DiscordNotifier.updatePlayerCount(0, server.getPlayerList().getMaxPlayers());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DiscordNotifier.sendOffline();
            DiscordNotifier.setChannelTopicOffline();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            DiscordNotifier.updatePlayerCount(
                server.getPlayerList().getPlayerCount(),
                server.getPlayerList().getMaxPlayers()
            ));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            DiscordNotifier.updatePlayerCount(
                Math.max(0, server.getPlayerList().getPlayerCount() - 1),
                server.getPlayerList().getMaxPlayers()
            ));
    }
}
