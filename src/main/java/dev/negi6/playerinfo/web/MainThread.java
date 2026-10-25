/*
 * PlayerInfo - Paper statistics & PvP fight summaries
 * Copyright (c) 2026 Mayank Negi (AFKZ Studios)
 * Licensed under the MIT License. See the LICENSE file for details.
 */

package dev.negi6.playerinfo.web;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Runs a task on the main server thread and waits for the result, so HTTP
 * threads can safely read Bukkit data. Falls back to direct execution when
 * already on the main thread.
 */
public final class MainThread {

    private MainThread() {
    }

    public static <T> T call(JavaPlugin plugin, Supplier<T> task, long timeoutMs) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            return task.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(task.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            }
        });
        return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
