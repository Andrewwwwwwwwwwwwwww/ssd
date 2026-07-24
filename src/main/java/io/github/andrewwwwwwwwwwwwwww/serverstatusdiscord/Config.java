package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Holds every user-configurable value, loaded from {@code config/serverstatusdiscord.json}.
 * Missing keys are written back with empty defaults so upgrades pick up new fields automatically.
 */
public final class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerStatusDiscord");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String botToken = "";

    // Everything happens in one chat channel: the two-way bridge, the live player-count topic,
    // and the online/offline embeds (posted through the chat webhook).
    public static String chatChannelId = "";
    public static String chatWebhookUrl = "";
    public static String consoleChannelId = "";

    private Config() {}

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("serverstatusdiscord.json");
        JsonObject json;
        if (Files.exists(path)) {
            try {
                json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.error("Failed to read config, using empty defaults", e);
                json = new JsonObject();
            }
        } else {
            json = new JsonObject();
        }

        botToken         = getString(json, "botToken");
        chatChannelId    = getString(json, "chatChannelId");
        chatWebhookUrl   = getString(json, "chatWebhookUrl");
        consoleChannelId = getString(json, "consoleChannelId");

        // Re-serialize so any newly introduced keys are added to the file on disk.
        JsonObject out = new JsonObject();
        out.addProperty("botToken", botToken);
        out.addProperty("chatChannelId", chatChannelId);
        out.addProperty("chatWebhookUrl", chatWebhookUrl);
        out.addProperty("consoleChannelId", consoleChannelId);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(out));
        } catch (Exception e) {
            LOGGER.error("Failed to write config", e);
        }
    }

    /** True when a bot token is present, which every Gateway-based feature requires. */
    public static boolean hasBot() {
        return botToken != null && !botToken.isBlank();
    }

    private static String getString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }
}
