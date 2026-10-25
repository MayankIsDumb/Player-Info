/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo;

import dev.negi6.playerinfo.stats.DeathStore;
import dev.negi6.playerinfo.stats.FightStatsCommand;
import dev.negi6.playerinfo.stats.FightTracker;
import dev.negi6.playerinfo.stats.LeaderboardService;
import dev.negi6.playerinfo.stats.OptOutStore;
import dev.negi6.playerinfo.stats.PlayerDataStore;
import dev.negi6.playerinfo.stats.PlayerTracker;
import dev.negi6.playerinfo.stats.ServerData;
import dev.negi6.playerinfo.stats.StatsCommand;
import dev.negi6.playerinfo.stats.StatsGui;
import dev.negi6.playerinfo.stats.StatsGuiListener;
import dev.negi6.playerinfo.stats.TopCommand;
import dev.negi6.playerinfo.web.WebServerService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerInfoPlugin extends JavaPlugin {

    private JoinListener joinListener;
    private FightTracker fightTracker;
    private DeathStore deathStore;
    private LeaderboardService leaderboards;
    private WebServerService webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Capture the world folder on the main thread — background readers
        // (leaderboard scans, website) must not call Bukkit.getWorlds().
        ServerData.init(getServer().getWorldContainer().toPath()
                .resolve(getServer().getWorlds().get(0).getName()));

        PluginManager plugins = getServer().getPluginManager();
        applyJoinListener();

        PluginCommand command = getCommand("playerinfo");
        if (command != null) {
            PlayerInfoCommand handler = new PlayerInfoCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        PlayerTracker tracker = new PlayerTracker();
        plugins.registerEvents(tracker, this);

        deathStore = new DeathStore(getDataFolder());
        plugins.registerEvents(deathStore, this);

        fightTracker = new FightTracker(getDataFolder(),
                getConfig().getBoolean("pvp-fight-tracking", true),
                getConfig().getLong("fight-idle-timeout-seconds", 30) * 1000L,
                getConfig().getInt("keep-fight-summaries", 50));
        if (getConfig().getBoolean("pvp-fight-tracking", true)) {
            plugins.registerEvents(fightTracker, this);
        }

        OptOutStore optOut = new OptOutStore(getDataFolder());
        PlayerDataStore playerData = new PlayerDataStore(getDataFolder());

        leaderboards = new LeaderboardService(this,
                getConfig().getLong("leaderboard-refresh-minutes", 5), optOut);
        leaderboards.start();

        StatsGui gui = new StatsGui(tracker, fightTracker, deathStore, leaderboards, playerData);
        plugins.registerEvents(new StatsGuiListener(this, gui, fightTracker, deathStore), this);

        PluginCommand top = getCommand("top");
        if (top != null) {
            TopCommand topHandler = new TopCommand(gui, leaderboards);
            top.setExecutor(topHandler);
            top.setTabCompleter(topHandler);
        }

        PluginCommand stats = getCommand("stats");
        if (stats != null) {
            StatsCommand statsHandler = new StatsCommand(gui, optOut, playerData);
            stats.setExecutor(statsHandler);
            stats.setTabCompleter(statsHandler);
        }

        PluginCommand fightStats = getCommand("fightstats");
        if (fightStats != null) {
            fightStats.setExecutor(new FightStatsCommand(fightTracker));
        }

        if (getConfig().getBoolean("web.enabled", true)) {
            webServer = new WebServerService(this, gui, fightTracker, leaderboards, optOut, playerData);
            webServer.start();
        }

        getLogger().info("PlayerInfo enabled on " + getServer().getName() + " " + getServer().getVersion());
    }

    /** Re-reads config.yml and applies it without a restart. */
    void reloadSettings() {
        reloadConfig();
        applyJoinListener();
        fightTracker.applySettings(
                getConfig().getBoolean("pvp-fight-tracking", true),
                getConfig().getLong("fight-idle-timeout-seconds", 30) * 1000L,
                getConfig().getInt("keep-fight-summaries", 50));
    }

    private void applyJoinListener() {
        if (joinListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(joinListener);
            joinListener = null;
        }
        if (getConfig().getBoolean("join-welcome-message", true)) {
            joinListener = new JoinListener();
            getServer().getPluginManager().registerEvents(joinListener, this);
        }
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
        }
        if (leaderboards != null) {
            leaderboards.stop();
        }
        getLogger().info("PlayerInfo disabled");
    }
}
