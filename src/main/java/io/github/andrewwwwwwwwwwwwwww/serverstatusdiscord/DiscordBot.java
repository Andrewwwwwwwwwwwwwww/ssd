package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the JDA Gateway connection and every feature that needs to <em>receive</em> from Discord:
 * the two-way chat bridge, the OP-gated console channel, and the {@code /link}, {@code /unlink},
 * {@code /update} slash commands. Started on server start, shut down on server stop.
 */
public final class DiscordBot extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerStatusDiscord");
    private static final int DISCORD_MESSAGE_LIMIT = 2000;

    private static DiscordBot instance;

    /**
     * Set on the server thread while broadcasting a message that came FROM Discord, so the
     * GAME_MESSAGE relay can skip it and avoid a Discord -> MC -> Discord echo loop.
     */
    private static final ThreadLocal<Boolean> RELAYING_FROM_DISCORD = ThreadLocal.withInitial(() -> false);

    private final MinecraftServer server;
    private JDA jda;
    private boolean startAnnounced = false;

    /** True while this thread is broadcasting a Discord-originated message into Minecraft. */
    public static boolean isRelayingFromDiscord() {
        return RELAYING_FROM_DISCORD.get();
    }

    private DiscordBot(MinecraftServer server) {
        this.server = server;
    }

    public static synchronized void start(MinecraftServer server) {
        if (!Config.hasBot()) {
            LOGGER.info("No bot token configured — chat bridge, console channel, and slash commands are disabled.");
            return;
        }
        if (instance != null) return;
        instance = new DiscordBot(server);
        instance.connect();
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            instance.disconnect();
            instance = null;
        }
    }

    public static DiscordBot get() {
        return instance;
    }

    private void connect() {
        try {
            jda = JDABuilder.createLight(Config.botToken,
                    EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .setActivity(net.dv8tion.jda.api.entities.Activity.playing("Minecraft"))
                .addEventListeners(this)
                .build();
            LOGGER.info("Discord bot connecting…");
        } catch (Exception e) {
            LOGGER.error("Failed to start Discord bot", e);
        }
    }

    private void disconnect() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    // ---- Gateway events -------------------------------------------------------------------

    @Override
    public void onReady(ReadyEvent event) {
        LOGGER.info("Discord bot ready as {}.", event.getJDA().getSelfUser().getAsTag());
        // Register slash commands per-guild so they appear immediately (global commands take ~1h).
        OptionData codeOption = new OptionData(OptionType.STRING, "code",
            "The 6-character code shown to you in-game after running /link", true);
        for (Guild guild : event.getJDA().getGuilds()) {
            guild.updateCommands().addCommands(
                net.dv8tion.jda.api.interactions.commands.build.Commands.slash("link",
                    "Link your Discord account using a code generated in-game").addOptions(codeOption),
                net.dv8tion.jda.api.interactions.commands.build.Commands.slash("unlink",
                    "Unlink your Minecraft account from Discord"),
                net.dv8tion.jda.api.interactions.commands.build.Commands.slash("update",
                    "Check whether a newer SSD version has been released")
            ).queue();
        }

        // Announce startup once the bot is actually connected (so it posts as the bot, not a webhook).
        if (!startAnnounced) {
            startAnnounced = true;
            sendToChatChannel("✅ **Server started!**");
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore anything the bot or a webhook posted, to avoid loops.
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;

        // Keep the cached Discord name fresh so in-game @mentions resolve to the current name.
        AccountLinks.refreshDiscordName(event.getAuthor().getId(), event.getMember() != null
            ? event.getMember().getEffectiveName() : event.getAuthor().getEffectiveName());

        String channelId = event.getChannel().getId();
        String content = event.getMessage().getContentDisplay();

        if (channelId.equals(Config.chatChannelId) && !Config.chatChannelId.isBlank()) {
            relayDiscordToMc(event.getAuthor().getEffectiveName(), content);
        } else if (channelId.equals(Config.consoleChannelId) && !Config.consoleChannelId.isBlank()) {
            handleConsole(event, content);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "link" -> {
                String code = event.getOption("code") != null ? event.getOption("code").getAsString() : "";
                String discordName = event.getMember() != null
                    ? event.getMember().getEffectiveName() : event.getUser().getEffectiveName();
                Optional<String> linkedName = AccountLinks.redeemCode(code, event.getUser().getId(), discordName);
                if (linkedName.isPresent()) {
                    event.reply("Linked to Minecraft account **" + linkedName.get() + "**.")
                        .setEphemeral(true).queue();
                } else {
                    event.reply("That code is invalid or has expired. Run `/link` in-game to get a fresh one.")
                        .setEphemeral(true).queue();
                }
            }
            case "unlink" -> {
                boolean removed = AccountLinks.unlinkByDiscord(event.getUser().getId());
                event.reply(removed ? "Your Minecraft account has been unlinked."
                                    : "You don't have a linked Minecraft account.")
                    .setEphemeral(true).queue();
            }
            case "update" -> {
                event.deferReply().queue();
                UpdateChecker.check().thenAccept(msg ->
                    event.getHook().editOriginal(msg).queue());
            }
            default -> { /* unknown command, ignore */ }
        }
    }

    // ---- Chat bridge ----------------------------------------------------------------------

    private void relayDiscordToMc(String author, String content) {
        if (content.isBlank()) return;
        Component line = Component.literal("[Discord] " + author + ": " + content);
        server.execute(() -> {
            RELAYING_FROM_DISCORD.set(true);
            try {
                server.getPlayerList().broadcastSystemMessage(line, false);
            } finally {
                RELAYING_FROM_DISCORD.set(false);
            }
        });
    }

    /** Send a message into the chat channel as the bot (used for join/leave/death/advancement/server events). */
    public void sendToChatChannel(String text) {
        if (jda == null || Config.chatChannelId == null || Config.chatChannelId.isBlank()) return;
        var channel = jda.getTextChannelById(Config.chatChannelId);
        // Never let relayed text ping anyone, even if a player typed "@everyone" in /say.
        if (channel != null) channel.sendMessage(text).setAllowedMentions(java.util.Collections.emptyList()).queue();
    }

    /** Like {@link #sendToChatChannel} but blocks until sent — used on shutdown before JDA closes. */
    public void sendToChatChannelBlocking(String text) {
        if (jda == null || Config.chatChannelId == null || Config.chatChannelId.isBlank()) return;
        var channel = jda.getTextChannelById(Config.chatChannelId);
        if (channel == null) return;
        try {
            channel.sendMessage(text).setAllowedMentions(java.util.Collections.emptyList()).complete();
        } catch (Exception e) {
            LOGGER.warn("Failed to send shutdown message: {}", e.getMessage());
        }
    }

    // ---- Console channel ------------------------------------------------------------------

    private void handleConsole(MessageReceivedEvent event, String command) {
        if (command.isBlank()) return;
        String discordId = event.getAuthor().getId();

        Optional<UUID> mcUuid = AccountLinks.mcUuidFor(discordId);
        if (mcUuid.isEmpty()) {
            event.getMessage().reply("You must link your Minecraft account first (`/link` in-game, then here).").queue();
            return;
        }
        if (!isOp(mcUuid.get())) {
            event.getMessage().reply("Your linked Minecraft account is not an operator, so you can't run console commands.").queue();
            return;
        }

        // Run on the server thread, capturing command feedback, then reply with it.
        server.submit(() -> runCapturingOutput(command)).thenAccept(output -> {
            String reply = output.isBlank() ? "(no output)" : output;
            if (reply.length() > DISCORD_MESSAGE_LIMIT - 10) {
                reply = reply.substring(0, DISCORD_MESSAGE_LIMIT - 10);
            }
            event.getMessage().reply("```\n" + reply + "\n```").queue();
        });
    }

    private String runCapturingOutput(String command) {
        StringBuilder out = new StringBuilder();
        CommandSource capture = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { out.append(message.getString()).append('\n'); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        CommandSourceStack stack = server.createCommandSourceStack().withSource(capture);
        try {
            server.getCommands().performPrefixedCommand(stack, command);
        } catch (Exception e) {
            out.append("Error: ").append(e.getMessage());
        }
        return out.toString();
    }

    private boolean isOp(UUID uuid) {
        // ServerOpList keys entries purely by UUID, so the name here is irrelevant to the lookup.
        return server.getPlayerList().isOp(new NameAndId(uuid, "linked"));
    }
}
