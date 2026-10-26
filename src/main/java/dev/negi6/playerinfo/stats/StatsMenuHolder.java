/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks and describes one of our stats GUIs. The listener identifies our
 * inventories by {@code getHolder() instanceof StatsMenuHolder} and reads the
 * state from here, so menu titles never need to be parsed.
 */
public final class StatsMenuHolder implements InventoryHolder {

    public enum Screen { ROOT, GENERAL, ITEMS_SUB, MOBS_SUB, LIST, COMBAT, LOGIN_INFO, KILLER_PLAYERS, FIGHT_RECORDS, DEATH_LOG, ADVANCEMENTS, LEADERBOARD, COMPARE }

    private final StatsSource target;
    private final Screen screen;
    private final StatsList list;
    private final int page;
    private final UUID other;
    private List<Object> entryKeys = List.of();
    private Inventory inventory;

    public StatsMenuHolder(StatsSource target, Screen screen, StatsList list, int page) {
        this(target, screen, list, page, null);
    }

    public StatsMenuHolder(StatsSource target, Screen screen, StatsList list, int page, UUID other) {
        this.target = target;
        this.screen = screen;
        this.list = list;
        this.page = page;
        this.other = other;
    }

    public StatsSource target() {
        return target;
    }

    public Screen screen() {
        return screen;
    }

    public StatsList list() {
        return list;
    }

    public int page() {
        return page;
    }

    /** The other participant for KILLER_PLAYERS / FIGHT_RECORDS screens. */
    public UUID other() {
        return other;
    }

    /** Leaderboard category (metric name) for the LEADERBOARD screen. */
    private String category;
    private boolean weekly;

    public String category() {
        return category;
    }

    void category(String category) {
        this.category = category;
    }

    public boolean weekly() {
        return weekly;
    }

    void weekly(boolean weekly) {
        this.weekly = weekly;
    }

    /** Per-slot keys for clickable entries (EntityType, UUID or fight id). */
    public List<Object> entryKeys() {
        return entryKeys;
    }

    void entryKeys(List<Object> entryKeys) {
        this.entryKeys = entryKeys == null ? List.of() : entryKeys;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
