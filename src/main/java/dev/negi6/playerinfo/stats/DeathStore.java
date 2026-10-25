/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Records every player death with world, coordinates, cause and time so
 * players can find back lost items. Persisted to
 * {@code plugins/PlayerInfo/deaths.json}, capped per player. Listens at
 * LOWEST priority so the cause is captured before the fight tracker rewrites
 * PvP death messages.
 */
public final class DeathStore implements Listener {

    private static final int MAX_PER_PLAYER = 20;

    private final File dataFile;
    private final Map<UUID, List<DeathRecord>> deaths = new HashMap<>();

    public record DeathRecord(long time, String world, int x, int y, int z, String cause) {}

    public DeathStore(File dataFolder) {
        this.dataFile = new File(dataFolder, "deaths.json");
        load();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location loc = player.getLocation();
        String cause = event.deathMessage() == null ? "Died"
                : PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        DeathRecord record = new DeathRecord(System.currentTimeMillis(),
                loc.getWorld() == null ? "unknown" : loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), cause);
        List<DeathRecord> list = deaths.computeIfAbsent(player.getUniqueId(), key -> new ArrayList<>());
        list.addFirst(record);
        while (list.size() > MAX_PER_PLAYER) {
            list.removeLast();
        }
        save();
    }

    /** Newest first. */
    public List<DeathRecord> deaths(UUID player) {
        return List.copyOf(deaths.getOrDefault(player, List.of()));
    }

    public DeathRecord death(UUID player, int index) {
        List<DeathRecord> list = deaths(player);
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    /** A clickable chat line that copies the coordinates to the clipboard. */
    public Component chatMessage(DeathRecord record) {
        String coords = record.x() + " " + record.y() + " " + record.z();
        return Component.text()
                .append(Component.text("Death — ", NamedTextColor.GRAY))
                .append(Component.text(StatsGui.dateTime(record.time()), NamedTextColor.GOLD))
                .append(Component.text(" in " + record.world() + " at ", NamedTextColor.GRAY))
                .append(Component.text(coords, NamedTextColor.AQUA,
                        net.kyori.adventure.text.format.TextDecoration.UNDERLINED))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Click to copy coordinates", NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.copyToClipboard(coords))
                .build();
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, List<DeathRecord>> entry : deaths.entrySet()) {
                JsonArray array = new JsonArray();
                for (DeathRecord record : entry.getValue()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("time", record.time());
                    o.addProperty("world", record.world());
                    o.addProperty("x", record.x());
                    o.addProperty("y", record.y());
                    o.addProperty("z", record.z());
                    o.addProperty("cause", record.cause());
                    array.add(o);
                }
                root.add(entry.getKey().toString(), array);
            }
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception saveFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not save death log: "
                    + saveFailure.getMessage());
        }
    }

    private void load() {
        if (!dataFile.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : root.entrySet()) {
                List<DeathRecord> list = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    JsonObject o = element.getAsJsonObject();
                    list.add(new DeathRecord(
                            o.get("time").getAsLong(),
                            o.get("world").getAsString(),
                            o.get("x").getAsInt(),
                            o.get("y").getAsInt(),
                            o.get("z").getAsInt(),
                            o.get("cause").getAsString()));
                }
                deaths.put(UUID.fromString(entry.getKey()), list);
            }
            Bukkit.getLogger().info("[PlayerInfo] Loaded death log for " + deaths.size() + " players.");
        } catch (Exception loadFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not load death log: "
                    + loadFailure.getMessage());
        }
    }
}
