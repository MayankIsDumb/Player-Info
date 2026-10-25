/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;

/**
 * Advancement completion for online (live API) and offline (advancements
 * file) players. Recipe advancements are excluded so the counts match the
 * in-game advancements screen.
 */
public final class AdvancementSource {

    private AdvancementSource() {
    }

    public record Summary(int done, int total, List<String> remainingKeys) {}

    public static Summary of(StatsSource target) {
        Set<String> doneKeys = target.isOnline()
                ? doneFromApi(target.onlinePlayer())
                : doneFromFile(target.uuid());

        int total = 0;
        int done = 0;
        List<String> remaining = new ArrayList<>();
        Iterator<Advancement> advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            String key = advancement.getKey().toString();
            if (key.contains("/recipes/")) {
                continue;
            }
            total++;
            if (doneKeys.contains(key)) {
                done++;
            } else {
                remaining.add(key);
            }
        }
        return new Summary(done, total, remaining);
    }

    private static Set<String> doneFromApi(Player player) {
        Set<String> done = new HashSet<>();
        Iterator<Advancement> advancements = Bukkit.advancementIterator();
        while (advancements.hasNext()) {
            Advancement advancement = advancements.next();
            if (player.getAdvancementProgress(advancement).isDone()) {
                done.add(advancement.getKey().toString());
            }
        }
        return done;
    }

    private static Set<String> doneFromFile(UUID uuid) {
        Path worldPath = Bukkit.getWorlds().isEmpty()
                ? null
                : Bukkit.getWorlds().get(0).getWorldFolder().toPath();
        if (worldPath == null) {
            return Set.of();
        }
        // 26.x keeps advancements under <world>/players/advancements/; older
        // worlds used <world>/advancements/.
        for (Path dir : List.of(worldPath.resolve("players").resolve("advancements"),
                worldPath.resolve("advancements"))) {
            Path file = dir.resolve(uuid + ".json");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Set<String> done = new HashSet<>();
                for (var entry : root.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        JsonObject progress = entry.getValue().getAsJsonObject();
                        if (progress.has("done") && progress.get("done").isJsonPrimitive()
                                && progress.get("done").getAsBoolean()) {
                            done.add(entry.getKey());
                        }
                    }
                }
                return done;
            } catch (Exception brokenAdvancementFile) {
                Bukkit.getLogger().warning("[PlayerInfo] Could not read advancements for "
                        + uuid + ": " + brokenAdvancementFile.getMessage());
                return Set.of();
            }
        }
        return Set.of();
    }
}
