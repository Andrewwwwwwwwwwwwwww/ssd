package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class ServerStatusDiscord implements ModInitializer {
    private static long disconnectPendingTick = -1L;

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

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            disconnectPendingTick = server.overworld().getGameTime() + 1L;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (disconnectPendingTick < 0L) return;
            if (server.overworld().getGameTime() < disconnectPendingTick) return;
            DiscordNotifier.updatePlayerCount(
                server.getPlayerList().getPlayerCount(),
                server.getPlayerList().getMaxPlayers()
            );
            disconnectPendingTick = -1L;
        });
    }
}
