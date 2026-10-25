/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.stats;

import java.nio.file.Path;

/**
 * The main world folder, captured once on the main thread at startup.
 * Background threads (stats file scans, web requests) must use this instead
 * of Bukkit.getWorlds(), which is not safe off the main thread.
 */
public final class ServerData {

    private static volatile Path worldPath;

    private ServerData() {
    }

    public static void init(Path path) {
        worldPath = path;
    }

    public static Path worldPath() {
        return worldPath;
    }
}
