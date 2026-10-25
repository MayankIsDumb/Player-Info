package dev.negi6.playerinfo;

import dev.negi6.playerinfo.stats.FightStatsCommand;
import dev.negi6.playerinfo.stats.FightTracker;
import dev.negi6.playerinfo.stats.PlayerTracker;
import dev.negi6.playerinfo.stats.StatsCommand;
import dev.negi6.playerinfo.stats.StatsGui;
import dev.negi6.playerinfo.stats.StatsGuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerInfoPlugin extends JavaPlugin {

    private JoinListener joinListener;
    private FightTracker fightTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

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

        fightTracker = new FightTracker(getDataFolder(),
                getConfig().getBoolean("pvp-fight-tracking", true),
                getConfig().getLong("fight-idle-timeout-seconds", 30) * 1000L,
                getConfig().getInt("keep-fight-summaries", 50));
        if (getConfig().getBoolean("pvp-fight-tracking", true)) {
            plugins.registerEvents(fightTracker, this);
        }

        StatsGui gui = new StatsGui(tracker, fightTracker);
        plugins.registerEvents(new StatsGuiListener(this, gui, fightTracker), this);

        PluginCommand stats = getCommand("stats");
        if (stats != null) {
            StatsCommand statsHandler = new StatsCommand(gui);
            stats.setExecutor(statsHandler);
            stats.setTabCompleter(statsHandler);
        }

        PluginCommand fightStats = getCommand("fightstats");
        if (fightStats != null) {
            fightStats.setExecutor(new FightStatsCommand(fightTracker));
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
        getLogger().info("PlayerInfo disabled");
    }
}
