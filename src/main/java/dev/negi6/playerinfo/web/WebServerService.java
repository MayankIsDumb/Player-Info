/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.negi6.playerinfo.stats.FightTracker;
import dev.negi6.playerinfo.stats.LeaderboardService;
import dev.negi6.playerinfo.stats.StatsGui;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The built-in website: serves the bundled dashboard and a read-only JSON
 * API at http://&lt;server-ip&gt;:&lt;port&gt;. HTTP threads only read
 * snapshots and files — game data is fetched through {@link MainThread}.
 */
public final class WebServerService {

    private final JavaPlugin plugin;
    private final StatsGui gui;
    private final FightTracker fightTracker;
    private final LeaderboardService leaderboards;
    private final dev.negi6.playerinfo.stats.OptOutStore optOut;
    private final dev.negi6.playerinfo.stats.PlayerDataStore playerData;
    private final dev.negi6.playerinfo.stats.PlaytimeRanks playtimeRanks;
    private final SnapshotCache snapshot;
    private final Map<String, long[]> rateBuckets = new java.util.concurrent.ConcurrentHashMap<>();
    private com.sun.net.httpserver.HttpServer server;
    private java.util.concurrent.ExecutorService executor;

    public WebServerService(JavaPlugin plugin, StatsGui gui, FightTracker fightTracker,
                            LeaderboardService leaderboards, dev.negi6.playerinfo.stats.OptOutStore optOut,
                            dev.negi6.playerinfo.stats.PlayerDataStore playerData,
                            dev.negi6.playerinfo.stats.PlaytimeRanks playtimeRanks) {
        this.plugin = plugin;
        this.gui = gui;
        this.fightTracker = fightTracker;
        this.leaderboards = leaderboards;
        this.optOut = optOut;
        this.playerData = playerData;
        this.playtimeRanks = playtimeRanks;
        this.snapshot = new SnapshotCache(plugin);
    }

    public void start() {
        try {
            ConfigurationSection web = plugin.getConfig().getConfigurationSection("web");
            int port = web == null ? 8080 : web.getInt("port", 8080);
            String bind = web == null ? "0.0.0.0" : web.getString("bind", "0.0.0.0");
            server = com.sun.net.httpserver.HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName(bind), port), 0);
            server.createContext("/", this::handle);
            executor = Executors.newFixedThreadPool(4, task -> {
                Thread thread = new Thread(task, "PlayerInfo-Web");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            snapshot.start();
            plugin.getLogger().info("Website running at http://" + bind + ":" + port);
        } catch (Exception startFailure) {
            plugin.getLogger().severe("[PlayerInfo] Could not start the website: "
                    + startFailure.getMessage());
            server = null;
        }
    }

    public void stop() {
        snapshot.stop();
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        try {
            if (path.startsWith("/api/")) {
                api(exchange, path);
                return;
            }
            switch (path) {
                case "/app.js" -> serveResource(exchange, "web/app.js",
                        "application/javascript; charset=utf-8");
                case "/style.css" -> serveResource(exchange, "web/style.css",
                        "text/css; charset=utf-8");
                default -> serveResource(exchange, "web/index.html",
                        "text/html; charset=utf-8");
            }
        } catch (Exception handlerFailure) {
            plugin.getLogger().warning("[PlayerInfo] Web error on " + path + ": " + handlerFailure);
            try {
                respond(exchange, 500, "{\"error\":\"internal error\"}");
            } catch (Exception ignored) {
                // connection already gone
            }
        }
    }

    private void api(HttpExchange exchange, String path) throws Exception {
        if (!allowRate(exchange)) {
            respond(exchange, 429, "{\"error\":\"rate limited\"}");
            return;
        }
        if (path.equals("/api/server")) {
            respond(exchange, 200, WebJson.serverJson(snapshot).toString());
            return;
        }
        if (path.equals("/api/meta")) {
            respond(exchange, 200, WebJson.metaJson(plugin, leaderboards).toString());
            return;
        }
        if (path.equals("/api/fights")) {
            boolean publicFights = configFlag("public-fights", true);
            respond(exchange, 200, WebJson.fightsJson(fightTracker, optOut, publicFights).toString());
            return;
        }
        if (path.equals("/api/search")) {
            String query = queryParam(exchange, "query");
            respond(exchange, 200, WebJson.searchJson(leaderboards, query, 10).toString());
            return;
        }
        if (path.startsWith("/api/fights/")) {
            int id;
            try {
                id = Integer.parseInt(path.substring("/api/fights/".length()));
            } catch (NumberFormatException badId) {
                respond(exchange, 400, "{\"error\":\"bad fight id\"}");
                return;
            }
            FightTracker.Fight fight = fightTracker.finished(id);
            if (fight == null) {
                respond(exchange, 404, "{\"error\":\"unknown fight\"}");
                return;
            }
            respond(exchange, 200, WebJson.fightJson(fight).toString());
            return;
        }
        if (path.startsWith("/api/player/")) {
            String name = URLDecoder.decode(path.substring("/api/player/".length()),
                    StandardCharsets.UTF_8);
            com.google.gson.JsonObject json = WebJson.playerJson(plugin, gui, name,
                    optOut, playerData, playtimeRanks);
            if (json == null) {
                respond(exchange, 404, "{\"error\":\"unknown player\"}");
                return;
            }
            respond(exchange, 200, json.toString());
            return;
        }
        if (path.startsWith("/api/leaderboard/")) {
            String category = URLDecoder.decode(path.substring("/api/leaderboard/".length()),
                    StandardCharsets.UTF_8);
            boolean weekly = "weekly".equals(queryParam(exchange, "period"));
            com.google.gson.JsonObject json = WebJson.leaderboardJson(leaderboards, category, 50, weekly);
            if (json == null) {
                respond(exchange, 404, "{\"error\":\"unknown category\"}");
                return;
            }
            respond(exchange, 200, json.toString());
            return;
        }
        respond(exchange, 404, "{\"error\":\"not found\"}");
    }

    private boolean allowRate(HttpExchange exchange) {
        long perMinute = configLong("rate-limit-per-minute", 120);
        if (perMinute <= 0) {
            return true;
        }
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        long[] bucket = rateBuckets.computeIfAbsent(ip, key -> new long[]{now, perMinute});
        synchronized (bucket) {
            if (now - bucket[0] >= 60_000) {
                bucket[0] = now;
                bucket[1] = perMinute;
            }
            if (bucket[1] <= 0) {
                return false;
            }
            bucket[1]--;
            return true;
        }
    }

    private boolean configFlag(String key, boolean def) {
        ConfigurationSection web = plugin.getConfig().getConfigurationSection("web");
        return web == null ? def : web.getBoolean(key, def);
    }

    private long configLong(String key, long def) {
        ConfigurationSection web = plugin.getConfig().getConfigurationSection("web");
        return web == null ? def : web.getLong(key, def);
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return "";
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(key)) {
                return URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private void serveResource(HttpExchange exchange, String resource, String contentType)
            throws IOException {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                respond(exchange, 404, "not found");
                return;
            }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
