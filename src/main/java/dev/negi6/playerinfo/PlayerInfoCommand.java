/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PlayerInfoCommand implements CommandExecutor, TabCompleter {

    private final PlayerInfoPlugin plugin;

    public PlayerInfoCommand(PlayerInfoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("playerinfo.reload")) {
                sender.sendMessage(Component.text("You don't have permission to reload PlayerInfo.",
                        NamedTextColor.RED));
                return true;
            }
            plugin.reloadSettings();
            sender.sendMessage(Component.text("PlayerInfo configuration reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /" + label + " <player>", NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("No online player named '" + args[0] + "'.", NamedTextColor.RED));
            return true;
        }

        Location loc = target.getLocation();
        sender.sendMessage(Component.text("--- " + target.getName() + " ---", NamedTextColor.GOLD));
        sendInfo(sender, "UUID", target.getUniqueId().toString());
        sendInfo(sender, "World", target.getWorld().getName());
        sendInfo(sender, "Location", String.format(Locale.ROOT, "%d %d %d",
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        sendInfo(sender, "Health", Math.round(target.getHealth()) + " HP");
        sendInfo(sender, "Food", target.getFoodLevel() + "/20");
        sendInfo(sender, "Game mode", target.getGameMode().name());
        sendInfo(sender, "Ping", target.getPing() + " ms");
        return true;
    }

    private void sendInfo(CommandSender sender, String key, String value) {
        sender.sendMessage(Component.text(key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (sender.hasPermission("playerinfo.reload")
                && "reload".startsWith(prefix)) {
            out.add("reload");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(player.getName());
            }
        }
        return out;
    }
}
