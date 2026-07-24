package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DiscordNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerStatusDiscord");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ScheduledExecutorService SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerStatusDiscord-Scheduler");
            t.setDaemon(true);
            return t;
        });

    /** Discord lets us PATCH a channel topic at most 2 times per 10 minutes, so debounce. */
    private static final long TOPIC_DEBOUNCE_MS = 5L * 60L * 1000L;
    private static volatile String pendingTopic = null;
    private static volatile long lastTopicSentAtMs = 0L;
    private static volatile ScheduledFuture<?> pendingTopicTask = null;

    /**
     * Queues a channel-topic update, debounced to stay within Discord's 2-edits-per-10-minutes cap.
     * The most recent topic wins if several arrive inside the debounce window.
     */
    public static synchronized void updateTopic(String topic) {
        pendingTopic = topic;
        long elapsed = System.currentTimeMillis() - lastTopicSentAtMs;
        if (elapsed >= TOPIC_DEBOUNCE_MS) {
            flushPendingTopic();
        } else if (pendingTopicTask == null || pendingTopicTask.isDone()) {
            pendingTopicTask = SCHEDULER.schedule(DiscordNotifier::flushPendingTopic,
                TOPIC_DEBOUNCE_MS - elapsed, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Sets the topic right now, bypassing the debounce, and blocks until the request completes.
     * Used on shutdown so the "Server offline" topic actually lands before the JVM exits.
     */
    public static synchronized void setTopicImmediately(String topic) {
        if (pendingTopicTask != null && !pendingTopicTask.isDone()) {
            pendingTopicTask.cancel(false);
            pendingTopicTask = null;
        }
        pendingTopic = null;
        lastTopicSentAtMs = System.currentTimeMillis();
        sendChannelTopicNow(topic, true);
    }

    private static synchronized void flushPendingTopic() {
        if (pendingTopic == null) return;
        String topic = pendingTopic;
        pendingTopic = null;
        lastTopicSentAtMs = System.currentTimeMillis();
        sendChannelTopicNow(topic, false);
    }

    private static void sendChannelTopicNow(String topic, boolean blocking) {
        // The live status is shown as the chat channel's topic (description).
        if (Config.botToken.isBlank() || Config.chatChannelId.isBlank()) return;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("topic", topic);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://discord.com/api/v10/channels/" + Config.chatChannelId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bot " + Config.botToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(5))
                .build();

            if (blocking) {
                HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            } else {
                HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        LOGGER.warn("Failed to update channel topic: {}", e.getMessage());
                        return null;
                    });
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to update channel topic", e);
        }
    }

    /**
     * Relays an in-game chat line to Discord through the chat webhook, presenting it as a
     * pseudo-user: the message shows the player's name and skin-head avatar. Works for every
     * player regardless of whether their account is linked, because it is keyed on UUID only.
     */
    public static void relayPlayerChat(String playerName, UUID uuid, String message) {
        if (Config.chatWebhookUrl == null || Config.chatWebhookUrl.isBlank()) return;
        if (message == null || message.isBlank()) return;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", playerName);
            payload.addProperty("avatar_url", "https://mc-heads.net/avatar/" + uuid + "/100");
            payload.addProperty("content", message);
            // Never let player text ping @everyone / roles / users from Discord.
            JsonObject allowed = new JsonObject();
            allowed.add("parse", new JsonArray());
            payload.add("allowed_mentions", allowed);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(Config.chatWebhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(5))
                .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    LOGGER.warn("Failed to relay chat to Discord: {}", e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            LOGGER.warn("Failed to relay chat to Discord", e);
        }
    }
}
