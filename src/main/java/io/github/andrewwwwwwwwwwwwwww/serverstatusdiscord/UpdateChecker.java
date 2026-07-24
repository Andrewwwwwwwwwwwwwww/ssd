package io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Compares the running mod version against the latest GitHub release of the {@code ssd} repo.
 * Used by the Discord {@code /update} slash command.
 */
public final class UpdateChecker {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String RELEASES_API =
        "https://api.github.com/repos/Andrewwwwwwwwwwwwwww/ssd/releases/latest";

    private UpdateChecker() {}

    /** The mod's own version string, e.g. {@code 1.1.0+mc26.2}. */
    public static String currentVersion() {
        return FabricLoader.getInstance().getModContainer("serverstatusdiscord")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    public static CompletableFuture<String> check() {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(RELEASES_API))
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(UpdateChecker::describe)
            .exceptionally(e -> "Update check failed: " + e.getMessage());
    }

    private static String describe(HttpResponse<String> response) {
        String current = currentVersion();
        if (response.statusCode() == 404) {
            return "No published releases found. Running version: `" + current + "`.";
        }
        if (response.statusCode() != 200) {
            return "GitHub returned HTTP " + response.statusCode() + ". Running version: `" + current + "`.";
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String tag = json.get("tag_name").getAsString();
            String url = json.has("html_url") ? json.get("html_url").getAsString() : "";
            String latest = tag.startsWith("v") ? tag.substring(1) : tag;

            // Compare against the mod-version portion (strip the +mc… build suffix).
            String currentCore = current.contains("+") ? current.substring(0, current.indexOf('+')) : current;
            String latestCore = latest.contains("+") ? latest.substring(0, latest.indexOf('+')) : latest;

            if (currentCore.equals(latestCore)) {
                return "SSD is up to date (version `" + current + "`).";
            }
            return "A new SSD version is available: **" + tag + "** (you have `" + current + "`).\n" + url;
        } catch (Exception e) {
            return "Could not parse the GitHub response. Running version: `" + current + "`.";
        }
    }
}
