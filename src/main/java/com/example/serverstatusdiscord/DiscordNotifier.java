package com.example.serverstatusdiscord;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public class DiscordNotifier {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String webhookUrl = "";
    private static String botToken = "";
    private static String playerCountChannelId = "";

    public static void loadConfig() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("serverstatusdiscord.json");
        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has("webhookUrl"))          webhookUrl = json.get("webhookUrl").getAsString();
                if (json.has("botToken"))            botToken = json.get("botToken").getAsString();
                if (json.has("playerCountChannelId")) playerCountChannelId = json.get("playerCountChannelId").getAsString();
            } catch (Exception e) {
                System.err.println("[ServerStatusDiscord] Failed to load config: " + e.getMessage());
            }
        } else {
            JsonObject json = new JsonObject();
            json.addProperty("webhookUrl", "");
            json.addProperty("botToken", "");
            json.addProperty("playerCountChannelId", "");
            try {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, GSON.toJson(json));
                System.out.println("[ServerStatusDiscord] Config created at " + configPath + " — fill in your Discord webhook URL, bot token, and player count channel ID.");
            } catch (Exception e) {
                System.err.println("[ServerStatusDiscord] Failed to create config: " + e.getMessage());
            }
        }
    }

    public static void sendOnline() {
        sendEmbed("Server Online", "The server is now online and ready to play!", 5763719);
    }

    public static void sendOffline() {
        sendEmbed("Server Offline", "The server has gone offline.", 15548997);
    }

    public static void updatePlayerCount(int current, int max) {
        setChannelTopic("Players online: " + current + "/" + max);
    }

    public static void setChannelTopicOffline() {
        setChannelTopic("Server Offline");
    }

    private static void setChannelTopic(String topic) {
        if (botToken.isBlank() || playerCountChannelId.isBlank()) return;

        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("topic", topic);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://discord.com/api/v10/channels/" + playerCountChannelId))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bot " + botToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(5))
                .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    System.err.println("[ServerStatusDiscord] Failed to update channel topic: " + e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            System.err.println("[ServerStatusDiscord] Failed to update channel topic: " + e.getMessage());
        }
    }

    private static void sendEmbed(String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        try {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", title);
            embed.addProperty("description", description);
            embed.addProperty("color", color);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonArray embeds = new JsonArray();
            embeds.add(embed);

            JsonObject payload = new JsonObject();
            payload.add("embeds", embeds);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(5))
                .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(e -> {
                    System.err.println("[ServerStatusDiscord] Failed to send Discord notification: " + e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            System.err.println("[ServerStatusDiscord] Failed to send Discord notification: " + e.getMessage());
        }
    }
}
