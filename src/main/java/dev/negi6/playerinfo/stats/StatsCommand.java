/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import dev.negi6.playerinfo.messages.Messages;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    private final PlayerDataStore playerData;
    private final Messages messages;

    public StatsCommand(StatsGui gui, PlayerDataStore playerData, Messages messages) {
        this.gui = gui;
        this.playerData = playerData;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(messages.chat("general.players-only"));
            return true;
        }
        if (args.length >= 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "setbio" -> {
                    String bio = join(args, 100);
                    playerData.set(viewer.getUniqueId(), PlayerDataStore.BIO, bio);
                    viewer.sendMessage(messages.chat(bio == null ? "stats.setbio-cleared" : "stats.setbio-updated"));
                    return true;
                }
                case "setfav" -> {
                    String favorite = join(args, 40);
                    playerData.set(viewer.getUniqueId(), PlayerDataStore.FAVORITE, favorite);
                    viewer.sendMessage(messages.chat(favorite == null ? "stats.setfav-cleared" : "stats.setfav-updated"));
                    return true;
                }
                case "setbase" -> {
                    Location loc = viewer.getLocation();
                    String base = loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ()
                            + " (" + loc.getWorld().getName() + ")";
                    playerData.set(viewer.getUniqueId(), PlayerDataStore.BASE, base);
                    viewer.sendMessage(messages.chat("stats.setbase-saved", "base", base));
                    return true;
                }
                case "compare" -> {
                    if (args.length != 2) {
                        viewer.sendMessage(messages.raw("general.usage-stats-compare"));
                        return true;
                    }
                    if (!viewer.hasPermission("playerinfo.stats.others")) {
                        viewer.sendMessage(messages.chat("general.compare-no-permission"));
                        return true;
                    }
                    StatsSource other = resolve(args[1]);
                    if (other == null) {
                        viewer.sendMessage(messages.chat("general.unknown-offline-player", "name", args[1]));
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
                viewer.sendMessage(messages.chat("general.stats-no-permission"));
                return true;
            }
            gui.open(viewer, StatsSource.of(viewer), StatsMenuHolder.Screen.ROOT, null, 0);
            return true;
        }
        if (!viewer.hasPermission("playerinfo.stats.others")) {
            viewer.sendMessage(messages.chat("general.self-stats-only"));
            return true;
        }
        StatsSource target = resolve(args[0]);
        if (target == null) {
            viewer.sendMessage(messages.chat("general.unknown-offline-player", "name", args[0]));
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
        for (String sub : List.of("compare", "setbio", "setfav", "setbase")) {
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
