/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/**
 * Unified view of a player's statistics, online or offline. Online players are
 * read through the Bukkit API; offline players are read from the vanilla stats
 * file ({@code <world>/stats/<uuid>.json}). Keyed {@link Material} and
 * {@link EntityType} keys mirror the vanilla ids used in that file, and
 * {@link Statistic#getKey()} mirrors the vanilla custom stat ids.
 */
public final class StatsSource {

    private final OfflinePlayer player;
    private final Player online;     // null when the player is offline
    private final JsonObject custom; // "minecraft:custom" entries, null when online
    private final Map<String, JsonObject> groups;

    private StatsSource(OfflinePlayer player, Player online, JsonObject custom, Map<String, JsonObject> groups) {
        this.player = player;
        this.online = online;
        this.custom = custom;
        this.groups = groups;
    }

    public static StatsSource of(Player player) {
        return new StatsSource(player, player, null, Map.of());
    }

    /** Resolves a player by UUID, online (live API) or offline (stats file). */
    public static StatsSource ofUuid(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        return online != null ? of(online) : offline(Bukkit.getOfflinePlayer(uuid));
    }

    public static StatsSource offline(OfflinePlayer player) {
        JsonObject stats = readStatsFile(player.getUniqueId());
        JsonObject custom = stats == null ? null : stats.getAsJsonObject("minecraft:custom");
        Map<String, JsonObject> groups = new HashMap<>();
        if (stats != null) {
            for (StatsList list : StatsList.values()) {
                JsonObject group = stats.getAsJsonObject("minecraft:" + list.jsonGroup);
                if (group != null) {
                    groups.put(list.jsonGroup, group);
                }
            }
        }
        return new StatsSource(player, null, custom, groups);
    }

    private static JsonObject readStatsFile(UUID uuid) {
        Path worldPath = ServerData.worldPath();
        if (worldPath == null) {
            return null;
        }
        // Minecraft 26.x stores stats under <world>/players/stats/; older worlds
        // used <world>/stats/ — check both so upgraded worlds keep working.
        List<Path> candidates = List.of(
                worldPath.resolve("players").resolve("stats"),
                worldPath.resolve("stats"));
        for (Path dir : candidates) {
            Path file = dir.resolve(uuid + ".json");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                // The file nests everything under "stats" — return that object.
                return root.has("stats") ? root.getAsJsonObject("stats") : root;
            } catch (Exception brokenStatsFile) {
                Bukkit.getLogger().warning("[PlayerInfo] Could not read stats file for " + uuid + ": "
                        + brokenStatsFile.getMessage());
                return null;
            }
        }
        return null;
    }

    public boolean isOnline() {
        return online != null;
    }

    public OfflinePlayer player() {
        return player;
    }

    public Player onlinePlayer() {
        return online;
    }

    public String name() {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }

    public UUID uuid() {
        return player.getUniqueId();
    }

    /** Bukkit enum names that differ from the vanilla stat ids in stats files. */
    private static final Map<String, String> VANILLA_ALIASES = Map.of(
            "PLAY_ONE_MINUTE", "play_time",
            "DROP_COUNT", "dropped",
            "RECORD_PLAYED", "play_record",
            "CRAFTING_TABLE_INTERACTION", "interact_with_crafting_table",
            "FURNACE_INTERACTION", "interact_with_furnace",
            "CHEST_OPENED", "open_chest",
            "ENDERCHEST_OPENED", "open_enderchest",
            "BREWINGSTAND_INTERACTION", "interact_with_brewingstand",
            "BEACON_INTERACTION", "interact_with_beacon");

    public long untyped(Statistic stat) {
        if (online != null) {
            return online.getStatistic(stat);
        }
        if (custom == null) {
            return 0;
        }
        long value = longOf(custom, "minecraft:" + stat.getKey().getKey());
        if (value == 0) {
            // Fallback for stats whose Bukkit name differs from the vanilla id.
            value = longOf(custom, "minecraft:" + VANILLA_ALIASES.getOrDefault(
                    stat.name(), stat.name().toLowerCase(Locale.ROOT)));
        }
        return value;
    }

    public long material(StatsList list, Material material) {
        if (online != null) {
            try {
                return online.getStatistic(list.statistic, material);
            } catch (Exception unsupportedPairing) {
                return 0;
            }
        }
        JsonObject group = groups.get(list.jsonGroup);
        return group == null ? 0 : longOf(group, material.getKey().toString());
    }

    public long entity(StatsList list, EntityType type) {
        if (online != null) {
            try {
                return online.getStatistic(list.statistic, type);
            } catch (Exception unsupportedPairing) {
                return 0;
            }
        }
        JsonObject group = groups.get(list.jsonGroup);
        return group == null ? 0 : longOf(group, type.getKey().toString());
    }

    /** Total blocks mined across all materials. */
    public long minedTotal() {
        if (online != null) {
            long sum = 0;
            for (Material material : Material.values()) {
                if (material.isAir() || !material.isBlock()) {
                    continue;
                }
                try {
                    sum += online.getStatistic(Statistic.MINE_BLOCK, material);
                } catch (Exception unsupportedPairing) {
                    // skip
                }
            }
            return sum;
        }
        JsonObject mined = groups.get("mined");
        if (mined == null) {
            return 0;
        }
        long sum = 0;
        for (var entry : mined.entrySet()) {
            sum += longOf(mined, entry.getKey());
        }
        return sum;
    }

    /** Total distance traveled across all distance statistics (centimetres). */
    public long distanceTotal() {
        if (online != null) {
            long sum = 0;
            for (Statistic statistic : Statistic.values()) {
                if (statistic.name().endsWith("_ONE_CM")) {
                    try {
                        sum += online.getStatistic(statistic);
                    } catch (Exception unsupportedPairing) {
                        // skip
                    }
                }
            }
            return sum;
        }
        JsonObject customObject = custom;
        if (customObject == null) {
            return 0;
        }
        long sum = 0;
        for (var entry : customObject.entrySet()) {
            if (entry.getKey().endsWith("_one_cm")) {
                sum += longOf(customObject, entry.getKey());
            }
        }
        return sum;
    }

    private static long longOf(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : 0;
    }
}
