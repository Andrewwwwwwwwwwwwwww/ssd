# SSD (Server Status to Discord) Changelog

## [0.2.3] - 2026-06-16

### Changed
- **Updated to Minecraft 26.2.** Bumped `minecraft_version` to `26.2`, Fabric Loader to `0.19.3`, and
  Fabric API to `0.152.1+26.2` (Loom stays on `1.16.2`). `fabric.mod.json` dependency bounds raised to
  `minecraft ~26.2`, `fabricloader >=0.19.3`, `fabric-api >=0.152.1`. No code changes were required.

## [0.2.2] - 2026-05-15

### Changed
- Mod display name corrected to `SSD (Server Status to Discord)`.
- GitHub repository renamed from `serverstatusdiscord` to `ssd` — all contact URLs updated in `fabric.mod.json`.

---

## [0.2.1] - 2026-05-15

### Fixed
- "Server Offline" Discord channel topic now always sends when the server stops, even if the last player-count update was within the 5-minute debounce window. Previously the update was queued to a daemon-thread scheduler that was killed with the JVM, leaving the topic stuck on the last player count.

### Changed
- Mod display name shortened to `SSD`.
- `environment` changed from `"*"` to `"server"` — this mod has no client-side logic; players do not need it installed on their client.
- Fabric Loom pinned to `1.16.2` (stable) — was previously on `1.16-SNAPSHOT`.
- Fabric API dependency tightened to `>=0.148.2` instead of the wildcard `*`.

### Added
- MIT `LICENSE` file added to the repository and packaged in the JAR.
- Mod icon.

---

## [0.2.0] - 2026-05-15

### Added
- Repackaged to `io.github.andrewwwwwwwwwwwwwww.serverstatusdiscord` namespace.
- SLF4J logger replaces raw stderr output.
- Discord channel topic rate-limiting — updates are debounced to stay within Discord's 2 edits per 10 minutes cap, preventing 429 errors on busy servers.
- Delayed player count update on disconnect — waits one tick after a player leaves before reading the new count, ensuring the count is accurate.
- `fabric.mod.json` contact metadata (homepage, sources, issues).
- README.md with full feature and install documentation.

---

## [0.1.0] - 2026-05-15

### Added
- Initial release.
- Online/offline Discord webhook embeds — colored embeds sent when the server starts and stops.
- Live player count in Discord channel topic — updated as players join and leave, set to "Server Offline" on shutdown.
- Config file at `config/serverstatusdiscord.json` for webhook URL, bot token, and channel ID. Generated with empty defaults on first run.
- All Discord HTTP calls are async and fire-and-forget to avoid blocking the server tick.
