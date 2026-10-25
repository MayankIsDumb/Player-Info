/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.GsonBuilder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Players who opted out of the public website via /stats hide. Their stats
 * are excluded from leaderboards, search and the website API.
 */
public final class OptOutStore {

    private final File dataFile;
    private final Set<UUID> hidden = new HashSet<>();

    public OptOutStore(File dataFolder) {
        this.dataFile = new File(dataFolder, "hidden.json");
        load();
    }

    public boolean isHidden(UUID player) {
        return hidden.contains(player);
    }

    /** @return true if the player is now hidden, false if now visible. */
    public boolean toggle(UUID player) {
        boolean nowHidden;
        if (hidden.contains(player)) {
            hidden.remove(player);
            nowHidden = false;
        } else {
            hidden.add(player);
            nowHidden = true;
        }
        save();
        return nowHidden;
    }

    private void save() {
        try {
            JsonObject root = new JsonObject();
            JsonArray array = new JsonArray();
            for (UUID uuid : hidden) {
                array.add(uuid.toString());
            }
            root.add("hidden", array);
            dataFile.getParentFile().mkdirs();
            Files.writeString(dataFile.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception saveFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not save the opt-out list: "
                    + saveFailure.getMessage());
        }
    }

    private void load() {
        if (!dataFile.isFile()) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (var element : root.getAsJsonArray("hidden")) {
                hidden.add(UUID.fromString(element.getAsString()));
            }
        } catch (Exception loadFailure) {
            Bukkit.getLogger().warning("[PlayerInfo] Could not load the opt-out list: "
                    + loadFailure.getMessage());
        }
    }
}
