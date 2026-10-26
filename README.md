# PlayerInfo

A Paper statistics plugin for SMP servers: an in-game statistics GUI, PvP fight
summaries with skin-head death messages, persistent fight history, a built-in
**website** with player profiles / fights feed / leaderboards, a **Discord
webhook**, playtime **ranks**, death log, advancement progress, and
PlaceholderAPI support.

Works on **Paper (or forks like Purpur) 1.21.1 through 26.x** with Java 21+.
Spigot is not supported (Paper-only APIs). MIT licensed — © Mayank Negi
(AFKZ Studios).

## Commands

| Command | Permission | Description |
|---|---|---|
| `/playerinfo <player>` | `playerinfo.use` (op) | Chat card: UUID, world, location, health, gamemode, ping, rank |
| `/playerinfo reload` | `playerinfo.reload` (op) | Reload config.yml + messages.yml |
| `/stats` | `playerinfo.stats` (everyone) | Opens your statistics GUI |
| `/stats <player>` | `playerinfo.stats.others` (op) | Opens another player's GUI — online **or offline** |
| `/stats compare <player>` | `playerinfo.stats.others` (op) | Side-by-side stat comparison |
| `/stats hide` | everyone | Opt in/out of the public website |
| `/stats setbio <text>` / `setfav` / `setbase` | everyone | Personalize your profile |
| `/fightstats <id>` | everyone | Opens a PvP fight summary |
| `/top [weekly] <category>` | everyone | Leaderboards: playtime, blocks, mobs, distance, deaths, kills, kd |

## The statistics GUI

`/stats` opens a profile screen with the player's skin head in the center:

- **General** — all vanilla statistics, formatted and paginated
- **Blocks** — per-block mining counts
- **Items** — crafted, used, broken, picked up, dropped, enchanted
- **Mobs** — killed and killed-by lists
- **Combat** — damage dealt/taken/absorbed + critical hits (plugin-tracked)
- **Login Info** — session time, first join, IP (self/ops only)
- **Death Log** — recent deaths with coordinates (click-to-copy in chat)
- **Advancements** — "87/122 completed" with what's left
- **Players Killed / Times Died** — drill down into the fight records

Offline players work: the plugin reads both the 26.x stats layout
(`<world>/players/stats/`) and the legacy one (`<world>/stats/`).

## PvP fight summaries

PvP deaths produce an interactive death message — the killer's name carries
their **skin head** on hover and opens the fight summary when clicked. The
summary shows hits, hits taken, crits, best combo, damage, arrows, potions and
totems for both players. Fights are tracked live, persisted to
`plugins/PlayerInfo/fights.json`, and survive restarts. Tracking can be turned
off in the config.

## Website

The plugin embeds a dashboard at **`http://<server-ip>:8080`** (configurable):
home page with the server IP and live player list, searchable player profiles
with skin avatars, a recent-fights feed, and leaderboards with an all-time /
weekly toggle. Players can opt out with `/stats hide`; public responses never
contain IP addresses or UUIDs, and the API is read-only and rate-limited.

```yaml
web:
  enabled: true
  port: 8080
  bind: 0.0.0.0
  site-name: "Server Stats"
  server-address: ""      # shown on the home page, e.g. play.example.com
  cache-seconds: 10
  rate-limit-per-minute: 120
  public-fights: true
```

## Discord webhook

Set `discord.enabled: true` and paste a webhook URL into the `discord:` section
and the plugin posts PvP fights, completed challenges, playtime milestones and
(optionally) deaths. Playtime milestones (10/50/100 hours by default) are also
announced in-game, whether or not Discord is enabled. All toggles are in the
config; `milestones-hours` controls the thresholds.

## PlaceholderAPI

When PlaceholderAPI is installed, `%playerinfo_playtime%`, `%playerinfo_blocks%`,
`%playerinfo_mobs%`, `%playerinfo_distance%`, `%playerinfo_deaths%`,
`%playerinfo_kills%`, `%playerinfo_rank%` and `%playerinfo_fights%` become
available (values refresh with the leaderboard scan).

## Configuration

- `config.yml` — feature toggles, fight tracking, ranks, milestones, web,
  Discord, AFK timeout (`afk-minutes`)
- `messages.yml` — every chat message in MiniMessage format; placeholders use
  `<name>`-style tags (for example `<player>` and `<hours>`)
- `fights.json` / `deaths.json` / `ranks.json` / `milestones.json` /
  `playerdata.json` / `hidden.json` — plugin data
- Data files never contain IP addresses; the website never exposes them

## Development

```bash
./gradlew build        # builds the jar and runs the message-rendering tests
./gradlew test         # tests only
./gradlew runServer    # boots a Paper 26.3 test server (requires Java 25)
```

The jar is compiled against `paper-api 1.21.1` with Java 21 bytecode and
verified against the APIs of 1.21.11, 26.1.x, 26.2 and 26.3.

## License

[MIT](LICENSE) © 2026 Mayank Negi (AFKZ Studios)
