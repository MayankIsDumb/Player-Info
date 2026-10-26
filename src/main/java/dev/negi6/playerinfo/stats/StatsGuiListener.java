/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import dev.negi6.playerinfo.PlayerInfoPlugin;

/**
 * Cancels all interaction with stats GUIs and routes clicks on the nav and
 * category buttons back into {@link StatsGui#open}.
 */
public final class StatsGuiListener implements Listener {

    private final PlayerInfoPlugin plugin;
    private final StatsGui gui;
    private final FightTracker tracker;
    private final DeathStore deathStore;

    public StatsGuiListener(PlayerInfoPlugin plugin, StatsGui gui, FightTracker tracker, DeathStore deathStore) {
        this.plugin = plugin;
        this.gui = gui;
        this.tracker = tracker;
        this.deathStore = deathStore;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof FightStatsGui.Holder fightHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player viewer)
                    || event.getClickedInventory() != event.getInventory()) {
                return;
            }
            switch (event.getSlot()) {
                case 3 -> gui.open(viewer, StatsSource.ofUuid(fightHolder.fight().winner()),
                        StatsMenuHolder.Screen.ROOT, null, 0);
                case 5 -> gui.open(viewer, StatsSource.ofUuid(fightHolder.fight().loser()),
                        StatsMenuHolder.Screen.ROOT, null, 0);
                case FightStatsGui.SLOT_BACK ->
                        Bukkit.getScheduler().runTask(plugin, (Runnable) viewer::closeInventory);
                default -> { }
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof StatsMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player viewer)
                || event.getClickedInventory() != event.getInventory()) {
            return;
        }
        StatsSource target = holder.target();
        int slot = event.getSlot();
        switch (holder.screen()) {
            case ROOT -> {
                switch (slot) {
                    case StatsGui.SLOT_GENERAL -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.GENERAL, null, 0);
                    case StatsGui.SLOT_BLOCKS -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.LIST, StatsList.MINED, 0);
                    case StatsGui.SLOT_ITEMS -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.ITEMS_SUB, null, 0);
                    case StatsGui.SLOT_MOBS -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.MOBS_SUB, null, 0);
                    case StatsGui.SLOT_COMBAT -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.COMBAT, null, 0);
                    case StatsGui.SLOT_LOGIN -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.LOGIN_INFO, null, 0);
                    case StatsGui.SLOT_KILLS -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.KILLER_PLAYERS, null, 0, null);
                    case StatsGui.SLOT_DEATHS -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.LIST, StatsList.KILLED_BY, 0);
                    case StatsGui.SLOT_DEATH_LOG -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.DEATH_LOG, null, 0);
                    case StatsGui.SLOT_ADVANCEMENTS -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.ADVANCEMENTS, null, 0);
                    default -> { }
                }
            }
            case GENERAL, COMBAT, LOGIN_INFO -> {
                if (slot == StatsGui.SLOT_BACK) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
                }
            }
            case DEATH_LOG -> {
                if (slot == StatsGui.SLOT_BACK) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
                } else if (slot < 45 && slot < holder.entryKeys().size()
                        && holder.entryKeys().get(slot) instanceof Integer index) {
                    DeathStore.DeathRecord record = deathStore.death(target.uuid(), index);
                    if (record != null) {
                        viewer.sendMessage(deathStore.chatMessage(record));
                    }
                }
            }
            case ADVANCEMENTS -> {
                if (slot == StatsGui.SLOT_BACK) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
                }
            }
            case COMPARE -> {
                if (slot == StatsGui.SLOT_BACK || slot == 3) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
                } else if (slot == 5 && holder.other() != null) {
                    gui.open(viewer, StatsSource.ofUuid(holder.other()),
                            StatsMenuHolder.Screen.ROOT, null, 0);
                }
            }
            case LEADERBOARD -> {
                if (slot == StatsGui.SLOT_BACK) {
                    Bukkit.getScheduler().runTask(plugin, (Runnable) viewer::closeInventory);
                } else if (slot == StatsGui.SLOT_TOGGLE_PERIOD && holder.category() != null) {
                    LeaderboardService.Metric metric = LeaderboardService.parseMetric(holder.category());
                    if (metric != null) {
                        gui.openLeaderboard(viewer, metric, !holder.weekly(), 0);
                    }
                } else if (slot == StatsGui.SLOT_PREV) {
                    gui.openLeaderboard(viewer, LeaderboardService.parseMetric(holder.category()),
                            holder.weekly(), holder.page() - 1);
                } else if (slot == StatsGui.SLOT_NEXT) {
                    gui.openLeaderboard(viewer, LeaderboardService.parseMetric(holder.category()),
                            holder.weekly(), holder.page() + 1);
                } else if (slot < 45 && slot < holder.entryKeys().size()
                        && holder.entryKeys().get(slot) instanceof UUID player) {
                    gui.open(viewer, StatsSource.ofUuid(player),
                            StatsMenuHolder.Screen.ROOT, null, 0);
                }
            }
            case ITEMS_SUB, MOBS_SUB -> {
                if (slot == StatsGui.SLOT_BACK) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
                    return;
                }
                StatsList list = listForSlot(holder.screen(), slot);
                if (list != null) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.LIST, list, 0);
                }
            }
            case LIST -> {
                switch (slot) {
                    case StatsGui.SLOT_BACK -> gui.open(viewer, target,
                            holder.list().parent, null, 0);
                    case StatsGui.SLOT_PREV -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.LIST, holder.list(), holder.page() - 1);
                    case StatsGui.SLOT_NEXT -> gui.open(viewer, target,
                            StatsMenuHolder.Screen.LIST, holder.list(), holder.page() + 1);
                    default -> {
                        if (holder.list() != null && holder.list().kind == StatsList.Kind.ENTITY
                                && slot < 45 && slot < holder.entryKeys().size()
                                && holder.entryKeys().get(slot) instanceof EntityType type
                                && type == EntityType.PLAYER) {
                            gui.open(viewer, target, StatsMenuHolder.Screen.KILLER_PLAYERS,
                                    holder.list(), 0, null);
                        }
                    }
                }
            }
            case KILLER_PLAYERS -> {
                if (slot == StatsGui.SLOT_BACK) {
                    if (holder.list() != null) {
                        gui.open(viewer, target, StatsMenuHolder.Screen.LIST, holder.list(), 0);
                    } else {
                        gui.open(viewer, target, StatsMenuHolder.Screen.ROOT, null, 0);
                    }
                } else if (slot < 45 && slot < holder.entryKeys().size()
                        && holder.entryKeys().get(slot) instanceof UUID opponent) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.FIGHT_RECORDS,
                            holder.list(), 0, opponent);
                }
            }
            case FIGHT_RECORDS -> {
                if (slot == StatsGui.SLOT_BACK) {
                    gui.open(viewer, target, StatsMenuHolder.Screen.KILLER_PLAYERS,
                            holder.list(), 0, holder.other());
                } else if (slot < 45 && slot < holder.entryKeys().size()
                        && holder.entryKeys().get(slot) instanceof Integer fightId) {
                    FightTracker.Fight fight = tracker.finished(fightId);
                    if (fight != null) {
                        FightStatsGui.open(viewer, fight);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StatsMenuHolder
                || event.getInventory().getHolder() instanceof FightStatsGui.Holder) {
            event.setCancelled(true);
        }
    }

    private StatsList listForSlot(StatsMenuHolder.Screen screen, int slot) {
        return switch (screen) {
            case ITEMS_SUB -> switch (slot) {
                case 12 -> StatsList.CRAFTED;
                case 13 -> StatsList.USED;
                case 14 -> StatsList.BROKEN;
                case 30 -> StatsList.PICKED_UP;
                case 31 -> StatsList.DROPPED;
                case 32 -> StatsList.ENCHANTED;
                default -> null;
            };
            case MOBS_SUB -> switch (slot) {
                case 21 -> StatsList.MOBS_KILLED;
                case 23 -> StatsList.KILLED_BY;
                default -> null;
            };
            default -> null;
        };
    }
}
