# SSD — Server Status to Discord

A single-server Fabric mod that bridges a Minecraft server and one Discord server: a live status
line in the chat channel's topic, two-way chat with skin-head avatars, account linking, an OP-gated
console channel, and an update checker.

## Features

- **Live status in the chat channel topic** — the chat channel's topic (description) shows a live status line, updated as players join and leave:

  > ✅ 2/12 player(s) online | Server started \<14 hours ago\> | Last updated: \<July 23, 2026 6:40 PM\>

  When the server shuts down it becomes `🛑 Server offline | Last updated: …`. The times render as Discord's live timestamp pills.
- **Two-way chat bridge** — in-game chat appears in a Discord channel and messages in that channel are broadcast in-game.
- **Skin-head chat avatars** — Minecraft messages post to Discord as a pseudo-user with the player's name and skin head, keyed on UUID (works whether or not the player is linked).
- **Account linking (MC-first)** — players run `/link` in-game for a 6-character code, then `/link <code>` on Discord to bind. Linking can only ever start in-game. One MC account maps to one Discord account.
- **OP-gated console channel** — messages in a designated channel are run as server commands, with the output replied back — but only for users whose linked Minecraft account is a server operator.
- **Slash commands** — `/link <code>`, `/unlink`, and `/update` (checks for a newer SSD release).
- **Rate-limited & async** — channel-topic edits are debounced (Discord caps them at 2 per 10 min) and all HTTP is off the server tick.

## Configuration

A config file is created at `config/serverstatusdiscord.json` on first run:

```json
{
  "botToken": "your-bot-token-without-the-Bot-prefix",
  "chatChannelId": "the-chat-channel-id-here",
  "chatWebhookUrl": "https://discord.com/api/webhooks/...",
  "consoleChannelId": "the-console-channel-id-here"
}
```

- **botToken** — required for everything (chat bridge, live topic, console channel, linking, slash commands). Discord Developer Portal → your application → Bot → Reset Token. **Enable the Message Content Intent** on that page, and invite the bot with the `applications.commands` scope. The bot needs `Manage Channels` in the chat channel to update its topic.
- **chatChannelId** — the two-way chat bridge channel. Its **topic (description)** shows the live status line. Right-click the channel (Developer Mode on) → Copy Channel ID.
- **chatWebhookUrl** — a webhook on the chat channel, used to post player messages with skin-head avatars. Discord channel → Edit → Integrations → Webhooks → New Webhook → Copy URL.
- **consoleChannelId** — the channel where OP-linked users can run console commands. Keep it private.

Leave any field blank to disable the corresponding feature. Bindings are stored in `config/serverstatusdiscord/links.json`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop the JAR into your `mods` folder.
3. Start the server once to generate `config/serverstatusdiscord.json`, then fill in the values.
4. Restart the server.

## License

All Rights Reserved. See the [LICENSE](LICENSE) file — these mods are proprietary.
