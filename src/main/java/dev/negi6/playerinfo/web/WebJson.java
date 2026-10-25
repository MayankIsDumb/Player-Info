/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.negi6.playerinfo.stats.AdvancementSource;
import dev.negi6.playerinfo.stats.FightTracker;
import dev.negi6.playerinfo.stats.LeaderboardService;
import dev.negi6.playerinfo.stats.StatsGui;
import dev.negi6.playerinfo.stats.StatsList;
import dev.negi6.playerinfo.stats.StatsSource;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Builds the JSON responses for the website API from data the plugin already
 * tracks. Online players are read on the main thread; offline players are
 * read from their stats files directly.
 */
public final class WebJson {

    private WebJson() {
    }

    public static String playerName(FightTracker.Fight fight, UUID player) {
        return fight.name(player);
    }

    public static JsonObject serverJson(SnapshotCache snapshot) {
        SnapshotCache.ServerSnapshot s = snapshot.current();
        JsonObject o = new JsonObject();
        o.addProperty("siteName", s.siteName());
        o.addProperty("serverAddress", s.serverAddress());
        o.addProperty("motd", s.motd());
        o.addProperty("version", s.version());
        o.addProperty("online", s.online());
        o.addProperty("max", s.max());
        o.addProperty("builtAt", s.builtAt());
        JsonArray players = new JsonArray();
        for (SnapshotCache.OnlinePlayer p : s.players()) {
            JsonObject po = new JsonObject();
            po.addProperty("name", p.name());
            po.addProperty("world", p.world());
            po.addProperty("gamemode", p.gamemode());
            po.addProperty("ping", p.ping());
            players.add(po);
        }
        o.add("players", players);
        return o;
    }

    public static JsonObject metaJson(JavaPlugin plugin, LeaderboardService leaderboards) {
        JsonObject o = new JsonObject();
        o.addProperty("pluginVersion", plugin.getPluginMeta().getVersion());
        o.addProperty("serverVersion", Bukkit.getMinecraftVersion());
        o.addProperty("knownPlayers", leaderboards.knownPlayers());
        return o;
    }

    public static JsonObject leaderboardJson(LeaderboardService leaderboards, String category, int limit) {
        LeaderboardService.Metric metric = LeaderboardService.parseMetric(category);
        if (metric == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("category", category);
        JsonArray entries = new JsonArray();
        int rank = 1;
        for (LeaderboardService.Entry entry : leaderboards.top(metric, limit)) {
            JsonObject e = new JsonObject();
            e.addProperty("rank", rank++);
            e.addProperty("uuid", entry.player().toString());
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.player());
            String name = offline.getName();
            e.addProperty("name", name != null ? name : entry.player().toString());
            e.addProperty("value", entry.value());
            entries.add(e);
        }
        o.add("entries", entries);
        return o;
    }

    public static JsonArray fightsJson(FightTracker tracker) {
        JsonArray array = new JsonArray();
        for (FightTracker.Fight fight : tracker.allFightsNewestFirst()) {
            array.add(fightJson(fight));
        }
        return array;
    }

    public static JsonObject fightJson(FightTracker.Fight fight) {
        JsonObject o = new JsonObject();
        o.addProperty("id", fight.id());
        o.addProperty("time", fight.startedAt());
        o.add("winner", sideJson(fight, fight.winner(), true));
        o.add("loser", sideJson(fight, fight.loser(), false));
        return o;
    }

    private static JsonObject sideJson(FightTracker.Fight fight, UUID player, boolean winner) {
        FightTracker.Side side = fight.side(player);
        JsonObject o = new JsonObject();
        o.addProperty("name", fight.name(player));
        o.addProperty("winner", winner);
        o.addProperty("hits", side.hits);
        o.addProperty("hitsTaken", side.hitsTaken);
        o.addProperty("crits", side.crits);
        o.addProperty("bestCombo", side.bestCombo);
        o.addProperty("damageDealt", side.damageDealt);
        o.addProperty("damageTaken", side.damageTaken);
        o.addProperty("arrowsHit", side.arrowsHit);
        o.addProperty("potsThrown", side.potsThrown);
        o.addProperty("totems", side.totems);
        return o;
    }

    /**
     * Full profile JSON for a player, or null if the name is unknown.
     * Online players are read on the main thread; offline players from their
     * stats files.
     */
    public static JsonObject playerJson(JavaPlugin plugin, StatsGui gui, String rawName) throws Exception {
        String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        record Lookup(UUID uuid, boolean online, String name, OfflinePlayer offline) {}
        Lookup lookup = MainThread.call(plugin, () -> {
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                return new Lookup(online.getUniqueId(), true, online.getName(), null);
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
            if (offline != null && offline.hasPlayedBefore()) {
                return new Lookup(offline.getUniqueId(), false, offline.getName(), offline);
            }
            return null;
        }, 3000);
        if (lookup == null) {
            return null;
        }

        JsonObject o = new JsonObject();
        o.addProperty("name", lookup.name());
        o.addProperty("online", lookup.online());

        JsonObject stats;
        if (lookup.online()) {
            stats = MainThread.call(plugin, () -> {
                Player player = Bukkit.getPlayer(lookup.uuid());
                if (player == null) {
                    return null;
                }
                JsonObject built = buildStatsJson(gui, StatsSource.of(player));
                built.addProperty("firstPlayed", player.getFirstPlayed());
                built.addProperty("lastSeen", player.getLastSeen());
                return built;
            }, 5000);
        } else {
            stats = buildStatsJson(gui, StatsSource.offline(lookup.offline()));
            stats.addProperty("firstPlayed", lookup.offline().getFirstPlayed());
            stats.addProperty("lastSeen", lookup.offline().getLastSeen());
        }
        if (stats == null) {
            throw new IllegalStateException("player went offline");
        }
        for (String key : stats.keySet()) {
            o.add(key, stats.get(key));
        }
        return o;
    }

    private static JsonObject buildStatsJson(StatsGui gui, StatsSource src) {
        JsonObject o = new JsonObject();

        JsonArray general = new JsonArray();
        for (StatsGui.WebStat stat : StatsGui.generalWebStats(src)) {
            JsonObject g = new JsonObject();
            g.addProperty("label", stat.label());
            g.addProperty("value", stat.formatted());
            general.add(g);
        }
        o.add("general", general);

        JsonObject breakdowns = new JsonObject();
        breakdowns.add("mined", topList(gui, src, StatsList.MINED, 25));
        breakdowns.add("crafted", topList(gui, src, StatsList.CRAFTED, 25));
        breakdowns.add("used", topList(gui, src, StatsList.USED, 25));
        breakdowns.add("broken", topList(gui, src, StatsList.BROKEN, 25));
        breakdowns.add("picked_up", topList(gui, src, StatsList.PICKED_UP, 25));
        breakdowns.add("dropped", topList(gui, src, StatsList.DROPPED, 25));
        breakdowns.add("enchanted", topList(gui, src, StatsList.ENCHANTED, 25));
        o.add("breakdowns", breakdowns);

        JsonObject mobs = new JsonObject();
        mobs.add("killed", topList(gui, src, StatsList.MOBS_KILLED, 25));
        mobs.add("killed_by", topList(gui, src, StatsList.KILLED_BY, 25));
        o.add("mobs", mobs);

        AdvancementSource.Summary advancements = AdvancementSource.of(src);
        JsonObject advJson = new JsonObject();
        advJson.addProperty("done", advancements.done());
        advJson.addProperty("total", advancements.total());
        o.add("advancements", advJson);

        return o;
    }

    private static JsonArray topList(StatsGui gui, StatsSource src, StatsList list, int limit) {
        JsonArray arr = new JsonArray();
        for (StatsGui.Entry entry : gui.listEntries(src, list, new ArrayList<>())) {
            if (arr.size() >= limit) {
                break;
            }
            JsonObject o = new JsonObject();
            o.addProperty("name", entry.name());
            o.addProperty("value", entry.value());
            arr.add(o);
        }
        return arr;
    }
}
