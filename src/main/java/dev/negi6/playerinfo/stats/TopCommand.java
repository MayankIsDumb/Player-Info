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
        boolean weekly = args.length >= 1 && args[0].equalsIgnoreCase("weekly");
        String categoryName = weekly ? (args.length >= 2 ? args[1] : "playtime")
                : (args.length >= 1 ? args[0] : "playtime");
        LeaderboardService.Metric metric = parse(categoryName);
        if (metric == null) {
            viewer.sendMessage(Component.text("Unknown category '" + categoryName
                    + "'. Categories: " + String.join(", ", CATEGORIES), NamedTextColor.RED));
            return true;
        }
        if (!leaderboards.isReady()) {
            viewer.sendMessage(Component.text("Leaderboards are still being built — try again in a minute.",
                    NamedTextColor.YELLOW));
            return true;
        }
        gui.openLeaderboard(viewer, metric, weekly, 0);
        return true;
    }

    private LeaderboardService.Metric parse(String name) {
        return LeaderboardService.parseMetric(name);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new java.util.ArrayList<>();
            if ("weekly".startsWith(prefix)) {
                out.add("weekly");
            }
            for (String category : CATEGORIES) {
                if (category.startsWith(prefix)) {
                    out.add(category);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("weekly")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return CATEGORIES.stream().filter(c -> c.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
