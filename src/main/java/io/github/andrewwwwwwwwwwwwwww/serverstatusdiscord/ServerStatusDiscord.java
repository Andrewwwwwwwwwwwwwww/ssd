package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class ServerStatusDiscord implements ModInitializer {
    private static long disconnectPendingTick = -1L;
    /** Epoch seconds when the server finished starting, for the "Server started <t:…:R>" pill. */
    private static long serverStartedEpochSeconds = 0L;

    @Override
    public void onInitialize() {
        Config.load();
        AccountLinks.load();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverStartedEpochSeconds = System.currentTimeMillis() / 1000L;
            DiscordBot.start(server);
            DiscordNotifier.updateTopic(onlineTopic(server));
            DiscordNotifier.relayServerMessage("**Server started**", false);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // Send synchronously so both land before the JVM exits.
            DiscordNotifier.relayServerMessage("**Server stopped**", true);
            DiscordNotifier.setTopicImmediately(offlineTopic());
            DiscordBot.shutdown();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            DiscordNotifier.updateTopic(onlineTopic(server)));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            disconnectPendingTick = server.overworld().getGameTime() + 1L);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (disconnectPendingTick < 0L) return;
            if (server.overworld().getGameTime() < disconnectPendingTick) return;
            DiscordNotifier.updateTopic(onlineTopic(server));
            disconnectPendingTick = -1L;
        });

        // Forward in-game chat to Discord as a webhook pseudo-user (name + skin-head avatar).
        ServerMessageEvents.CHAT_MESSAGE.register((message, player, type) ->
            DiscordNotifier.relayPlayerChat(
                player.getName().getString(),
                player.getUUID(),
                message.signedContent()
            ));

        // Forward server broadcasts (/say, /tellraw @a, deaths, advancements, join/leave, console
        // chat) to Discord. Skip messages we ourselves broadcast from Discord to avoid an echo loop.
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (DiscordBot.isRelayingFromDiscord()) return;
            DiscordNotifier.relayServerMessage(message.getString(), false);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerLinkCommand(dispatcher));
    }

    // ---- Channel-topic status line --------------------------------------------------------
    // Discord renders <t:epoch:R> / <t:epoch:f> as the live "x hours ago" / full-date pills,
    // even inside a channel topic.

    private static String onlineTopic(MinecraftServer server) {
        long now = System.currentTimeMillis() / 1000L;
        return "✅ " + server.getPlayerList().getPlayerCount()
            + "/" + server.getPlayerList().getMaxPlayers() + " player(s) online"
            + " | Server started <t:" + serverStartedEpochSeconds + ":R>"
            + " | Last updated: <t:" + now + ":f>";
    }

    private static String offlineTopic() {
        long now = System.currentTimeMillis() / 1000L;
        return "🛑 Server offline | Last updated: <t:" + now + ":f>";
    }

    private static void registerLinkCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("link").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) {
                ctx.getSource().sendFailure(Component.literal("Only players can generate a link code."));
                return 0;
            }
            if (!Config.hasBot()) {
                ctx.getSource().sendFailure(Component.literal("Account linking is not configured on this server."));
                return 0;
            }
            String code = AccountLinks.generateCode(player.getUUID(), player.getName().getString());
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Your Discord link code is: " + code
                + "\nRun /link " + code + " in Discord within 5 minutes to finish linking."), false);
            return 1;
        }));
    }
}
