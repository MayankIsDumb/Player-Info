/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Playtime ranks: configured hour thresholds with display names and optional
 * console-command rewards. The highest reached rank applies; promotions are
 * broadcast, rewarded and persisted to {@code plugins/PlayerInfo/ranks.json}.
 * Hours come from vanilla playtime (includes AFK time).
 */
public final class PlaytimeRanks {

    public record Rank(long hours, String name, List<String> commands) {}

    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Long> announced = new HashMap<>();
    private final List<Rank> ranks = new ArrayList<>();
    private boolean enabled = true;
    private BukkitTask task;

    public PlaytimeRanks(JavaPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.dataFile = new File(dataFolder, "ranks.json");
    }

    public void applySettings() {
        enabled = plugin.getConfig().getBoolean("playtime-ranks.enabled", true);
        ranks.clear();
        for (Map<?, ?> entry : plugin.getConfig().getMapList("playtime-ranks.thresholds")) {
            Object name = entry.get("name");
            if (!(entry.get("hours") instanceof Number hours) || name == null) {
                continue;
            }
            List<String> commands = new ArrayList<>();
            if (entry.get("commands") instanceof List<?> list) {
                for (Object command : list) {
                    commands.add(String.valueOf(command));
                }
            }
            ranks.add(new Rank(hours.longValue(), String.valueOf(name), commands));
        }
        ranks.sort(Comparator.comparingLong(Rank::hours));
    }

    public void start() {
        applySettings();
        if (!enabled || ranks.isEmpty()) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** Highest rank for the given playtime hours, or null below the first. */
    public Rank rankOf(long hours) {
        Rank out = null;
        for (Rank rank : ranks) {
            if (hours >= rank.hours()) {
                out = rank;
            }
        }
        return out;
    }

    private void check() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            long hours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000L;
            Rank rank = rankOf(hours);
            if (rank == null) {
                continue;
            }
            long previously = announced.getOrDefault(player.getUniqueId(), 0L);
            if (rank.hours() > previously) {
                announced.put(player.getUniqueId(), rank.hours());
                save();
                Bukkit.broadcast(Component.text("⭐ " + player.getName() + " is now a "
                        + rank.name() + "! (" + hours + "h)", NamedTextColor.GOLD));
                for (String command : rank.commands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command
                            .replace("{player}", player.getName())
                            .replace("{rank}", rank.name())
                            .replace("{hours}", String.valueOf(hours)));
                }
            }
        }
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (var entry : announced.entrySet()) {
                root.addProperty(entry.getKey().toString(), entry.getValue());
            }
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception saveFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not save playtime ranks: "
                    + saveFailure.getMessage());
        }
    }

    private void load() {
        if (!dataFile.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                announced.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
            }
        } catch (Exception loadFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not load playtime ranks: "
                    + loadFailure.getMessage());
        }
    }
}
