/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class StatsCommand implements CommandExecutor, TabCompleter {

    private final StatsGui gui;

    public StatsCommand(StatsGui gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Only in-game players can open the stats GUI.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            if (!viewer.hasPermission("playerinfo.stats")) {
                viewer.sendMessage(Component.text("You don't have permission to view statistics.",
                        NamedTextColor.RED));
                return true;
            }
            gui.open(viewer, StatsSource.of(viewer), StatsMenuHolder.Screen.ROOT, null, 0);
            return true;
        }
        if (!viewer.hasPermission("playerinfo.stats.others")) {
            viewer.sendMessage(Component.text("You can only view your own statistics.",
                    NamedTextColor.RED));
            return true;
        }

        String name = args[0];
        Player online = Bukkit.getPlayerExact(name);
        StatsSource target;
        if (online != null) {
            target = StatsSource.of(online);
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
            if (offline == null || !offline.hasPlayedBefore()) {
                viewer.sendMessage(Component.text(
                        "No player named '" + name + "' has played on this server.", NamedTextColor.RED));
                return true;
            }
            target = StatsSource.offline(offline);
        }
        gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1
                || !sender.hasPermission("playerinfo.stats.others")
                || !(sender instanceof Player)) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && offline.hasPlayedBefore()) {
                names.add(name);
            }
        }
        return names.stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct()
                .sorted()
                .toList();
    }
}
