/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.negi6.playerinfo.PlayerInfoPlugin;
import dev.negi6.playerinfo.stats.DeathStore;
import dev.negi6.playerinfo.stats.FightTracker;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Posts fights, completed challenges, playtime milestones and deaths to a
 * Discord webhook, and announces playtime milestones in-game. Everything is
 * config-gated; nothing is sent while the webhook URL is empty.
 */
public final class DiscordService implements Listener {

    private final PlayerInfoPlugin plugin;
    private final HttpClient http = HttpClient.newHttpClient();
    private final MilestoneStore milestones;
    private String webhookUrl = "";
    private boolean discordEnabled;
    private boolean postFights = true;
    private boolean postChallenges = true;
    private boolean postMilestones = true;
    private boolean postDeaths = false;
    private List<Long> milestoneHours = List.of(10L, 50L, 100L);
    private BukkitTask milestoneTask;

    public DiscordService(PlayerInfoPlugin plugin, File dataFolder) {
        this.plugin = plugin;
        this.milestones = new MilestoneStore(dataFolder);
    }

    public void start() {
        applySettings();
        if (enabled() && postChallenges) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
        // In-game milestones are independent of the Discord webhook; the
        // webhook post is gated separately inside postMilestone().
        if (!milestoneHours.isEmpty()) {
            milestoneTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    this::checkMilestones, 20L * 60, 20L * 60);
        }
    }

    public void stop() {
        if (milestoneTask != null) {
            milestoneTask.cancel();
            milestoneTask = null;
        }
    }

    /** Re-reads config and re-registers listeners/tasks (used by reload). */
    public void reload() {
        stop();
        HandlerList.unregisterAll(this);
        start();
    }

    public void applySettings() {
        ConfigurationSection discord = plugin.getConfig().getConfigurationSection("discord");
        discordEnabled = discord != null && discord.getBoolean("enabled", false);
        webhookUrl = discord == null ? "" : discord.getString("webhook-url", "");
        postFights = discord == null || discord.getBoolean("post-fights", true);
        postChallenges = discord == null || discord.getBoolean("post-challenges", true);
        postMilestones = discord == null || discord.getBoolean("post-milestones", true);
        postDeaths = discord != null && discord.getBoolean("post-deaths", false);
        List<Long> hours = new ArrayList<>();
        for (long value : plugin.getConfig().getLongList("milestones-hours")) {
            if (value > 0) {
                hours.add(value);
            }
        }
        milestoneHours = hours;
    }

    private boolean enabled() {
        return discordEnabled && webhookUrl != null && !webhookUrl.isEmpty();
    }

    public void postFight(FightTracker.Fight fight) {
        if (!enabled() || !postFights) {
            return;
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⚔ PvP Fight #" + fight.id());
        embed.addProperty("description", "**" + fight.name(fight.winner())
                + "** defeated **" + fight.name(fight.loser()) + "**");
        embed.addProperty("color", 0x38BDF8);
        embed.addProperty("timestamp", Instant.ofEpochMilli(fight.startedAt()).toString());
        FightTracker.Side winner = fight.side(fight.winner());
        JsonArray fields = new JsonArray();
        addField(fields, "Winner damage", String.format(Locale.ROOT, "%.1f pts",
                winner.damageDealt / 10));
        addField(fields, "Winner crits", String.valueOf(winner.crits));
        addField(fields, "Winner best combo", String.valueOf(winner.bestCombo));
        embed.add("fields", fields);
        send(embed);
    }

    public void postMilestone(Player player, long hours) {
        if (!enabled() || !postMilestones) {
            return;
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "⭐ Playtime milestone");
        embed.addProperty("description", "**" + player.getName()
                + "** just hit **" + hours + " hours** of playtime!");
        embed.addProperty("color", 0xFBBF24);
        send(embed);
    }

    public void postDeath(String playerName, DeathStore.DeathRecord record) {
        if (!enabled() || !postDeaths) {
            return;
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "💀 Death");
        embed.addProperty("description", "**" + playerName + "** — " + record.cause());
        embed.addProperty("color", 0x9A9AA3);
        JsonArray fields = new JsonArray();
        addField(fields, "Location", record.world() + " " + record.x() + " " + record.y() + " " + record.z());
        embed.add("fields", fields);
        send(embed);
    }

    private void onChallenge(Player player, AdvancementDisplay display) {
        String title = PlainTextComponentSerializer.plainText().serialize(display.title());
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🏆 Challenge completed");
        embed.addProperty("description", "**" + player.getName() + "** completed **" + title + "**");
        embed.addProperty("color", 0xF1C40F);
        send(embed);
    }

    private void checkMilestones() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            for (long hours : milestoneHours) {
                long required = hours * 72000L; // 20 ticks × 3600 s
                if (ticks >= required && !milestones.announced(player.getUniqueId(), hours)) {
                    milestones.mark(player.getUniqueId(), hours);
                    Bukkit.broadcast(plugin.messages().raw("broadcast.milestone",
                            "player", player.getName(),
                            "hours", String.valueOf(hours)));
                    postMilestone(player, hours);
                }
            }
        }
    }

    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!enabled() || !postChallenges) {
            return;
        }
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null || display.frame() != AdvancementDisplay.Frame.CHALLENGE) {
            return;
        }
        onChallenge(event.getPlayer(), display);
    }

    private void addField(JsonArray fields, String name, String value) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", true);
        fields.add(field);
    }

    private void send(JsonObject embed) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", "PlayerInfo");
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .whenComplete((response, failure) -> {
                        if (failure != null) {
                            plugin.getLogger().warning("[PlayerInfo] Discord webhook failed: "
                                    + failure.getMessage());
                        }
                    });
        } catch (Exception webhookFailure) {
            plugin.getLogger().warning("[PlayerInfo] Discord webhook failed: "
                    + webhookFailure.getMessage());
        }
    }
}
