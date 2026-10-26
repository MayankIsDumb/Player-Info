/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
    private long refreshMs;
    private final File snapshotsFile;
    private final List<Snapshot> snapshots = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile Map<UUID, long[]> aggregates = Map.of();
    private volatile Map<UUID, String> names = Map.of();
    private volatile long lastScan;
    private BukkitTask task;

    /** A point-in-time copy of every player's aggregate values. */
    public record Snapshot(long time, Map<UUID, long[]> values) {}

    public LeaderboardService(JavaPlugin plugin, long refreshMinutes, File dataFolder) {
        this.plugin = plugin;
        this.refreshMs = Math.max(1, refreshMinutes) * 60_000L;
        this.snapshotsFile = new File(dataFolder, "snapshots.json");
        loadSnapshots();
    }

    public record SearchHit(UUID uuid, String name) {}

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

    /** Applies a new refresh interval and restarts the scan task. */
    public void reload(long refreshMinutes) {
        stop();
        this.refreshMs = Math.max(1, refreshMinutes) * 60_000L;
        start();
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

    /** Cached display name for a scanned player, or null if unknown. */
    public String nameOf(UUID uuid) {
        return names.get(uuid);
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

    /**
     * Leaderboard over the last 7 days: current totals minus the newest
     * snapshot that is at least 7 days old (or the oldest snapshot available,
     * in which case the window is shorter). Needs a few days of history to be
     * complete.
     */
    public List<Entry> topWeekly(Metric metric, int limit) {
        if (snapshots.isEmpty()) {
            return List.of();
        }
        long cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;
        Snapshot base = null;
        for (Snapshot snapshot : snapshots) {
            if (snapshot.time() <= cutoff) {
                base = snapshot;
            }
        }
        if (base == null) {
            base = snapshots.getFirst();
        }
        List<Entry> out = new ArrayList<>();
        for (var e : aggregates.entrySet()) {
            long now = e.getValue()[metric.ordinal()];
            long[] baseValues = base.values().get(e.getKey());
            long before = baseValues == null ? 0 : baseValues[metric.ordinal()];
            long delta = now - before;
            if (delta > 0) {
                out.add(new Entry(e.getKey(), delta));
            }
        }
        out.sort(Comparator.comparingLong(Entry::value).reversed());
        return new ArrayList<>(out.subList(0, Math.min(limit, out.size())));
    }

    private void recordSnapshot(Map<UUID, long[]> parsed) {
        long now = System.currentTimeMillis();
        snapshots.add(new Snapshot(now, parsed));
        long pruneBefore = now - 8L * 24 * 60 * 60 * 1000;
        snapshots.removeIf(snapshot -> snapshot.time() < pruneBefore);
        // Serializing every snapshot can be heavy; keep it off the game thread.
        List<Snapshot> copy = List.copyOf(snapshots);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveSnapshots(copy));
    }

    private void loadSnapshots() {
        if (!snapshotsFile.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(snapshotsFile.toPath(), StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject o = element.getAsJsonObject();
                Map<UUID, long[]> values = new HashMap<>();
                for (var entry : o.getAsJsonObject("values").entrySet()) {
                    JsonArray slots = entry.getValue().getAsJsonArray();
                    long[] parsed = new long[SLOTS];
                    for (int i = 0; i < SLOTS && i < slots.size(); i++) {
                        parsed[i] = slots.get(i).getAsLong();
                    }
                    values.put(UUID.fromString(entry.getKey()), parsed);
                }
                snapshots.add(new Snapshot(o.get("time").getAsLong(), values));
            }
            snapshots.sort(Comparator.comparingLong(Snapshot::time));
        } catch (Exception loadFailure) {
            plugin.getLogger().warning("[PlayerInfo] Could not load snapshots: "
                    + loadFailure.getMessage());
        }
    }

    private void saveSnapshots(List<Snapshot> toSave) {
        try {
            JsonArray array = new JsonArray();
            for (Snapshot snapshot : toSave) {
                JsonObject o = new JsonObject();
                o.addProperty("time", snapshot.time());
                JsonObject values = new JsonObject();
                for (var entry : snapshot.values().entrySet()) {
                    JsonArray slots = new JsonArray();
                    for (long slot : entry.getValue()) {
                        slots.add(slot);
                    }
                    values.add(entry.getKey().toString(), slots);
                }
                o.add("values", values);
                array.add(o);
            }
            snapshotsFile.getParentFile().mkdirs();
            Files.writeString(snapshotsFile.toPath(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(array));
        } catch (Exception saveFailure) {
            plugin.getLogger().warning("[PlayerInfo] Could not save snapshots: "
                    + saveFailure.getMessage());
        }
    }

    /** Aggregate value for one player and metric, or null if never scanned. */
    public Long aggregateOf(UUID uuid, Metric metric) {
        long[] values = aggregates.get(uuid);
        return values == null ? null : values[metric.ordinal()];
    }

    /** Server-wide total for one metric (KD unsupported). */
    public long total(Metric metric) {
        long sum = 0;
        for (var e : aggregates.entrySet()) {
            sum += e.getValue()[metric.ordinal()];
        }
        return sum;
    }

    private void scan() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, long[]> parsed = scanFiles();
            // Bukkit player lookups must stay on the main thread.
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<UUID, String> parsedNames = new HashMap<>();
                Map<UUID, String> previousNames = names;
                for (UUID uuid : parsed.keySet()) {
                    String name = previousNames.get(uuid);
                    if (name == null) {
                        name = Bukkit.getOfflinePlayer(uuid).getName();
                    }
                    if (name != null) {
                        parsedNames.put(uuid, name);
                    }
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    parsed.put(player.getUniqueId(), liveValues(player));
                    parsedNames.put(player.getUniqueId(), player.getName());
                }
                aggregates = parsed;
                names = parsedNames;
                lastScan = System.currentTimeMillis();
                // Hourly point-in-time snapshots for the weekly leaderboards.
                if (snapshots.isEmpty() || lastScan - snapshots.getLast().time() >= 60L * 60 * 1000) {
                    recordSnapshot(parsed);
                }
                plugin.getLogger().info("[PlayerInfo] Leaderboards updated for "
                        + parsed.size() + " players.");
            });
        });
    }

    private Map<UUID, long[]> scanFiles() {
        Map<UUID, long[]> out = new HashMap<>();
        Path worldPath = ServerData.worldPath();
        if (worldPath == null) {
            return out;
        }
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
