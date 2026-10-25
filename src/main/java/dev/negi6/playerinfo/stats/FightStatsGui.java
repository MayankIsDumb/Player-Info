/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Two-panel fight summary styled after duels end-screens: a Winner panel and a
 * Defeated panel, each with Hits, Hits Taken, Crits, Best Combo, Damage Dealt,
 * Damage Taken, Arrows Hit, Pots Thrown and Totems. Opened by clicking the
 * interactive death message or via /fightstats &lt;id&gt;.
 */
public final class FightStatsGui {

    public static final int SLOT_BACK = 45;

    public static final class Holder implements InventoryHolder {
        private final FightTracker.Fight fight;
        private Inventory inventory;

        Holder(FightTracker.Fight fight) {
            this.fight = fight;
        }

        public FightTracker.Fight fight() {
            return fight;
        }

        void inventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record Stat(String label, Material icon, Function<FightTracker.Side, String> value) {}

    private static final List<Stat> FIGHT_STATS = List.of(
            new Stat("Hits", Material.IRON_SWORD, s -> Integer.toString(s.hits)),
            new Stat("Hits Taken", Material.SHIELD, s -> Integer.toString(s.hitsTaken)),
            new Stat("Crits", Material.NETHERITE_SWORD, s -> Integer.toString(s.crits)),
            new Stat("Best Combo", Material.SUGAR, s -> Integer.toString(s.bestCombo)),
            new Stat("Damage Dealt", Material.DIAMOND_AXE, s -> StatsGui.wholeOrOneDecimal(s.damageDealt) + " pts"),
            new Stat("Damage Taken", Material.COBWEB, s -> StatsGui.wholeOrOneDecimal(s.damageTaken) + " pts"),
            new Stat("Arrows Hit", Material.BOW, s -> Integer.toString(s.arrowsHit)),
            new Stat("Pots Thrown", Material.SPLASH_POTION, s -> Integer.toString(s.potsThrown)),
            new Stat("Totems", Material.TOTEM_OF_UNDYING, s -> Integer.toString(s.totems)));

    private record OverallStat(String label, Material icon, Function<StatsSource, String> value) {}

    private static final List<OverallStat> OVERALL_STATS = List.of(
            new OverallStat("Player Kills", Material.PLAYER_HEAD, s -> StatsGui.fmt(s.untyped(org.bukkit.Statistic.PLAYER_KILLS))),
            new OverallStat("Deaths", Material.SKELETON_SKULL, s -> StatsGui.fmt(s.untyped(org.bukkit.Statistic.DEATHS))),
            new OverallStat("Play Time", Material.CLOCK, s -> StatsGui.ticks(s.untyped(org.bukkit.Statistic.PLAY_ONE_MINUTE))));

    private static final ItemStack FILLER = StatsGui.item(Material.BLACK_STAINED_GLASS_PANE,
            Component.text(" ", NamedTextColor.DARK_GRAY), List.of());

    private FightStatsGui() {
    }

    public static void open(Player viewer, FightTracker.Fight fight) {
        Holder holder = new Holder(fight);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text(
                "Fight Summary — " + fight.name(fight.winner()) + " vs " + fight.name(fight.loser()),
                NamedTextColor.DARK_GRAY));
        holder.inventory(inv);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, FILLER);
        }

        inv.setItem(3, head(fight, fight.winner(), "Winner", NamedTextColor.GREEN));
        inv.setItem(5, head(fight, fight.loser(), "Defeated", NamedTextColor.RED));

        FightTracker.Side winner = fight.side(fight.winner());
        FightTracker.Side loser = fight.side(fight.loser());
        StatsSource winnerSource = StatsSource.ofUuid(fight.winner());
        StatsSource loserSource = StatsSource.ofUuid(fight.loser());
        // Winner panel: columns 1-4; loser panel: columns 6-9. 9 fight stats
        // first, then the 3 overall statistics.
        for (int i = 0; i < FIGHT_STATS.size(); i++) {
            Stat stat = FIGHT_STATS.get(i);
            inv.setItem(winnerSlot(i), statItem(stat.icon(), stat.label(),
                    stat.value().apply(winner), NamedTextColor.GREEN));
            inv.setItem(loserSlot(i), statItem(stat.icon(), stat.label(),
                    stat.value().apply(loser), NamedTextColor.RED));
        }
        for (int i = 0; i < OVERALL_STATS.size(); i++) {
            OverallStat stat = OVERALL_STATS.get(i);
            int fightStatCount = FIGHT_STATS.size();
            inv.setItem(winnerSlot(fightStatCount + i), overallItem(stat, winnerSource));
            inv.setItem(loserSlot(fightStatCount + i), overallItem(stat, loserSource));
        }

        inv.setItem(SLOT_BACK, StatsGui.button(Material.BARRIER, "Close", "Return to game"));
        viewer.openInventory(inv);
    }

    /** Winner panel occupies columns 1-4, the loser panel columns 6-9. */
    private static int winnerSlot(int statIndex) {
        int row = statIndex / 4;
        int col = statIndex % 4;
        return (row + 1) * 9 + col;
    }

    private static int loserSlot(int statIndex) {
        return winnerSlot(statIndex) + 5;
    }

    private static ItemStack overallItem(OverallStat stat, StatsSource source) {
        return StatsGui.item(stat.icon(),
                Component.text(stat.label(), NamedTextColor.AQUA),
                List.of(Component.text(stat.value().apply(source), NamedTextColor.GOLD),
                        Component.text("Overall statistics", NamedTextColor.DARK_GRAY)));
    }

    private static ItemStack statItem(Material icon, String label, String value, NamedTextColor valueColor) {
        return StatsGui.item(icon,
                Component.text(label, NamedTextColor.AQUA),
                List.of(Component.text(value, valueColor)));
    }

    private static ItemStack head(FightTracker.Fight fight, UUID player, String title, NamedTextColor color) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(player));
        meta.displayName(Component.text(title + ": " + fight.name(player), color));
        meta.lore(List.of(Component.text("Click to view their full statistics", NamedTextColor.GRAY)));
        head.setItemMeta(meta);
        return head;
    }
}
