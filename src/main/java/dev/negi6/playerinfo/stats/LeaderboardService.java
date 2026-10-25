/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodically aggregates every player's statistics (async scan of the stats
 * files, live values for online players applied on the main thread) to power
 * the leaderboards and server-wide totals. One jar covers both the 26.x
 * layout ({@code <world>/players/stats/}) and the legacy layout.
 */
public final class LeaderboardService {

    public enum Metric { PLAYTIME, BLOCKS, MOBS, DISTANCE, DEATHS, KILLS, KD }

    /** Resolves a user-supplied category name, or null if unknown. */
    public static Metric parseMetric(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "playtime", "time" -> Metric.PLAYTIME;
            case "blocks", "mined" -> Metric.BLOCKS;
            case "mobs" -> Metric.MOBS;
            case "distance" -> Metric.DISTANCE;
            case "deaths" -> Metric.DEATHS;
            case "kills" -> Metric.KILLS;
            case "kd" -> Metric.KD;
            default -> null;
        };
    }

    public record Entry(UUID player, long value) {}

    /** Aggregate slots: indexed by the ordinals of PLAYTIME..KILLS. */
    private static final int SLOT_PLAYTIME = Metric.PLAYTIME.ordinal();
    private static final int SLOT_BLOCKS = Metric.BLOCKS.ordinal();
    private static final int SLOT_MOBS = Metric.MOBS.ordinal();
    private static final int SLOT_DISTANCE = Metric.DISTANCE.ordinal();
    private static final int SLOT_DEATHS = Metric.DEATHS.ordinal();
    private static final int SLOT_KILLS = Metric.KILLS.ordinal();
    private static final int SLOTS = SLOT_KILLS + 1;

    private final JavaPlugin plugin;
    private final long refreshMs;
    private volatile Map<UUID, long[]> aggregates = Map.of();
    private volatile long lastScan;
    private BukkitTask task;

    public LeaderboardService(JavaPlugin plugin, long refreshMinutes) {
        this.plugin = plugin;
        this.refreshMs = Math.max(1, refreshMinutes) * 60_000L;
    }

    public void start() {
        scan();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::scan, 20L * 60, refreshMs / 50);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isReady() {
        return lastScan > 0;
    }

    public long lastScan() {
        return lastScan;
    }

    public int knownPlayers() {
        return aggregates.size();
    }

    public List<Entry> top(Metric metric, int limit) {
        List<Entry> out = new ArrayList<>();
        if (metric == Metric.KD) {
            for (var e : aggregates.entrySet()) {
                long kills = e.getValue()[SLOT_KILLS];
                long deaths = e.getValue()[SLOT_DEATHS];
                if (kills > 0) {
                    out.add(new Entry(e.getKey(), Math.round(kills * 1000.0 / Math.max(1, deaths))));
                }
            }
        } else {
            for (var e : aggregates.entrySet()) {
                long value = e.getValue()[metric.ordinal()];
                if (value > 0) {
                    out.add(new Entry(e.getKey(), value));
                }
            }
        }
        out.sort(Comparator.comparingLong(Entry::value).reversed());
        return new ArrayList<>(out.subList(0, Math.min(limit, out.size())));
    }

    /** Server-wide total for one metric (KD unsupported). */
    public long total(Metric metric) {
        long sum = 0;
        for (long[] values : aggregates.values()) {
            sum += values[metric.ordinal()];
        }
        return sum;
    }

    private void scan() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, long[]> parsed = scanFiles();
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    parsed.put(player.getUniqueId(), liveValues(player));
                }
                aggregates = parsed;
                lastScan = System.currentTimeMillis();
                plugin.getLogger().info("[PlayerInfo] Leaderboards updated for "
                        + parsed.size() + " players.");
            });
        });
    }

    private Map<UUID, long[]> scanFiles() {
        Map<UUID, long[]> out = new HashMap<>();
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return out;
        }
        Path worldPath = world.getWorldFolder().toPath();
        List<Path> dirs = List.of(worldPath.resolve("players").resolve("stats"),
                worldPath.resolve("stats"));
        for (Path dir : dirs) {
            File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(".json"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String base = file.getName().substring(0, file.getName().length() - 5);
                UUID uuid;
                try {
                    uuid = UUID.fromString(base);
                } catch (IllegalArgumentException notAPlayerFile) {
                    continue;
                }
                if (out.containsKey(uuid)) {
                    continue;
                }
                long[] values = parseFile(file.toPath());
                if (values != null) {
                    out.put(uuid, values);
                }
            }
        }
        return out;
    }

    private long[] parseFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject stats = root.has("stats") ? root.getAsJsonObject("stats") : null;
            if (stats == null) {
                return null;
            }
            long[] values = new long[SLOTS];
            JsonObject custom = stats.has("minecraft:custom")
                    ? stats.getAsJsonObject("minecraft:custom") : null;
            if (custom != null) {
                values[SLOT_PLAYTIME] = longOf(custom, "minecraft:play_time");
                values[SLOT_DEATHS] = longOf(custom, "minecraft:deaths");
                values[SLOT_MOBS] = longOf(custom, "minecraft:mob_kills");
                values[SLOT_KILLS] = longOf(custom, "minecraft:player_kills");
                for (var entry : custom.entrySet()) {
                    if (entry.getKey().endsWith("_one_cm")) {
                        values[SLOT_DISTANCE] += longOf(custom, entry.getKey());
                    }
                }
            }
            JsonObject mined = stats.has("minecraft:mined")
                    ? stats.getAsJsonObject("minecraft:mined") : null;
            if (mined != null) {
                for (var entry : mined.entrySet()) {
                    values[SLOT_BLOCKS] += longOf(mined, entry.getKey());
                }
            }
            return values;
        } catch (Exception brokenFile) {
            return null;
        }
    }

    private long[] liveValues(Player player) {
        long[] values = new long[SLOTS];
        values[SLOT_PLAYTIME] = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        values[SLOT_DEATHS] = player.getStatistic(Statistic.DEATHS);
        values[SLOT_MOBS] = player.getStatistic(Statistic.MOB_KILLS);
        values[SLOT_KILLS] = player.getStatistic(Statistic.PLAYER_KILLS);
        long mined = 0;
        for (Material material : Material.values()) {
            if (material.isAir() || !material.isBlock()) {
                continue;
            }
            try {
                mined += player.getStatistic(Statistic.MINE_BLOCK, material);
            } catch (Exception unsupportedPairing) {
                // skip materials the API rejects
            }
        }
        values[SLOT_BLOCKS] = mined;
        long distance = 0;
        for (Statistic statistic : Statistic.values()) {
            if (statistic.name().endsWith("_ONE_CM")) {
                try {
                    distance += player.getStatistic(statistic);
                } catch (Exception unsupportedPairing) {
                    // skip
                }
            }
        }
        values[SLOT_DISTANCE] = distance;
        return values;
    }

    private static long longOf(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                ? object.get(key).getAsLong() : 0;
    }
}
