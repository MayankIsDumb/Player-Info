/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Builds all statistics GUIs. Every page is a 54-slot chest whose holder is a
 * {@link StatsMenuHolder}; the listener routes clicks back into {@link #open}.
 * Works for online players (live API) and offline players (stats file) via
 * {@link StatsSource}.
 */
public final class StatsGui {

    private static final int PAGE_SIZE = 45;
    static final int SLOT_BACK = 45;
    static final int SLOT_PREV = 48;
    static final int SLOT_PAGE = 49;
    static final int SLOT_NEXT = 50;

    // Root profile layout: head in the middle, options on the sides.
    static final int SLOT_HEAD = 22;
    static final int SLOT_GENERAL = 10;
    static final int SLOT_BLOCKS = 12;
    static final int SLOT_ITEMS = 14;
    static final int SLOT_MOBS = 16;
    static final int SLOT_COMBAT = 28;
    static final int SLOT_LOGIN = 30;
    static final int SLOT_KILLS = 32;
    static final int SLOT_DEATHS = 34;
    static final int SLOT_DEATH_LOG = 21;
    static final int SLOT_ADVANCEMENTS = 23;

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final ItemStack FILLER = item(Material.BLACK_STAINED_GLASS_PANE,
            Component.text(" ", NamedTextColor.DARK_GRAY), List.of());

    private enum Fmt { COUNT, TICKS, CM, DAMAGE }

    private record GeneralStat(Statistic stat, String label, Material icon, Fmt fmt) {}

    private record Entry(String name, long value, ItemStack icon) {}

    private static final List<GeneralStat> GENERAL_STATS = buildGeneralStats();

    private static List<GeneralStat> buildGeneralStats() {
        List<GeneralStat> stats = new ArrayList<>(List.of(
                new GeneralStat(Statistic.PLAY_ONE_MINUTE, "Play Time", Material.CLOCK, Fmt.TICKS),
                new GeneralStat(Statistic.TOTAL_WORLD_TIME, "Total World Time", Material.COMPASS, Fmt.TICKS),
                new GeneralStat(Statistic.TIME_SINCE_DEATH, "Time Since Death", Material.RED_DYE, Fmt.TICKS),
                new GeneralStat(Statistic.TIME_SINCE_REST, "Time Since Sleep", Material.PHANTOM_MEMBRANE, Fmt.TICKS),
                new GeneralStat(Statistic.SNEAK_TIME, "Sneak Time", Material.GRAY_DYE, Fmt.TICKS),
                new GeneralStat(Statistic.LEAVE_GAME, "Games Quit", Material.OAK_DOOR, Fmt.COUNT),
                new GeneralStat(Statistic.JUMP, "Jumps", Material.RABBIT_FOOT, Fmt.COUNT),
                new GeneralStat(Statistic.DEATHS, "Deaths", Material.SKELETON_SKULL, Fmt.COUNT),
                new GeneralStat(Statistic.MOB_KILLS, "Mob Kills", Material.IRON_SWORD, Fmt.COUNT),
                new GeneralStat(Statistic.PLAYER_KILLS, "Player Kills", Material.PLAYER_HEAD, Fmt.COUNT),
                new GeneralStat(Statistic.DAMAGE_DEALT, "Damage Dealt", Material.DIAMOND_AXE, Fmt.DAMAGE),
                new GeneralStat(Statistic.DAMAGE_TAKEN, "Damage Taken", Material.COBWEB, Fmt.DAMAGE),
                new GeneralStat(Statistic.DAMAGE_ABSORBED, "Damage Absorbed", Material.GOLDEN_APPLE, Fmt.DAMAGE),
                new GeneralStat(Statistic.DROP_COUNT, "Items Dropped", Material.HOPPER, Fmt.COUNT),
                new GeneralStat(Statistic.ANIMALS_BRED, "Animals Bred", Material.WHEAT, Fmt.COUNT),
                new GeneralStat(Statistic.FISH_CAUGHT, "Fish Caught", Material.FISHING_ROD, Fmt.COUNT),
                new GeneralStat(Statistic.RECORD_PLAYED, "Music Discs Played", Material.MUSIC_DISC_CAT, Fmt.COUNT),
                new GeneralStat(Statistic.SLEEP_IN_BED, "Times Slept", Material.RED_BED, Fmt.COUNT),
                new GeneralStat(Statistic.RAID_TRIGGER, "Raids Triggered", Material.WHITE_BANNER, Fmt.COUNT),
                new GeneralStat(Statistic.RAID_WIN, "Raids Won", Material.TOTEM_OF_UNDYING, Fmt.COUNT),
                new GeneralStat(Statistic.TARGET_HIT, "Target Hits", Material.TARGET, Fmt.COUNT),
                new GeneralStat(Statistic.TRADED_WITH_VILLAGER, "Trades Made", Material.EMERALD, Fmt.COUNT),
                new GeneralStat(Statistic.WALK_ONE_CM, "Walked", Material.LEATHER_BOOTS, Fmt.CM),
                new GeneralStat(Statistic.SPRINT_ONE_CM, "Sprinted", Material.SUGAR, Fmt.CM),
                new GeneralStat(Statistic.SWIM_ONE_CM, "Swum", Material.TROPICAL_FISH, Fmt.CM),
                new GeneralStat(Statistic.FLY_ONE_CM, "Flown", Material.ELYTRA, Fmt.CM),
                new GeneralStat(Statistic.AVIATE_ONE_CM, "Elytra Flown", Material.FIREWORK_ROCKET, Fmt.CM),
                new GeneralStat(Statistic.FALL_ONE_CM, "Fallen", Material.FEATHER, Fmt.CM),
                new GeneralStat(Statistic.CLIMB_ONE_CM, "Climbed", Material.LADDER, Fmt.CM),
                new GeneralStat(Statistic.BOAT_ONE_CM, "By Boat", Material.OAK_BOAT, Fmt.CM),
                new GeneralStat(Statistic.MINECART_ONE_CM, "By Minecart", Material.MINECART, Fmt.CM),
                new GeneralStat(Statistic.HORSE_ONE_CM, "By Horse", Material.SADDLE, Fmt.CM),
                new GeneralStat(Statistic.PIG_ONE_CM, "By Pig", Material.CARROT_ON_A_STICK, Fmt.CM),
                new GeneralStat(Statistic.STRIDER_ONE_CM, "By Strider", Material.WARPED_FUNGUS_ON_A_STICK, Fmt.CM),
                new GeneralStat(Statistic.CRAFTING_TABLE_INTERACTION, "Crafting Tables Used", Material.CRAFTING_TABLE, Fmt.COUNT),
                new GeneralStat(Statistic.FURNACE_INTERACTION, "Furnaces Used", Material.FURNACE, Fmt.COUNT),
                new GeneralStat(Statistic.CHEST_OPENED, "Chests Opened", Material.CHEST, Fmt.COUNT),
                new GeneralStat(Statistic.ENDERCHEST_OPENED, "Ender Chests Opened", Material.ENDER_CHEST, Fmt.COUNT),
                new GeneralStat(Statistic.BREWINGSTAND_INTERACTION, "Brewing Stands Used", Material.BREWING_STAND, Fmt.COUNT),
                new GeneralStat(Statistic.BEACON_INTERACTION, "Beacons Used", Material.BEACON, Fmt.COUNT),
                new GeneralStat(Statistic.INTERACT_WITH_ANVIL, "Anvils Used", Material.ANVIL, Fmt.COUNT)));

        // Added in 1.21.6 — resolved by name so older servers simply skip it.
        Statistic happyGhast = statisticOrNull("HAPPY_GHAST_ONE_CM");
        Material harness = Material.matchMaterial("GRAY_HARNESS");
        if (happyGhast != null && harness != null) {
            stats.add(new GeneralStat(happyGhast, "By Happy Ghast", harness, Fmt.CM));
        }
        // Riptide distance — also resolved by name for older APIs.
        Statistic nautilus = statisticOrNull("NAUTILUS_ONE_CM");
        if (nautilus != null) {
            stats.add(new GeneralStat(nautilus, "Riptide Rides", Material.NAUTILUS_SHELL, Fmt.CM));
        }
        return stats;
    }

    private static Statistic statisticOrNull(String name) {
        try {
            return Statistic.valueOf(name);
        } catch (IllegalArgumentException missingOnThisVersion) {
            return null;
        }
    }

    private final PlayerTracker tracker;
    private final FightTracker fightTracker;
    private final DeathStore deathStore;

    public StatsGui(PlayerTracker tracker, FightTracker fightTracker, DeathStore deathStore) {
        this.tracker = tracker;
        this.fightTracker = fightTracker;
        this.deathStore = deathStore;
    }

    public void open(Player viewer, StatsSource target, StatsMenuHolder.Screen screen, StatsList list, int page) {
        open(viewer, target, screen, list, page, null);
    }

    public void open(Player viewer, StatsSource target, StatsMenuHolder.Screen screen, StatsList list, int page, UUID other) {
        if (target.onlinePlayer() != null && !target.isOnline()) {
            viewer.sendMessage(Component.text(target.name() + " is no longer online.", NamedTextColor.RED));
            return;
        }
        switch (screen) {
            case ROOT -> openRoot(viewer, target);
            case GENERAL -> openGeneral(viewer, target, page);
            case ITEMS_SUB -> openSub(viewer, target, StatsMenuHolder.Screen.ITEMS_SUB,
                    List.of(StatsList.CRAFTED, StatsList.USED, StatsList.BROKEN,
                            StatsList.PICKED_UP, StatsList.DROPPED, StatsList.ENCHANTED));
            case MOBS_SUB -> openSub(viewer, target, StatsMenuHolder.Screen.MOBS_SUB,
                    List.of(StatsList.MOBS_KILLED, StatsList.KILLED_BY));
            case LIST -> openList(viewer, target, list, page);
            case COMBAT -> openCombat(viewer, target);
            case LOGIN_INFO -> openLogin(viewer, target);
            case KILLER_PLAYERS -> openPlayerFights(viewer, target, list, page);
            case FIGHT_RECORDS -> openFightRecords(viewer, target, other, list, page);
            case DEATH_LOG -> openDeathLog(viewer, target, page);
            case ADVANCEMENTS -> openAdvancements(viewer, target, page);
        }
    }

    private void openRoot(Player viewer, StatsSource target) {
        StatsMenuHolder holder = new StatsMenuHolder(target, StatsMenuHolder.Screen.ROOT, null, 0);
        Inventory inv = newMenu(holder, "Profile");
        inv.setItem(SLOT_HEAD, head(target));
        inv.setItem(SLOT_GENERAL, button(Material.CLOCK, "General Stats", "All general statistics"));
        inv.setItem(SLOT_BLOCKS, button(Material.GRASS_BLOCK, "Blocks", "Blocks mined"));
        inv.setItem(SLOT_ITEMS, button(Material.CRAFTING_TABLE, "Items",
                "Crafted, used, broken, picked up, dropped, enchanted"));
        inv.setItem(SLOT_MOBS, button(Material.ZOMBIE_HEAD, "Mobs", "Mobs killed and killed by"));
        inv.setItem(SLOT_DEATH_LOG, button(Material.BONE, "Death Log", "Recent deaths with coordinates"));
        AdvancementSource.Summary advancements = AdvancementSource.of(target);
        inv.setItem(SLOT_ADVANCEMENTS, button(Material.NETHER_STAR, "Advancements",
                advancements.done() + " / " + advancements.total() + " completed"));
        inv.setItem(SLOT_COMBAT, button(Material.DIAMOND_AXE, "Combat",
                "Damage dealt, taken, absorbed, critical hits"));
        inv.setItem(SLOT_LOGIN, button(Material.OAK_SIGN, "Login Info",
                target.isOnline() ? "Login time, session length, IP, first join"
                        : "Last seen, first join"));
        inv.setItem(SLOT_KILLS, statIcon(Material.PLAYER_HEAD, "Players Killed",
                target.untyped(Statistic.PLAYER_KILLS), "Click to view player fights"));
        inv.setItem(SLOT_DEATHS, statIcon(Material.SKELETON_SKULL, "Times Died",
                target.untyped(Statistic.DEATHS), "Click for what killed them"));
        viewer.openInventory(inv);
    }

    private void openGeneral(Player viewer, StatsSource target, int page) {
        List<Entry> entries = new ArrayList<>();
        for (GeneralStat gs : GENERAL_STATS) {
            long value = target.untyped(gs.stat());
            entries.add(new Entry(gs.label(), value, item(gs.icon(),
                    Component.text(gs.label(), NamedTextColor.AQUA),
                    List.of(Component.text(valueText(gs, value), NamedTextColor.GOLD)))));
        }
        openPagedList(viewer, target, entries, StatsMenuHolder.Screen.GENERAL, null, "General", page);
    }

    private void openList(Player viewer, StatsSource target, StatsList list, int page) {
        List<Object> keys = new ArrayList<>();
        List<Entry> entries = listEntries(target, list, keys);
        openListPage(viewer, target, entries, keys, StatsMenuHolder.Screen.LIST, list, null, list.title, page);
    }

    private void openPagedList(Player viewer, StatsSource target, List<Entry> entries,
                               StatsMenuHolder.Screen screen, StatsList list, String title, int page) {
        openListPage(viewer, target, entries, null, screen, list, null, title, page);
    }

    private void openListPage(Player viewer, StatsSource target, List<Entry> entries, List<Object> entryKeys,
                              StatsMenuHolder.Screen screen, StatsList list, UUID other, String title, int page) {
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 0), pages - 1);
        StatsMenuHolder holder = new StatsMenuHolder(target, screen, list, current, other);
        holder.entryKeys(entryKeys);
        Inventory inv = newMenu(holder, title);
        if (entries.isEmpty()) {
            String message = screen == StatsMenuHolder.Screen.KILLER_PLAYERS
                    || screen == StatsMenuHolder.Screen.FIGHT_RECORDS
                    ? "No recorded fights yet"
                    : "No statistics recorded yet";
            inv.setItem(SLOT_PAGE, item(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text(message, NamedTextColor.GRAY), List.of()));
        } else {
            int start = current * PAGE_SIZE;
            for (int i = start; i < Math.min(entries.size(), start + PAGE_SIZE); i++) {
                inv.setItem(i - start, entries.get(i).icon());
            }
        }
        inv.setItem(SLOT_BACK, button(Material.BARRIER, "Back", "Return"));
        if (pages > 1) {
            if (current > 0) {
                inv.setItem(SLOT_PREV, button(Material.ARROW, "Previous Page", "Page " + current));
            }
            inv.setItem(SLOT_PAGE, item(Material.PAPER,
                    Component.text("Page " + (current + 1) + " / " + pages, NamedTextColor.GOLD), List.of()));
            if (current < pages - 1) {
                inv.setItem(SLOT_NEXT, button(Material.ARROW, "Next Page", "Page " + (current + 2)));
            }
        }
        viewer.openInventory(inv);
    }

    private void openSub(Player viewer, StatsSource target, StatsMenuHolder.Screen screen,
                         List<StatsList> lists) {
        StatsMenuHolder holder = new StatsMenuHolder(target, screen, null, 0);
        Inventory inv = newMenu(holder, screen == StatsMenuHolder.Screen.ITEMS_SUB ? "Items" : "Mobs");
        int[] slots = screen == StatsMenuHolder.Screen.ITEMS_SUB
                ? new int[]{12, 13, 14, 30, 31, 32}
                : new int[]{21, 23};
        for (int i = 0; i < lists.size(); i++) {
            StatsList list = lists.get(i);
            inv.setItem(slots[i], button(list.icon, list.title, "Click to view"));
        }
        inv.setItem(SLOT_BACK, button(Material.BARRIER, "Back", "Return"));
        viewer.openInventory(inv);
    }

    private void openCombat(Player viewer, StatsSource target) {
        StatsMenuHolder holder = new StatsMenuHolder(target, StatsMenuHolder.Screen.COMBAT, null, 0);
        Inventory inv = newMenu(holder, "Combat");
        inv.setItem(20, statIcon(Material.DIAMOND_AXE, "Damage Dealt",
                damage(target.untyped(Statistic.DAMAGE_DEALT))));
        inv.setItem(24, statIcon(Material.COBWEB, "Damage Taken",
                damage(target.untyped(Statistic.DAMAGE_TAKEN))));
        inv.setItem(29, statIcon(Material.GOLDEN_APPLE, "Damage Absorbed",
                damage(target.untyped(Statistic.DAMAGE_ABSORBED))));
        inv.setItem(33, statIcon(Material.NETHERITE_SWORD, "Critical Hits",
                target.isOnline() ? tracker.criticalHits(target.uuid()) : 0,
                "Vanilla does not track crits;",
                target.isOnline() ? "counted by this plugin since login" : "not tracked for offline players"));
        inv.setItem(SLOT_BACK, button(Material.BARRIER, "Back", "Return"));
        viewer.openInventory(inv);
    }

    private void openLogin(Player viewer, StatsSource target) {
        StatsMenuHolder holder = new StatsMenuHolder(target, StatsMenuHolder.Screen.LOGIN_INFO, null, 0);
        Inventory inv = newMenu(holder, "Login Info");

        if (target.isOnline()) {
            Instant login = tracker.loginTime(target.uuid());
            List<Component> loginLore = login == null
                    ? List.of(Component.text("Unknown (rejoin to track)", NamedTextColor.GRAY))
                    : List.of(
                            Component.text(DATE_TIME.format(login), NamedTextColor.GOLD),
                            Component.text("Session: " + duration(Duration.between(login, Instant.now())),
                                    NamedTextColor.GRAY));
            inv.setItem(20, item(Material.CLOCK, Component.text("Logged In", NamedTextColor.AQUA), loginLore));
        } else {
            long lastSeen = target.player().getLastSeen();
            inv.setItem(20, item(Material.CLOCK, Component.text("Last Seen", NamedTextColor.AQUA),
                    List.of(Component.text(
                            lastSeen > 0 ? DATE_TIME.format(Instant.ofEpochMilli(lastSeen)) : "Unknown",
                            NamedTextColor.GOLD))));
        }

        long firstPlayed = target.player().getFirstPlayed();
        inv.setItem(22, item(Material.WRITABLE_BOOK, Component.text("First Joined", NamedTextColor.AQUA),
                List.of(Component.text(firstPlayed > 0 ? DATE_TIME.format(Instant.ofEpochMilli(firstPlayed)) : "Unknown",
                        NamedTextColor.GOLD))));

        List<Component> ipLore;
        if (target.isOnline()
                && (viewer.getUniqueId().equals(target.uuid())
                        || viewer.hasPermission("playerinfo.stats.others"))) {
            var address = target.onlinePlayer().getAddress();
            ipLore = List.of(Component.text(
                    address == null ? "Unknown" : address.getAddress().getHostAddress(),
                    NamedTextColor.GOLD));
        } else if (!target.isOnline()) {
            ipLore = List.of(Component.text("Unavailable for offline players", NamedTextColor.GRAY));
        } else {
            ipLore = List.of(Component.text("Hidden", NamedTextColor.GRAY));
        }
        inv.setItem(24, item(Material.NAME_TAG, Component.text("IP Address", NamedTextColor.AQUA), ipLore));

        inv.setItem(SLOT_BACK, button(Material.BARRIER, "Back", "Return"));
        viewer.openInventory(inv);
    }

    private List<Entry> listEntries(StatsSource target, StatsList list, List<Object> keys) {
        List<Entry> entries = new ArrayList<>();
        switch (list.kind) {
            case MATERIAL -> {
                boolean blocksOnly = list.statistic == Statistic.MINE_BLOCK;
                for (Material material : Material.values()) {
                    if (material.isAir() || (blocksOnly ? !material.isBlock() : !material.isItem())) {
                        continue;
                    }
                    try {
                        long value = target.material(list, material);
                        if (value <= 0) {
                            continue;
                        }
                        ItemStack icon = statIcon(material, pretty(material.name()), value);
                        keys.add(material);
                        entries.add(new Entry(pretty(material.name()), value, icon));
                    } catch (Exception brokenEntry) {
                        // A single bad material must never kill the whole page.
                    }
                }
            }
            case ENTITY -> {
                for (EntityType type : Registry.ENTITY_TYPE) {
                    try {
                        long value = target.entity(list, type);
                        if (value <= 0) {
                            continue;
                        }
                        ItemStack icon = statIcon(entityIcon(type), pretty(type.name()), value);
                        if (type == EntityType.PLAYER && isPlayerFightList(list)) {
                            icon = withFightHint(icon);
                        }
                        keys.add(type);
                        entries.add(new Entry(pretty(type.name()), value, icon));
                    } catch (Exception brokenEntry) {
                        // A single bad entity must never kill the whole page.
                    }
                }
            }
        }
        entries.sort(Comparator.comparingLong(Entry::value).reversed()
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return entries;
    }

    private static boolean isPlayerFightList(StatsList list) {
        return list.statistic == Statistic.KILL_ENTITY || list.statistic == Statistic.ENTITY_KILLED_BY;
    }

    private static ItemStack withFightHint(ItemStack icon) {
        ItemStack hinted = icon.clone();
        ItemMeta meta = hinted.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.add(Component.text("Click to view recorded fights", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        hinted.setItemMeta(meta);
        return hinted;
    }

    private void openDeathLog(Player viewer, StatsSource target, int page) {
        List<DeathStore.DeathRecord> records = deathStore.deaths(target.uuid());
        List<Entry> entries = new ArrayList<>();
        List<Object> keys = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            DeathStore.DeathRecord record = records.get(i);
            keys.add(i);
            entries.add(new Entry("Death — " + dateTime(record.time()), record.time(),
                    item(Material.BONE, Component.text("Death — " + dateTime(record.time()),
                            NamedTextColor.AQUA), List.of(
                            Component.text(record.cause(), NamedTextColor.GRAY),
                            Component.text(record.world() + " — "
                                    + record.x() + " " + record.y() + " " + record.z(),
                                    NamedTextColor.GOLD),
                            Component.text("Click to copy coordinates", NamedTextColor.DARK_GRAY)))));
        }
        openListPage(viewer, target, entries, keys, StatsMenuHolder.Screen.DEATH_LOG,
                null, null, "Death Log", page);
    }

    private void openAdvancements(Player viewer, StatsSource target, int page) {
        AdvancementSource.Summary summary = AdvancementSource.of(target);
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("Advancements", summary.done(),
                item(Material.NETHER_STAR, Component.text("Advancements", NamedTextColor.AQUA), List.of(
                        Component.text(summary.done() + " / " + summary.total() + " completed",
                                NamedTextColor.GOLD),
                        Component.text("Remaining shown below", NamedTextColor.DARK_GRAY)))));
        for (String key : summary.remainingKeys()) {
            String path = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            entries.add(new Entry(pretty(name), 0,
                    item(Material.BOOK, Component.text(pretty(name), NamedTextColor.WHITE), List.of(
                            Component.text("Not completed yet", NamedTextColor.DARK_GRAY)))));
        }
        openListPage(viewer, target, entries, null, StatsMenuHolder.Screen.ADVANCEMENTS,
                null, null, "Advancements", page);
    }

    private void openPlayerFights(Player viewer, StatsSource target, StatsList originList, int page) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (FightTracker.Fight fight : fightTracker.fightsInvolving(target.uuid())) {
            UUID opponent = fight.winner().equals(target.uuid()) ? fight.loser() : fight.winner();
            counts.merge(opponent, 1, Integer::sum);
        }
        List<Entry> entries = new ArrayList<>();
        List<Object> keys = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(count -> {
                    keys.add(count.getKey());
                    entries.add(new Entry(playerName(count.getKey()), count.getValue(),
                            fightHead(count.getKey(), count.getValue())));
                });
        openListPage(viewer, target, entries, keys, StatsMenuHolder.Screen.KILLER_PLAYERS,
                originList, null, "Player Fights", page);
    }

    private void openFightRecords(Player viewer, StatsSource target, UUID other, StatsList originList, int page) {
        if (other == null) {
            openPlayerFights(viewer, target, originList, page);
            return;
        }
        List<FightTracker.Fight> fights = new ArrayList<>(fightTracker.fightsInvolving(target.uuid()));
        fights.removeIf(fight -> !fight.involves(other));
        fights.sort(Comparator.comparingLong(FightTracker.Fight::startedAt).reversed());
        List<Entry> entries = new ArrayList<>();
        List<Object> keys = new ArrayList<>();
        for (FightTracker.Fight fight : fights) {
            keys.add(fight.id());
            boolean targetWon = fight.winner().equals(target.uuid());
            entries.add(new Entry("Fight #" + fight.id(), fight.id(),
                    item(Material.CLOCK, Component.text("Fight #" + fight.id(), NamedTextColor.AQUA), List.of(
                            Component.text("vs " + playerName(other), NamedTextColor.GRAY),
                            Component.text("Winner: " + playerName(fight.winner()),
                                    targetWon ? NamedTextColor.GREEN : NamedTextColor.RED),
                            Component.text(DATE_TIME.format(Instant.ofEpochMilli(fight.startedAt())),
                                    NamedTextColor.DARK_GRAY),
                            Component.text("Click to open", NamedTextColor.DARK_GRAY)))));
        }
        openListPage(viewer, target, entries, keys, StatsMenuHolder.Screen.FIGHT_RECORDS,
                originList, other, "Fights vs " + playerName(other), page);
    }

    private ItemStack fightHead(UUID player, int fights) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(player));
        meta.displayName(Component.text(playerName(player), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Fights: " + fmt(fights), NamedTextColor.GOLD),
                Component.text("Click to view recorded fights", NamedTextColor.DARK_GRAY)));
        head.setItemMeta(meta);
        return head;
    }

    private static String playerName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    private Inventory newMenu(StatsMenuHolder holder, String title) {
        String suffix = holder.target().isOnline() ? "" : " (offline)";
        Component fullTitle = Component.text(title + " — " + holder.target().name() + suffix,
                NamedTextColor.DARK_GRAY);
        Inventory inv = Bukkit.createInventory(holder, 54, fullTitle);
        holder.inventory(inv);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, FILLER);
        }
        return inv;
    }

    private ItemStack head(StatsSource target) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) stack.getItemMeta();
        meta.setOwningPlayer(target.player());
        meta.displayName(Component.text(target.name(), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (target.isOnline()) {
            Instant login = tracker.loginTime(target.uuid());
            lore.add(Component.text("Session: "
                    + (login == null ? "—" : duration(Duration.between(login, Instant.now()))),
                    NamedTextColor.GRAY));
        } else {
            long lastSeen = target.player().getLastSeen();
            lore.add(Component.text("Offline — last seen "
                    + (lastSeen > 0 ? DATE_TIME.format(Instant.ofEpochMilli(lastSeen)) : "unknown"),
                    NamedTextColor.GRAY));
        }
        lore.add(Component.text("Mob Kills: " + fmt(target.untyped(Statistic.MOB_KILLS)), NamedTextColor.GRAY));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack statIcon(Material icon, String name, long value, String... extraLore) {
        return statIcon(icon, name, fmt(value), extraLore);
    }

    private ItemStack statIcon(Material icon, String name, String value, String... extraLore) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(value, NamedTextColor.GOLD));
        for (String line : extraLore) {
            lore.add(Component.text(line, NamedTextColor.DARK_GRAY));
        }
        return item(icon, Component.text(name, NamedTextColor.AQUA), lore);
    }

    private static Material entityIcon(EntityType type) {
        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
        return egg != null ? egg : Material.SPAWNER;
    }

    static ItemStack button(Material material, String name, String... lore) {
        List<Component> loreComponents = new ArrayList<>();
        for (String line : lore) {
            loreComponents.add(Component.text(line, NamedTextColor.GRAY));
        }
        return item(material, Component.text(name, NamedTextColor.YELLOW), loreComponents);
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static String valueText(GeneralStat stat, long value) {
        return switch (stat.fmt()) {
            case COUNT -> fmt(value);
            case TICKS -> ticks(value);
            case CM -> cm(value);
            case DAMAGE -> damage(value);
        };
    }

    /**
     * Vanilla stores damage in tenths of a health point (1 heart = 2 points
     * = 20 units), so convert to points for display.
     */
    static String damage(long units) {
        double points = units / 10.0;
        double hearts = units / 20.0;
        return wholeOrOneDecimal(points) + " pts (" + wholeOrOneDecimal(hearts) + " hearts)";
    }

    static String wholeOrOneDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.format(Locale.ROOT, "%,d", (long) value);
        }
        return String.format(Locale.ROOT, "%,.1f", value);
    }

    private static String pretty(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    static String fmt(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    static String dateTime(long epochMillis) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    static String ticks(long tickCount) {
        return duration(Duration.ofSeconds(tickCount / 20));
    }

    static String duration(Duration duration) {
        long total = duration.getSeconds();
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    private static String cm(long centimetres) {
        if (centimetres >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1f km", centimetres / 1_000_000.0);
        }
        if (centimetres >= 1_000) {
            return String.format(Locale.ROOT, "%d m", centimetres / 100);
        }
        return centimetres + " cm";
    }
}
