/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.messages;

import java.io.File;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * All player-facing chat messages, loaded from messages.yml and rendered with
 * MiniMessage. Placeholders are passed as key/value pairs ({@code "player",
 * name}) and inserted with {@code <player>} tags in the templates; values are
 * inserted unparsed so player-provided text can never inject formatting.
 * Missing keys fall back to the built-in defaults.
 */
public final class Messages {

    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("prefix", "<gray>[</gray><gradient:#22c55e:#38bdf8><bold>PlayerInfo</bold></gradient><gray>]</gray> "),
            Map.entry("join.welcome", "<gray>Welcome! Try /playerinfo to inspect players and /stats for your statistics.</gray>"),
            Map.entry("general.reload-done", "<green>PlayerInfo configuration reloaded.</green>"),
            Map.entry("general.no-permission", "<red>You don't have permission to do that.</red>"),
            Map.entry("general.players-only", "<red>Only in-game players can use this command.</red>"),
            Map.entry("general.usage-playerinfo", "<red>Usage: /playerinfo \\<player\\></red>"),
            Map.entry("general.usage-stats-compare", "<red>Usage: /stats compare \\<player\\></red>"),
            Map.entry("general.unknown-offline-player", "<red>No player named '<white><name></white>' has played on this server.</red>"),
            Map.entry("general.unknown-online-player", "<red>No online player named '<white><name></white>'.</red>"),
            Map.entry("general.stats-no-permission", "<red>You don't have permission to view statistics.</red>"),
            Map.entry("general.compare-no-permission", "<red>You don't have permission to compare with other players.</red>"),
            Map.entry("general.self-stats-only", "<red>You can only view your own statistics.</red>"),
            Map.entry("stats.setbio-updated", "<green>Bio updated.</green>"),
            Map.entry("stats.setbio-cleared", "<gray>Bio cleared.</gray>"),
            Map.entry("stats.setfav-updated", "<green>Favorite updated.</green>"),
            Map.entry("stats.setfav-cleared", "<gray>Favorite cleared.</gray>"),
            Map.entry("stats.setbase-saved", "<green>Base location saved: <white><base></white></green>"),
            Map.entry("fight.slain", "<gray>was slain by</gray>"),
            Map.entry("fight.view-label", "<aqua><underlined>[View Fight]</underlined></aqua>"),
            Map.entry("deathlog.chat", "<gray>Death — </gray><gold><time></gold><gray> in <world> at </gray><aqua><underlined><coords></underlined></aqua>"),
            Map.entry("broadcast.rank", "<gold>⭐ <player> is now a <rank>! (<hours>h)</gold>"),
            Map.entry("broadcast.milestone", "<gold>⭐ <player> just hit <hours> hours of playtime!</gold>"));

    private final JavaPlugin plugin;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, String> file = new HashMap<>();
    private String prefix = "";

    /** Older messages.yml files used {name} instead of MiniMessage's <name>. */
    private static final Pattern LEGACY_PLACEHOLDER =
            Pattern.compile("\\{(name|base|time|world|coords|player|rank|hours)}");

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File fileOnDisk = new File(plugin.getDataFolder(), "messages.yml");
        if (!fileOnDisk.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        this.file.clear();
        if (fileOnDisk.isFile()) {
            try (Reader reader = Files.newBufferedReader(fileOnDisk.toPath(), StandardCharsets.UTF_8)) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(fileOnDisk);
                for (String key : config.getKeys(true)) {
                    if (config.isString(key)) {
                        this.file.put(key, config.getString(key));
                    }
                }
            } catch (Exception loadFailure) {
                plugin.getLogger().warning("[PlayerInfo] Could not load messages.yml: "
                        + loadFailure.getMessage());
            }
        }
        prefix = file.getOrDefault("prefix", DEFAULTS.get("prefix"));
    }

    /** Chat message with the configured prefix. */
    public Component chat(String key, String... placeholders) {
        return withPrefix(key, placeholders);
    }

    /** Message without the prefix (in-game broadcasts, death messages). */
    public Component raw(String key, String... placeholders) {
        return render(key, false, placeholders);
    }

    private Component withPrefix(String key, String... placeholders) {
        Component message = render(key, false, placeholders);
        if (prefix == null || prefix.isEmpty()) {
            return message;
        }
        return mini.deserialize(prefix).append(message);
    }

    private Component render(String key, boolean withPrefix, String... placeholders) {
        String template = file.getOrDefault(key, DEFAULTS.get(key));
        if (template == null) {
            return Component.text(key);
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            values.put(placeholders[i], placeholders[i + 1]);
        }
        return renderTemplate(template, values);
    }

    /** Renders one template; values are inserted unparsed so they cannot inject tags. */
    static Component renderTemplate(String template, Map<String, String> values) {
        // Accept messages.yml files created before the switch to MiniMessage tags.
        String normalized = LEGACY_PLACEHOLDER.matcher(template).replaceAll("<$1>");
        List<TagResolver> resolvers = new ArrayList<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            resolvers.add(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        return MiniMessage.miniMessage().deserialize(normalized,
                resolvers.toArray(new TagResolver[0]));
    }
}
