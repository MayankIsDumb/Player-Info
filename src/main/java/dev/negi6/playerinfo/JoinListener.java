/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class JoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text(
                "Welcome! Try /playerinfo <player> to inspect a player.",
                NamedTextColor.GRAY));
    }
}
