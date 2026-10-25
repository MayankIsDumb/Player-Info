# PlayerInfo Website — Plan

A dashboard website **served by the plugin itself**: visitors open
`http://<server-ip>:<port>` and see live server info, every player's stats,
recent PvP fights, and leaderboards. No external hosting, no database — the
plugin is the backend, using data it already tracks.

## How it works (architecture)

- The plugin embeds a small HTTP server (JDK built-in `HttpServer` — zero new
  dependencies) on a configurable port, e.g. `http://1.2.3.4:8080`.
- **Live data never touches HTTP threads.** A snapshot cache is refreshed on
  the main thread every few seconds; HTTP handlers only read the cache and
  files, so the game server can never lag from web traffic.
- The dashboard is a static single-page site (vanilla HTML/CSS/JS, dark theme
  matching the in-game fight panels) bundled inside the jar — no build tools,
  no CDN, works offline.
- Skin avatars are rendered client-side from `mc-heads.net` (name-based, no
  UUIDs exposed); if that service is unreachable the site degrades to a
  default head.

## JSON API

| Method | Path | Source | Returns |
|---|---|---|---|
| GET | `/api/server` | snapshot cache | name, MOTD, version, online/max, player list (name, world, gamemode, ping) |
| GET | `/api/player/{name}` | `StatsSource` (existing) | full stats — same data as `/stats`: general, blocks, items, mobs, combat, first/last seen, online flag |
| GET | `/api/fights?offset=0&limit=25` | `FightTracker` (existing) | recent fights, newest first |
| GET | `/api/fights/{id}` | `FightTracker` | one fight: winner, loser, all 9 stats per side, timestamp |
| GET | `/api/leaderboard/{stat}` | leaderboard cache | top N — `kills`, `deaths`, `kd`, `playtime`, `damage`, `crits`, `combo`, `mined`, `mobkills` |
| GET | `/api/deaths/{name}` | `DeathStore` | recent deaths — world, coordinates, cause, time |
| GET | `/api/advancements/{name}` | `AdvancementSource` | done/total + list of remaining advancements |
| GET | `/api/meta` | plugin | version, uptime, enabled features (for the site footer) |

All responses are read-only JSON. Nothing in v1 accepts writes.

## Dashboard pages

1. **Home** — server IP with click-to-copy, MOTD, online count, live player
   grid with heads, search bar.
2. **Player profile** (`#/player/<name>`) — skin avatar, online/offline badge,
   first join / last seen, every stats section the in-game GUI has.
3. **Recent fights** (`#/fights`) — feed of Winner/Defeated cards, newest
   first, "time ago" labels.
4. **Fight detail** (`#/fight/<id>`) — the full two-panel summary (hits, crits,
   best combo, damage, arrows, pots, totems) like the in-game GUI.
5. **Leaderboards** (`#/leaderboard/<stat>`) — category tabs, top 50, click a
   name → profile.
6. **Header search** — autocomplete over known player names.

## Data sources — all already exist

- Player stats → `StatsSource` (live API for online players, stats files for
  offline — handles both 26.x and legacy layouts).
- Fights → `FightTracker` + `fights.json` (persistent).
- Leaderboards → a `LeaderboardService` that periodically scans the stats
  directory (async, cached, refreshed every few minutes).

## Privacy rules

- **IP addresses are never exposed on the web** — the Login Info page stays
  in-game/op-only.
- No UUIDs in public responses (skins use names).
- `public-stats: true` config toggle + `/stats hide` opt-out: a player can
  exclude themselves from the website (persisted per-UUID).
- Fight summaries are public by default (configurable) — they contain no
  sensitive data.

## Config additions

```yaml
web:
  enabled: true
  port: 8080
  bind: 0.0.0.0        # 127.0.0.1 if serving behind a reverse proxy
  site-name: "Server Stats"
  cache-seconds: 10     # live snapshot refresh rate
  leaderboard-size: 50
  leaderboard-refresh-minutes: 5
  public-fights: true
  public-stats: true    # false = only opted-in players appear
```

## Security

- Read-only endpoints only; no commands can be triggered from the web.
- Configurable bind address (loopback for reverse-proxy setups).
- Simple per-IP rate limiting on the API (light token bucket) — phase 2.
- Pagination on every list endpoint.

## Phases

**Phase 1 — MVP (first build):** embedded server, `/api/server`,
`/api/player/{name}`, `/api/fights`, home page, profile page, fights feed,
config section. Everything from the plugin's current features visible on the
web.

**Phase 2:** leaderboards (`LeaderboardService`), `/api/fights/{id}` +
fight detail page, `/stats hide` opt-out, rate limiting, header search
autocomplete.

**Phase 3 (later ideas, not in scope yet):** Tier Rating (TR) system —
ELO-style rating updated per fight, shown on profiles and its own leaderboard;
Discord webhook for new fights; kills/deaths graphs.

## SMP roadmap (approved direction)

For an SMP audience the focus shifts from competition to progression,
community and memories. PvP fight tracking stays exactly as is (config
toggle) — no new PvP features.

| Order | Feature | Status |
|---|---|---|
| 1 | **Death log** — `/deaths` style page in GUI, coordinates + cause + time, click-to-copy in chat; persisted to `deaths.json`; website endpoint + page | ✅ in-game done |
| 2 | **Advancement progress** — "87/122 completed" with remaining list in GUI (online via API, offline via advancements file); website endpoint + page | ✅ in-game done |
| 3 | **Stats-scan service** → all-time leaderboards (playtime, blocks, mobs, distance, deaths) + server-wide totals; cached async scan of every stats file; website endpoints | next |
| 4 | **Profile personalization** — bio, base coordinates, favorite activity in `playerdata.json`, shown in GUI + website | next |
| 5 | **`/stats compare <player>`** — side-by-side comparison screen | next |
| 6 | **Discord webhook** — advancements, milestones, deaths (config-gated URL) | later |
| 7 | **AFK detection** — `Player#getIdleDuration()` badge; keeps leaderboards honest | later |
| 8 | **Playtime ranks/rewards** — needs AFK-aware playtime accumulation | later |
| 9 | **PlaceholderAPI + messages.yml** — adoption polish | later |
| — | Weekly/monthly leaderboards — deferred: vanilla keeps lifetime totals only, needs a snapshot-and-delta system | deferred |
| — | TR / Elo rating — dropped for SMP (was a PvP-competition feature) | dropped |

## Files this adds/changes

- `src/main/java/dev/negi6/playerinfo/web/WebServerService.java` — HTTP server
  lifecycle + routing
- `.../web/SnapshotCache.java` — main-thread live snapshot
- `.../web/LeaderboardService.java` — phase 2
- `.../web/Json.java` — small JSON writer helpers (runtime Gson from Paper)
- `src/main/resources/web/index.html`, `app.js`, `style.css` — dashboard
- `PlayerInfoPlugin` — start/stop the service with the plugin
- `config.yml` — `web:` section
- New command: `/stats hide` (opt-out), phase 2
