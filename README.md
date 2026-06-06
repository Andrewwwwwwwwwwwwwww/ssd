# MC Server Status → Discord

A Fabric mod that posts server online/offline notifications to a Discord webhook and keeps a Discord channel's topic in sync with the live player count.

## Features

- **Online/offline embeds** — when the server starts and stops, a colored embed is sent to your Discord webhook.
- **Live player count** — the topic of a Discord channel of your choice is kept current as players join and leave (`Players online: 3/20`). When the server shuts down it switches to `Server Offline`.
- **Rate-limited** — Discord caps channel-topic edits to 2 per 10 minutes, so updates are debounced server-side to avoid 429s on busy servers.
- **Async HTTP** — all Discord traffic is fire-and-forget so the server tick is never blocked.

## Configuration

A config file is created at `config/serverstatusdiscord.json` on first run:

```json
{
  "webhookUrl": "https://discord.com/api/webhooks/...",
  "botToken": "your-bot-token-without-the-Bot-prefix",
  "playerCountChannelId": "the-channel-id-here"
}
```

- **webhookUrl** — Discord channel → Edit → Integrations → Webhooks → New Webhook → Copy URL.
- **botToken** — needed only for player count topic updates. Discord Developer Portal → your application → Bot → Reset Token. The bot must have `Manage Channels` permission in the target channel.
- **playerCountChannelId** — right-click the channel in Discord (with Developer Mode enabled) → Copy Channel ID.

Leave any field blank to disable the corresponding feature.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Drop the JAR into your `mods` folder.
3. Start the server once to generate `config/serverstatusdiscord.json`, then fill in the values.
4. Restart the server.

## License

All Rights Reserved. See the [LICENSE](LICENSE) file — these mods are proprietary.
