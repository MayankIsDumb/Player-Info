/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Tracks live PvP engagements (which vanilla does not record) and turns PvP
 * deaths into clickable death messages that open the fight summary GUI.
 * Fights start on the first hit between two players, end on a death, and
 * expire after the configured idle timeout without damage. Finished summaries
 * are persisted to {@code plugins/PlayerInfo/fights.json} so the history
 * survives restarts.
 */
public final class FightTracker implements Listener {

    private final File dataFile;
    private final java.util.function.Consumer<Fight> onFightRecorded;
    private boolean trackNewFights;
    private long idleTimeoutMs;
    private int keptSummaries;

    /** Per-player combat counters for one fight. */
    public static final class Side {
        public int hits, hitsTaken, crits, bestCombo, currentCombo, arrowsHit, potsThrown, totems;
        public double damageDealt, damageTaken;
    }

    public static final class Fight {
        private final UUID a;
        private final UUID b;
        private UUID winner;
        private UUID loser;
        private final Map<UUID, Side> sides = new HashMap<>();
        private long startedAt = System.currentTimeMillis();
        private long lastHit = startedAt;
        private int id = -1;

        Fight(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        public Side side(UUID player) {
            return sides.computeIfAbsent(player, key -> new Side());
        }

        Map<UUID, Side> sides() {
            return sides;
        }

        boolean involves(UUID player) {
            return a.equals(player) || b.equals(player);
        }

        public UUID winner() {
            return winner;
        }

        public UUID loser() {
            return loser;
        }

        public int id() {
            return id;
        }

        public long startedAt() {
            return startedAt;
        }

        public String name(UUID player) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
            String name = offline.getName();
            return name != null ? name : player.toString().substring(0, 8);
        }
    }

    private final List<Fight> active = new ArrayList<>();
    private final java.util.concurrent.ConcurrentSkipListMap<Integer, Fight> finished = new java.util.concurrent.ConcurrentSkipListMap<>();
    private final AtomicInteger nextId = new AtomicInteger();

    public FightTracker(File dataFolder, boolean trackNewFights, long idleTimeoutMs, int keptSummaries,
                        java.util.function.Consumer<Fight> onFightRecorded) {
        this.dataFile = new File(dataFolder, "fights.json");
        this.onFightRecorded = onFightRecorded;
        applySettings(trackNewFights, idleTimeoutMs, keptSummaries);
        load();
    }

    /** Applies new settings (from a config reload) without touching saved history. */
    public void applySettings(boolean trackNewFights, long idleTimeoutMs, int keptSummaries) {
        this.trackNewFights = trackNewFights;
        this.idleTimeoutMs = idleTimeoutMs;
        this.keptSummaries = keptSummaries;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!trackNewFights) {
            return;
        }
        Player attacker = attackerOf(event);
        if (!(event.getEntity() instanceof Player victim)
                || attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        sweep();
        Fight fight = active.stream()
                .filter(f -> f.involves(attacker.getUniqueId()) && f.involves(victim.getUniqueId()))
                .findFirst()
                .orElseGet(() -> {
                    Fight created = new Fight(attacker.getUniqueId(), victim.getUniqueId());
                    active.add(created);
                    return created;
                });
        fight.lastHit = System.currentTimeMillis();

        Side atk = fight.side(attacker.getUniqueId());
        Side vic = fight.side(victim.getUniqueId());
        double damage = event.getFinalDamage();
        atk.hits++;
        atk.damageDealt += damage;
        vic.hitsTaken++;
        vic.damageTaken += damage;
        if (event.isCritical()) {
            atk.crits++;
        }
        if (event.getDamager() instanceof Arrow) {
            atk.arrowsHit++;
        }
        atk.currentCombo++;
        atk.bestCombo = Math.max(atk.bestCombo, atk.currentCombo);
        vic.currentCombo = 0;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion)
                || !(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }
        for (Fight fight : fightsOf(shooter.getUniqueId())) {
            fight.side(shooter.getUniqueId()).potsThrown++;
        }
    }

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (Fight fight : fightsOf(player.getUniqueId())) {
            fight.side(player.getUniqueId()).totems++;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        active.removeIf(fight -> fight.involves(event.getPlayer().getUniqueId()));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!trackNewFights) {
            return;
        }
        Player victim = event.getPlayer();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        sweep();
        Fight fight = takeActive(killer.getUniqueId(), victim.getUniqueId());
        if (fight == null) {
            fight = new Fight(killer.getUniqueId(), victim.getUniqueId());
        }
        fight.winner = killer.getUniqueId();
        fight.loser = victim.getUniqueId();
        fight.id = nextId.incrementAndGet();
        finished.put(fight.id, fight);
        while (finished.size() > keptSummaries) {
            finished.remove(finished.keySet().iterator().next());
        }
        save();
        if (onFightRecorded != null) {
            onFightRecorded.accept(fight);
        }
        event.deathMessage(deathMessage(victim, killer, fight));
    }

    /** The finished fight with the given id, or null (ids count up per install). */
    public Fight finished(int id) {
        return finished.get(id);
    }

    /** All kept fights, newest first (safe for web threads). */
    public List<Fight> allFightsNewestFirst() {
        List<Fight> out = new ArrayList<>();
        for (Fight fight : finished.descendingMap().values()) {
            out.add(fight);
        }
        return out;
    }

    /** Finished fights (this session) in which the given player took part. */
    public List<Fight> fightsInvolving(UUID player) {
        List<Fight> out = new ArrayList<>();
        for (Fight fight : finished.values()) {
            if (fight.involves(player)) {
                out.add(fight);
            }
        }
        return out;
    }

    private Component deathMessage(Player victim, Player killer, Fight fight) {
        String command = "fightstats " + fight.id();
        Component killerName = Component.text(killer.getName(), NamedTextColor.YELLOW)
                .hoverEvent(headFor(killer).asHoverEvent())
                .clickEvent(ClickEvent.runCommand(command));
        Component view = Component.text("[View Fight]", NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(
                        Component.text("Click to view the combat summary", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(command));
        return Component.text()
                .append(Component.text(victim.getName(), NamedTextColor.RED))
                .append(Component.text(" was slain by ", NamedTextColor.GRAY))
                .append(killerName)
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(view)
                .build();
    }

    private ItemStack headFor(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(Component.text(player.getName(), NamedTextColor.GOLD));
        head.setItemMeta(meta);
        return head;
    }

    private Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof AbstractArrow arrow
                && arrow.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private List<Fight> fightsOf(UUID player) {
        return active.stream().filter(f -> f.involves(player)).toList();
    }

    private Fight takeActive(UUID killer, UUID victim) {
        for (Fight fight : active) {
            if (fight.involves(killer) && fight.involves(victim)) {
                active.remove(fight);
                return fight;
            }
        }
        return null;
    }

    private void sweep() {
        active.removeIf(f -> System.currentTimeMillis() - f.lastHit > idleTimeoutMs);
    }

    /** Appends every kept summary to plugins/PlayerInfo/fights.json. */
    private void save() {
        try {
            JsonArray array = new JsonArray();
            for (Fight fight : finished.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", fight.id);
                o.addProperty("winner", fight.winner.toString());
                o.addProperty("loser", fight.loser.toString());
                o.addProperty("startedAt", fight.startedAt);
                JsonObject sides = new JsonObject();
                for (Map.Entry<UUID, Side> sideEntry : fight.sides.entrySet()) {
                    Side side = sideEntry.getValue();
                    JsonObject s = new JsonObject();
                    s.addProperty("hits", side.hits);
                    s.addProperty("hitsTaken", side.hitsTaken);
                    s.addProperty("crits", side.crits);
                    s.addProperty("bestCombo", side.bestCombo);
                    s.addProperty("damageDealt", side.damageDealt);
                    s.addProperty("damageTaken", side.damageTaken);
                    s.addProperty("arrowsHit", side.arrowsHit);
                    s.addProperty("potsThrown", side.potsThrown);
                    s.addProperty("totems", side.totems);
                    sides.add(sideEntry.getKey().toString(), s);
                }
                o.add("sides", sides);
                array.add(o);
            }
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(array));
        } catch (Exception saveFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not save fight history: "
                    + saveFailure.getMessage());
        }
    }

    private void load() {
        if (!dataFile.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            int maxId = 0;
            for (JsonElement element : array) {
                JsonObject o = element.getAsJsonObject();
                Fight fight = new Fight(UUID.fromString(o.get("winner").getAsString()),
                        UUID.fromString(o.get("loser").getAsString()));
                fight.winner = fight.a;
                fight.loser = fight.b;
                fight.id = o.get("id").getAsInt();
                fight.startedAt = o.get("startedAt").getAsLong();
                fight.lastHit = fight.startedAt;
                JsonObject sides = o.getAsJsonObject("sides");
                for (var sideEntry : sides.entrySet()) {
                    Side side = fight.side(UUID.fromString(sideEntry.getKey()));
                    JsonObject s = sideEntry.getValue().getAsJsonObject();
                    side.hits = s.get("hits").getAsInt();
                    side.hitsTaken = s.get("hitsTaken").getAsInt();
                    side.crits = s.get("crits").getAsInt();
                    side.bestCombo = s.get("bestCombo").getAsInt();
                    side.damageDealt = s.get("damageDealt").getAsDouble();
                    side.damageTaken = s.get("damageTaken").getAsDouble();
                    side.arrowsHit = s.get("arrowsHit").getAsInt();
                    side.potsThrown = s.get("potsThrown").getAsInt();
                    side.totems = s.get("totems").getAsInt();
                }
                finished.put(fight.id, fight);
                maxId = Math.max(maxId, fight.id);
            }
            nextId.set(maxId + 1);
            Bukkit.getLogger().info("[PlayerInfo] Loaded " + finished.size()
                    + " recorded fight summaries from disk.");
        } catch (Exception loadFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not load fight history: "
                    + loadFailure.getMessage());
        }
    }
}
