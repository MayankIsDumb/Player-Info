/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;

public final class TopCommand implements CommandExecutor, TabCompleter {

    private static final List<String> CATEGORIES = List.of(
            "playtime", "blocks", "mobs", "distance", "deaths", "kills", "kd");

    private final StatsGui gui;
    private final LeaderboardService leaderboards;

    public TopCommand(StatsGui gui, LeaderboardService leaderboards) {
        this.gui = gui;
        this.leaderboards = leaderboards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Only in-game players can open the leaderboard.",
                    NamedTextColor.RED));
            return true;
        }
        LeaderboardService.Metric metric = parse(args.length == 0 ? "playtime" : args[0]);
        if (metric == null) {
            viewer.sendMessage(Component.text("Unknown category '" + args[0]
                    + "'. Categories: " + String.join(", ", CATEGORIES), NamedTextColor.RED));
            return true;
        }
        if (!leaderboards.isReady()) {
            viewer.sendMessage(Component.text("Leaderboards are still being built — try again in a minute.",
                    NamedTextColor.YELLOW));
            return true;
        }
        gui.openLeaderboard(viewer, metric, 0);
        return true;
    }

    private LeaderboardService.Metric parse(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "playtime", "time" -> LeaderboardService.Metric.PLAYTIME;
            case "blocks", "mined" -> LeaderboardService.Metric.BLOCKS;
            case "mobs" -> LeaderboardService.Metric.MOBS;
            case "distance" -> LeaderboardService.Metric.DISTANCE;
            case "deaths" -> LeaderboardService.Metric.DEATHS;
            case "kills" -> LeaderboardService.Metric.KILLS;
            case "kd" -> LeaderboardService.Metric.KD;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return CATEGORIES.stream().filter(c -> c.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
