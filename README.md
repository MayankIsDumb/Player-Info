# PlayerInfo

A Paper statistics plugin with a vanilla-style statistics GUI, per-fight PvP
summaries, and offline-player support. Works on **Paper (or forks like Purpur)
1.21.1 through 26.x** — one jar for the whole range. Spigot is not supported
(the plugin uses Paper-only APIs).

**Server requirements:** Paper 1.21.1+ (or 26.x) and **Java 21+** (26.x servers
already run Java 25). Old worlds are handled: offline-player stats are read
from both the 26.x layout (`<world>/players/stats/`) and the legacy one
(`<world>/stats/`).

**Verified against the API of each line:** Minecraft 1.21.11, 26.1.x
(26.1 / 26.1.1 / 26.1.2 — stable builds of that line ship as
`26.1.2.build.N`), 26.2, and 26.3.

**Configuration:** on first start the plugin creates
`plugins/PlayerInfo/config.yml` — PvP fight tracking, the fight idle timeout,
history size, and the join welcome message can all be toggled there. Fight
summaries are stored in `plugins/PlayerInfo/fights.json`.

A Paper plugin development environment targeting **Minecraft 26.3** (Paper API
`26.3.build.+`, the new `<mc>.build.<n>-stable` versioning). A 1.21.11 backup of the
dev world is kept in `run/world-1.21.11-backup`. The plugin itself compiles
against `paper-api 1.21.1` with Java 21 bytecode for maximum compatibility;
newer-only entries (e.g. Happy Ghast travel stats) resolve dynamically at
runtime and are skipped on servers that lack them.
- Gradle is **not** needed — the project ships the Gradle 9.8.2 wrapper, which downloads itself on first run.

## Development commands

Run these from the project root (in Git Bash / PowerShell / CMD):

| Command | What it does |
|---|---|
| `./gradlew build` | Compiles the plugin and produces `build/libs/PlayerInfo-0.1.0.jar` |
| `./gradlew runServer` | Boots a local Paper 26.3 test server in `run/` with your plugin loaded |
| `./gradlew clean build` | Clean rebuild |

`runServer` downloads the Paper server jar on first use (cached), pre-accepts the EULA
(`run/eula.txt`), creates a fresh world, and hot-loads your plugin jar. Type `stop` in the
server console to shut it down. To test the permission-gated command, run `op <yourname>`
in the server console, then join and use `/playerinfo <player>`.

To install the plugin on a real server, copy `build/libs/PlayerInfo-0.1.0.jar` into that
server's `plugins/` folder.

## Commands

| Command | Permission | What it does |
|---|---|---|
| `/playerinfo <player>` | `playerinfo.use` (op) | Chat summary: UUID, world, location, health, gamemode, ping |
| `/stats [player]` | `playerinfo.stats` (everyone, self only) | Opens the statistics GUI |
| `/stats <player>` | `playerinfo.stats.others` (op) | Opens another online player's statistics GUI |

## The `/stats` GUI

`/stats [player]` opens a profile screen with the player's skin head in the
centre and options on the sides:

- **General** — all general vanilla statistics (play time, jumps, deaths, damage,
  distances, trades, interactions), formatted (ticks → time, cm → m/km), paginated.
- **Blocks** — every block mined, sorted by count, paginated.
- **Items** — sub-menu: Crafted, Used, Broken, Picked Up, Dropped, Enchanted.
- **Mobs** — sub-menu: Mobs Killed and Killed By, one icon per entity type.
- **Players Killed / Times Died** — live counts on the profile screen; clicking
  opens the per-entity lists behind them.
- **Combat** — damage dealt, taken, absorbed, plus **critical hits**. Vanilla
  does not track crits, so the plugin counts them itself (melee and critical
  arrow hits) per session — counters reset when the player quits or the server
  restarts.
- **Login Info** — login time and session length (tracked since the player's
  next join), first-ever join date, and the login IP. The IP is only shown to
  the player themselves or to viewers with `playerinfo.stats.others`.

Statistics are available for **online players** (live via the Bukkit API) and — for
operators with `playerinfo.stats.others` — for **offline players** too: their stats are
read from the vanilla stats files (`world/stats/<uuid>.json`). Offline profiles show
last-seen/first-join dates instead of live login info, and tab completion suggests
offline players as well.

## Project layout

```
build.gradle.kts               Build config: Paper API dependency, Java 21, runServer task
settings.gradle.kts            Project name
src/main/java/dev/negi6/playerinfo/
    PlayerInfoPlugin.java      Plugin entry point (JavaPlugin)
    JoinListener.java          Example PlayerJoinEvent listener
    PlayerInfoCommand.java     Example /playerinfo command with tab completion
src/main/resources/plugin.yml Plugin metadata (name, main class, commands, permissions)
run/                           Local test server data (gitignored)
```

## Making it yours

- **Rename** the plugin: change `rootProject.name` in `settings.gradle.kts`, the package
  `dev.negi6.playerinfo` in the Java sources, and the `main`/`name` entries in `plugin.yml`.
- **Version** lives in `build.gradle.kts` and is injected into `plugin.yml` at build time.
- **Dependencies**: the Paper API is `compileOnly` — everything it provides is present at
  runtime on the server. Add other libraries with `compileOnly` or `implementation` +
  shading as appropriate.
- **NMS / internals**: if you later need Mojang-mapped internal code, add the
  [paperweight-userdev](https://docs.papermc.io/paper/dev/userdev) Gradle setup — not needed
  for API-only plugins.
- **Upgrading Minecraft**: bump the `paper-api` version (use `<mc>.build.+` to track the
  latest stable build of a line) and the `minecraftVersion("26.3")` line in
  `build.gradle.kts` (check https://repo.papermc.io for exact version strings). Mind that
  Paper 26.x requires **Java 25** (toolchain in `build.gradle.kts`).

## License

[MIT](LICENSE) © 2026 Mayank Negi (AFKZ Studios)
