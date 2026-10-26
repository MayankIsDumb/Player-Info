/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo;

import dev.negi6.playerinfo.discord.DiscordService;
import dev.negi6.playerinfo.hooks.PlayerInfoExpansion;
import dev.negi6.playerinfo.messages.Messages;
import dev.negi6.playerinfo.stats.AdvancementSource;
import dev.negi6.playerinfo.stats.DeathStore;
import dev.negi6.playerinfo.stats.FightStatsCommand;
import dev.negi6.playerinfo.stats.FightTracker;
import dev.negi6.playerinfo.stats.LeaderboardService;
import dev.negi6.playerinfo.stats.PlayerDataStore;
import dev.negi6.playerinfo.stats.PlaytimeRanks;
import dev.negi6.playerinfo.stats.PlayerTracker;
import dev.negi6.playerinfo.stats.ServerData;
import dev.negi6.playerinfo.stats.StatsCommand;
import dev.negi6.playerinfo.stats.StatsGui;
import dev.negi6.playerinfo.stats.StatsGuiListener;
import dev.negi6.playerinfo.stats.TopCommand;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerInfoPlugin extends JavaPlugin {

    private JoinListener joinListener;
    private FightTracker fightTracker;
    private DeathStore deathStore;
    private LeaderboardService leaderboards;
    private DiscordService discord;
    private PlaytimeRanks playtimeRanks;
    private PlayerDataStore playerData;
    private StatsGui gui;
    private Messages messages;

    public Messages messages() {
        return messages;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Capture the world folder on the main thread — background readers
        // (leaderboard scans) must not call Bukkit.getWorlds().
        ServerData.init(getServer().getWorldContainer().toPath()
                .resolve(getServer().getWorlds().get(0).getName()));
        AdvancementSource.init();

        messages = new Messages(this);
        PluginManager plugins = getServer().getPluginManager();
        applyJoinListener();

        playerData = new PlayerDataStore(getDataFolder());
        playtimeRanks = new PlaytimeRanks(this, getDataFolder());
        playtimeRanks.start();

        PluginCommand command = getCommand("playerinfo");
        if (command != null) {
            PlayerInfoCommand handler = new PlayerInfoCommand(this, playtimeRanks);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        PlayerTracker tracker = new PlayerTracker();
        plugins.registerEvents(tracker, this);

        discord = new DiscordService(this, getDataFolder());
        discord.start();

        deathStore = new DeathStore(getDataFolder(), (uuid, record) -> {
            String name = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
            discord.postDeath(name != null ? name : uuid.toString(), record);
        }, messages);
        plugins.registerEvents(deathStore, this);

        fightTracker = new FightTracker(getDataFolder(),
                getConfig().getBoolean("pvp-fight-tracking", true),
                getConfig().getLong("fight-idle-timeout-seconds", 30) * 1000L,
                getConfig().getInt("keep-fight-summaries", 50),
                discord::postFight, messages);
        if (getConfig().getBoolean("pvp-fight-tracking", true)) {
            plugins.registerEvents(fightTracker, this);
        }

        leaderboards = new LeaderboardService(this,
                getConfig().getLong("leaderboard-refresh-minutes", 5), getDataFolder());
        leaderboards.start();

        gui = new StatsGui(tracker, fightTracker, deathStore, leaderboards, playerData);
        plugins.registerEvents(new StatsGuiListener(this, gui, fightTracker, deathStore), this);

        PluginCommand top = getCommand("top");
        if (top != null) {
            TopCommand topHandler = new TopCommand(gui, leaderboards);
            top.setExecutor(topHandler);
            top.setTabCompleter(topHandler);
        }

        PluginCommand stats = getCommand("stats");
        if (stats != null) {
            StatsCommand statsHandler = new StatsCommand(gui, playerData, messages);
            stats.setExecutor(statsHandler);
            stats.setTabCompleter(statsHandler);
        }

        PluginCommand fightStats = getCommand("fightstats");
        if (fightStats != null) {
            fightStats.setExecutor(new FightStatsCommand(fightTracker));
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlayerInfoExpansion(this, playtimeRanks, leaderboards, fightTracker).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("PlayerInfo enabled on " + getServer().getName() + " " + getServer().getVersion());
    }

    /** Re-reads config.yml and applies it without a restart. */
    void reloadSettings() {
        reloadConfig();
        messages.reload();
        applyJoinListener();
        fightTracker.applySettings(
                getConfig().getBoolean("pvp-fight-tracking", true),
                getConfig().getLong("fight-idle-timeout-seconds", 30) * 1000L,
                getConfig().getInt("keep-fight-summaries", 50));
        discord.reload();
        playtimeRanks.reload();
        leaderboards.reload(getConfig().getLong("leaderboard-refresh-minutes", 5));
    }

    private void applyJoinListener() {
        if (joinListener != null) {
            org.bukkit.event.HandlerList.unregisterAll(joinListener);
            joinListener = null;
        }
        if (getConfig().getBoolean("join-welcome-message", true)) {
            joinListener = new JoinListener(messages);
            getServer().getPluginManager().registerEvents(joinListener, this);
        }
    }

    @Override
    public void onDisable() {
        if (leaderboards != null) {
            leaderboards.stop();
        }
        if (playtimeRanks != null) {
            playtimeRanks.stop();
        }
        if (discord != null) {
            discord.stop();
        }
        getLogger().info("PlayerInfo disabled");
    }
}
