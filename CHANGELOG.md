# SSD (Server Status to Discord) Changelog

## [1.1.0] - 2026-07-23

### Added
- **Two-way chat bridge.** In-game chat is relayed to a Discord channel and Discord messages in that
  channel are broadcast in-game. Configured via `chatChannelId` and `chatWebhookUrl`.
- **Rich live status line in the chat channel topic**, e.g.
  `✅ 2/12 player(s) online | Server started <t:…:R> | Last updated: <t:…:f>`, using Discord's live
  timestamp pills (which render even inside a channel topic). Offline becomes
  `🛑 Server offline | Last updated: …`.

### Removed
- The separate **status webhook channel** (`webhookUrl`) and the online/offline embeds — the status
  is now conveyed entirely by the chat channel topic.
- The separate **player-count channel** (`playerCountChannelId`) — the status line lives on the chat
  channel topic instead.
- **Skin-head chat avatars.** Minecraft messages are posted to Discord through a webhook as a
  pseudo-user showing the player's name and skin head (via `mc-heads.net`). Works for every player,
  linked or not, since it is keyed on UUID.
- **Account linking (MC-first).** Players run `/link` in-game to get a 6-character code (valid 5
  minutes), then run `/link <code>` on Discord to bind their account. Binding can only ever start
  in-game — there is no way to link by typing a username on Discord. Relationship is 1 MC : 1 Discord.
  Bindings persist in `config/serverstatusdiscord/links.json`.
- **OP-gated console channel.** Messages typed in the configured `consoleChannelId` are executed as
  server console commands and the command output is replied back — but only for users whose linked
  Minecraft account is a server operator. Everyone else is refused.
- **Discord slash commands:** `/link <code>`, `/unlink`, and `/update` (compares the running version
  against the latest GitHub release of the `ssd` repo).
- **Join/leave lines** are posted to the chat channel.

### Changed
- Bundles **JDA 5.6.1** (shadowed into the jar; transitive libraries relocated) to power the Gateway
  connection. The jar is now ~16 MB as a result. slf4j is excluded since Minecraft already ships it.
- Config handling moved to a dedicated `Config` class; new keys are written back to existing config
  files automatically on upgrade.

### Requires
- The bot's **Message Content Intent** must be enabled in the Discord Developer Portal, and the bot
  must be invited with the `applications.commands` scope for slash commands to appear.

## [1.0.0] - 2026-06-27

### Changed
- **Stable 1.0.0 release.** No functional changes from 0.2.3 — marks the mod stable and aligns it with the
  unified release across the mod suite.
- **Jar filenames now include the Minecraft version** (e.g. `serverstatusdiscord-1.0.0+mc26.2.jar`).
- A parallel **MC 26.1.2** build is now published (`serverstatusdiscord-1.0.0+mc26.1.2.jar`).

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
