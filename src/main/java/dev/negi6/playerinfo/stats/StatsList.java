/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import org.bukkit.Material;
import org.bukkit.Statistic;

/**
 * The per-entry (substatistic) pages: each shows one value per Material or
 * EntityType. {@code parent} is the screen the back button returns to;
 * {@code jsonGroup} is the vanilla stats-file group for offline players.
 */
enum StatsList {

    MINED(Statistic.MINE_BLOCK, Kind.MATERIAL, Material.GRASS_BLOCK,
            "Blocks Mined", StatsMenuHolder.Screen.ROOT, "mined"),

    CRAFTED(Statistic.CRAFT_ITEM, Kind.MATERIAL, Material.CRAFTING_TABLE,
            "Items Crafted", StatsMenuHolder.Screen.ITEMS_SUB, "crafted"),
    USED(Statistic.USE_ITEM, Kind.MATERIAL, Material.IRON_PICKAXE,
            "Items Used", StatsMenuHolder.Screen.ITEMS_SUB, "used"),
    BROKEN(Statistic.BREAK_ITEM, Kind.MATERIAL, Material.WOODEN_PICKAXE,
            "Items Broken", StatsMenuHolder.Screen.ITEMS_SUB, "broken"),
    PICKED_UP(Statistic.PICKUP, Kind.MATERIAL, Material.HOPPER,
            "Items Picked Up", StatsMenuHolder.Screen.ITEMS_SUB, "picked_up"),
    DROPPED(Statistic.DROP, Kind.MATERIAL, Material.DISPENSER,
            "Items Dropped", StatsMenuHolder.Screen.ITEMS_SUB, "dropped"),
    ENCHANTED(Statistic.ITEM_ENCHANTED, Kind.MATERIAL, Material.ENCHANTING_TABLE,
            "Items Enchanted", StatsMenuHolder.Screen.ITEMS_SUB, "enchanted"),

    MOBS_KILLED(Statistic.KILL_ENTITY, Kind.ENTITY, Material.DIAMOND_SWORD,
            "Mobs Killed", StatsMenuHolder.Screen.MOBS_SUB, "killed"),
    KILLED_BY(Statistic.ENTITY_KILLED_BY, Kind.ENTITY, Material.SKELETON_SKULL,
            "Killed By", StatsMenuHolder.Screen.MOBS_SUB, "killed_by");

    enum Kind { MATERIAL, ENTITY }

    final Statistic statistic;
    final Kind kind;
    final Material icon;
    final String title;
    final StatsMenuHolder.Screen parent;
    final String jsonGroup;

    StatsList(Statistic statistic, Kind kind, Material icon, String title,
              StatsMenuHolder.Screen parent, String jsonGroup) {
        this.statistic = statistic;
        this.kind = kind;
        this.icon = icon;
        this.title = title;
        this.parent = parent;
        this.jsonGroup = jsonGroup;
    }
}
