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
import org.bukkit.entity.Player;

public final class FightStatsCommand implements CommandExecutor {

    private final FightTracker tracker;

    public FightStatsCommand(FightTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text("Only in-game players can open the fight summary.",
                    NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            viewer.sendMessage(Component.text("Usage: /" + label + " <id> (click a death message)",
                    NamedTextColor.RED));
            return true;
        }
        FightTracker.Fight fight;
        try {
            fight = tracker.finished(Integer.parseInt(args[0]));
        } catch (NumberFormatException badId) {
            fight = null;
        }
        if (fight == null) {
            viewer.sendMessage(Component.text("No fight summary #" + args[0]
                    + " (summaries are kept for this session only).", NamedTextColor.RED));
            return true;
        }
        FightStatsGui.open(viewer, fight);
        return true;
    }
}
