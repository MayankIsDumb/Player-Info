/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.discord;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import org.bukkit.Bukkit;

/**
 * Tracks which playtime milestones (hours) have already been announced per
 * player, persisted to {@code plugins/PlayerInfo/milestones.json}.
 */
public final class MilestoneStore {

    private final File dataFile;
    private final Map<UUID, List<Long>> announced = new HashMap<>();

    public MilestoneStore(File dataFolder) {
        this.dataFile = new File(dataFolder, "milestones.json");
        load();
    }

    public boolean announced(UUID player, long hours) {
        List<Long> list = announced.get(player);
        return list != null && list.contains(hours);
    }

    public void mark(UUID player, long hours) {
        announced.computeIfAbsent(player, key -> new ArrayList<>()).add(hours);
        save();
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            for (Map.Entry<UUID, List<Long>> entry : announced.entrySet()) {
                JsonArray array = new JsonArray();
                for (Long hours : entry.getValue()) {
                    array.add(hours);
                }
                root.add(entry.getKey().toString(), array);
            }
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception saveFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not save milestones: "
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
                List<Long> hours = new ArrayList<>();
                for (var element : entry.getValue().getAsJsonArray()) {
                    hours.add(element.getAsLong());
                }
                announced.put(UUID.fromString(entry.getKey()), hours);
            }
        } catch (Exception loadFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not load milestones: "
                    + loadFailure.getMessage());
        }
    }
}
