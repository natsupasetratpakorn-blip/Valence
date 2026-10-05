package dev.m4sh3r.valence.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Small wrapper around the region aware schedulers so the plugin runs the same on Paper and Folia.
 */
public final class Tasks {

    private static Plugin plugin;

    private Tasks() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    public static void entity(Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduled -> task.run(), null);
    }

    public static void entityLater(Entity entity, Runnable task, long ticks) {
        entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, Math.max(1, ticks));
    }

    public static void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public static void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static void asyncLater(Runnable task, long delay, TimeUnit unit) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), delay, unit);
    }

    public static ScheduledTask asyncRepeating(Runnable task, long period, TimeUnit unit) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(), period, period, unit);
    }

    public static void cancelAll() {
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
