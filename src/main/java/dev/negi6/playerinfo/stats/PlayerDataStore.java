/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Per-player profile personalization (bio, favorite activity, base
 * coordinates) set with /playerinfo setbio|setfav|setbase, persisted to
 * {@code plugins/PlayerInfo/playerdata.json} and shown in the GUI.
 */
public final class PlayerDataStore {

    public static final String BIO = "bio";
    public static final String FAVORITE = "favorite";
    public static final String BASE = "base";

    private final File dataFile;
    private final Map<UUID, JsonObject> data = new HashMap<>();

    public PlayerDataStore(File dataFolder) {
        this.dataFile = new File(dataFolder, "playerdata.json");
        load();
    }

    public String get(UUID player, String key) {
        JsonObject o = data.get(player);
        return o != null && o.has(key) && !o.get(key).isJsonNull()
                ? o.get(key).getAsString() : null;
    }

    public void set(UUID player, String key, String value) {
        JsonObject o = data.computeIfAbsent(player, k -> new JsonObject());
        if (value == null || value.isEmpty()) {
            o.remove(key);
        } else {
            o.addProperty(key, value);
        }
        save();
    }

    private void save() {
        try {
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(data));
        } catch (Exception saveFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not save player data: "
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
                data.put(UUID.fromString(entry.getKey()), entry.getValue().getAsJsonObject());
            }
        } catch (Exception loadFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not load player data: "
                    + loadFailure.getMessage());
        }
    }
}
