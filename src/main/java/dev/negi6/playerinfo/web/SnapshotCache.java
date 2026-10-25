/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.web;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * A periodically refreshed snapshot of server info and the online player
 * list, built on the main thread so HTTP handlers can serve live data
 * without touching Bukkit from their own threads.
 */
public final class SnapshotCache {

    public record OnlinePlayer(String name, String world, String gamemode, int ping, boolean afk) {}

    public record ServerSnapshot(String siteName, String serverAddress, String motd, String version,
                                 int online, int max, List<OnlinePlayer> players, long builtAt) {}

    private final JavaPlugin plugin;
    private volatile ServerSnapshot current = new ServerSnapshot(
            "Server Stats", "", "", "", 0, 0, List.of(), 0);
    private BukkitTask task;

    public SnapshotCache(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        ConfigurationSection web = plugin.getConfig().getConfigurationSection("web");
        long seconds = web == null ? 10 : Math.max(2, web.getLong("cache-seconds", 10));
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, seconds * 20L, seconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public ServerSnapshot current() {
        return current;
    }

    private void refresh() {
        ConfigurationSection web = plugin.getConfig().getConfigurationSection("web");
        String siteName = web == null ? "Server Stats" : web.getString("site-name", "Server Stats");
        String address = web == null ? "" : web.getString("server-address", "");
        List<OnlinePlayer> players = new ArrayList<>();
        long afkMinutes = plugin.getConfig().getLong("afk-minutes", 5);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean afk = player.getIdleDuration().toMinutes() >= afkMinutes;
            players.add(new OnlinePlayer(player.getName(),
                    player.getWorld() == null ? "?" : player.getWorld().getName(),
                    player.getGameMode().name(), player.getPing(), afk));
        }
        current = new ServerSnapshot(siteName, address, Bukkit.getMotd(),
                Bukkit.getMinecraftVersion(), players.size(), Bukkit.getMaxPlayers(),
                players, System.currentTimeMillis());
    }
}
