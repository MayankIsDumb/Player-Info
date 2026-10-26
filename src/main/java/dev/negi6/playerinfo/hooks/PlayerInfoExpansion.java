/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.hooks;

import dev.negi6.playerinfo.PlayerInfoPlugin;
import dev.negi6.playerinfo.stats.FightTracker;
import dev.negi6.playerinfo.stats.LeaderboardService;
import dev.negi6.playerinfo.stats.PlaytimeRanks;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI placeholders: %playerinfo_playtime%, %playerinfo_blocks%,
 * %playerinfo_mobs%, %playerinfo_distance%, %playerinfo_deaths%,
 * %playerinfo_kills%, %playerinfo_rank%, %playerinfo_fights%.
 * Values come from the leaderboard scan, so they refresh every few minutes.
 */
public final class PlayerInfoExpansion extends PlaceholderExpansion {

    private final PlayerInfoPlugin plugin;
    private final PlaytimeRanks ranks;
    private final LeaderboardService leaderboards;
    private final FightTracker fightTracker;

    public PlayerInfoExpansion(PlayerInfoPlugin plugin, PlaytimeRanks ranks,
                               LeaderboardService leaderboards, FightTracker fightTracker) {
        this.plugin = plugin;
        this.ranks = ranks;
        this.leaderboards = leaderboards;
        this.fightTracker = fightTracker;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playerinfo";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Mayank Negi";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(java.util.Locale.ROOT);
        UUID uuid = player == null ? null : player.getUniqueId();
        Long aggregate = uuid == null ? null : leaderboards.aggregateOf(uuid,
                switch (key) {
                    case "playtime", "rank" -> LeaderboardService.Metric.PLAYTIME;
                    case "blocks" -> LeaderboardService.Metric.BLOCKS;
                    case "mobs" -> LeaderboardService.Metric.MOBS;
                    case "distance" -> LeaderboardService.Metric.DISTANCE;
                    case "deaths" -> LeaderboardService.Metric.DEATHS;
                    case "kills" -> LeaderboardService.Metric.KILLS;
                    default -> null;
                });
        return switch (key) {
            case "playtime" -> aggregate == null ? null
                    : String.format(java.util.Locale.ROOT, "%.1f", aggregate / 72000.0);
            case "blocks", "mobs", "distance", "deaths", "kills" -> aggregate == null ? null
                    : String.valueOf(aggregate);
            case "rank" -> {
                if (aggregate == null) {
                    yield null;
                }
                PlaytimeRanks.Rank rank = ranks.rankOf(aggregate / 72000L);
                yield rank == null ? "None" : rank.name();
            }
            case "fights" -> uuid == null ? null
                    : String.valueOf(fightTracker.fightsInvolving(uuid).size());
            default -> null;
        };
    }
}
