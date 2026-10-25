/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class StatsCommand implements CommandExecutor, TabCompleter {

    private final StatsGui gui;
    private final OptOutStore optOut;
    private final PlayerDataStore playerData;

    public StatsCommand(StatsGui gui, OptOutStore optOut, PlayerDataStore playerData) {
        this.gui = gui;
        this.optOut = optOut;
        this.playerData = playerData;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Only in-game players can use this command.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "hide" -> {
                    boolean nowHidden = optOut.toggle(viewer.getUniqueId());
                    viewer.sendMessage(Component.text(nowHidden
                            ? "Your statistics are now hidden from the website."
                            : "Your statistics are visible on the website again.",
                            nowHidden ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
                    return true;
                }
                case "setbio" -> {
                    playerData.set(viewer.getUniqueId(), PlayerDataStore.BIO, join(args, 100));
                    viewer.sendMessage(Component.text(
                            join(args, 100) == null ? "Bio cleared." : "Bio updated.",
                            NamedTextColor.GREEN));
                    return true;
                }
                case "setfav" -> {
                    playerData.set(viewer.getUniqueId(), PlayerDataStore.FAVORITE, join(args, 40));
                    viewer.sendMessage(Component.text(
                            join(args, 40) == null ? "Favorite cleared." : "Favorite updated.",
                            NamedTextColor.GREEN));
                    return true;
                }
                case "setbase" -> {
                    Location loc = viewer.getLocation();
                    String base = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                            + " (" + loc.getWorld().getName() + ")";
                    playerData.set(viewer.getUniqueId(), PlayerDataStore.BASE, base);
                    viewer.sendMessage(Component.text("Base location saved: " + base,
                            NamedTextColor.GREEN));
                    return true;
                }
                case "compare" -> {
                    if (args.length != 2) {
                        viewer.sendMessage(Component.text("Usage: /stats compare <player>",
                                NamedTextColor.RED));
                        return true;
                    }
                    if (!viewer.hasPermission("playerinfo.stats.others")) {
                        viewer.sendMessage(Component.text(
                                "You don't have permission to compare with other players.",
                                NamedTextColor.RED));
                        return true;
                    }
                    StatsSource other = resolve(args[1]);
                    if (other == null) {
                        viewer.sendMessage(Component.text("No player named '" + args[1]
                                + "' has played on this server.", NamedTextColor.RED));
                        return true;
                    }
                    gui.openCompare(viewer, StatsSource.of(viewer), other);
                    return true;
                }
                default -> { }
            }
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
        StatsSource target = resolve(args[0]);
        if (target == null) {
            viewer.sendMessage(Component.text("No player named '" + args[0]
                    + "' has played on this server.", NamedTextColor.RED));
            return true;
        }
        gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
        return true;
    }

    private StatsSource resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return StatsSource.of(online);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null || !offline.hasPlayedBefore()) {
            return null;
        }
        return StatsSource.offline(offline);
    }

    /** Joins args[1..] with spaces, capped to maxLength; null when empty. */
    private static String join(String[] args, int maxLength) {
        if (args.length < 2) {
            return null;
        }
        String joined = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        return joined.length() > maxLength ? joined.substring(0, maxLength) : joined;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String sub : List.of("hide", "compare", "setbio", "setfav", "setbase")) {
            if (sub.startsWith(prefix)) {
                out.add(sub);
            }
        }
        if (sender.hasPermission("playerinfo.stats.others")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
            for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                String name = offline.getName();
                if (name != null && offline.hasPlayedBefore()
                        && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(name);
                }
            }
        }
        return out.stream().distinct().sorted().toList();
    }
}
