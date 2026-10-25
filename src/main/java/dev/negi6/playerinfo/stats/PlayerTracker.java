/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Session-scoped data vanilla does not track: when each player logged in and
 * how many critical hits they landed. Cleared when the player quits.
 */
public final class PlayerTracker implements Listener {

    private final Map<UUID, Instant> loginTimes = new HashMap<>();
    private final Map<UUID, Integer> criticalHits = new HashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loginTimes.put(event.getPlayer().getUniqueId(), Instant.now());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        loginTimes.remove(uuid);
        criticalHits.remove(uuid);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!event.isCritical()) {
            return;
        }
        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof AbstractArrow arrow
                && arrow.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker != null) {
            criticalHits.merge(attacker.getUniqueId(), 1, Integer::sum);
        }
    }

    public Instant loginTime(UUID uuid) {
        return loginTimes.get(uuid);
    }

    public int criticalHits(UUID uuid) {
        return criticalHits.getOrDefault(uuid, 0);
    }
}
