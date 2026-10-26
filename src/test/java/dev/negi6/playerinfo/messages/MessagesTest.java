/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.messages;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessagesTest {

    /** Placeholder tags that the plugin passes to {@link Messages}. */
    private static final Pattern PLACEHOLDER =
            Pattern.compile("(?<!\\\\)<(name|base|time|world|coords|player|rank|hours)>");

    private static final Map<String, String> VALUES = Map.of(
            "name", "Steve",
            "base", "10 64 -20 (world)",
            "time", "12:00",
            "world", "world",
            "coords", "10 64 -20",
            "player", "Steve",
            "rank", "Veteran",
            "hours", "50");

    private static YamlConfiguration messages() throws Exception {
        try (Reader reader = new InputStreamReader(
                MessagesTest.class.getClassLoader().getResourceAsStream("messages.yml"),
                StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    @Test
    void templatesUseMiniMessagePlaceholders() throws Exception {
        YamlConfiguration config = messages();
        assertNotNull(config.getString("prefix"));
        assertFalse(config.getKeys(true).isEmpty());

        for (String key : config.getKeys(true)) {
            String template = config.getString(key);
            if (template == null) {
                continue;
            }
            assertFalse(template.matches("(?s).*\\{[a-z][a-z0-9_]*\\}.*"),
                    key + " still uses a literal {placeholder}: " + template);
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                assertTrue(VALUES.containsKey(matcher.group(1)),
                        key + " uses an unknown placeholder <" + matcher.group(1) + ">");
            }
        }
    }

    @Test
    void placeholdersRenderAsValues() throws Exception {
        String template = messages().getString("general.unknown-offline-player");
        assertNotNull(template);
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(Messages.renderTemplate(template, VALUES));
        assertTrue(plain.contains("No player named 'Steve'"), plain);
        assertFalse(plain.contains("<name>"), plain);
    }

    @Test
    void legacyBracePlaceholdersStillRender() {
        String template = "<red>No player named '<white>{name}</white>'.</red>";
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(Messages.renderTemplate(template, Map.of("name", "Steve")));
        assertTrue(plain.contains("Steve"), plain);
        assertFalse(plain.contains("{name}"), plain);
    }

    @Test
    void playerInputIsInsertedUnparsed() {
        String template = "<red>No player named '<white><name></white>'.</red>";
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(Messages.renderTemplate(template, Map.of("name", "<bold>evil</bold>")));
        assertTrue(plain.contains("<bold>evil</bold>"), plain);
    }
}
