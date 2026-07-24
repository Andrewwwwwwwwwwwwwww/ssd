package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent Minecraft&nbsp;&harr;&nbsp;Discord account bindings, plus the short-lived verification
 * codes used to establish them.
 *
 * <p>Binding is strictly MC-first: a code can only be minted by a player running {@code /link}
 * in-game, and the Discord {@code /link <code>} slash command can only ever <em>consume</em> a code.
 * There is no way to bind by typing a username on Discord. The relationship is 1&nbsp;MC&nbsp;:&nbsp;1&nbsp;Discord.
 */
public final class AccountLinks {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerStatusDiscord");
    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no easily-confused chars
    private static final long CODE_TTL_MS = 5L * 60L * 1000L;

    // mcUuid -> discordId  (the persisted binding; MC UUID is unique so it is the primary key here)
    private static final Map<UUID, String> MC_TO_DISCORD = new ConcurrentHashMap<>();

    // pending code -> generating player
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private static Path storePath;

    private record Pending(UUID mcUuid, String mcName, long expiresAt) {}

    private AccountLinks() {}

    public static void load() {
        storePath = FabricLoader.getInstance().getConfigDir()
            .resolve("serverstatusdiscord").resolve("links.json");
        MC_TO_DISCORD.clear();
        if (!Files.exists(storePath)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(storePath)).getAsJsonObject();
            for (String uuid : json.keySet()) {
                MC_TO_DISCORD.put(UUID.fromString(uuid), json.get(uuid).getAsString());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load account links", e);
        }
    }

    private static synchronized void save() {
        if (storePath == null) return;
        JsonObject json = new JsonObject();
        MC_TO_DISCORD.forEach((uuid, discordId) -> json.addProperty(uuid.toString(), discordId));
        try {
            Files.createDirectories(storePath.getParent());
            Files.writeString(storePath, GSON.toJson(json));
        } catch (Exception e) {
            LOGGER.error("Failed to save account links", e);
        }
    }

    /** Generates (or replaces) a verification code for the given player. Returns the code. */
    public static String generateCode(UUID mcUuid, String mcName) {
        // Drop any earlier code from this same player so only one is ever live per account.
        PENDING.values().removeIf(p -> p.mcUuid().equals(mcUuid));
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            code = sb.toString();
        } while (PENDING.containsKey(code));
        PENDING.put(code, new Pending(mcUuid, mcName, System.currentTimeMillis() + CODE_TTL_MS));
        return code;
    }

    /**
     * Consumes a code on the Discord side and binds it to the given Discord user.
     * Enforces 1 MC : 1 Discord — a fresh binding for an MC account overwrites its previous one.
     *
     * @return the linked player's name on success, or empty if the code was invalid/expired.
     */
    public static synchronized Optional<String> redeemCode(String code, String discordId) {
        Pending pending = PENDING.remove(code == null ? "" : code.toUpperCase());
        if (pending == null) return Optional.empty();
        if (System.currentTimeMillis() > pending.expiresAt()) return Optional.empty();

        // One Discord account can hold at most one MC account here (kept simple): clear any
        // other MC accounts already bound to this Discord user.
        MC_TO_DISCORD.entrySet().removeIf(e -> e.getValue().equals(discordId));
        MC_TO_DISCORD.put(pending.mcUuid(), discordId);
        save();
        return Optional.of(pending.mcName());
    }

    /** Unlinks whatever MC account is bound to the given Discord user. Returns true if one was removed. */
    public static synchronized boolean unlinkByDiscord(String discordId) {
        boolean removed = MC_TO_DISCORD.entrySet().removeIf(e -> e.getValue().equals(discordId));
        if (removed) save();
        return removed;
    }

    /** The Discord user ID linked to this MC account, if any. */
    public static Optional<String> discordIdFor(UUID mcUuid) {
        return Optional.ofNullable(MC_TO_DISCORD.get(mcUuid));
    }

    /** The MC UUID linked to this Discord user, if any. */
    public static Optional<UUID> mcUuidFor(String discordId) {
        return MC_TO_DISCORD.entrySet().stream()
            .filter(e -> e.getValue().equals(discordId))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    public static boolean isLinked(String discordId) {
        return MC_TO_DISCORD.containsValue(discordId);
    }

    public static Map<UUID, String> allLinks() {
        return Map.copyOf(MC_TO_DISCORD);
    }
}
