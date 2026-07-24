package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
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
            // The "Server started!" chat message is sent by the bot once it finishes connecting
            // (see DiscordBot.onReady), so it posts as the bot rather than a webhook.
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DiscordBot bot = DiscordBot.get();
            if (bot != null) bot.sendToChatChannelBlocking("🔴 **Server stopped!**");
            DiscordNotifier.setTopicImmediately(offlineTopic());
            DiscordBot.shutdown();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DiscordNotifier.updateTopic(onlineTopic(server));
            remindIfUnlinked(handler.player);
        });

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

        // Forward server broadcasts (join/leave, deaths, advancements, /say, console chat) to
        // Discord via the bot, formatted per event type. Skip action-bar overlays and messages we
        // ourselves broadcast from Discord (to avoid an echo loop).
        ServerMessageEvents.GAME_MESSAGE.register((server, message, overlay) -> {
            if (overlay || DiscordBot.isRelayingFromDiscord()) return;
            DiscordBot bot = DiscordBot.get();
            if (bot == null) return;
            String formatted = formatBroadcast(message);
            if (formatted != null) bot.sendToChatChannel(formatted);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerLinkCommand(dispatcher));
    }

    /** On join, nudge players who haven't linked their Discord account yet (only if a bot is set up). */
    private static void remindIfUnlinked(ServerPlayer player) {
        if (!Config.hasBot()) return;
        if (AccountLinks.isMcLinked(player.getUUID())) return;
        player.sendSystemMessage(Component.literal(
            "[Discord] Link your account to chat with Discord and get @mentioned in-game!")
            .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal(
            "Step 1: run /link here to get a 6-character code.").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(
            "Step 2: enter /link <code> in our Discord server.").withStyle(ChatFormatting.GRAY));
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

    // ---- Server-broadcast formatting ------------------------------------------------------
    // Classifies a vanilla broadcast by its translation key and gives it a Discord-friendly form
    // with an event icon. Returns null to skip. Bold/italic markdown renders in the bot's messages.

    private static String formatBroadcast(Component message) {
        if (message.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            Object[] args = tc.getArgs();
            if (key.startsWith("multiplayer.player.joined")) {
                return "👋 " + argString(args, 0) + " joined the server";
            }
            if (key.equals("multiplayer.player.left")) {
                return "🚪 " + argString(args, 0) + " left the server";
            }
            if (key.startsWith("death.")) {
                return "💀 " + message.getString();
            }
            if (key.startsWith("chat.type.advancement")) {
                return formatAdvancement(args);
            }
            if (key.equals("chat.type.announcement")) {
                return "📢 " + message.getString();
            }
        }
        // Console chat, /me, mod broadcasts, and anything else: relay the resolved text as-is.
        String text = message.getString();
        return text.isBlank() ? null : text;
    }

    private static String formatAdvancement(Object[] args) {
        String player = argString(args, 0);
        String title = "";
        String description = "";
        if (args != null && args.length > 1 && args[1] instanceof Component adv) {
            title = adv.getString(); // already wrapped in [ ]
            HoverEvent hover = adv.getStyle().getHoverEvent();
            if (hover instanceof HoverEvent.ShowText showText) {
                // The hover text is "Title\nDescription"; take everything after the first newline.
                String hoverText = showText.value().getString();
                int newline = hoverText.indexOf('\n');
                if (newline >= 0) description = hoverText.substring(newline + 1).trim();
            }
        }
        String msg = "🤩 " + player + " has made the advancement **" + title + "**";
        if (!description.isEmpty()) msg += "\n*" + description + "*";
        return msg;
    }

    private static String argString(Object[] args, int index) {
        if (args == null || args.length <= index || args[index] == null) return "";
        Object arg = args[index];
        return (arg instanceof Component component) ? component.getString() : String.valueOf(arg);
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
